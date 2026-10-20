package com.mangaviewer.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class FolderCount(val folder: String, val total: Int)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE galleryId = :id LIMIT 1")
    suspend fun find(id: Long): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE galleryId = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE favorites SET folder = :folder WHERE galleryId = :id")
    suspend fun moveToFolder(id: Long, folder: String)

    @Query("SELECT * FROM favorites")
    suspend fun all(): List<FavoriteEntity>

    @Query("SELECT folder, COUNT(*) AS total FROM favorites GROUP BY folder")
    fun observeFolderCounts(): Flow<List<FolderCount>>
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE galleryId = :id LIMIT 1")
    suspend fun find(id: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, updatedAt = :now WHERE galleryId = :id")
    suspend fun setStatus(id: Long, status: String, now: Long)

    @Query("DELETE FROM downloads WHERE galleryId = :id")
    suspend fun delete(id: Long)

    /** 一次性取全量，用于冷启动复位与"全部暂停/全部继续"。 */
    @Query("SELECT * FROM downloads")
    suspend fun all(): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('QUEUED','RUNNING')")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'DONE'")
    fun observeDoneCount(): Flow<Int>
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE galleryId = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CommentDraftDao {
    @Query("SELECT * FROM comment_drafts WHERE galleryId = :id LIMIT 1")
    suspend fun find(id: Long): CommentDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CommentDraftEntity)

    @Query("DELETE FROM comment_drafts WHERE galleryId = :id")
    suspend fun delete(id: Long)
}
