package com.mangaviewer.app.data.repo

import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.db.DEFAULT_FOLDER
import com.mangaviewer.app.data.db.FavoriteEntity
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.data.model.artistsJson
import com.mangaviewer.app.data.model.categoriesJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FavoriteRepository(private val container: AppContainer) {

    fun observeAll(): Flow<List<FavoriteEntity>> = container.favoriteDao.observeAll()

    fun observeIds(): Flow<Set<Long>> = container.favoriteDao.observeAll().map { list ->
        list.mapTo(HashSet()) { it.galleryId }
    }

    fun observeFolderCounts(): Flow<Map<String, Int>> =
        container.favoriteDao.observeFolderCounts().map { rows ->
            rows.associate { it.folder to it.total }
        }

    suspend fun isFavorite(id: Long): Boolean = container.favoriteDao.find(id) != null

    /** 切换收藏，返回切换后的状态：true = 已收藏。 */
    suspend fun toggle(ui: GalleryUi, folder: String = DEFAULT_FOLDER): Boolean {
        if (container.favoriteDao.find(ui.id) != null) {
            container.favoriteDao.delete(ui.id)
            return false
        }
        container.favoriteDao.upsert(
            FavoriteEntity(
                galleryId = ui.id,
                title = ui.title,
                prettyTitle = ui.subtitle,
                thumbPath = ui.thumbPath,
                numPages = ui.numPages,
                numFavorites = ui.numFavorites,
                folder = folder,
                addedAt = System.currentTimeMillis(),
                artistsJson = ui.artistsJson(),
                categoriesJson = ui.categoriesJson(),
            ),
        )
        return true
    }

    /**
     * 详情页加载完成后顺手补全收藏的作者 / 分类（筛选器用）。
     * 每条都是「只补空列」的单条 UPDATE：不读快照、不覆盖已有值，天然避开并发写。
     */
    suspend fun fillMetadata(ui: GalleryUi) {
        ui.artistsJson()?.let { container.favoriteDao.fillArtistsIfMissing(ui.id, it) }
        ui.categoriesJson()?.let { container.favoriteDao.fillCategoriesIfMissing(ui.id, it) }
    }

    /** 冷启动时用下载记录里已有的元数据回填旧收藏（本地 SQL，不联网）。 */
    suspend fun backfillMetadataFromDownloads() {
        runCatching { container.favoriteDao.backfillArtistsFromDownloads() }
        runCatching { container.favoriteDao.backfillCategoriesFromDownloads() }
    }

    suspend fun remove(id: Long) = container.favoriteDao.delete(id)

    suspend fun moveToFolder(id: Long, folder: String) = container.favoriteDao.moveToFolder(id, folder)
}
