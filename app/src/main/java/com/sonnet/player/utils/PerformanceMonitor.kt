package com.sonnet.player.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import com.sonnet.player.BuildConfig
import java.util.*
import kotlin.math.roundToInt

/**
 * Performance monitoring utility (debug only)
 * Tracks frame drops, memory usage, and provides performance metrics
 */
class PerformanceMonitor(
    private val context: Context,
    private val enabled: Boolean = BuildConfig.DEBUG
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private val _metrics = MutableStateFlow(PerformanceMetrics())
    val metrics: StateFlow<PerformanceMetrics> = _metrics.asStateFlow()

    private var isMonitoring = false
    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameTimeNanos = 0L
    private var frameCount = 0
    private var droppedFrames = 0

    // Performance thresholds
    private val targetFrameTimeMs = 16.67f // 60 FPS
    private val frameDropThresholdMs = 32f // ~30 FPS

    /**
     * Performance metrics data class
     */
    data class PerformanceMetrics(
        val fps: Float = 0f,
        val averageFrameTime: Float = 0f,
        val droppedFrameCount: Int = 0,
        val droppedFramePercentage: Float = 0f,
        val memoryUsageMb: Float = 0f,
        val memoryAvailableMb: Float = 0f,
        val memoryPercentage: Float = 0f,
        val nativeHeapSizeMb: Float = 0f,
        val nativeHeapAllocatedMb: Float = 0f,
        val cpuUsagePercentage: Float = 0f,
        val threadCount: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Performance warning level
     */
    enum class WarningLevel {
        NONE,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    /**
     * Performance warning
     */
    data class PerformanceWarning(
        val level: WarningLevel,
        val type: WarningType,
        val message: String,
        val value: Float,
        val threshold: Float
    )

    enum class WarningType {
        FRAME_DROP,
        MEMORY_HIGH,
        MEMORY_CRITICAL,
        CPU_HIGH
    }

    /**
     * Start performance monitoring
     */
    fun startMonitoring() {
        if (!enabled || isMonitoring) return

        isMonitoring = true
        startFrameMonitoring()
        startMemoryMonitoring()
    }

    /**
     * Stop performance monitoring
     */
    fun stopMonitoring() {
        if (!enabled || !isMonitoring) return

        isMonitoring = false
        stopFrameMonitoring()
    }

    /**
     * Start frame drop detection
     */
    private fun startFrameMonitoring() {
        frameCount = 0
        droppedFrames = 0
        lastFrameTimeNanos = 0L

        frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!isMonitoring) return

                if (lastFrameTimeNanos != 0L) {
                    val frameTimeMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000f

                    frameCount++

                    // Detect dropped frames
                    if (frameTimeMs > frameDropThresholdMs) {
                        val droppedCount = (frameTimeMs / targetFrameTimeMs).roundToInt() - 1
                        droppedFrames += droppedCount
                    }

                    // Update metrics every second
                    if (frameCount % 60 == 0) {
                        updateFrameMetrics(frameTimeMs)
                    }
                }

                lastFrameTimeNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        handler.post {
            Choreographer.getInstance().postFrameCallback(frameCallback!!)
        }
    }

    /**
     * Stop frame monitoring
     */
    private fun stopFrameMonitoring() {
        frameCallback?.let {
            handler.post {
                Choreographer.getInstance().removeFrameCallback(it)
            }
        }
        frameCallback = null
    }

    /**
     * Start memory usage monitoring
     */
    private fun startMemoryMonitoring() {
        scope.launch {
            while (isMonitoring) {
                updateMemoryMetrics()
                delay(1000) // Update every second
            }
        }
    }

    /**
     * Update frame metrics
     */
    private fun updateFrameMetrics(currentFrameTimeMs: Float) {
        val currentMetrics = _metrics.value

        val fps = if (currentFrameTimeMs > 0) 1000f / currentFrameTimeMs else 0f
        val droppedPercentage = if (frameCount > 0) {
            (droppedFrames.toFloat() / frameCount) * 100
        } else 0f

        _metrics.value = currentMetrics.copy(
            fps = fps,
            averageFrameTime = currentFrameTimeMs,
            droppedFrameCount = droppedFrames,
            droppedFramePercentage = droppedPercentage
        )
    }

    /**
     * Update memory metrics
     */
    private fun updateMemoryMetrics() {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
        val maxMemory = runtime.maxMemory() / (1024f * 1024f)
        val memoryPercentage = (usedMemory / maxMemory) * 100

        val nativeHeapSize = Debug.getNativeHeapSize() / (1024f * 1024f)
        val nativeHeapAllocated = Debug.getNativeHeapAllocatedSize() / (1024f * 1024f)

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val availableMemory = memoryInfo.availMem / (1024f * 1024f)

        val threadCount = Thread.activeCount()

        val cpuUsage = getCpuUsage()

        _metrics.value = _metrics.value.copy(
            memoryUsageMb = usedMemory,
            memoryAvailableMb = availableMemory,
            memoryPercentage = memoryPercentage,
            nativeHeapSizeMb = nativeHeapSize,
            nativeHeapAllocatedMb = nativeHeapAllocated,
            cpuUsagePercentage = cpuUsage,
            threadCount = threadCount,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Get CPU usage (approximation)
     */
    private fun getCpuUsage(): Float {
        return try {
            val pid = android.os.Process.myPid()
            val statFile = File("/proc/$pid/stat")
            if (statFile.exists()) {
                val stat = statFile.readText().split(" ")
                if (stat.size > 14) {
                    val utime = stat[13].toLongOrNull() ?: 0L
                    val stime = stat[14].toLongOrNull() ?: 0L
                    ((utime + stime).toFloat() / 100f)
                } else 0f
            } else 0f
        } catch (e: Exception) {
            0f
        }
    }

    /**
     * Get current warnings
     */
    fun getWarnings(): List<PerformanceWarning> {
        val warnings = mutableListOf<PerformanceWarning>()
        val current = _metrics.value

        // Frame drop warnings
        if (current.droppedFramePercentage > 20f) {
            warnings.add(
                PerformanceWarning(
                    level = WarningLevel.CRITICAL,
                    type = WarningType.FRAME_DROP,
                    message = "Critical frame drops detected",
                    value = current.droppedFramePercentage,
                    threshold = 20f
                )
            )
        } else if (current.droppedFramePercentage > 10f) {
            warnings.add(
                PerformanceWarning(
                    level = WarningLevel.HIGH,
                    type = WarningType.FRAME_DROP,
                    message = "High frame drops detected",
                    value = current.droppedFramePercentage,
                    threshold = 10f
                )
            )
        } else if (current.droppedFramePercentage > 5f) {
            warnings.add(
                PerformanceWarning(
                    level = WarningLevel.MEDIUM,
                    type = WarningType.FRAME_DROP,
                    message = "Moderate frame drops detected",
                    value = current.droppedFramePercentage,
                    threshold = 5f
                )
            )
        }

        // Memory warnings
        if (current.memoryPercentage > 90f) {
            warnings.add(
                PerformanceWarning(
                    level = WarningLevel.CRITICAL,
                    type = WarningType.MEMORY_CRITICAL,
                    message = "Critical memory usage",
                    value = current.memoryPercentage,
                    threshold = 90f
                )
            )
        } else if (current.memoryPercentage > 75f) {
            warnings.add(
                PerformanceWarning(
                    level = WarningLevel.HIGH,
                    type = WarningType.MEMORY_HIGH,
                    message = "High memory usage",
                    value = current.memoryPercentage,
                    threshold = 75f
                )
            )
        }

        // CPU warnings
        if (current.cpuUsagePercentage > 80f) {
            warnings.add(
                PerformanceWarning(
                    level = WarningLevel.HIGH,
                    type = WarningType.CPU_HIGH,
                    message = "High CPU usage",
                    value = current.cpuUsagePercentage,
                    threshold = 80f
                )
            )
        }

        return warnings
    }

    /**
     * Get formatted metrics report
     */
    fun getMetricsReport(): String {
        val current = _metrics.value
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        return buildString {
            appendLine("Performance Metrics Report")
            appendLine("=" .repeat(40))
            appendLine("Timestamp: ${dateFormat.format(Date(current.timestamp))}")
            appendLine()
            appendLine("Frame Performance:")
            appendLine("  FPS: ${String.format("%.1f", current.fps)}")
            appendLine("  Avg Frame Time: ${String.format("%.2f", current.averageFrameTime)} ms")
            appendLine("  Dropped Frames: ${current.droppedFrameCount} (${String.format("%.2f", current.droppedFramePercentage)}%)")
            appendLine()
            appendLine("Memory Usage:")
            appendLine("  Used: ${String.format("%.2f", current.memoryUsageMb)} MB (${String.format("%.1f", current.memoryPercentage)}%)")
            appendLine("  Available: ${String.format("%.2f", current.memoryAvailableMb)} MB")
            appendLine("  Native Heap: ${String.format("%.2f", current.nativeHeapAllocatedMb)} / ${String.format("%.2f", current.nativeHeapSizeMb)} MB")
            appendLine()
            appendLine("System:")
            appendLine("  CPU Usage: ${String.format("%.1f", current.cpuUsagePercentage)}%")
            appendLine("  Thread Count: ${current.threadCount}")
            appendLine()

            val warnings = getWarnings()
            if (warnings.isNotEmpty()) {
                appendLine("Warnings:")
                warnings.forEach { warning ->
                    appendLine("  [${warning.level}] ${warning.message}")
                    appendLine("    Value: ${String.format("%.2f", warning.value)}, Threshold: ${String.format("%.2f", warning.threshold)}")
                }
            } else {
                appendLine("No performance warnings")
            }
        }
    }

    /**
     * Log metrics to logcat (debug only)
     */
    fun logMetrics() {
        if (!enabled) return
        android.util.Log.d("PerformanceMonitor", getMetricsReport())
    }

    /**
     * Reset counters
     */
    fun reset() {
        frameCount = 0
        droppedFrames = 0
        _metrics.value = PerformanceMetrics()
    }

    /**
     * Force garbage collection (use sparingly)
     */
    fun forceGc() {
        System.gc()
        System.runFinalization()
    }

    /**
     * Get memory info
     */
    fun getDetailedMemoryInfo(): String {
        val runtime = Runtime.getRuntime()
        val memoryInfo = Debug.MemoryInfo()
        Debug.getMemoryInfo(memoryInfo)

        return buildString {
            appendLine("Detailed Memory Info")
            appendLine("=" .repeat(40))
            appendLine("JVM Memory:")
            appendLine("  Max: ${runtime.maxMemory() / (1024 * 1024)} MB")
            appendLine("  Total: ${runtime.totalMemory() / (1024 * 1024)} MB")
            appendLine("  Free: ${runtime.freeMemory() / (1024 * 1024)} MB")
            appendLine("  Used: ${(runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)} MB")
            appendLine()
            appendLine("Native Memory:")
            appendLine("  Heap Size: ${Debug.getNativeHeapSize() / (1024 * 1024)} MB")
            appendLine("  Heap Allocated: ${Debug.getNativeHeapAllocatedSize() / (1024 * 1024)} MB")
            appendLine("  Heap Free: ${Debug.getNativeHeapFreeSize() / (1024 * 1024)} MB")
            appendLine()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                appendLine("Process Memory:")
                appendLine("  Total PSS: ${memoryInfo.totalPss / 1024} MB")
                appendLine("  Total Private Dirty: ${memoryInfo.totalPrivateDirty / 1024} MB")
                appendLine("  Total Shared Dirty: ${memoryInfo.totalSharedDirty / 1024} MB")
            }
        }
    }

    /**
     * Release resources
     */
    fun release() {
        stopMonitoring()
        scope.cancel()
    }

    companion object {
        /**
         * Create performance monitor (only if debug build)
         */
        fun create(context: Context): PerformanceMonitor? {
            return if (BuildConfig.DEBUG) {
                PerformanceMonitor(context, enabled = true)
            } else {
                null
            }
        }
    }
}
