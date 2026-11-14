package com.mangaviewer.app.data.repo
import com.mangaviewer.app.i18n.AppLocale
import com.mangaviewer.app.i18n.failedPages


import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.db.DOWNLOAD_ORIGIN_APP
import com.mangaviewer.app.data.db.DOWNLOAD_ORIGIN_SCAN
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_DONE
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_FAILED
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_PAUSED
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_QUEUED
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_RUNNING
import com.mangaviewer.app.data.db.DownloadEntity
import com.mangaviewer.app.data.db.encodeNameList
import com.mangaviewer.app.data.local.Settings
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.data.remote.ApiException
import com.mangaviewer.app.data.remote.ImageProxyInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** 本地目录扫描的实时进度（UI 顶部的细进度条用），结束后带上一次性结果。 */
data class ScanProgress(
    val running: Boolean = false,
    val enriching: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val manual: Boolean = false,
    /** 每次扫描结束自增，UI 用它只消费一次结果、不重复弹提示。 */
    val reportId: Long = 0,
    val report: ScanReport? = null,
)

/** 一次扫描的结果；matched = 已有记录与磁盘状态对齐的条数。 */
data class ScanReport(
    val imported: Int = 0,
    val matched: Int = 0,
    val enriched: Int = 0,
    val failed: Int = 0,
    val pendingMetadata: Int = 0,
)

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
    private val jobs = ConcurrentHashMap<Long, Job>()

    /** 已从列表移除的 id；扫描期间即使拿到旧快照也不要重新导入。 */
    private val removedIds = ConcurrentHashMap.newKeySet<Long>()

    /** 设置页选择的自定义下载目录（SAF tree URI）；空 = 应用私有目录。 */
    @Volatile
    private var customTree: String = ""

    /** 历史上选过的目录，只用于读取（恢复默认后旧下载仍可读）。 */
    @Volatile
    private var customTrees: List<String> = emptyList()

    private val storage = DownloadStorage(container.appContext, { customTree }, { customTrees })

    /** 扫描互斥：自动扫描与手动「扫描本地」不会同时跑。 */
    private val scanMutex = Mutex()
    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()
    @Volatile
    private var lastAutoScanAt = 0L
    private var scanReportSeq = 0L

    init {
        // 下载可能早于设置页被调用，先同步读一次当前值，之后跟随设置变化。
        val initial = runCatching {
            kotlinx.coroutines.runBlocking { container.settings.settingsFlow.first() }
        }.getOrNull()
        customTree = initial?.downloadTree.orEmpty()
        customTrees = initial?.downloadTrees.orEmpty()
        scope.launch {
            container.settings.settingsFlow.collect {
                val changed = customTree != it.downloadTree || customTrees != it.downloadTrees
                customTree = it.downloadTree
                customTrees = it.downloadTrees
                // 切换下载目录后立即允许下一次自动扫描，不用等 5 分钟节流。
                if (changed) lastAutoScanAt = 0L
            }
        }
    }

    fun observeAll(): Flow<List<DownloadEntity>> = container.downloadDao.observeAll()

    /** 默认下载目录的绝对路径（设置页展示用）。 */
    fun internalDirText(): String = storage.internalText()

    /** 自定义下载目录的可读路径；未设置时为 null。 */
    fun customTreeText(): String? = storage.customTreeText()

    fun hasCustomTree(): Boolean = storage.hasCustomTree()

    /** 某部漫画本地已有的页面：应用私有目录 + 自定义目录合并，按页号排序。 */
    fun localPages(galleryId: Long): List<String> = storage.localSources(galleryId)

    /** 已下载到本地的页数（用于详情页/下载页展示"已有 N 页"）。 */
    fun downloadedPages(galleryId: Long, total: Int = Int.MAX_VALUE): Int =
        storage.countExisting(galleryId, total)

    suspend fun enqueue(ui: GalleryUi, pagePaths: List<String>) {
        val now = System.currentTimeMillis()
        removedIds.remove(ui.id)
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
                artistsJson = encodeNameList(ui.tags.filter { it.type == "artist" }.map { it.name }),
                categoriesJson = encodeNameList(ui.tags.filter { it.type == "category" }.map { it.name }),
                origin = DOWNLOAD_ORIGIN_APP,
            ),
        )
        start(ui.id)
    }

    fun start(galleryId: Long) {
        if (jobs[galleryId]?.isActive == true) return
        // 先登记 Job 再启动，避免任务瞬间完成时 runTask 的 finally 找不到自己的表项。
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { runTask(galleryId) }
        jobs[galleryId] = job
        job.start()
    }

    /**
     * 暂停：取消协程并立刻把状态落成 PAUSED。
     * 不清 jobs 表：让 remove() 还能拿到这个 Job 并等它真正退出；
     * runTask 的 finally 会按 Job 身份移除自己的表项，不会误删重启后的新任务。
     */
    fun pause(galleryId: Long) {
        jobs[galleryId]?.cancel()
        scope.launch {
            container.downloadDao.markPausedIfActive(galleryId, System.currentTimeMillis())
        }
    }

    /**
     * 删除：在仓库作用域里先等取消的下载协程退出，再删文件 / 数据库行。
     * 避免 OkHttp 阻塞请求在删除完成后又把图片写回目录，导致下次扫描「复活」。
     * 同时把 id 放进 removedIds，扫描拿到旧快照也不会重新导入。
     */
    fun remove(galleryId: Long) {
        removedIds.add(galleryId)
        scope.launch {
            jobs.remove(galleryId)?.cancelAndJoin()
            val deleted = withContext(Dispatchers.IO) { storage.deleteGallery(galleryId) }
            if (deleted) {
                container.downloadDao.delete(galleryId)
            } else {
                // 删除失败时保留记录，行上的错误提示让用户检查目录权限后重试。
                removedIds.remove(galleryId)
                val entity = container.downloadDao.find(galleryId)
                if (entity != null) {
                    container.downloadDao.upsert(
                        entity.copy(
                            error = AppLocale.strings().messages.deleteFailed,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

    fun pauseAll() {
        jobs.keys.toList().forEach { pause(it) }
    }

    /**
     * 把所有未完成任务（RUNNING / QUEUED / PAUSED）重新排进队列。
     *
     * 扫描导入的条目（origin = SCAN）不在此自动恢复：它们可能只是本地残缺目录，
     * 若用户没有明确点「继续」，不应因为前台服务启动就偷偷开始联网下载。
     * 用户显式点「全部继续」走 [startPaused]，不受此限制。
     */
    fun startAll() {
        scope.launch {
            container.downloadDao.all()
                .filter {
                    (it.status == DOWNLOAD_STATUS_PAUSED ||
                        it.status == DOWNLOAD_STATUS_QUEUED ||
                        it.status == DOWNLOAD_STATUS_RUNNING) &&
                        it.origin != DOWNLOAD_ORIGIN_SCAN
                }
                .forEach { start(it.galleryId) }
        }
    }

    fun startPaused() {
        scope.launch {
            // 用户显式点「全部继续」时连 pending 的缺页 / 网络错误一起重试；
            // 之前 error != null 的过滤会让「联网后点继续」的扫描条目被静默跳过。
            container.downloadDao.all()
                .filter { it.status == DOWNLOAD_STATUS_PAUSED }
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
                // 列级 UPDATE + 状态守卫，避免整行 REPLACE 覆盖并发补全的元数据。
                container.downloadDao.resetInterrupted(
                    id = entity.galleryId,
                    done = countExisting(entity.galleryId, entity.totalPages),
                    now = System.currentTimeMillis(),
                )
            }
        Unit
    }

    suspend fun cleanupOrphans(validIds: Set<Long>) = withContext(Dispatchers.IO) {
        storage.cleanupOrphans(validIds)
    }

    fun countExisting(galleryId: Long, total: Int): Int = storage.countExisting(galleryId, total)

    // ---------------- 本地目录扫描 / 匹配 ----------------

    /**
     * 自动扫描：只做本地目录识别与匹配，不联网。
     * 应用启动、进入下载页时调用；5 分钟内重复调用会被忽略（全目录扫描较重）。
     */
    fun autoScan() {
        val now = System.currentTimeMillis()
        if (now - lastAutoScanAt < AUTO_SCAN_INTERVAL_MS) return
        lastAutoScanAt = now
        scope.launch {
            runCatching { scanLocal(deep = false) }
        }
    }

    /**
     * 在仓库自己的作用域里发起一次扫描（离开下载页也会继续）。
     * 结果通过 [scanProgress] 的 reportId / report 一次性广播给 UI。
     */
    fun requestScan(deep: Boolean = true) {
        scope.launch {
            runCatching { scanLocal(deep) }
        }
    }

    /**
     * 扫描：识别 / 导入本地已有的漫画；deep=true 时并从网络补全缺失的标题、封面、作者、分类。
     * 返回 null 表示已有扫描在跑。扫描只读本地文件，不会删除任何内容。
     */
    suspend fun scanLocal(deep: Boolean = true): ScanReport? = withContext(Dispatchers.IO) {
        if (!scanMutex.tryLock()) return@withContext null
        _scanProgress.value = ScanProgress(running = true, manual = deep)
        try {
            val report = performScan(deep)
            scanReportSeq += 1
            _scanProgress.value = ScanProgress(
                manual = deep,
                reportId = scanReportSeq,
                report = report,
            )
            report
        } catch (e: CancellationException) {
            _scanProgress.value = ScanProgress()
            throw e
        } catch (e: Exception) {
            // 别静默消失：给 UI 一条失败报告，至少能弹一次提示。
            scanReportSeq += 1
            _scanProgress.value = ScanProgress(
                manual = deep,
                reportId = scanReportSeq,
                report = ScanReport(failed = 1),
            )
            throw e
        } finally {
            scanMutex.unlock()
        }
    }

    /** 单条补全（下载页每条缺失信息的快捷操作）；返回是否成功。 */
    suspend fun reloadMetadata(galleryId: Long): Boolean = withContext(Dispatchers.IO) {
        val entity = container.downloadDao.find(galleryId) ?: return@withContext false
        if (entity.status == DOWNLOAD_STATUS_RUNNING || entity.status == DOWNLOAD_STATUS_QUEUED) {
            return@withContext false
        }
        try {
            enrichOne(galleryId, null)
            container.downloadDao.find(galleryId)?.isMissingMetadata == false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun performScan(deep: Boolean): ScanReport {
        val local = storage.scanLocalGalleries()
        val now = System.currentTimeMillis()
        var imported = 0
        var matched = 0
        _scanProgress.value = ScanProgress(running = true, total = local.size)

        for ((index, gallery) in local.withIndex()) {
            // 扫描期间用户可能刚删掉这一本：旧快照不能再把它导回来。
            if (gallery.galleryId in removedIds) {
                _scanProgress.value = ScanProgress(running = true, processed = index + 1, total = local.size)
                continue
            }
            val latest = container.downloadDao.find(gallery.galleryId)
            when {
                latest == null -> {
                    val candidate = buildImportedEntity(gallery, now)
                    // 构建期间用户可能刚好从详情页加入下载：再确认一次，绝不覆盖新任务。
                    if (container.downloadDao.find(gallery.galleryId) == null) {
                        container.downloadDao.upsert(candidate)
                        imported++
                    }
                }

                latest.status == DOWNLOAD_STATUS_RUNNING ||
                    latest.status == DOWNLOAD_STATUS_QUEUED -> Unit

                else -> {
                    val merged = mergeScanned(latest, gallery, now)
                    if (merged != latest) {
                        // 补标题 / 封面期间任务可能被恢复：写回前再挡一次进行中的任务。
                        val guard = container.downloadDao.find(gallery.galleryId)
                        if (guard != null &&
                            guard.status != DOWNLOAD_STATUS_RUNNING &&
                            guard.status != DOWNLOAD_STATUS_QUEUED
                        ) {
                            container.downloadDao.upsert(merged)
                            matched++
                        }
                    }
                }
            }
            _scanProgress.value = ScanProgress(running = true, processed = index + 1, total = local.size)
        }

        val localPageCounts = local.associate { it.galleryId to it.pageCount }
        var enriched = 0
        var failed = 0
        if (deep) {
            val targets = container.downloadDao.all()
                .filter {
                    it.status != DOWNLOAD_STATUS_RUNNING &&
                        it.status != DOWNLOAD_STATUS_QUEUED &&
                        it.needsMetadata()
                }
                .take(MANUAL_ENRICH_LIMIT)
            _scanProgress.value = ScanProgress(
                running = true,
                enriching = true,
                total = targets.size,
            )
            for ((index, target) in targets.withIndex()) {
                try {
                    enrichOne(target.galleryId, localPageCounts[target.galleryId])
                    enriched++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ApiException.Network) {
                    // 反代没开 / 网络断了：后面的请求同样会失败，直接结束补全。
                    failed += targets.size - index
                    break
                } catch (e: Exception) {
                    failed++
                }
                _scanProgress.value = ScanProgress(
                    running = true,
                    enriching = true,
                    processed = index + 1,
                    total = targets.size,
                )
            }
        }

        val pending = container.downloadDao.all().count { it.needsMetadata() }
        return ScanReport(
            imported = imported,
            matched = matched,
            enriched = enriched,
            failed = failed,
            pendingMetadata = pending,
        )
    }

    private suspend fun buildImportedEntity(
        gallery: DownloadStorage.LocalGallery,
        now: Long,
    ): DownloadEntity {
        val favorite = runCatching { container.favoriteDao.find(gallery.galleryId) }.getOrNull()
        val history = runCatching { container.historyDao.find(gallery.galleryId) }.getOrNull()
        val knownTotal = favorite?.numPages?.takeIf { it > 0 }
            ?: history?.numPages?.takeIf { it > 0 }
        // 没有远端总页数时，用最大页号判断是否下完：1,2,5 页说明缺页，不能当 3/3 已完成。
        val total = maxOf(knownTotal ?: 0, gallery.maxPage)
        val done = minOf(gallery.pageCount, total)
        // 没有可信的总页数时不能宣称「已完成」：截断下载 0001..0050 看起来也是连续的。
        // 先保持可继续状态，等手动扫描从 API 拿到 numPages 再判断。
        val complete = knownTotal != null && total > 0 && done >= total
        return DownloadEntity(
            galleryId = gallery.galleryId,
            title = favorite?.title?.takeIf { it.isNotBlank() }
                ?: history?.title?.takeIf { it.isNotBlank() }
                ?: "#${gallery.galleryId}",
            thumbPath = favorite?.thumbPath?.takeIf { it.isNotBlank() }
                ?: history?.thumbPath?.takeIf { it.isNotBlank() }
                ?: gallery.cover.orEmpty(),
            totalPages = total,
            donePages = done,
            status = if (complete) DOWNLOAD_STATUS_DONE else DOWNLOAD_STATUS_PAUSED,
            pagesJson = EMPTY_PAGES_JSON,
            createdAt = gallery.lastModified.takeIf { it > 0 } ?: now,
            updatedAt = now,
            error = null,
            bytes = gallery.bytes,
            artistsJson = null,
            categoriesJson = null,
            origin = DOWNLOAD_ORIGIN_SCAN,
        )
    }

    /** 把磁盘上的实际页数同步进已有记录；只改数值，不删文件、不动进行中的任务。 */
    private suspend fun mergeScanned(
        entity: DownloadEntity,
        gallery: DownloadStorage.LocalGallery,
        now: Long,
    ): DownloadEntity {
        // 已有记录的总页数是可信的（收藏 / 历史 / API 写入过）；本地最大页号只在
        // totalPages 缺失时兜底，避免用户多放一张图就把已完成的任务改成「缺页」。
        val total = if (entity.totalPages > 0) entity.totalPages else gallery.maxPage
        val done = minOf(gallery.pageCount, total)
        // 扫描导入且尚未补全元数据的条目，totalPages 可能只是「本地最大页号」，
        // 不能据此升级为 DONE；但已经 DONE 的记录不要因为元数据为空而回退。
        val totalConfirmed = !entity.isImported || !entity.isMissingMetadata
        val status = when {
            total > 0 && done >= total &&
                (totalConfirmed || entity.status == DOWNLOAD_STATUS_DONE) -> DOWNLOAD_STATUS_DONE
            entity.status == DOWNLOAD_STATUS_DONE && done < total -> DOWNLOAD_STATUS_PAUSED
            else -> entity.status
        }
        var title = entity.title
        var thumb = entity.thumbPath
        if (title.isBlank() || title == "#${gallery.galleryId}" || thumb.isBlank()) {
            val favorite = runCatching { container.favoriteDao.find(gallery.galleryId) }.getOrNull()
            val history = runCatching { container.historyDao.find(gallery.galleryId) }.getOrNull()
            title = favorite?.title?.takeIf { it.isNotBlank() }
                ?: history?.title?.takeIf { it.isNotBlank() }
                ?: title
            thumb = favorite?.thumbPath?.takeIf { it.isNotBlank() }
                ?: history?.thumbPath?.takeIf { it.isNotBlank() }
                ?: thumb
        }
        val updated = entity.copy(
            title = title,
            thumbPath = thumb,
            totalPages = total,
            donePages = done,
            status = status,
            bytes = gallery.bytes,
            error = if (status == DOWNLOAD_STATUS_DONE) null else entity.error,
        )
        return if (updated != entity) updated.copy(updatedAt = now) else entity
    }

    /** 从服务端补全一部漫画的元数据，并按本地实际页数重算状态。 */
    private suspend fun enrichOne(galleryId: Long, localPageCount: Int?) {
        val before = container.downloadDao.find(galleryId) ?: return
        if (before.status == DOWNLOAD_STATUS_RUNNING || before.status == DOWNLOAD_STATUS_QUEUED) return
        val detail = container.galleries.detail(galleryId)
        val ui = detail.ui
        // 网络请求期间用户可能点了继续下载：以最新状态为准，且不覆盖进行中的任务。
        val latest = container.downloadDao.find(galleryId) ?: return
        if (latest.status == DOWNLOAD_STATUS_RUNNING || latest.status == DOWNLOAD_STATUS_QUEUED) return
        val total = ui.numPages.takeIf { it > 0 } ?: latest.totalPages
        // 只有本次扫描真的读到了这个目录才调整进度；SAF 被卸载 / 权限被回收时
        // countExisting 会返回 0，不能据此把已完成的任务降级。
        val done = if (localPageCount != null) {
            minOf(localPageCount, total)
        } else {
            latest.donePages
        }
        val status = if (localPageCount != null) {
            when {
                total > 0 && done >= total -> DOWNLOAD_STATUS_DONE
                latest.status == DOWNLOAD_STATUS_DONE -> DOWNLOAD_STATUS_PAUSED
                else -> latest.status
            }
        } else {
            // 读不到本地页数时只做「单向升级」：确认总页数后，原本 N/N 的条目
            // 可以补成 DONE，但绝不因为这次读不到文件而降级。
            when {
                total > 0 && latest.donePages >= total -> DOWNLOAD_STATUS_DONE
                else -> latest.status
            }
        }
        container.downloadDao.upsert(
            latest.copy(
                title = ui.title,
                thumbPath = ui.thumbPath,
                totalPages = total,
                donePages = done,
                status = status,
                error = if (status == DOWNLOAD_STATUS_DONE) null else latest.error,
                artistsJson = encodeNameList(ui.tags.filter { it.type == "artist" }.map { it.name }),
                categoriesJson = encodeNameList(ui.tags.filter { it.type == "category" }.map { it.name }),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun runTask(galleryId: Long) {
        val self = coroutineContext[Job]
        try {
            runTaskInner(galleryId)
        } finally {
            // 按 Job 身份移除：pause 后用户可能已经为同一 id 启动了新任务，
            // 旧任务退出时不能把新任务的表项删掉。
            if (self != null) jobs.remove(galleryId, self) else jobs.remove(galleryId)
        }
    }

    private suspend fun runTaskInner(galleryId: Long) {
        var entity = container.downloadDao.find(galleryId)
        android.util.Log.i("DownloadRepo", "runTask id=" + galleryId + " status=" + entity?.status)
        if (entity == null) return
        if (entity.status == DOWNLOAD_STATUS_DONE) return
        container.downloadDao.setStatus(galleryId, DOWNLOAD_STATUS_RUNNING, System.currentTimeMillis())
        var paths = decodePaths(entity.pagesJson)
        var loadFailure: Throwable? = null
        if (paths.isEmpty()) {
            // 扫描导入的记录没有远端页地址：续传前先向服务端要一次页列表与元数据。
            val result = runCatching { container.galleries.detail(galleryId) }
            loadFailure = result.exceptionOrNull()
            val resolved = result.getOrNull()
            if (resolved != null && resolved.pages.isNotEmpty()) {
                paths = resolved.pages.map { it.path }
                entity = entity.copy(
                    title = resolved.ui.title,
                    thumbPath = resolved.ui.thumbPath,
                    totalPages = paths.size,
                    pagesJson = encodePaths(paths),
                    artistsJson = encodeNameList(
                        resolved.ui.tags.filter { it.type == "artist" }.map { it.name },
                    ),
                    categoriesJson = encodeNameList(
                        resolved.ui.tags.filter { it.type == "category" }.map { it.name },
                    ),
                )
                container.downloadDao.upsert(entity)
            }
        }
        if (paths.isEmpty()) {
            // 导入条目只是缺一份在线页列表：网络问题下保持「可继续」，别误标成失败。
            val transient = loadFailure is ApiException.Network || loadFailure is ApiException.RateLimited
            val keepResumable = entity.isImported && transient
            container.downloadDao.upsert(
                entity.copy(
                    status = if (keepResumable) DOWNLOAD_STATUS_PAUSED else DOWNLOAD_STATUS_FAILED,
                    error = if (keepResumable) {
                        AppLocale.strings().messages.needNetworkForPages
                    } else {
                        AppLocale.strings().messages.pageListEmpty
                    },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return
        }
        val concurrency = container.settings.settingsFlow.first().concurrentDownloads.coerceIn(1, 8)
        val semaphore = Semaphore(concurrency)
        // 一次列出已有页号，避免每页都重新枚举所有 SAF 目录（也兼容 1.png 等非标准页名）。
        val presentPages = storage.existingIndices(galleryId)
        val done = AtomicInteger(presentPages.count { it in 1..paths.size })
        val failed = AtomicInteger(0)
        val progress = ProgressWriter(galleryId, entity, paths.size, done)
        try {
            coroutineScope {
                paths.forEachIndexed { index, path ->
                    launch {
                        semaphore.withPermit {
                            if ((index + 1) !in presentPages) {
                                if (fetch(path, galleryId, index + 1)) {
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
                else -> AppLocale.strings().failedPages(failed.get())
            }
            container.downloadDao.upsert(
                entity.copy(
                    donePages = finished,
                    status = status,
                    updatedAt = System.currentTimeMillis(),
                    bytes = storage.galleryBytes(galleryId),
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
                    error = e.message ?: AppLocale.strings().messages.downloadFailed,
                ),
            )
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

    private fun fetch(path: String, galleryId: Long, pageIndex: Int): Boolean {
        // base 为 null 表示直连模式：不加 /img?url= 包装，直接请求图床地址。
        val base = proxyBaseOrNull()
        for (candidate in ImageProxyInterceptor.candidates(path, thumb = false)) {
            val url = if (base == null) candidate else proxyUrl(base, candidate)
            val request = Request.Builder().url(url).build()
            try {
                container.client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body
                    val saved = body.byteStream().use { input ->
                        storage.writePage(galleryId, pageIndex, input)
                    }
                    if (saved) return true
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
        /** 手动扫描单次最多补全的条目数（API 限流 12 次/分钟，避免一次跑太久）。 */
        private const val MANUAL_ENRICH_LIMIT = 24
        private const val AUTO_SCAN_INTERVAL_MS = 5 * 60_000L
        private const val EMPTY_PAGES_JSON = "{\"pages\":[]}"

        fun statusLabel(status: String): String = when (status) {
            DOWNLOAD_STATUS_QUEUED -> AppLocale.strings().messages.statusQueued
            DOWNLOAD_STATUS_RUNNING -> AppLocale.strings().messages.statusRunning
            DOWNLOAD_STATUS_PAUSED -> AppLocale.strings().messages.statusPaused
            DOWNLOAD_STATUS_DONE -> AppLocale.strings().messages.statusDone
            DOWNLOAD_STATUS_FAILED -> AppLocale.strings().messages.statusFailed
            else -> status
        }
    }
}

/** 作者 / 分类尚未补全（旧版本记录或刚扫描导入）。 */
private fun DownloadEntity.needsMetadata(): Boolean =
    artistsJson == null || categoriesJson == null
