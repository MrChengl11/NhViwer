package com.mangaviewer.app.data.repo

import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.db.HistoryEntity
import com.mangaviewer.app.data.model.GalleryUi
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val container: AppContainer) {

    fun observeRecent(limit: Int = 100): Flow<List<HistoryEntity>> =
        container.historyDao.observeRecent(limit)

    suspend fun record(ui: GalleryUi, page: Int, total: Int) {
        container.historyDao.upsert(
            HistoryEntity(
                galleryId = ui.id,
                title = ui.title,
                thumbPath = ui.thumbPath,
                numPages = total,
                lastPage = page,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clear() = container.historyDao.clear()

    suspend fun delete(id: Long) = container.historyDao.delete(id)
}
