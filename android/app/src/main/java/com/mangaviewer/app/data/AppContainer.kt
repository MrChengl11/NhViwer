package com.mangaviewer.app.data

import android.content.Context
import androidx.room.Room
import com.mangaviewer.app.data.db.AppDatabase
import com.mangaviewer.app.data.local.SettingsRepository
import com.mangaviewer.app.data.remote.AuthStore
import com.mangaviewer.app.data.remote.ImageProxyInterceptor
import com.mangaviewer.app.data.remote.NhentaiApi
import com.mangaviewer.app.data.repo.AccountRepository
import com.mangaviewer.app.data.repo.CommentRepository
import com.mangaviewer.app.data.repo.DownloadRepository
import com.mangaviewer.app.data.repo.FavoriteRepository
import com.mangaviewer.app.data.repo.GalleryRepository
import com.mangaviewer.app.data.repo.HistoryRepository
import com.mangaviewer.app.data.repo.TagIndexRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 手写依赖容器。应用规模不需要 Hilt：所有依赖在此显式创建，方便阅读与测试替换。
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "manga.db",
    ).build()

    val favoriteDao = database.favoriteDao()
    val downloadDao = database.downloadDao()
    val historyDao = database.historyDao()
    val commentDao = database.commentDraftDao()

    val settings = SettingsRepository(appContext)

    val authStore = AuthStore(appContext)

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .writeTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val apiCache = java.util.concurrent.ConcurrentHashMap<String, NhentaiApi>()

    fun api(baseUrl: String): NhentaiApi {
        val trimmed = baseUrl.trimEnd('/')
        return apiCache.getOrPut(trimmed) {
            NhentaiApi(client = client, baseUrl = trimmed, authStore = authStore)
        }
    }

    /**
     * Coil 拦截器：反代模式下把远端 https 图片地址改写成宿主反代的 /img?url=；
     * 直连模式下返回 null，不做改写（设备自己挂着代理时图片直连图床更快）。
     */
    val imageProxyInterceptor = ImageProxyInterceptor {
        val s = runBlocking { settings.settingsFlow.first() }
        if (s.directMode) null else s.apiBase
    }

    val account: AccountRepository by lazy { AccountRepository(this) }
    val galleries: GalleryRepository by lazy { GalleryRepository(this) }
    val favorites: FavoriteRepository by lazy { FavoriteRepository(this) }
    val downloads: DownloadRepository by lazy { DownloadRepository(this) }
    val comments: CommentRepository by lazy { CommentRepository(this) }
    val history: HistoryRepository by lazy { HistoryRepository(this) }
    val tagIndex: TagIndexRepository by lazy { TagIndexRepository(this) }
}