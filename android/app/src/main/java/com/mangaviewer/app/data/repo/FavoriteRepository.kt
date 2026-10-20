package com.mangaviewer.app.data.repo

import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.db.DEFAULT_FOLDER
import com.mangaviewer.app.data.db.FavoriteEntity
import com.mangaviewer.app.data.model.GalleryUi
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
            ),
        )
        return true
    }

    suspend fun remove(id: Long) = container.favoriteDao.delete(id)

    suspend fun moveToFolder(id: Long, folder: String) = container.favoriteDao.moveToFolder(id, folder)
}
