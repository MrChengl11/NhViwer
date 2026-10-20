package com.mangaviewer.app.data.remote

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 登录态存储（只支持 API Key）。
 *
 * v2 API 的 securitySchemes 有两种：`Authorization: User <access_token>` 与
 * `Authorization: Key <api_key>`。实测下来只有 API Key 通道真正可用：
 * - 访问令牌由网页登录产生，会过期，且网页登录在本机网络下无法完成（DNS 污染 + Turnstile 域名绑定）；
 * - 账号密码登录被服务端强制 Turnstile 人机验证（400 CAPTCHA solution is required），本应用不绕过验证码。
 *
 * 因此这里只保留 API Key：官方给第三方客户端推荐的、长期有效的凭证。
 */
class AuthStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("manga-auth", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun apiKey(): String? = _state.value.apiKey

    fun username(): String? = _state.value.username

    /** Authorization 头的值；未登录返回 null。 */
    fun authHeader(): String? = _state.value.apiKey
        ?.takeIf { it.isNotBlank() }
        ?.let { "Key " + it }

    fun saveApiKey(
        apiKey: String,
        username: String? = null,
        userId: Long? = null,
        avatarUrl: String? = null,
    ) {
        prefs.edit()
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_USERNAME, username)
            .putLong(KEY_USER_ID, userId ?: 0L)
            .putString(KEY_AVATAR, avatarUrl)
            .apply()
        _state.value = load()
    }

    fun logout() {
        prefs.edit().clear().apply()
        _state.value = load()
    }

    private fun load(): AuthState = AuthState(
        apiKey = prefs.getString(KEY_API_KEY, null),
        username = prefs.getString(KEY_USERNAME, null),
        userId = prefs.getLong(KEY_USER_ID, 0L),
        avatarUrl = prefs.getString(KEY_AVATAR, null),
    )

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_USERNAME = "username"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_AVATAR = "avatar_url"
    }
}

data class AuthState(
    val apiKey: String? = null,
    val username: String? = null,
    val userId: Long = 0L,
    val avatarUrl: String? = null,
) {
    val isLoggedIn: Boolean get() = !apiKey.isNullOrBlank()
}
