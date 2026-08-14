package com.sonnet.player.model

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VideoHistory::class],
    version = 1,
    exportSchema = false
)
abstract class VideoDatabase : RoomDatabase() {

    abstract fun videoHistoryDao(): VideoHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: VideoDatabase? = null

        fun getInstance(context: Context): VideoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    VideoDatabase::class.java,
                    "video_player.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
