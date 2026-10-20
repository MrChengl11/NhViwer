package com.mangaviewer.app.data.repo

import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.model.CommentUi
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.data.model.TagUi
import com.mangaviewer.app.data.model.toUi
import com.mangaviewer.app.data.remote.NhentaiApi
import com.mangaviewer.app.data.remote.dto.PageInfo

class GalleryRepository(private val container: AppContainer) {

    private suspend fun api(): NhentaiApi = container.api(container.settings.current().effectiveBase)

    suspend fun latest(page: Int, perPage: Int = 25): List<GalleryUi> {
        val jp = container.settings.current().preferJapaneseTitle
        return api().latest(page, perPage).map { it.toUi(jp) }
    }

    suspend fun popular(page: Int, perPage: Int = 25): List<GalleryUi> {
        val jp = container.settings.current().preferJapaneseTitle
        return api().popular(page, perPage).map { it.toUi(jp) }
    }

    suspend fun random(): GalleryUi = api().random().toUi(true)

    suspend fun tagged(tagId: Long, page: Int, sort: String, perPage: Int = 25): List<GalleryUi> {
        val jp = container.settings.current().preferJapaneseTitle
        return api().tagged(tagId, page, perPage, sort).map { it.toUi(jp) }
    }

    suspend fun search(query: String, page: Int, sort: String, perPage: Int = 25): List<GalleryUi> {
        val jp = container.settings.current().preferJapaneseTitle
        return api().search(query, page, perPage, sort).map { it.toUi(jp) }
    }

    suspend fun detail(id: Long): GalleryDetail {
        val dto = api().detail(id)
        return GalleryDetail(
            ui = dto.toUi(true),
            pages = dto.pages,
            related = dto.related.orEmpty().map { it.toUi(true) },
            comments = dto.comments.orEmpty().map { it.toUi() },
            commentCount = dto.commentCount ?: dto.comments?.size ?: 0,
        )
    }

    suspend fun tags(type: String, page: Int, sort: String, perPage: Int = 50): List<TagUi> =
        api().tags(type, page, perPage, sort).result.map { it.toUi() }
}

data class GalleryDetail(
    val ui: GalleryUi,
    val pages: List<PageInfo>,
    val related: List<GalleryUi>,
    val comments: List<CommentUi>,
    val commentCount: Int,
)
