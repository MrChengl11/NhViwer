package com.mangaviewer.app.data.model

import com.mangaviewer.app.data.remote.dto.CommentDto
import com.mangaviewer.app.data.remote.dto.GalleryDetailDto
import com.mangaviewer.app.data.remote.dto.GalleryListItemDto
import com.mangaviewer.app.data.remote.dto.TagDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

/** 服务端 upload_date 可能是 Unix 秒整数，也可能是 ISO 字符串，统一成毫秒时间戳。 */
fun JsonElement?.toEpochMillis(): Long? {
    val raw = this?.jsonPrimitive?.content ?: return null
    raw.toLongOrNull()?.let { value -> return if (value < 10_000_000_000L) value * 1000L else value }
    return runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()
}

/** 标题选择：优先一方的标题为主标题，另一个作为副标题。 */
fun pickTitle(english: String?, japanese: String?, preferJapanese: Boolean): String {
    val en = english?.trim().orEmpty()
    val jp = japanese?.trim().orEmpty()
    return when {
        preferJapanese && jp.isNotEmpty() -> jp
        en.isNotEmpty() -> en
        else -> jp
    }
}

fun pickSubtitle(english: String?, japanese: String?, preferJapanese: Boolean): String? {
    val en = english?.trim().orEmpty()
    val jp = japanese?.trim().orEmpty()
    val chosen = if (preferJapanese) jp else en
    val other = if (preferJapanese) en else jp
    return other.takeIf { it.isNotEmpty() && it != chosen }
}

fun GalleryListItemDto.toUi(preferJapanese: Boolean = false): GalleryUi = GalleryUi(
    id = id,
    mediaId = mediaId,
    title = pickTitle(englishTitle, japaneseTitle, preferJapanese).ifBlank { "#" + id },
    subtitle = pickSubtitle(englishTitle, japaneseTitle, preferJapanese),
    thumbPath = thumbnail,
    numPages = numPages,
    numFavorites = numFavorites,
    uploadDate = uploadDate.toEpochMillis(),
    tags = tags.orEmpty().map { it.toUi() },
)

fun GalleryDetailDto.toUi(preferJapanese: Boolean = false): GalleryUi = GalleryUi(
    id = id,
    mediaId = mediaId,
    title = pickTitle(title.english, title.japanese, preferJapanese)
        .ifBlank { title.pretty.orEmpty() }
        .ifBlank { "#" + id },
    subtitle = pickSubtitle(title.english, title.japanese, preferJapanese),
    thumbPath = thumbnail?.path?.takeIf { it.isNotBlank() } ?: cover.path,
    numPages = numPages,
    numFavorites = numFavorites,
    uploadDate = uploadDate.toEpochMillis(),
    tags = tags.map { it.toUi() },
)

fun TagDto.toUi(): TagUi = TagUi(id = id, type = type, name = name, count = count)

fun CommentDto.toUi(): CommentUi = CommentUi(
    id = id,
    body = body,
    author = (poster ?: user)?.username?.takeIf { it.isNotBlank() } ?: "匿名",
    postedAt = postedAt.toEpochMillis(),
)

/** 详情页按标签类型分组，顺序固定，便于布局稳定。 */
fun groupTags(tags: List<TagUi>): List<TagGroup> {
    val order = listOf("category", "language", "parody", "character", "artist", "group", "tag")
    val known = order.mapNotNull { type ->
        val items = tags.filter { it.type == type }
        if (items.isEmpty()) null else TagGroup(type, tagTypeLabel(type), items)
    }
    val rest = tags.filter { it.type !in order }
        .groupBy { it.type }
        .map { (type, items) -> TagGroup(type, tagTypeLabel(type), items) }
    return known + rest
}
