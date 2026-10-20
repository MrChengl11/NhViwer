package com.mangaviewer.app.data.repo

import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_DONE
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_FAILED
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_PAUSED
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_QUEUED
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_RUNNING
import com.mangaviewer.app.data.db.DownloadEntity
import com.mangaviewer.app.data.local.Settings
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.data.remote.ImageProxyInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * 下载仓库：入队 → 协程池按并发度抓图 → 落盘到 filesDir/downloads/<galleryId>/0001.jpg。
 *
 * 关键行为：
 * - 断点续传：已存在的非空文件直接算完成，重复点"下载"不会重下已完成的页；
 * - 进度节流写库（默认 400ms 一次），不会出现每页一次事务把主线程压住；
 * - 状态机明确：全部成功=DONE；全部失败=FAILED；部分成功=PAUSED（带错误文案，可继续）；
 * - 进程被杀后重启：残留的 RUNNING / QUEUED 记录在 [resumeInterrupted] 里复位成 PAUSED，不会永远转圈。
 */
class DownloadRepository(private val container: AppContainer) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<Long, Job>()

    fun observeAll(): Flow<List<DownloadEntity>> = container.downloadDao.observeAll()

    fun rootDir(): File = File(container.appContext.filesDir, "downloads").also { it.mkdirs() }

    fun galleryDir(galleryId: Long): File = File(rootDir(), galleryId.toString()).also { it.mkdirs() }

    fun pageFile(galleryId: Long, index: Int): File = File(galleryDir(galleryId), fileName(index))

    fun fileName(index: Int): String = "%04d.jpg".format(index)

    /** 已下载到本地的页数（用于详情页/下载页展示"已有 N 页"）。 */
    fun downloadedPages(galleryId: Long): Int = galleryDir(galleryId)
        .listFiles()
        ?.count { it.isFile && it.length() > 0L && it.name.endsWith(".jpg") }
        ?: 0

    suspend fun enqueue(ui: GalleryUi, pagePaths: List<String>) {
        val now = System.currentTimeMillis()
        val existing = container.downloadDao.find(ui.id)
        container.downloadDao.upsert(
            DownloadEntity(
                galleryId = ui.id,
                title = ui.title,
                thumbPath = ui.thumbPath,
                totalPages = pagePaths.size,
                donePages = countExisting(ui.id, pagePaths.size),
                status = DOWNLOAD_STATUS_QUEUED,
                pagesJson = encodePaths(pagePaths),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                error = null,
                bytes = existing?.bytes ?: 0L,
            ),
        )
        start(ui.id)
    }

    fun start(galleryId: Long) {
        if (jobs[galleryId]?.isActive == true) return
        jobs[galleryId] = scope.launch { runTask(galleryId) }
    }

    /** 暂停：取消协程并立刻把状态落成 PAUSED（runTask 的 finally 负责，这里只置兜底）。 */
    fun pause(galleryId: Long) {
        val job = jobs.remove(galleryId)
        job?.cancel()
        scope.launch {
            val entity = container.downloadDao.find(galleryId) ?: return@launch
            if (entity.status == DOWNLOAD_STATUS_RUNNING || entity.status == DOWNLOAD_STATUS_QUEUED) {
                container.downloadDao.upsert(
                    entity.copy(
                        donePages = countExisting(galleryId, entity.totalPages),
                        status = DOWNLOAD_STATUS_PAUSED,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    suspend fun remove(galleryId: Long) {
        jobs.remove(galleryId)?.cancel()
        withContext(Dispatchers.IO) { galleryDir(galleryId).deleteRecursively() }
        container.downloadDao.delete(galleryId)
    }

    fun pauseAll() {
        jobs.keys.toList().forEach { pause(it) }
    }

    /** 把所有未完成任务（RUNNING / QUEUED / PAUSED）重新排进队列。 */
    fun startAll() {
        scope.launch {
            container.downloadDao.all()
                .filter {
                    it.status == DOWNLOAD_STATUS_PAUSED ||
                        it.status == DOWNLOAD_STATUS_QUEUED ||
                        it.status == DOWNLOAD_STATUS_RUNNING
                }
                .forEach { start(it.galleryId) }
        }
    }

    fun startPaused() {
        scope.launch {
            container.downloadDao.all()
                .filter { it.status == DOWNLOAD_STATUS_PAUSED && it.error == null }
                .forEach { start(it.galleryId) }
        }
    }

    fun observeActiveCount(): Flow<Int> = container.downloadDao.observeActiveCount()

    /** 真正查一次数据库：是否还有排队/进行中的任务（服务收工前用它兜底确认）。 */
    suspend fun hasActiveTasks(): Boolean = withContext(Dispatchers.IO) {
        container.downloadDao.all().any {
            it.status == DOWNLOAD_STATUS_RUNNING ||
                it.status == DOWNLOAD_STATUS_QUEUED ||
                jobs[it.galleryId]?.isActive == true
        }
    }

    /** 冷启动时把上次被系统杀掉的 RUNNING/QUEUED 记录复位，避免界面卡在"下载中 0%"。 */
    suspend fun resumeInterrupted() = withContext(Dispatchers.IO) {
        val all = container.downloadDao.all()
        all.filter { it.status == DOWNLOAD_STATUS_RUNNING || it.status == DOWNLOAD_STATUS_QUEUED }
            .forEach { entity ->
                container.downloadDao.upsert(
                    entity.copy(
                        donePages = countExisting(entity.galleryId, entity.totalPages),
                        status = DOWNLOAD_STATUS_PAUSED,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        Unit
    }

    suspend fun cleanupOrphans(validIds: Set<Long>) = withContext(Dispatchers.IO) {
        rootDir().listFiles()?.forEach { file ->
            val id = file.name.toLongOrNull()
            if (id != null && id !in validIds) file.deleteRecursively()
        }
    }

    fun countExisting(galleryId: Long, total: Int): Int {
        var count = 0
        for (i in 1..total.coerceAtLeast(0)) if (pageFile(galleryId, i).length() > 0L) count++
        return count
    }

    private suspend fun runTask(galleryId: Long) {
        val entity = container.downloadDao.find(galleryId)
        android.util.Log.i("DownloadRepo", "runTask id=" + galleryId + " status=" + entity?.status)
        if (entity == null) return
        if (entity.status == DOWNLOAD_STATUS_DONE) return
        container.downloadDao.setStatus(galleryId, DOWNLOAD_STATUS_RUNNING, System.currentTimeMillis())
        val paths = decodePaths(entity.pagesJson)
        if (paths.isEmpty()) {
            container.downloadDao.upsert(
                entity.copy(
                    status = DOWNLOAD_STATUS_FAILED,
                    error = "页面列表为空",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return
        }
        val concurrency = container.settings.settingsFlow.first().concurrentDownloads.coerceIn(1, 8)
        val semaphore = Semaphore(concurrency)
        val done = AtomicInteger(countExisting(galleryId, paths.size))
        val failed = AtomicInteger(0)
        val progress = ProgressWriter(galleryId, entity, paths.size, done)
        try {
            coroutineScope {
                paths.forEachIndexed { index, path ->
                    launch {
                        semaphore.withPermit {
                            val target = pageFile(galleryId, index + 1)
                            if (target.length() <= 0L) {
                                if (fetch(path, target)) {
                                    done.incrementAndGet()
                                } else {
                                    failed.incrementAndGet()
                                }
                                progress.maybeFlush(done.get())
                            }
                        }
                    }
                }
            }
            progress.flush(done.get(), force = true)
            val total = paths.size
            val finished = done.get()
            val status = when {
                finished >= total -> DOWNLOAD_STATUS_DONE
                finished == 0 -> DOWNLOAD_STATUS_FAILED
                else -> DOWNLOAD_STATUS_PAUSED
            }
            val message = when {
                failed.get() == 0 -> null
                else -> "${failed.get()} 页下载失败，可点继续重试"
            }
            container.downloadDao.upsert(
                entity.copy(
                    donePages = finished,
                    status = status,
                    updatedAt = System.currentTimeMillis(),
                    bytes = directorySize(galleryDir(galleryId)),
                    error = message,
                ),
            )
        } catch (e: CancellationException) {
            progress.flush(done.get(), force = true, status = DOWNLOAD_STATUS_PAUSED)
            throw e
        } catch (e: Exception) {
            container.downloadDao.upsert(
                entity.copy(
                    donePages = done.get(),
                    status = DOWNLOAD_STATUS_FAILED,
                    updatedAt = System.currentTimeMillis(),
                    error = e.message ?: "下载失败",
                ),
            )
        } finally {
            jobs.remove(galleryId)
        }
    }

    /** 进度写入节流器：最多每 [intervalMs] 毫秒落一次库，结束时强制 flush。 */
    private inner class ProgressWriter(
        private val galleryId: Long,
        private val base: DownloadEntity,
        private val total: Int,
        private val done: AtomicInteger,
    ) {
        private var lastFlush = 0L

        suspend fun maybeFlush(current: Int) {
            val now = System.currentTimeMillis()
            if (now - lastFlush < INTERVAL_MS) return
            lastFlush = now
            persist(current)
        }

        suspend fun flush(current: Int, force: Boolean = false, status: String = DOWNLOAD_STATUS_RUNNING) {
            if (!force && current == base.donePages) return
            lastFlush = System.currentTimeMillis()
            container.downloadDao.upsert(
                base.copy(
                    donePages = minOf(current, total),
                    status = status,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }

        private suspend fun persist(current: Int) {
            container.downloadDao.upsert(
                base.copy(
                    donePages = minOf(current, total),
                    status = DOWNLOAD_STATUS_RUNNING,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun fetch(path: String, target: File): Boolean {
        // base 为 null 表示直连模式：不加 /img?url= 包装，直接请求图床地址。
        val base = proxyBaseOrNull()
        for (candidate in ImageProxyInterceptor.candidates(path, thumb = false)) {
            val url = if (base == null) candidate else proxyUrl(base, candidate)
            val request = Request.Builder().url(url).build()
            try {
                container.client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body
                    target.parentFile?.mkdirs()
                    val temp = File(target.parentFile, target.name + ".part")
                    body.byteStream().use { input ->
                        temp.outputStream().use { out -> input.copyTo(out) }
                    }
                    if (temp.length() > 0L) {
                        if (target.exists()) target.delete()
                        temp.renameTo(target)
                        return true
                    }
                    temp.delete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 换下一个图床
            }
        }
        return false
    }

    /** 反代地址；直连模式返回 null（调用方据此跳过 /img?url= 改写）。 */
    private fun proxyBaseOrNull(): String? = runCatching {
        val s = kotlinx.coroutines.runBlocking { container.settings.settingsFlow.first() }
        if (s.directMode) null else s.apiBase
    }.getOrNull()

    private fun proxyUrl(base: String, remote: String): String =
        base.trimEnd('/') + "/img?url=" + java.net.URLEncoder.encode(remote, "UTF-8")

    private fun directorySize(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun encodePaths(paths: List<String>): String {
        val array = JSONArray()
        paths.forEach { array.put(it) }
        return JSONObject().put("pages", array).toString()
    }

    private fun decodePaths(raw: String): List<String> = runCatching {
        val array = JSONObject(raw).optJSONArray("pages") ?: JSONArray()
        (0 until array.length()).map { array.getString(it) }
    }.getOrDefault(emptyList())

    companion object {
        private const val INTERVAL_MS = 400L

        fun statusLabel(status: String): String = when (status) {
            DOWNLOAD_STATUS_QUEUED -> "排队中"
            DOWNLOAD_STATUS_RUNNING -> "下载中"
            DOWNLOAD_STATUS_PAUSED -> "已暂停"
            DOWNLOAD_STATUS_DONE -> "已完成"
            DOWNLOAD_STATUS_FAILED -> "失败"
            else -> status
        }
    }
}
