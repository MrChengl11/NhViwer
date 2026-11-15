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

    /**
     * 账号同步用：本地还没有这条收藏时才插入。
     * 不能再用整行 REPLACE：那会把并发补全的作者 / 分类列覆盖回旧快照。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: FavoriteEntity)

    /** 账号同步用：只刷新展示字段，绝不碰文件夹、加入时间与作者 / 分类列。 */
    @Query(
        """
        UPDATE favorites SET title = :title, prettyTitle = :prettyTitle, thumbPath = :thumbPath,
            numPages = :numPages, numFavorites = :numFavorites
        WHERE galleryId = :id
        """,
    )
    suspend fun updateDisplay(
        id: Long,
        title: String,
        prettyTitle: String?,
        thumbPath: String,
        numPages: Int,
        numFavorites: Long,
    )

    /** 只在本地缺作者时补上（单条 UPDATE 原子完成，不会覆盖并发写入的新值）。 */
    @Query("UPDATE favorites SET artistsJson = :json WHERE galleryId = :id AND artistsJson IS NULL")
    suspend fun fillArtistsIfMissing(id: Long, json: String)

    /** 只在本地缺分类时补上。 */
    @Query("UPDATE favorites SET categoriesJson = :json WHERE galleryId = :id AND categoriesJson IS NULL")
    suspend fun fillCategoriesIfMissing(id: Long, json: String)

    /**
     * 旧收藏补全作者：用下载记录里已抓到的作者信息回填（只填空缺，不覆盖已有值）。
     * 下载页的扫描 / 补全流程本来就会写 artistsJson，这里直接复用，不额外发请求。
     */
    @Query(
        """
        UPDATE favorites SET artistsJson = (
            SELECT d.artistsJson FROM downloads d
            WHERE d.galleryId = favorites.galleryId AND d.artistsJson IS NOT NULL
            LIMIT 1
        )
        WHERE artistsJson IS NULL AND EXISTS (
            SELECT 1 FROM downloads d
            WHERE d.galleryId = favorites.galleryId AND d.artistsJson IS NOT NULL
        )
        """,
    )
    suspend fun backfillArtistsFromDownloads()

    /** 同上，补分类。 */
    @Query(
        """
        UPDATE favorites SET categoriesJson = (
            SELECT d.categoriesJson FROM downloads d
            WHERE d.galleryId = favorites.galleryId AND d.categoriesJson IS NOT NULL
            LIMIT 1
        )
        WHERE categoriesJson IS NULL AND EXISTS (
            SELECT 1 FROM downloads d
            WHERE d.galleryId = favorites.galleryId AND d.categoriesJson IS NOT NULL
        )
        """,
    )
    suspend fun backfillCategoriesFromDownloads()

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
