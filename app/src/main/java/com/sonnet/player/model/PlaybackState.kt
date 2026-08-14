package com.sonnet.player.model

/**
 * Represents the current playback state
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isBuffering: Boolean = false,
    val error: PlaybackError? = null
) {
    val progress: Float
        get() = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

    val bufferedProgress: Float
        get() = if (duration > 0) bufferedPosition.toFloat() / duration.toFloat() else 0f

    val isComplete: Boolean
        get() = duration > 0 && currentPosition >= duration - 500 // 500ms threshold

    companion object {
        val IDLE = PlaybackState()
    }
}

/**
 * Playback error information
 */
data class PlaybackError(
    val code: Int,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Aspect ratio display modes
 */
enum class AspectRatioMode {
    FIT,        // Fit inside view, maintain aspect ratio
    FILL,       // Fill view, maintain aspect ratio, may crop
    CROP,       // Crop to fill view completely
    STRETCH     // Stretch to fill view, ignore aspect ratio
}

/**
 * Playback speed presets
 */
enum class PlaybackSpeed(val value: Float) {
    VERY_SLOW(0.25f),
    SLOW(0.5f),
    THREE_QUARTER(0.75f),
    NORMAL(1.0f),
    FAST(1.25f),
    FASTER(1.5f),
    VERY_FAST(2.0f);

    companion object {
        fun fromValue(value: Float): PlaybackSpeed {
            return entries.minByOrNull { kotlin.math.abs(it.value - value) } ?: NORMAL
        }
    }
}
