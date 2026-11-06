package com.mangaviewer.app.data.remote

import com.mangaviewer.app.i18n.AppLocale

/** 统一的接口异常类型；提示文案跟随当前界面语言（默认英语）。 */
sealed class ApiException(message: String) : Exception(message) {

    class Network(message: String) : ApiException(message)

    class Http(val code: Int, val body: String) :
        ApiException("HTTP " + code + (if (body.isBlank()) "" else ": " + body.take(200)))

    class Parse(message: String) : ApiException(message)

    object Unauthorized : ApiException("") {
        override val message: String
            get() = AppLocale.strings().messages.unauthorized
    }

    class RateLimited(val retryAfterMs: Long) : ApiException(AppLocale.strings().messages.rateLimited)

    /** API Key 被服务端拒绝（401/403）。 */
    class CredentialInvalid : ApiException(AppLocale.strings().messages.invalidKey)
}
