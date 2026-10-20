package com.mangaviewer.app.data.remote

import coil3.intercept.Interceptor
import coil3.request.ImageResult
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Coil 拦截器：把 i*.nhentai.net / t*.nhentai.net / tt*.nhentai.net 的图片地址改写成
 * [反代]/img?url=原始地址，让所有图片流量都走宿主反代。
 *
 * [proxyBaseProvider] 返回 null 表示「直连模式」：设备自己能访问图床（例如挂了全局代理 / TUN），
 * 此时不做任何改写，让 Coil 直接请求原图床域名。
 */
class ImageProxyInterceptor(private val proxyBaseProvider: () -> String?) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val data = chain.request.data
        val raw = when (data) {
            is String -> data
            is HttpUrl -> data.toString()
            else -> null
        }
        if (raw != null && isNhentaiImage(raw)) {
            val rewritten = rewrite(raw)
            // 直连模式 rewrite 原样返回，此时不重建请求
            if (rewritten != raw) {
                val request = chain.request.newBuilder().data(rewritten).build()
                return chain.withRequest(request).proceed()
            }
        }
        return chain.proceed()
    }

    /** 反代模式：<反代>/img?url=原始地址；直连模式：原样返回。 */
    fun rewrite(raw: String): String {
        val base = proxyBaseProvider()?.trimEnd('/') ?: return raw
        return base + "/img?url=" + java.net.URLEncoder.encode(raw, "UTF-8")
    }

    companion object {
        // 注意必须包含 i* 主机（阅读页大图走 i1..i4），否则这些地址不会被改写，
        // 会直连被 DNS 污染的域名而超时，表现为阅读器整屏黑。
        private val HOST_RE = Regex("^([it]t?[0-9]{1,2})\\.nhentai\\.net$")

        fun isNhentaiImage(url: String): Boolean {
            val host = runCatching { url.toHttpUrl().host }.getOrNull() ?: return false
            return HOST_RE.matches(host) || host == "nhentai.net"
        }

        /** 大图主机顺序：i* 正常时优先，t* 作为实测更稳的后备。 */
        private val BIG_HOSTS = listOf("i2", "i3", "i4", "i1", "t3", "t1", "t4", "t2")

        /** 缩略图主机顺序：t* 体积小、命中率高，tt* 兜底。 */
        private val THUMB_HOSTS = listOf("t1", "t2", "t3", "t4", "tt1", "tt2", "tt3", "tt4")

        /**
         * 图片站候选地址（按成功率排序，调用方按序 failover）。
         *
         * 本机实测：i*.nhentai.net 时好时坏（曾整体 520，之后恢复 200），t* 一直稳定，
         * nhentai 官方图床还有 tt 开头的域名。两个列表都把 t 系列作为后备，
         * 保证任一图床抖动时仍能出图。
         */
        fun candidates(path: String, thumb: Boolean): List<String> {
            val clean = path.trim('/')
            val hosts = if (thumb) THUMB_HOSTS else BIG_HOSTS
            return hosts.map { "https://" + it + ".nhentai.net/" + clean }
        }

        /** 阅读页首选地址。 */
        fun viewerCandidate(path: String, withQuality: (String) -> String): String {
            val base = "https://i2.nhentai.net/" + path.trim('/')
            return withQuality(base)
        }

        /** 阅读页的全部候选地址（带质量参数）。 */
        fun viewerCandidates(path: String, withQuality: (String) -> String): List<String> =
            candidates(path, thumb = false).map(withQuality)
    }
}
