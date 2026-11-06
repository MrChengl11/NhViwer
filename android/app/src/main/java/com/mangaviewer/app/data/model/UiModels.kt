package com.mangaviewer.app.data.model
import com.mangaviewer.app.i18n.AppLocale


/** UI 层统一画廊模型（列表与详情共用），字段已归一化，避免各页面重复处理服务端差异。 */
data class GalleryUi(
    val id: Long,
    val mediaId: Long,
    val title: String,
    val subtitle: String?,
    val thumbPath: String,
    val numPages: Int,
    val numFavorites: Long,
    val uploadDate: Long?,
    val tags: List<TagUi> = emptyList(),
)

data class TagUi(
    val id: Long,
    val type: String,
    val name: String,
    val count: Long? = null,
)

data class TagGroup(val type: String, val label: String, val tags: List<TagUi>)

/** 标签分组标题；每次调用都取当前语言，切换语言后无需重建数据。 */
fun tagTypeLabel(type: String): String {
    val t = AppLocale.strings().tagTypes
    return when (type) {
        "tag" -> t.tag
        "artist" -> t.artist
        "parody" -> t.parody
        "character" -> t.character
        "group" -> t.group
        "language" -> t.language
        "category" -> t.category
        else -> type
    }
}

data class CommentUi(
    val id: Long,
    val body: String,
    val author: String,
    val postedAt: Long?,
)
