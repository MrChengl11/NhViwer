package com.mangaviewer.app.data.remote

/** 统一的接口异常类型，UI 层据此显示中文提示。 */
sealed class ApiException(message: String) : Exception(message) {

    class Network(message: String) : ApiException(message)

    class Http(val code: Int, val body: String) :
        ApiException("HTTP " + code + (if (body.isBlank()) "" else ": " + body.take(200)))

    class Parse(message: String) : ApiException(message)

    object Unauthorized : ApiException("需要登录后操作")

    class RateLimited(val retryAfterMs: Long) : ApiException("请求过于频繁，请稍后再试")

    /** API Key 被服务端拒绝（401/403）。 */
    class CredentialInvalid :
        ApiException("API Key 无效或已失效，请到网页端 账号设置 → API Keys 重新生成")
}
