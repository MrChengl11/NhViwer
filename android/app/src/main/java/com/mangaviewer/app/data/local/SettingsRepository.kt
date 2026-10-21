package com.mangaviewer.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "manga-settings")

enum class ReaderLayout { PAGED_HORIZONTAL, PAGED_DOUBLE, PAGED_VERTICAL, CONTINUOUS }

enum class ImageQuality(val query: String) {
    ORIGINAL(""),
    HIGH("?max=1600"),
    MEDIUM("?max=1080"),
    LOW("?max=720"),
}

data class Settings(
    val apiBase: String = DEFAULT_API_BASE,
    val adultConfirmed: Boolean = false,
    val readerLayout: ReaderLayout = ReaderLayout.PAGED_HORIZONTAL,
    val readerRtl: Boolean = true,
    val preloadCount: Int = 3,
    val imageQuality: ImageQuality = ImageQuality.ORIGINAL,
    val darkMode: Boolean = false,
    val dynamicColor: Boolean = true,
    val seedColor: Long = 0xFF6750A4,
    val concurrentDownloads: Int = 3,
    val preferJapaneseTitle: Boolean = true,
    val showPageNumber: Boolean = true,
    /** 下载器：是否常驻前台服务，锁屏/切后台也继续下。 */
    val foregroundDownloads: Boolean = true,
    /**
     * 连接方式：true = 直连（设备自己挂了代理/TUN，直接访问 nhentai.net）；
     * false = 经宿主本地反代（设备自己连不上时用）。
     *
     * 直连模式下图片地址不再改写成 /img?url=，而是让 Coil 直接请求原图床域名。
     */
    val directMode: Boolean = false,
    /** 自定义下载目录的 SAF tree URI（空 = 应用私有目录）。只影响切换之后的新下载。 */
    val downloadTree: String = "",
    /** 历史选择过的下载目录（SAF tree URI），用于继续读取旧目录里已下载的内容。 */
    val downloadTrees: List<String> = emptyList(),
) {
    /**
     * 实际的 API 根地址。直连模式固定走官网，反代模式走用户在设置里填的地址。
     * 所有仓库/UI 都必须用这个值，不要直接读 apiBase（那是「反代地址」这一项本身）。
     */
    val effectiveBase: String
        get() = if (directMode) DIRECT_API_BASE else apiBase

    companion object {
        /** 反代默认地址；执行 adb reverse tcp:8787 tcp:8787 后 127.0.0.1 即可复用。 */
        const val DEFAULT_API_BASE = "http://127.0.0.1:8787"

        /** 直连模式的根地址：设备自己能解析并连通 nhentai.net 时直接用它。 */
        const val DIRECT_API_BASE = "https://nhentai.net"

        /** 模拟器/真机常见替代地址，设置页可一键切换后重测。 */
        val ALT_API_BASES = listOf(
            DEFAULT_API_BASE,
            "http://10.0.2.2:8787",
        )
    }
}

/** 设置读写（DataStore Preferences）。所有写入都做范围收敛，避免 UI 传入越界值。 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val API_BASE = stringPreferencesKey("api_base")
        val ADULT = booleanPreferencesKey("adult_confirmed")
        val LAYOUT = stringPreferencesKey("reader_layout")
        val RTL = booleanPreferencesKey("reader_rtl")
        val PRELOAD = intPreferencesKey("preload")
        val QUALITY = stringPreferencesKey("image_quality")
        val DARK = booleanPreferencesKey("dark_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val SEED = stringPreferencesKey("seed_color")
        val CONCURRENCY = intPreferencesKey("concurrency")
        val JP_TITLE = booleanPreferencesKey("prefer_japanese_title")
        val PAGE_NUMBER = booleanPreferencesKey("show_page_number")
        val FOREGROUND_DOWNLOADS = booleanPreferencesKey("foreground_downloads")
        val DIRECT_MODE = booleanPreferencesKey("direct_mode")
        val DOWNLOAD_TREE = stringPreferencesKey("download_tree")
        val DOWNLOAD_TREES = stringSetPreferencesKey("download_trees")
    }

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            apiBase = p[Keys.API_BASE] ?: Settings.DEFAULT_API_BASE,
            adultConfirmed = p[Keys.ADULT] ?: false,
            readerLayout = ReaderLayout.entries.firstOrNull { it.name == p[Keys.LAYOUT] }
                ?: ReaderLayout.PAGED_HORIZONTAL,
            readerRtl = p[Keys.RTL] ?: true,
            preloadCount = p[Keys.PRELOAD] ?: 3,
            imageQuality = ImageQuality.entries.firstOrNull { it.name == p[Keys.QUALITY] }
                ?: ImageQuality.ORIGINAL,
            darkMode = p[Keys.DARK] ?: false,
            dynamicColor = p[Keys.DYNAMIC] ?: true,
            seedColor = p[Keys.SEED]?.toLongOrNull(16) ?: 0xFF6750A4,
            concurrentDownloads = p[Keys.CONCURRENCY] ?: 3,
            preferJapaneseTitle = p[Keys.JP_TITLE] ?: true,
            showPageNumber = p[Keys.PAGE_NUMBER] ?: true,
            foregroundDownloads = p[Keys.FOREGROUND_DOWNLOADS] ?: true,
            directMode = p[Keys.DIRECT_MODE] ?: false,
            downloadTree = p[Keys.DOWNLOAD_TREE] ?: "",
            downloadTrees = (p[Keys.DOWNLOAD_TREES] ?: emptySet()).toList(),
        )
    }

    suspend fun current(): Settings = settingsFlow.first()

    suspend fun setApiBase(value: String) = edit { it[Keys.API_BASE] = value.trim().trimEnd('/') }
    suspend fun setAdultConfirmed(value: Boolean) = edit { it[Keys.ADULT] = value }
    suspend fun setLayout(value: ReaderLayout) = edit { it[Keys.LAYOUT] = value.name }
    suspend fun setRtl(value: Boolean) = edit { it[Keys.RTL] = value }
    suspend fun setPreload(value: Int) = edit { it[Keys.PRELOAD] = value.coerceIn(1, 8) }
    suspend fun setQuality(value: ImageQuality) = edit { it[Keys.QUALITY] = value.name }
    suspend fun setDarkMode(value: Boolean) = edit { it[Keys.DARK] = value }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.DYNAMIC] = value }
    suspend fun setSeedColor(value: Long) = edit { it[Keys.SEED] = value.toString(16) }
    suspend fun setConcurrency(value: Int) = edit { it[Keys.CONCURRENCY] = value.coerceIn(1, 6) }
    suspend fun setPreferJapaneseTitle(value: Boolean) = edit { it[Keys.JP_TITLE] = value }
    suspend fun setShowPageNumber(value: Boolean) = edit { it[Keys.PAGE_NUMBER] = value }
    suspend fun setForegroundDownloads(value: Boolean) = edit { it[Keys.FOREGROUND_DOWNLOADS] = value }

    /** 切换连接方式：直连 / 本地反代。切换后下一次请求即生效（图片拦截器每次读取）。 */
    suspend fun setDirectMode(value: Boolean) = edit { it[Keys.DIRECT_MODE] = value }

    /**
     * 设置自定义下载目录（SAF tree URI）；传空字符串恢复应用私有目录。
     * 选过的目录会记入历史，恢复默认后旧目录里已下载的内容仍然可读。
     */
    suspend fun setDownloadTree(value: String) {
        val v = value.trim()
        edit { p ->
            p[Keys.DOWNLOAD_TREE] = v
            if (v.isNotBlank()) p[Keys.DOWNLOAD_TREES] = (p[Keys.DOWNLOAD_TREES] ?: emptySet()) + v
        }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
