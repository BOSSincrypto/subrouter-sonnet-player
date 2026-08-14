package com.sonnet.player

import android.net.Uri

data class VideoItem(
    val id: Long,
    val title: String,
    val uri: Uri,
    val duration: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val path: String
) {
    fun getFormattedDuration(): String {
        val seconds = duration / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
        } else {
            String.format("%02d:%02d", minutes, seconds % 60)
        }
    }

    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun getResolution(): String {
        return if (width > 0 && height > 0) {
            "${width}x${height}"
        } else {
            ""
        }
    }

    fun getSubtitle(): String {
        val parts = mutableListOf<String>()
        val sizeStr = getFormattedSize()
        val resStr = getResolution()

        if (sizeStr.isNotEmpty()) parts.add(sizeStr)
        if (resStr.isNotEmpty()) parts.add(resStr)

        return parts.joinToString(" • ")
    }
}
