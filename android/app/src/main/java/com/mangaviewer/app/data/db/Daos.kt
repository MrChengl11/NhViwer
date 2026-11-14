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
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC, galleryId DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE galleryId = :id LIMIT 1")
    suspend fun find(id: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, updatedAt = :now WHERE galleryId = :id")
    suspend fun setStatus(id: Long, status: String, now: Long)

    /** 暂停时只改仍处于活跃状态的行，避免覆盖刚完成的 DONE 或已被删除的记录。 */
    @Query("UPDATE downloads SET status = 'PAUSED', updatedAt = :now WHERE galleryId = :id AND status IN ('RUNNING','QUEUED')")
    suspend fun markPausedIfActive(id: Long, now: Long)

    /** 冷启动复位：只改活跃行，附带一次已完成页数校正，不整行覆盖元数据。 */
    @Query("UPDATE downloads SET status = 'PAUSED', donePages = :done, updatedAt = :now WHERE galleryId = :id AND status IN ('RUNNING','QUEUED')")
    suspend fun resetInterrupted(id: Long, done: Int, now: Long)

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

    /** 扫描本地目录时，用历史记录补全标题 / 封面。 */
    @Query("SELECT * FROM history WHERE galleryId = :id LIMIT 1")
    suspend fun find(id: Long): HistoryEntity?

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
