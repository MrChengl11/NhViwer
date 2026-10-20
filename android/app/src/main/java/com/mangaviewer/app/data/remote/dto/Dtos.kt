package com.mangaviewer.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 与 nhentai v2 API 对应的数据模型。
 * 服务端字段命名混用 snake_case，全部显式标注 SerialName，避免依赖命名策略猜测。
 */

@Serializable
data class GalleryTitle(
    val english: String? = null,
    val japanese: String? = null,
    val pretty: String? = null,
)

@Serializable
data class CoverInfo(
    val path: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class PageInfo(
    val number: Int = 0,
    val path: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val thumbnail: String? = null,
)

@Serializable
data class TagDto(
    val id: Long = 0,
    val type: String = "",
    val name: String = "",
    val slug: String = "",
    val url: String = "",
    val count: Long? = null,
)

@Serializable
data class GalleryListItemDto(
    val id: Long = 0,
    @SerialName("media_id") val mediaId: Long = 0,
    @SerialName("english_title") val englishTitle: String? = null,
    @SerialName("japanese_title") val japaneseTitle: String? = null,
    val thumbnail: String = "",
    @SerialName("thumbnail_width") val thumbnailWidth: Int = 0,
    @SerialName("thumbnail_height") val thumbnailHeight: Int = 0,
    @SerialName("num_pages") val numPages: Int = 0,
    @SerialName("num_favorites") val numFavorites: Long = 0,
    @SerialName("upload_date") val uploadDate: JsonElement? = null,
    val tags: List<TagDto>? = null,
    @SerialName("tag_ids") val tagIds: List<Long>? = null,
    val blacklisted: Boolean? = null,
)

@Serializable
data class GalleryDetailDto(
    val id: Long = 0,
    @SerialName("media_id") val mediaId: Long = 0,
    val title: GalleryTitle = GalleryTitle(),
    val cover: CoverInfo = CoverInfo(),
    val thumbnail: CoverInfo? = null,
    val scanlator: String? = null,
    @SerialName("upload_date") val uploadDate: JsonElement? = null,
    val tags: List<TagDto> = emptyList(),
    @SerialName("num_pages") val numPages: Int = 0,
    @SerialName("num_favorites") val numFavorites: Long = 0,
    val pages: List<PageInfo> = emptyList(),
    val comments: List<CommentDto>? = null,
    @SerialName("comment_count") val commentCount: Int? = null,
    val related: List<GalleryListItemDto>? = null,
    @SerialName("is_favorited") val isFavorited: Boolean? = null,
)

@Serializable
data class CommentDto(
    val id: Long = 0,
    val body: String = "",
    @SerialName("posted_at") val postedAt: JsonElement? = null,
    val poster: UserDto? = null,
    val user: UserDto? = null,
)

@Serializable
data class UserDto(
    val id: Long = 0,
    val username: String = "",
    val slug: String? = null,
    val avatar: String? = null,
)

@Serializable
data class PageMeta(
    val page: Int = 0,
    val perPage: Int = 0,
    val total: Int = 0,
    val lastPage: Int = 0,
)

@Serializable
data class GalleryPageDto(
    val result: List<GalleryListItemDto> = emptyList(),
    val num_pages: Int? = null,
    val per_page: Int? = null,
    @SerialName("total") val total: Int? = null,
)

@Serializable
data class CommentPageDto(
    val result: List<CommentDto> = emptyList(),
    val total: Int? = null,
    @SerialName("num_pages") val numPages: Int? = null,
    @SerialName("per_page") val perPage: Int? = null,
)

@Serializable
data class TagPageDto(
    val result: List<TagDto> = emptyList(),
    val total: Int? = null,
    @SerialName("num_pages") val numPages: Int? = null,
    /** 形如 {"#":[1,1],"A":[1,3],...}，值为 [起始页, 结束页]（1 基，闭区间） */
    val alphabet: Map<String, List<Int>> = emptyMap(),
)

@Serializable
data class PowDto(
    val challenge: String = "",
    val difficulty: Int = 0,
)

@Serializable
data class CreateCommentRequest(
    val body: String,
    @SerialName("pow_challenge") val powChallenge: String,
    @SerialName("pow_nonce") val powNonce: String,
    @SerialName("captcha_response") val captchaResponse: String? = null,
)

/** GET /api/v2/user 的响应。API key 认证时 email 为 null。 */
@Serializable
data class UserMeDto(
    val id: Long = 0,
    val username: String = "",
    val slug: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
    @SerialName("is_staff") val isStaff: Boolean = false,
    @SerialName("is_superuser") val isSuperuser: Boolean = false,
    val roles: List<String> = emptyList(),
    val about: String = "",
    val email: String? = null,
)

/** 收藏状态：GET/POST/DELETE /api/v2/galleries/{id}/favorite。 */
@Serializable
data class FavoriteCheckDto(
    val favorite: Boolean = false,
    @SerialName("num_favorites") val numFavorites: Long? = null,
    val count: Long? = null,
)

@Serializable
data class CreateApiKeyRequest(val name: String)

/** POST /api/v2/user/keys 返回；key 只在创建时可见一次。 */
@Serializable
data class ApiKeyCreatedDto(
    val id: String = "",
    val key: String = "",
    val name: String = "",
)

@Serializable
data class ApiErrorBody(
    val error: String? = null,
    val message: String? = null,
)