package com.sonnet.player.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * LRU cache for video thumbnails
 * Provides memory-efficient bitmap handling with background generation
 */
class ThumbnailCache(
    private val context: Context,
    maxMemoryMb: Int = 10
) {
    private val maxMemoryBytes = maxMemoryMb * 1024 * 1024

    // LRU cache with size based on bitmap byte count
    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(maxMemoryBytes) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (evicted && oldValue != newValue) {
                // Recycle bitmap when evicted from cache
                oldValue.recycle()
            }
        }
    }

    // Track ongoing loading operations to prevent duplicate requests
    private val loadingTasks = ConcurrentHashMap<String, Deferred<Bitmap?>>()

    // Coroutine scope for background operations
    private val cacheScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Get thumbnail from cache or load asynchronously
     */
    suspend fun getThumbnail(
        videoUri: Uri,
        width: Int = 320,
        height: Int = 180
    ): Bitmap? {
        val key = generateKey(videoUri, width, height)

        // Check cache first
        cache.get(key)?.let { return it }

        // Check if already loading
        loadingTasks[key]?.let {
            return try {
                it.await()
            } catch (e: Exception) {
                null
            }
        }

        // Start new loading task
        val deferred = cacheScope.async {
            try {
                FileUtils.extractThumbnail(context, videoUri, width, height)?.also { bitmap ->
                    cache.put(key, bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                loadingTasks.remove(key)
            }
        }

        loadingTasks[key] = deferred
        return deferred.await()
    }

    /**
     * Get thumbnail at specific time position
     */
    suspend fun getThumbnailAtTime(
        videoUri: Uri,
        timeUs: Long,
        width: Int = 320,
        height: Int = 180
    ): Bitmap? {
        val key = generateTimeKey(videoUri, timeUs, width, height)

        // Check cache first
        cache.get(key)?.let { return it }

        // Check if already loading
        loadingTasks[key]?.let {
            return try {
                it.await()
            } catch (e: Exception) {
                null
            }
        }

        // Start new loading task
        val deferred = cacheScope.async {
            try {
                FileUtils.extractThumbnailAtTime(context, videoUri, timeUs, width, height)?.also { bitmap ->
                    cache.put(key, bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                loadingTasks.remove(key)
            }
        }

        loadingTasks[key] = deferred
        return deferred.await()
    }

    /**
     * Preload thumbnail in background
     */
    fun preloadThumbnail(
        videoUri: Uri,
        width: Int = 320,
        height: Int = 180
    ) {
        val key = generateKey(videoUri, width, height)

        // Skip if already cached or loading
        if (cache.get(key) != null || loadingTasks.containsKey(key)) {
            return
        }

        val deferred = cacheScope.async {
            try {
                FileUtils.extractThumbnail(context, videoUri, width, height)?.also { bitmap ->
                    cache.put(key, bitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                loadingTasks.remove(key)
            }
        }

        loadingTasks[key] = deferred
    }

    /**
     * Preload multiple thumbnails in background
     */
    fun preloadThumbnails(
        videoUris: List<Uri>,
        width: Int = 320,
        height: Int = 180,
        maxConcurrent: Int = 3
    ) {
        cacheScope.launch {
            videoUris.chunked(maxConcurrent).forEach { chunk ->
                chunk.map { uri ->
                    async {
                        val key = generateKey(uri, width, height)
                        if (cache.get(key) == null && !loadingTasks.containsKey(key)) {
                            try {
                                FileUtils.extractThumbnail(context, uri, width, height)?.also { bitmap ->
                                    cache.put(key, bitmap)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    /**
     * Check if thumbnail is cached
     */
    fun isCached(videoUri: Uri, width: Int = 320, height: Int = 180): Boolean {
        val key = generateKey(videoUri, width, height)
        return cache.get(key) != null
    }

    /**
     * Remove thumbnail from cache
     */
    fun removeThumbnail(videoUri: Uri, width: Int = 320, height: Int = 180) {
        val key = generateKey(videoUri, width, height)
        cache.remove(key)?.recycle()
    }

    /**
     * Clear all cached thumbnails
     */
    fun clear() {
        // Cancel all loading tasks
        loadingTasks.values.forEach { it.cancel() }
        loadingTasks.clear()

        // Clear and recycle all bitmaps
        cache.evictAll()
    }

    /**
     * Get cache statistics
     */
    fun getStats(): CacheStats {
        return CacheStats(
            size = cache.size(),
            maxSize = cache.maxSize(),
            hitCount = cache.hitCount(),
            missCount = cache.missCount(),
            evictionCount = cache.evictionCount(),
            putCount = cache.putCount(),
            loadingCount = loadingTasks.size
        )
    }

    /**
     * Trim cache to specified percentage of max size
     */
    fun trimToSize(percentage: Int) {
        val targetSize = (maxMemoryBytes * (percentage / 100f)).toInt()
        cache.trimToSize(targetSize)
    }

    /**
     * Generate cache key from URI and dimensions
     */
    private fun generateKey(uri: Uri, width: Int, height: Int): String {
        return "${uri.toString()}_${width}x${height}"
    }

    /**
     * Generate cache key for time-specific thumbnail
     */
    private fun generateTimeKey(uri: Uri, timeUs: Long, width: Int, height: Int): String {
        return "${uri.toString()}_${timeUs}_${width}x${height}"
    }

    /**
     * Release resources
     */
    fun release() {
        cacheScope.cancel()
        clear()
    }

    /**
     * Cache statistics data class
     */
    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val hitCount: Int,
        val missCount: Int,
        val evictionCount: Int,
        val putCount: Int,
        val loadingCount: Int
    ) {
        val hitRate: Float
            get() = if (hitCount + missCount > 0) {
                hitCount.toFloat() / (hitCount + missCount)
            } else 0f

        val usagePercentage: Float
            get() = if (maxSize > 0) {
                (size.toFloat() / maxSize) * 100
            } else 0f

        override fun toString(): String {
            return """
                Cache Statistics:
                - Size: ${formatBytes(size)} / ${formatBytes(maxSize)} (${String.format("%.1f", usagePercentage)}%)
                - Hit Rate: ${String.format("%.2f", hitRate * 100)}% ($hitCount hits, $missCount misses)
                - Evictions: $evictionCount
                - Total Puts: $putCount
                - Currently Loading: $loadingCount
            """.trimIndent()
        }

        private fun formatBytes(bytes: Int): String {
            return when {
                bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024f * 1024f))
                bytes >= 1024 -> String.format("%.2f KB", bytes / 1024f)
                else -> "$bytes B"
            }
        }
    }

    companion object {
        /**
         * Calculate recommended cache size based on available memory
         */
        fun getRecommendedCacheSize(context: Context): Int {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryClass = activityManager.memoryClass

            // Use 1/8 of available memory for cache, max 20MB
            return minOf(memoryClass / 8, 20)
        }

        /**
         * Create cache with recommended size
         */
        fun createWithRecommendedSize(context: Context): ThumbnailCache {
            val recommendedSize = getRecommendedCacheSize(context)
            return ThumbnailCache(context, recommendedSize)
        }
    }
}
