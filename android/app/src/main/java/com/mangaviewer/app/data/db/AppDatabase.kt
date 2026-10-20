package com.mangaviewer.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        DownloadEntity::class,
        HistoryEntity::class,
        CommentDraftEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun downloadDao(): DownloadDao
    abstract fun historyDao(): HistoryDao
    abstract fun commentDraftDao(): CommentDraftDao
}
