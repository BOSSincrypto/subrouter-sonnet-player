package com.sonnet.player.model

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoHistoryDao {

    @Query("SELECT * FROM video_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentVideos(limit: Int = 50): Flow<List<VideoHistory>>

    @Query("SELECT * FROM video_history WHERE filePath = :path LIMIT 1")
    suspend fun getVideoByPath(path: String): VideoHistory?

    @Query("SELECT * FROM video_history WHERE filePath = :path LIMIT 1")
    fun getVideoByPathFlow(path: String): Flow<VideoHistory?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(video: VideoHistory): Long

    @Update
    suspend fun update(video: VideoHistory)

    @Delete
    suspend fun delete(video: VideoHistory)

    @Query("DELETE FROM video_history WHERE filePath = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM video_history")
    suspend fun clearAll()

    @Query("DELETE FROM video_history WHERE id NOT IN (SELECT id FROM video_history ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun deleteOldEntries(keepCount: Int = 100)

    @Query("SELECT COUNT(*) FROM video_history")
    suspend fun getCount(): Int

    @Transaction
    suspend fun upsertVideoProgress(path: String, fileName: String, position: Long, duration: Long) {
        val existing = getVideoByPath(path)
        if (existing != null) {
            update(existing.copy(
                position = position,
                duration = duration,
                timestamp = System.currentTimeMillis()
            ))
        } else {
            insert(VideoHistory(
                filePath = path,
                fileName = fileName,
                position = position,
                duration = duration
            ))
        }
    }
}
