package com.mangaviewer.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val DEFAULT_FOLDER = "default"
const val ALL_FOLDER = "all"

const val DOWNLOAD_STATUS_QUEUED = "QUEUED"
const val DOWNLOAD_STATUS_RUNNING = "RUNNING"
const val DOWNLOAD_STATUS_PAUSED = "PAUSED"
const val DOWNLOAD_STATUS_DONE = "DONE"
const val DOWNLOAD_STATUS_FAILED = "FAILED"

/** 应用内正常下载。 */
const val DOWNLOAD_ORIGIN_APP = "APP"
/** 从下载目录扫描导入（应用重装、数据库丢失后自动找回）。 */
const val DOWNLOAD_ORIGIN_SCAN = "SCAN"

@Entity(tableName = "favorites", indices = [Index("addedAt")])
data class FavoriteEntity(
    @PrimaryKey val galleryId: Long,
    val title: String,
    val prettyTitle: String?,
    val thumbPath: String,
    val numPages: Int,
    val numFavorites: Long,
    val folder: String = DEFAULT_FOLDER,
    val addedAt: Long,
    /** 作者名 JSON 数组（收藏页按作者筛选用）；null = 尚未补全（旧数据 / 接口没带标签）。 */
    val artistsJson: String? = null,
    /** 分类名 JSON 数组（收藏页按分类筛选用）；null = 尚未补全。 */
    val categoriesJson: String? = null,
) {
    /** 作者列表；null / 损坏数据回退为空列表。 */
    val artists: List<String>
        get() = decodeNameList(artistsJson)

    /** 分类列表；null / 损坏数据回退为空列表。 */
    val categories: List<String>
        get() = decodeNameList(categoriesJson)
}

@Entity(tableName = "downloads", indices = [Index("galleryId")])
data class DownloadEntity(
    @PrimaryKey val galleryId: Long,
    val title: String,
    val thumbPath: String,
    val totalPages: Int,
    val donePages: Int,
    val status: String,
    val pagesJson: String,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String? = null,
    val bytes: Long = 0,
    /** 作者名 JSON 数组；null 表示尚未补全（旧版本数据 / 刚扫描导入）。 */
    val artistsJson: String? = null,
    /** 分类名 JSON 数组；null 表示尚未补全。 */
    val categoriesJson: String? = null,
    /** APP = 应用内下载；SCAN = 从下载目录扫描导入；null = 旧版本记录。 */
    val origin: String? = null,
) {
    /** 作者列表（用于筛选，不入库）。 */
    val artists: List<String>
        get() = decodeNameList(artistsJson)

    /** 分类列表（用于筛选，不入库）。 */
    val categories: List<String>
        get() = decodeNameList(categoriesJson)

    val isImported: Boolean
        get() = origin == DOWNLOAD_ORIGIN_SCAN

    /** 作者 / 分类尚未补全（旧版本记录或刚扫描导入）。 */
    val isMissingMetadata: Boolean
        get() = artistsJson == null || categoriesJson == null
}

/** 把标签名编码为 JSON 数组；空列表也编码成 "[]"，表示「已补全，确实没有」。 */
fun encodeNameList(names: Collection<String>): String {
    val array = org.json.JSONArray()
    names.map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .forEach { array.put(it) }
    return array.toString()
}

/** 解码 [encodeNameList] 写出的 JSON；null / 损坏数据回退为空列表。 */
fun decodeNameList(raw: String?): List<String> = runCatching {
    val array = org.json.JSONArray(raw ?: return emptyList())
    (0 until array.length()).mapNotNull { index ->
        array.optString(index).takeIf { it.isNotBlank() }
    }
}.getOrDefault(emptyList())

@Entity(tableName = "history", indices = [Index("updatedAt")])
data class HistoryEntity(
    @PrimaryKey val galleryId: Long,
    val title: String,
    val thumbPath: String,
    val numPages: Int,
    val lastPage: Int,
    val updatedAt: Long,
)

@Entity(tableName = "comment_drafts")
data class CommentDraftEntity(
    @PrimaryKey val galleryId: Long,
    val body: String,
    val updatedAt: Long,
)
