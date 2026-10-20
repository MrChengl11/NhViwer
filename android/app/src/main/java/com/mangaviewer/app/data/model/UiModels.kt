package com.mangaviewer.app.data.model

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

val TAG_TYPE_LABEL: Map<String, String> = mapOf(
    "tag" to "标签",
    "artist" to "作者",
    "parody" to "原作",
    "character" to "角色",
    "group" to "社团",
    "language" to "语言",
    "category" to "分类",
)

fun tagTypeLabel(type: String): String = TAG_TYPE_LABEL[type] ?: type

data class CommentUi(
    val id: Long,
    val body: String,
    val author: String,
    val postedAt: Long?,
)
