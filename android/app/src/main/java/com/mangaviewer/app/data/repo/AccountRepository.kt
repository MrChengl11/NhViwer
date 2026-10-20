package com.mangaviewer.app.data.repo

import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.db.DEFAULT_FOLDER
import com.mangaviewer.app.data.db.FavoriteEntity
import com.mangaviewer.app.data.model.toUi
import com.mangaviewer.app.data.remote.ApiException
import com.mangaviewer.app.data.remote.AuthState
import com.mangaviewer.app.data.remote.NhentaiApi
import com.mangaviewer.app.data.remote.dto.UserMeDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 账号与云端收藏同步（只支持 API Key 登录）。
 *
 * 为什么只有这一条：访问令牌需要网页登录，而网页登录在本机网络下走不通
 * （DNS 污染 + Cloudflare Turnstile 与域名绑定）；账号密码登录被服务端强制
 * Turnstile 人机验证。API Key 是官方给第三方客户端准备的长期凭证，实测可用。
 */
class AccountRepository(private val container: AppContainer) {

    val auth: StateFlow<AuthState> = container.authStore.state

    private val _sync = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _sync.asStateFlow()

    private suspend fun api(): NhentaiApi = container.api(container.settings.current().effectiveBase)

    /** 校验一枚还没保存的 API Key，成功后写入登录态。 */
    suspend fun loginWithApiKey(key: String): UserMeDto {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty()) { "API Key 不能为空" }
        val me = try {
            api().meWithApiKey(trimmed)
        } catch (e: ApiException.Unauthorized) {
            throw ApiException.CredentialInvalid()
        }
        container.authStore.saveApiKey(
            apiKey = trimmed,
            username = me.username.ifBlank { null },
            userId = me.id,
            avatarUrl = me.avatarUrl.ifBlank { null },
        )
        return me
    }

    /** 拉取当前账号资料；未登录或凭证失效返回 null。 */
    suspend fun currentUser(): UserMeDto? {
        if (!container.authStore.state.value.isLoggedIn) return null
        return runCatching { api().me() }.getOrElse { e ->
            if (e is ApiException.Unauthorized) null else throw e
        }
    }

    fun logout() {
        container.authStore.logout()
        _sync.value = SyncState.Idle
    }

    /** 收藏状态变化时把操作推给服务端（登录后才有效）。 */
    suspend fun pushFavorite(galleryId: Long, favorite: Boolean): Boolean {
        if (!container.authStore.state.value.isLoggedIn) return false
        return runCatching {
            val result = api().setFavorite(galleryId, favorite)
            result.favorite == favorite
        }.getOrDefault(false)
    }

    /**
     * 双向同步收藏：
     * 1. 拉取远端全部分页 → 写入本地（已有条目保留原文件夹与加入时间）；
     * 2. 本地有、远端没有的 → 逐条推送到远端（受限流影响，失败计数返回）。
     */
    suspend fun syncFavorites(): SyncReport {
        if (!container.authStore.state.value.isLoggedIn) throw ApiException.Unauthorized
        _sync.value = SyncState.Running(0)
        val jp = container.settings.current().preferJapaneseTitle
        val remoteIds = LinkedHashSet<Long>()
        val remote = ArrayList<com.mangaviewer.app.data.model.GalleryUi>()
        var page = 1
        var pages = 1
        while (page <= pages) {
            val dto = api().favorites(page)
            remote += dto.result.map { it.toUi(jp) }
            pages = (dto.num_pages ?: 1).coerceAtLeast(1)
            if (page >= MAX_SYNC_PAGES) break
            page++
        }
        remote.forEach { remoteIds += it.id }

        var pulled = 0
        for (ui in remote) {
            val existing = container.favoriteDao.find(ui.id)
            container.favoriteDao.upsert(
                FavoriteEntity(
                    galleryId = ui.id,
                    title = ui.title,
                    prettyTitle = ui.subtitle,
                    thumbPath = ui.thumbPath,
                    numPages = ui.numPages,
                    numFavorites = ui.numFavorites,
                    folder = existing?.folder ?: DEFAULT_FOLDER,
                    addedAt = existing?.addedAt ?: System.currentTimeMillis(),
                ),
            )
            pulled++
            _sync.value = SyncState.Running(pulled)
        }

        var pushed = 0
        var failed = 0
        val localOnly = container.favoriteDao.all().filter { it.galleryId !in remoteIds }
        for (entity in localOnly) {
            val ok = runCatching { api().setFavorite(entity.galleryId, true) }.isSuccess
            if (ok) pushed++ else failed++
        }
        val report = SyncReport(pulled = pulled, pushed = pushed, failedPush = failed, pages = pages)
        _sync.value = SyncState.Last(report, System.currentTimeMillis())
        return report
    }

    companion object {
        /** 服务端收藏分页上限保护，避免异常响应导致死循环。 */
        private const val MAX_SYNC_PAGES = 40
    }
}

data class SyncReport(
    val pulled: Int,
    val pushed: Int,
    val failedPush: Int,
    val pages: Int,
)

sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val done: Int) : SyncState
    data class Last(val report: SyncReport, val at: Long) : SyncState
}
