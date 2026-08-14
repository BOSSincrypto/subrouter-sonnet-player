package com.sonnet.player.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a video file with metadata
 */
@Parcelize
data class VideoFile(
    val uri: Uri,
    val title: String,
    val duration: Long = 0L,
    val size: Long = 0L,
    val mimeType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val thumbnailPath: String? = null,
    val lastPlayedPosition: Long = 0L
) : Parcelable {

    val aspectRatio: Float
        get() = if (height > 0) width.toFloat() / height.toFloat() else 16f / 9f

    val durationFormatted: String
        get() = formatDuration(duration)

    val sizeFormatted: String
        get() = formatSize(size)

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
