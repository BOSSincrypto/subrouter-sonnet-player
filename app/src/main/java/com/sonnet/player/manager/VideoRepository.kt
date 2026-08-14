package com.sonnet.player.manager

import com.sonnet.player.model.VideoHistory
import com.sonnet.player.model.VideoHistoryDao
import kotlinx.coroutines.flow.Flow

class VideoRepository(private val dao: VideoHistoryDao) {

    fun getRecentVideos(limit: Int = 50): Flow<List<VideoHistory>> {
        return dao.getRecentVideos(limit)
    }

    suspend fun getVideoByPath(path: String): VideoHistory? {
        return dao.getVideoByPath(path)
    }

    fun getVideoByPathFlow(path: String): Flow<VideoHistory?> {
        return dao.getVideoByPathFlow(path)
    }

    suspend fun saveVideoProgress(path: String, fileName: String, position: Long, duration: Long) {
        dao.upsertVideoProgress(path, fileName, position, duration)
    }

    suspend fun deleteVideo(video: VideoHistory) {
        dao.delete(video)
    }

    suspend fun deleteByPath(path: String) {
        dao.deleteByPath(path)
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }

    suspend fun cleanupOldEntries(keepCount: Int = 100) {
        dao.deleteOldEntries(keepCount)
    }

    suspend fun getHistoryCount(): Int {
        return dao.getCount()
    }
}
