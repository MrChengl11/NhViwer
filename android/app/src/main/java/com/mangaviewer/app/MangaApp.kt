package com.mangaviewer.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.i18n.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MangaApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SingletonImageLoader.setSafe { context: PlatformContext -> buildImageLoader(context) }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // 语言设置：默认英语，设置页切到简体中文后全局（含通知/服务）立即跟随。
            container.settings.settingsFlow.collect { AppLocale.apply(it.language) }
        }
        // 冷启动复位上次被系统杀掉的下载任务，避免列表卡在"下载中 0%"。
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.downloads.resumeInterrupted()
        }
    }

    private fun buildImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(container.imageProxyInterceptor)
            }
            .build()
}
