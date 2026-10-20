package com.mangaviewer.app.data.remote

import com.mangaviewer.app.data.remote.dto.ApiKeyCreatedDto
import com.mangaviewer.app.data.remote.dto.CommentDto
import com.mangaviewer.app.data.remote.dto.CommentPageDto
import com.mangaviewer.app.data.remote.dto.CreateCommentRequest
import com.mangaviewer.app.data.remote.dto.GalleryDetailDto
import com.mangaviewer.app.data.remote.dto.GalleryListItemDto
import com.mangaviewer.app.data.remote.dto.CreateApiKeyRequest
import com.mangaviewer.app.data.remote.dto.FavoriteCheckDto
import com.mangaviewer.app.data.remote.dto.GalleryPageDto
import com.mangaviewer.app.data.remote.dto.PowDto
import com.mangaviewer.app.data.remote.dto.TagDto
import com.mangaviewer.app.data.remote.dto.TagPageDto
import com.mangaviewer.app.data.remote.dto.UserMeDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * nhentai v2 客户端。
 *
 * 注意：直接对 nhentai.net 发请求在本机行不通（DNS 被污染 + 无 CORS + 证书环境），
 * 因此 [baseUrl] 指向本机反代（默认 http://127.0.0.1:8787）。反代负责 IP 直连与图片代理。
 */
class NhentaiApi(
    private val client: OkHttpClient,
    private val baseUrl: String,
    private val authStore: AuthStore,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val gate = RateGate(perMinute = 12)
    private val mutex = Mutex()

    // ---------- 画廊 ----------

    suspend fun latest(page: Int = 1, perPage: Int = 25): List<GalleryListItemDto> =
        getList("/api/v2/galleries", mapOf("page" to "$page", "per_page" to "$perPage"))

    suspend fun popular(page: Int = 1, perPage: Int = 25): List<GalleryListItemDto> =
        getList("/api/v2/galleries/popular", mapOf("page" to "$page", "per_page" to "$perPage"))

    suspend fun random(): GalleryListItemDto {
        val raw = request("/api/v2/galleries/random", emptyMap())
        return json.decodeFromString(GalleryListItemDto.serializer(), raw)
    }

    suspend fun tagged(
        tagId: Long,
        page: Int = 1,
        perPage: Int = 25,
        sort: String = "date",
    ): List<GalleryListItemDto> = getList(
        "/api/v2/galleries/tagged",
        mapOf(
            "tag_id" to "$tagId",
            "page" to "$page",
            "per_page" to "$perPage",
            "sort" to sort,
        ),
    )

    suspend fun search(
        query: String,
        page: Int = 1,
        perPage: Int = 25,
        sort: String = "date",
    ): List<GalleryListItemDto> {
        // v2 搜索走 GET /api/v2/search，参数名必须是 query（用 q 会 400 "query: Field required"）。
        return getList(
            "/api/v2/search",
            mapOf("query" to query, "page" to "$page", "per_page" to "$perPage", "sort" to sort),
        )
    }

    suspend fun detail(id: Long): GalleryDetailDto {
        val raw = request("/api/v2/galleries/$id", mapOf("include" to "comments,related,favorite"))
        return json.decodeFromString(GalleryDetailDto.serializer(), raw)
    }

    suspend fun related(id: Long): List<GalleryListItemDto> =
        getList("/api/v2/galleries/$id/related", emptyMap())

    // ---------- 标签 ----------

    suspend fun tags(type: String, page: Int = 1, perPage: Int = 50, sort: String = "popular"): TagPageDto {
        val raw = request(
            "/api/v2/tags/$type",
            mapOf("page" to "$page", "per_page" to "$perPage", "sort" to sort),
        )
        return json.decodeFromString(TagPageDto.serializer(), raw)
    }

    suspend fun tag(id: Long): TagDto {
        val raw = request("/api/v2/tags/$id", emptyMap())
        return json.decodeFromString(TagDto.serializer(), raw)
    }

    // ---------- 评论 ----------

    suspend fun comments(galleryId: Long, page: Int = 0, perPage: Int = 25): CommentPageDto {
        val raw = request(
            "/api/v2/galleries/$galleryId/comments",
            mapOf("page" to "$page", "per_page" to "$perPage"),
        )
        return runCatching { json.decodeFromString(CommentPageDto.serializer(), raw) }
            .getOrElse { throw ApiException.Parse("评论列表解析失败") }
    }

    suspend fun postComment(galleryId: Long, body: String, challenge: String, nonce: String, captcha: String?): CommentDto {
        val payload = CreateCommentRequest(body, challenge, nonce, captcha)
        val raw = request(
            "/api/v2/galleries/$galleryId/comments",
            emptyMap(),
            method = "POST",
            bodyJson = json.encodeToString(CreateCommentRequest.serializer(), payload),
        )
        return json.decodeFromString(CommentDto.serializer(), raw)
    }

    suspend fun deleteComment(commentId: Long) {
        request("/api/v2/comments/$commentId", emptyMap(), method = "DELETE")
    }

    // ---------- 认证 ----------

    suspend fun pow(action: String = "login"): PowDto {
        val raw = request("/api/v2/pow", mapOf("action" to action))
        return json.decodeFromString(PowDto.serializer(), raw)
    }

    // ---------- 账号 / 同步 ----------

    /** 当前登录用户资料；未登录会抛 ApiException.Unauthorized。 */
    suspend fun me(): UserMeDto {
        val raw = request("/api/v2/user", emptyMap())
        return json.decodeFromString(UserMeDto.serializer(), raw)
    }

    /** 用一份**还没保存**的 API Key 校验身份，成功后才落盘。 */
    suspend fun meWithApiKey(apiKey: String): UserMeDto {
        val raw = request("/api/v2/user", emptyMap(), authOverride = "Key " + apiKey)
        return json.decodeFromString(UserMeDto.serializer(), raw)
    }

    /** 账号收藏（分页，响应带 num_pages）。 */
    suspend fun favorites(page: Int = 1): GalleryPageDto {
        val raw = request("/api/v2/favorites", mapOf("page" to "$page"))
        return json.decodeFromString(GalleryPageDto.serializer(), raw)
    }

    /** 加入 / 取消远端收藏。 */
    suspend fun setFavorite(galleryId: Long, favorite: Boolean): FavoriteCheckDto {
        val raw = request(
            "/api/v2/galleries/$galleryId/favorite",
            emptyMap(),
            method = if (favorite) "POST" else "DELETE",
        )
        return runCatching { json.decodeFromString(FavoriteCheckDto.serializer(), raw) }
            .getOrDefault(FavoriteCheckDto(favorite = favorite))
    }

    suspend fun checkFavorite(galleryId: Long): FavoriteCheckDto {
        val raw = request("/api/v2/galleries/$galleryId/favorite", emptyMap())
        return runCatching { json.decodeFromString(FavoriteCheckDto.serializer(), raw) }
            .getOrDefault(FavoriteCheckDto())
    }

    /** 给当前账号新建 API Key（需要 User 令牌登录态）。 */
    suspend fun createApiKey(name: String): ApiKeyCreatedDto {
        val raw = request(
            "/api/v2/user/keys",
            emptyMap(),
            method = "POST",
            bodyJson = json.encodeToString(CreateApiKeyRequest.serializer(), CreateApiKeyRequest(name)),
        )
        return json.decodeFromString(ApiKeyCreatedDto.serializer(), raw)
    }

    // ---------- 底层 ----------

    private suspend fun getList(path: String, params: Map<String, String>): List<GalleryListItemDto> {
        val raw = request(path, params)
        return runCatching {
            val page = runCatching { json.decodeFromString(GalleryPageDto.serializer(), raw) }.getOrNull()
            if (page != null && page.result.isNotEmpty()) {
                page.result
            } else {
                json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(GalleryListItemDto.serializer()), raw)
            }
        }.getOrElse { throw ApiException.Parse("列表解析失败") }
    }

    private fun buildUrl(path: String, params: Map<String, String>): HttpUrl {
        val builder = (baseUrl + path).toHttpUrl().newBuilder()
        params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
        return builder.build()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun request(
        path: String,
        params: Map<String, String>,
        method: String = "GET",
        bodyJson: String? = null,
        authOverride: String? = null,
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock { gate.acquire() }
        val url = buildUrl(path, params)
        val builder = Request.Builder().url(url)
        if (authOverride != null) {
            builder.header("Authorization", authOverride)
        } else {
            authStore.authHeader()?.let { builder.header("Authorization", it) }
        }
        builder.header("Accept", "application/json")

        var attempt = 0
        var lastError: Exception? = null
        while (attempt < MAX_RETRIES) {
            attempt++
            val body = bodyJson?.toRequestBody(JSON_MEDIA) ?: if (method == "POST") "{}".toRequestBody(JSON_MEDIA) else null
            val req = builder.method(method, body).build()
            try {
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    when {
                        resp.isSuccessful -> return@withContext text
                        resp.code == 401 || resp.code == 403 -> throw ApiException.Unauthorized
                        resp.code == 429 -> {
                            delay(1000L * attempt)
                            lastError = ApiException.RateLimited(1000L * attempt)
                        }
                        resp.code in 500..599 -> {
                            delay(400L * attempt)
                            lastError = ApiException.Http(resp.code, text)
                        }
                        else -> throw ApiException.Http(resp.code, text)
                    }
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                lastError = ApiException.Network(e.message ?: "网络错误")
                delay(300L * attempt)
            }
        }
        throw lastError ?: ApiException.Network("请求失败")
    }

    companion object {
        private const val MAX_RETRIES = 3
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}

/** 简易滑动窗口限流：nhentai 对高频请求较敏感。 */
class RateGate(private val perMinute: Int) {
    private val stamps = ArrayDeque<Long>()

    suspend fun acquire() {
        while (true) {
            val now = System.currentTimeMillis()
            stamps.removeAll { now - it > 60_000L }
            if (stamps.size < perMinute) {
                stamps.addLast(now)
                return
            }
            delay(1000L)
        }
    }
}