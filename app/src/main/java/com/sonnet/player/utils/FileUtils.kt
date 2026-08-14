package com.sonnet.player.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Utility class for video file operations
 * Handles scanning, format detection, and thumbnail extraction
 */
object FileUtils {

    /**
     * Represents a video file with metadata
     */
    data class VideoFile(
        val id: Long,
        val uri: Uri,
        val displayName: String,
        val path: String,
        val size: Long,
        val duration: Long,
        val mimeType: String,
        val dateAdded: Long,
        val dateModified: Long,
        val width: Int = 0,
        val height: Int = 0
    )

    /**
     * Supported video formats
     */
    private val SUPPORTED_VIDEO_FORMATS = setOf(
        "video/mp4",
        "video/x-matroska",
        "video/avi",
        "video/x-msvideo",
        "video/quicktime",
        "video/x-flv",
        "video/3gpp",
        "video/webm",
        "video/mpeg"
    )

    /**
     * Scan all video files from MediaStore
     * Uses optimized projection to minimize memory usage
     */
    suspend fun scanVideoFiles(context: Context): List<VideoFile> = withContext(Dispatchers.IO) {
        val videoFiles = mutableListOf<VideoFile>()

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        val cursor: Cursor? = context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val mimeTypeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dateAddedColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModifiedColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val widthColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val path = it.getString(pathColumn)
                val size = it.getLong(sizeColumn)
                val duration = it.getLong(durationColumn)
                val mimeType = it.getString(mimeTypeColumn)
                val dateAdded = it.getLong(dateAddedColumn)
                val dateModified = it.getLong(dateModifiedColumn)
                val width = it.getInt(widthColumn)
                val height = it.getInt(heightColumn)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                videoFiles.add(
                    VideoFile(
                        id = id,
                        uri = contentUri,
                        displayName = name,
                        path = path,
                        size = size,
                        duration = duration,
                        mimeType = mimeType,
                        dateAdded = dateAdded,
                        dateModified = dateModified,
                        width = width,
                        height = height
                    )
                )
            }
        }

        videoFiles
    }

    /**
     * Scan videos from a specific directory
     */
    suspend fun scanVideosFromDirectory(context: Context, directoryPath: String): List<VideoFile> =
        withContext(Dispatchers.IO) {
            val allVideos = scanVideoFiles(context)
            allVideos.filter { it.path.startsWith(directoryPath) }
        }

    /**
     * Detect video format from file extension and MIME type
     */
    fun detectVideoFormat(file: VideoFile): String {
        return when {
            file.mimeType.contains("mp4") -> "MP4"
            file.mimeType.contains("matroska") || file.displayName.endsWith(".mkv", ignoreCase = true) -> "MKV"
            file.mimeType.contains("avi") -> "AVI"
            file.mimeType.contains("quicktime") || file.displayName.endsWith(".mov", ignoreCase = true) -> "MOV"
            file.mimeType.contains("flv") -> "FLV"
            file.mimeType.contains("3gpp") -> "3GP"
            file.mimeType.contains("webm") -> "WEBM"
            file.mimeType.contains("mpeg") -> "MPEG"
            else -> file.displayName.substringAfterLast('.', "UNKNOWN").uppercase()
        }
    }

    /**
     * Check if a video format is supported
     */
    fun isFormatSupported(mimeType: String): Boolean {
        return SUPPORTED_VIDEO_FORMATS.contains(mimeType.lowercase())
    }

    /**
     * Extract thumbnail from video file
     * Optimized for memory efficiency
     */
    suspend fun extractThumbnail(
        context: Context,
        videoUri: Uri,
        width: Int = 320,
        height: Int = 180
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use optimized thumbnail loader on Android 10+
                context.contentResolver.loadThumbnail(
                    videoUri,
                    Size(width, height),
                    null
                )
            } else {
                // Fallback to MediaMetadataRetriever
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, videoUri)
                    retriever.getFrameAtTime(
                        0,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )?.let { bitmap ->
                        // Scale bitmap if needed
                        scaleBitmap(bitmap, width, height)
                    }
                } finally {
                    retriever.release()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Extract thumbnail at specific time position
     */
    suspend fun extractThumbnailAtTime(
        context: Context,
        videoUri: Uri,
        timeUs: Long,
        width: Int = 320,
        height: Int = 180
    ): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            retriever.getFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST
            )?.let { bitmap ->
                scaleBitmap(bitmap, width, height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            retriever.release()
        }
    }

    /**
     * Validate URI accessibility
     */
    suspend fun validateUri(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get video metadata using MediaMetadataRetriever
     */
    suspend fun getVideoMetadata(context: Context, uri: Uri): Map<String, String> =
        withContext(Dispatchers.IO) {
            val metadata = mutableMapOf<String, String>()
            val retriever = MediaMetadataRetriever()

            try {
                retriever.setDataSource(context, uri)

                // Extract common metadata
                metadata["duration"] = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: "0"
                metadata["width"] = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "0"
                metadata["height"] = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "0"
                metadata["bitrate"] = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE) ?: "0"
                metadata["mime_type"] = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
                metadata["rotation"] = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION) ?: "0"

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                retriever.release()
            }

            metadata
        }

    /**
     * Check if file exists and is accessible
     */
    suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(path).exists()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Format file size to human-readable string
     */
    fun formatFileSize(size: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024

        return when {
            size >= gb -> String.format("%.2f GB", size / gb)
            size >= mb -> String.format("%.2f MB", size / mb)
            size >= kb -> String.format("%.2f KB", size / kb)
            else -> "$size B"
        }
    }

    /**
     * Format duration to human-readable string (HH:MM:SS)
     */
    fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = durationMs / (1000 * 60 * 60)

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Scale bitmap to target dimensions while maintaining aspect ratio
     */
    private fun scaleBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetAspectRatio = targetWidth.toFloat() / targetHeight.toFloat()

        val (scaledWidth, scaledHeight) = if (aspectRatio > targetAspectRatio) {
            Pair(targetWidth, (targetWidth / aspectRatio).toInt())
        } else {
            Pair((targetHeight * aspectRatio).toInt(), targetHeight)
        }

        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true).also {
            if (it != bitmap) {
                bitmap.recycle()
            }
        }
    }
}
