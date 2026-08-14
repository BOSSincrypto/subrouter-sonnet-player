package com.sonnet.player.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_history")
data class VideoHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val position: Long,
    val duration: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val thumbnailPath: String? = null
) {
    val progressPercent: Int
        get() = if (duration > 0) ((position * 100) / duration).toInt() else 0
}
