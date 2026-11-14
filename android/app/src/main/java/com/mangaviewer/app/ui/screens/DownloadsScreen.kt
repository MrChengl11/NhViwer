package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_DONE
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_FAILED
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_PAUSED
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_QUEUED
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_RUNNING
import com.mangaviewer.app.data.db.DownloadEntity
import com.mangaviewer.app.data.local.DownloadsViewMode
import com.mangaviewer.app.data.repo.DownloadRepository
import com.mangaviewer.app.data.repo.ScanProgress
import com.mangaviewer.app.download.DownloadService
import com.mangaviewer.app.i18n.LocalStrings
import com.mangaviewer.app.i18n.UiStrings
import com.mangaviewer.app.i18n.downloadsTitle
import com.mangaviewer.app.i18n.enriching
import com.mangaviewer.app.i18n.filterDateRange
import com.mangaviewer.app.i18n.moreActionsFor
import com.mangaviewer.app.i18n.pendingInfo
import com.mangaviewer.app.i18n.removeDownloadBody
import com.mangaviewer.app.i18n.removeDownloadTitle
import com.mangaviewer.app.i18n.removeImportedBody
import com.mangaviewer.app.i18n.scanFailed
import com.mangaviewer.app.i18n.scanResult
import com.mangaviewer.app.ui.components.EmptyBox
import com.mangaviewer.app.ui.components.GalleryThumb
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private data class DownloadBucket(val key: String, val label: String, val items: List<DownloadEntity>)

private val BUCKET_ORDER = listOf(
    DOWNLOAD_STATUS_RUNNING,
    DOWNLOAD_STATUS_QUEUED,
    DOWNLOAD_STATUS_PAUSED,
    DOWNLOAD_STATUS_FAILED,
    DOWNLOAD_STATUS_DONE,
)

private val INITIALS: List<String> = listOf("#") + ('A'..'Z').map { it.toString() }

private fun bucketLabel(key: String, s: UiStrings): String = when (key) {
    DOWNLOAD_STATUS_RUNNING -> s.downloads.filterRunning
    DOWNLOAD_STATUS_QUEUED -> s.downloads.filterQueued
    DOWNLOAD_STATUS_PAUSED -> s.downloads.filterPaused
    DOWNLOAD_STATUS_FAILED -> s.downloads.filterFailed
    DOWNLOAD_STATUS_DONE -> s.downloads.filterDone
    else -> key
}

private fun bucketOf(list: List<DownloadEntity>, s: UiStrings): List<DownloadBucket> =
    BUCKET_ORDER.mapNotNull { key ->
        val items = list.filter { it.status == key }
        if (items.isEmpty()) null else DownloadBucket(key, bucketLabel(key, s), items)
    }

/**
 * 下载页筛选条件。日期基于下载记录的 createdAt（扫描导入的条目用目录修改时间），
 * 首字母取标题里第一个字母 / 数字（中日文与符号统一归到 "#"）。
 */
private data class DownloadFilter(
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val initial: String? = null,
    val artists: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
) {
    val activeCount: Int
        get() = (if (fromMillis != null || toMillis != null) 1 else 0) +
            (if (initial != null) 1 else 0) +
            (if (artists.isNotEmpty()) 1 else 0) +
            (if (categories.isNotEmpty()) 1 else 0) +
            (if (statuses.isNotEmpty()) 1 else 0)

    val isActive: Boolean get() = activeCount > 0

    fun matches(entity: DownloadEntity): Boolean {
        if (fromMillis != null && entity.createdAt < fromMillis) return false
        if (toMillis != null && entity.createdAt >= toMillis) return false
        if (initial != null && titleInitial(entity.title) != initial) return false
        if (artists.isNotEmpty() && entity.artists.none { it in artists }) return false
        if (categories.isNotEmpty() && entity.categories.none { it in categories }) return false
        if (statuses.isNotEmpty() && entity.status !in statuses) return false
        return true
    }
}

private val DownloadFilterSaver = listSaver<DownloadFilter, Any?>(
    save = { f ->
        listOf(
            f.fromMillis,
            f.toMillis,
            f.initial,
            ArrayList(f.artists),
            ArrayList(f.categories),
            ArrayList(f.statuses),
        )
    },
    restore = { v ->
        DownloadFilter(
            fromMillis = v.getOrNull(0) as? Long,
            toMillis = v.getOrNull(1) as? Long,
            initial = v.getOrNull(2) as? String,
            artists = (v.getOrNull(3) as? ArrayList<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
            categories = (v.getOrNull(4) as? ArrayList<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
            statuses = (v.getOrNull(5) as? ArrayList<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
        )
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(container: AppContainer, navController: NavController) {
    val downloads by container.downloads.observeAll().collectAsState(initial = emptyList())
    val settings by container.settings.settingsFlow.collectAsState(initial = null)
    val scan by container.downloads.scanProgress.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val s = LocalStrings.current
    val snackbar = remember { SnackbarHostState() }

    var filter by rememberSaveable(stateSaver = DownloadFilterSaver) {
        mutableStateOf(DownloadFilter())
    }
    var showFilter by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<DownloadEntity?>(null) }
    var lastReportId by rememberSaveable { mutableStateOf(0L) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    val grid = settings?.downloadsViewMode == DownloadsViewMode.GRID
    val visible = remember(downloads, filter) { downloads.filter { filter.matches(it) } }
    val buckets = remember(visible, s) { bucketOf(visible, s) }
    val active = downloads.count {
        it.status == DOWNLOAD_STATUS_RUNNING || it.status == DOWNLOAD_STATUS_QUEUED
    }
    val visibleBytes = visible.sumOf { it.bytes }
    val pendingMetadata = downloads.count { it.isMissingMetadata }
    val canResume = active == 0 && downloads.any { it.status == DOWNLOAD_STATUS_PAUSED }

    val openEntity: (DownloadEntity) -> Unit = { entity ->
        navController.navigate("reader/${entity.galleryId}?page=${maxOf(1, entity.donePages)}")
    }
    val toggleEntity: (DownloadEntity) -> Unit = { entity ->
        if (entity.status == DOWNLOAD_STATUS_DONE) {
            openEntity(entity)
        } else {
            toggleDownload(container, settings?.foregroundDownloads == true, entity)
        }
    }

    // 进入页面时自动识别下载目录里已有的漫画；仓库内部做 5 分钟节流。
    LaunchedEffect(Unit) { container.downloads.autoScan() }

    // 扫描在仓库作用域里跑，结果通过 reportId 一次性广播；自动扫描有变化也提示一次。
    LaunchedEffect(scan.reportId, scan.running) {
        val report = scan.report
        // 用 != 而不是 >：进程重启后 reportId 从 1 重新计数，旧的 lastReportId 不能把新结果挡住。
        if (!scan.running && report != null && scan.reportId != lastReportId) {
            lastReportId = scan.reportId
            val changes = report.imported + report.matched + report.enriched + report.failed
            if (scan.manual || changes > 0) {
                val text = if (changes == 0) {
                    s.downloads.scanNoChanges
                } else {
                    s.scanResult(report.imported, report.matched, report.enriched) +
                        if (report.failed > 0) s.scanFailed(report.failed) else ""
                }
                snackbar.showSnackbar(text)
            }
        }
    }

    fun requestScan() {
        if (scan.running) {
            scope.launch { snackbar.showSnackbar(s.downloads.scanBusy) }
            return
        }
        container.downloads.requestScan(deep = true)
    }

    fun reloadInfo(entity: DownloadEntity) {
        scope.launch {
            val ok = container.downloads.reloadMetadata(entity.galleryId)
            snackbar.showSnackbar(if (ok) s.downloads.infoUpdated else s.downloads.infoFailed)
        }
    }

    fun pauseAll() {
        container.downloads.pauseAll()
        DownloadService.stop(context)
    }

    fun resumeAll() {
        container.downloads.startPaused()
        if (settings?.foregroundDownloads == true) DownloadService.start(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.downloadsTitle(downloads.size)) },
                actions = {
                    IconButton(onClick = { showFilter = true }) {
                        BadgedBox(
                            badge = {
                                if (filter.activeCount > 0) Badge { Text(filter.activeCount.toString()) }
                            },
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = s.downloads.filter)
                        }
                    }
                    IconButton(onClick = { requestScan() }, enabled = !scan.running) {
                        if (scan.running) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(20.dp)
                                    .semantics { contentDescription = s.downloads.scanning },
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.FolderOpen, contentDescription = s.downloads.scanLocal)
                        }
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                container.settings.setDownloadsViewMode(
                                    if (grid) DownloadsViewMode.LIST else DownloadsViewMode.GRID,
                                )
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (grid) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = if (grid) s.downloads.viewList else s.downloads.viewGrid,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (downloads.isEmpty()) {
                EmptyBox(if (scan.running) s.downloads.scanning else s.downloads.empty)
            } else {
                SummaryBar(
                    total = downloads.size,
                    shown = visible.size,
                    active = active,
                    bytes = visibleBytes,
                    canPause = active > 0,
                    canResume = canResume,
                    onPauseAll = { pauseAll() },
                    onResumeAll = { resumeAll() },
                )
                when {
                    scan.running -> ScanProgressBar(scan)
                    pendingMetadata > 0 -> PendingInfoBar(pendingMetadata, onScan = { requestScan() })
                }
                if (filter.isActive) {
                    ActiveFilterBar(
                        filter = filter,
                        onFilterChange = { filter = it },
                    )
                }
                when {
                    visible.isEmpty() -> Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            s.downloads.noFilterResult,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { filter = DownloadFilter() }) {
                            Text(s.downloads.filterClear)
                        }
                    }

                    grid -> DownloadGrid(
                        buckets = buckets,
                        state = gridState,
                        onClick = openEntity,
                        onToggle = toggleEntity,
                        onReloadInfo = ::reloadInfo,
                        onRemove = { entity -> pendingRemove = entity },
                    )

                    else -> DownloadList(
                        buckets = buckets,
                        state = listState,
                        onClick = openEntity,
                        onToggle = toggleEntity,
                        onReloadInfo = ::reloadInfo,
                        onRemove = { entity -> pendingRemove = entity },
                    )
                }
            }
        }
    }

    if (showFilter) {
        DownloadFilterSheet(
            filter = filter,
            downloads = downloads,
            onFilterChange = { filter = it },
            onDismiss = { showFilter = false },
        )
    }

    pendingRemove?.let { entity ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text(s.removeDownloadTitle(entity.title)) },
            text = {
                Text(
                    if (entity.isImported) {
                        s.removeImportedBody(entity.donePages, formatBytes(entity.bytes))
                    } else {
                        s.removeDownloadBody(entity.donePages, formatBytes(entity.bytes))
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = entity.galleryId
                    pendingRemove = null
                    // remove() 内部用仓库作用域：离开下载页也不会把删除流程打断。
                    container.downloads.remove(id)
                }) { Text(s.common.delete) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text(s.common.cancel) }
            },
        )
    }
}

private fun toggleDownload(container: AppContainer, foreground: Boolean, entity: DownloadEntity) {
    when (entity.status) {
        DOWNLOAD_STATUS_RUNNING, DOWNLOAD_STATUS_QUEUED -> container.downloads.pause(entity.galleryId)
        else -> {
            container.downloads.start(entity.galleryId)
            if (foreground) DownloadService.start(container.appContext)
        }
    }
}

@Composable
private fun DownloadList(
    buckets: List<DownloadBucket>,
    state: LazyListState,
    onClick: (DownloadEntity) -> Unit,
    onToggle: (DownloadEntity) -> Unit,
    onReloadInfo: (DownloadEntity) -> Unit,
    onRemove: (DownloadEntity) -> Unit,
) {
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        buckets.forEach { bucket ->
            item(key = "h-${bucket.key}") {
                BucketHeader(bucket)
            }
            items(bucket.items, key = { it.galleryId }) { entity ->
                DownloadRow(
                    entity = entity,
                    onClick = { onClick(entity) },
                    onToggle = { onToggle(entity) },
                    onReloadInfo = { onReloadInfo(entity) },
                    onRemove = { onRemove(entity) },
                )
            }
        }
    }
}

@Composable
private fun DownloadGrid(
    buckets: List<DownloadBucket>,
    state: LazyGridState,
    onClick: (DownloadEntity) -> Unit,
    onToggle: (DownloadEntity) -> Unit,
    onReloadInfo: (DownloadEntity) -> Unit,
    onRemove: (DownloadEntity) -> Unit,
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        buckets.forEach { bucket ->
            item(key = "h-${bucket.key}", span = { GridItemSpan(maxLineSpan) }) {
                BucketHeader(bucket)
            }
            gridItems(bucket.items, key = { it.galleryId }) { entity ->
                DownloadGridCard(
                    entity = entity,
                    onClick = { onClick(entity) },
                    onToggle = { onToggle(entity) },
                    onReloadInfo = { onReloadInfo(entity) },
                    onRemove = { onRemove(entity) },
                )
            }
        }
    }
}

@Composable
private fun BucketHeader(bucket: DownloadBucket) {
    Text(
        "${bucket.label} · ${bucket.items.size}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun SummaryBar(
    total: Int,
    shown: Int,
    active: Int,
    bytes: Long,
    canPause: Boolean,
    canResume: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
) {
    val s = LocalStrings.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InfoChip(s.downloads.headerTask, if (shown == total) total.toString() else "$shown/$total")
            InfoChip(s.downloads.headerProgress, active.toString())
            InfoChip(s.downloads.headerSize, formatBytes(bytes))
            Spacer(Modifier.weight(1f))
            if (canPause) {
                AssistChip(onClick = onPauseAll, label = { Text(s.downloads.pauseAll) })
            } else if (canResume) {
                AssistChip(onClick = onResumeAll, label = { Text(s.downloads.resumeAll) })
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ScanProgressBar(scan: ScanProgress) {
    val s = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            if (scan.enriching) s.enriching(scan.processed, scan.total) else s.downloads.scanning,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (scan.total > 0) {
            LinearProgressIndicator(
                progress = { (scan.processed.toFloat() / scan.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

@Composable
private fun PendingInfoBar(count: Int, onScan: () -> Unit) {
    val s = LocalStrings.current
    AssistChip(
        onClick = onScan,
        label = { Text(s.pendingInfo(count)) },
        leadingIcon = { Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp)) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

@Composable
private fun ActiveFilterBar(
    filter: DownloadFilter,
    onFilterChange: (DownloadFilter) -> Unit,
) {
    val s = LocalStrings.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        if (filter.fromMillis != null || filter.toMillis != null) {
            item(key = "date") {
                InputChip(
                    selected = true,
                    onClick = { onFilterChange(filter.copy(fromMillis = null, toMillis = null)) },
                    label = { Text("${s.downloads.filterDate} · ${dateFilterLabel(filter, s)}") },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        filter.initial?.let { letter ->
            item(key = "initial") {
                InputChip(
                    selected = true,
                    onClick = { onFilterChange(filter.copy(initial = null)) },
                    label = { Text("${s.downloads.filterInitial} · $letter") },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        if (filter.artists.isNotEmpty()) {
            item(key = "artist") {
                InputChip(
                    selected = true,
                    onClick = { onFilterChange(filter.copy(artists = emptySet())) },
                    label = { Text("${s.downloads.filterArtist} · ${filter.artists.size}") },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        if (filter.categories.isNotEmpty()) {
            item(key = "category") {
                InputChip(
                    selected = true,
                    onClick = { onFilterChange(filter.copy(categories = emptySet())) },
                    label = { Text("${s.downloads.filterCategory} · ${filter.categories.size}") },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        if (filter.statuses.isNotEmpty()) {
            item(key = "status") {
                InputChip(
                    selected = true,
                    onClick = { onFilterChange(filter.copy(statuses = emptySet())) },
                    label = { Text("${s.downloads.filterStatus} · ${filter.statuses.size}") },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        item(key = "clear") {
            TextButton(onClick = { onFilterChange(DownloadFilter()) }) {
                Text(s.downloads.filterClear)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DownloadFilterSheet(
    filter: DownloadFilter,
    downloads: List<DownloadEntity>,
    onFilterChange: (DownloadFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }
    val facets = remember(downloads) {
        FilterFacets(
            artists = downloads.flatMap { it.artists }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER),
            categories = downloads.flatMap { it.categories }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER),
            statusCounts = BUCKET_ORDER.mapNotNull { key ->
                val count = downloads.count { it.status == key }
                if (count == 0) null else key to count
            },
        )
    }
    val today = presetToday()
    val last7 = presetDays(7)
    val last30 = presetDays(30)
    val thisYear = presetYear()
    val customSelected = filter.fromMillis != null &&
        listOf(today, last7, last30, thisYear).none {
            filter.fromMillis == it.from && filter.toMillis == it.to
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = 640.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.downloads.filterTitle,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onFilterChange(DownloadFilter()) }) {
                    Text(s.downloads.reset)
                }
            }

            FilterSectionTitle(s.downloads.filterDate)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FilterChip(
                    selected = filter.fromMillis == null && filter.toMillis == null,
                    onClick = { onFilterChange(filter.copy(fromMillis = null, toMillis = null)) },
                    label = { Text(s.downloads.dateAll) },
                )
                DatePresetChip(filter, today, s.downloads.dateToday, onFilterChange)
                DatePresetChip(filter, last7, s.downloads.date7, onFilterChange)
                DatePresetChip(filter, last30, s.downloads.date30, onFilterChange)
                DatePresetChip(filter, thisYear, s.downloads.dateYear, onFilterChange)
                FilterChip(
                    selected = customSelected,
                    onClick = { showDatePicker = true },
                    label = { Text(s.downloads.dateCustom) },
                    leadingIcon = {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
            }
            if (filter.fromMillis != null || filter.toMillis != null) {
                Text(
                    dateFilterLabel(filter, s),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            FilterDivider()
            FilterSectionTitle(s.downloads.filterInitial)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item(key = "all") {
                    FilterChip(
                        selected = filter.initial == null,
                        onClick = { onFilterChange(filter.copy(initial = null)) },
                        label = { Text(s.downloads.filterAll) },
                    )
                }
                items(INITIALS, key = { it }) { letter ->
                    FilterChip(
                        selected = filter.initial == letter,
                        onClick = {
                            onFilterChange(filter.copy(initial = if (filter.initial == letter) null else letter))
                        },
                        label = { Text(letter) },
                    )
                }
            }

            FilterDivider()
            FilterSectionTitle(s.downloads.filterArtist)
            if (facets.artists.isEmpty()) {
                FilterHint(s.downloads.filterNoArtists)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    facets.artists.forEach { artist ->
                        FilterChip(
                            selected = artist in filter.artists,
                            onClick = {
                                onFilterChange(filter.copy(artists = filter.artists.toggle(artist)))
                            },
                            label = { Text(artist) },
                        )
                    }
                }
            }

            FilterDivider()
            FilterSectionTitle(s.downloads.filterCategory)
            if (facets.categories.isEmpty()) {
                FilterHint(s.downloads.filterNoCategories)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    facets.categories.forEach { category ->
                        FilterChip(
                            selected = category in filter.categories,
                            onClick = {
                                onFilterChange(filter.copy(categories = filter.categories.toggle(category)))
                            },
                            label = { Text(category) },
                        )
                    }
                }
            }

            FilterDivider()
            FilterSectionTitle(s.downloads.filterStatus)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                facets.statusCounts.forEach { (key, count) ->
                    FilterChip(
                        selected = key in filter.statuses,
                        onClick = {
                            onFilterChange(filter.copy(statuses = filter.statuses.toggle(key)))
                        },
                        label = { Text("${bucketLabel(key, s)} · $count") },
                    )
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Text(s.downloads.sheetDone)
            }
        }
    }

    if (showDatePicker) {
        DownloadDateRangeDialog(
            initialFrom = filter.fromMillis,
            initialTo = filter.toMillis,
            onDismiss = { showDatePicker = false },
            onConfirm = { from, to ->
                onFilterChange(filter.copy(fromMillis = from, toMillis = to))
                showDatePicker = false
            },
        )
    }
}

@Composable
private fun DatePresetChip(
    filter: DownloadFilter,
    range: DateRange,
    label: String,
    onFilterChange: (DownloadFilter) -> Unit,
) {
    FilterChip(
        selected = filter.fromMillis == range.from && filter.toMillis == range.to,
        onClick = { onFilterChange(filter.copy(fromMillis = range.from, toMillis = range.to)) },
        label = { Text(label) },
    )
}

@Composable
private fun FilterSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun FilterHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun FilterDivider() {
    HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadDateRangeDialog(
    initialFrom: Long?,
    initialTo: Long?,
    onConfirm: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    // DatePicker 内部月份 / 星期跟随 state.locale；显式按应用语言创建，
    // 避免系统中文 + 应用英文出现中英混排。
    val state = remember(s.language) {
        DateRangePickerState(
            locale = s.language.locale,
            initialSelectedStartDateMillis = initialFrom?.let(::localStartToUtcDate),
            initialSelectedEndDateMillis = initialTo?.let { localStartToUtcDate(it - 1) },
        )
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    val end = state.selectedEndDateMillis ?: start
                    val from = utcDateToLocalStart(start)
                    val to = utcDateToLocalStart(end).let { endStart ->
                        Instant.ofEpochMilli(endStart)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .plusDays(1)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                    }
                    onConfirm(from, to)
                },
            ) {
                Text(s.downloads.sheetDone)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.common.cancel) }
        },
    ) {
        DateRangePicker(
            state = state,
            showModeToggle = false,
            modifier = Modifier.weight(1f),
            title = {
                Text(
                    s.downloads.filterDate,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                )
            },
            headline = {
                val start = state.selectedStartDateMillis
                val end = state.selectedEndDateMillis
                val label = when {
                    start == null -> s.downloads.dateRangeTitle
                    end == null -> formatUtcDate(start)
                    else -> s.filterDateRange(formatUtcDate(start), formatUtcDate(end))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            },
        )
    }
}

private data class FilterFacets(
    val artists: List<String>,
    val categories: List<String>,
    val statusCounts: List<Pair<String, Int>>,
)

private data class DateRange(val from: Long, val to: Long)

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

private fun titleInitial(title: String): String {
    // 全角字母先归一化（Ａ -> A），再跳过开头的括号 / 标点，取第一个字母或数字。
    val normalized = java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFKC)
    for (ch in normalized) {
        if (ch.isDigit()) return "#"
        if (ch.isLetter()) {
            val upper = ch.uppercaseChar()
            return if (upper in 'A'..'Z') upper.toString() else "#"
        }
    }
    return "#"
}

private fun startOfDay(date: LocalDate): Long =
    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun presetToday(): DateRange {
    val today = LocalDate.now()
    return DateRange(startOfDay(today), startOfDay(today.plusDays(1)))
}

private fun presetDays(days: Long): DateRange {
    val today = LocalDate.now()
    return DateRange(startOfDay(today.minusDays(days - 1)), startOfDay(today.plusDays(1)))
}

private fun presetYear(): DateRange {
    val today = LocalDate.now()
    return DateRange(startOfDay(today.withDayOfYear(1)), startOfDay(today.plusDays(1)))
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** DateRangePicker 给的是 UTC 零点；headline 里按「用户选的那一天」原样显示。 */
private fun formatUtcDate(utcMillis: Long): String =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().format(DATE_FORMAT)

private fun dateFilterLabel(filter: DownloadFilter, s: UiStrings): String {
    val from = filter.fromMillis ?: return s.downloads.dateAll
    val presets = listOf(
        presetToday() to s.downloads.dateToday,
        presetDays(7) to s.downloads.date7,
        presetDays(30) to s.downloads.date30,
        presetYear() to s.downloads.dateYear,
    )
    presets.firstOrNull { filter.fromMillis == it.first.from && filter.toMillis == it.first.to }
        ?.let { return it.second }
    val fromText = Instant.ofEpochMilli(from)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DATE_FORMAT)
    val to = filter.toMillis ?: return s.filterDateRange(fromText, "…")
    val toText = Instant.ofEpochMilli((to - 1).coerceAtLeast(from))
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DATE_FORMAT)
    return s.filterDateRange(fromText, toText)
}

private fun localStartToUtcDate(localMillis: Long): Long =
    Instant.ofEpochMilli(localMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

private fun utcDateToLocalStart(utcMillis: Long): Long =
    Instant.ofEpochMilli(utcMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

@Composable
private fun DownloadRow(
    entity: DownloadEntity,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onReloadInfo: () -> Unit,
    onRemove: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            GalleryThumb(
                path = entity.thumbPath,
                modifier = Modifier.size(width = 56.dp, height = 78.dp).clip(RoundedCornerShape(8.dp)),
            )
            if (entity.status == DOWNLOAD_STATUS_DONE) {
                Icon(
                    Icons.Filled.DownloadDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(9.dp)),
                )
            }
            if (entity.isImported) {
                Icon(
                    Icons.Filled.FolderOpen,
                    contentDescription = s.downloads.imported,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                        .padding(2.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                entity.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entity.status == DOWNLOAD_STATUS_FAILED) {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    "${entity.donePages}/${entity.totalPages} · ${DownloadRepository.statusLabel(entity.status)}" +
                        (entity.error?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (entity.status == DOWNLOAD_STATUS_FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (entity.isMissingMetadata) {
                    IconButton(onClick = onReloadInfo, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Filled.SyncProblem,
                            contentDescription = s.downloads.metadataMissing,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            if (entity.totalPages > 0) {
                LinearProgressIndicator(
                    progress = { (entity.donePages.toFloat() / entity.totalPages).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = when (entity.status) {
                    DOWNLOAD_STATUS_DONE -> Icons.Filled.PlayArrow
                    DOWNLOAD_STATUS_PAUSED, DOWNLOAD_STATUS_FAILED -> Icons.Filled.Refresh
                    else -> Icons.Filled.Pause
                },
                contentDescription = when (entity.status) {
                    DOWNLOAD_STATUS_DONE -> s.downloads.read
                    DOWNLOAD_STATUS_PAUSED, DOWNLOAD_STATUS_FAILED -> s.downloads.resume
                    else -> s.downloads.pause
                },
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = s.common.delete)
        }
    }
}

@Composable
private fun DownloadGridCard(
    entity: DownloadEntity,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onReloadInfo: () -> Unit,
    onRemove: () -> Unit,
) {
    val s = LocalStrings.current
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = { menu = true })
                .padding(4.dp),
        ) {
            Box {
                GalleryThumb(
                    path = entity.thumbPath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentDescription = entity.title,
                )
                if (entity.totalPages > 0 && entity.status != DOWNLOAD_STATUS_DONE) {
                    LinearProgressIndicator(
                        progress = { (entity.donePages.toFloat() / entity.totalPages).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                    )
                }
                when (entity.status) {
                    DOWNLOAD_STATUS_DONE -> Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 4.dp, bottom = 8.dp)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)),
                    )

                    DOWNLOAD_STATUS_FAILED -> Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 4.dp, bottom = 8.dp)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp)),
                    )
                }
                if (entity.isImported) {
                    Text(
                        s.downloads.imported,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                if (entity.isMissingMetadata) {
                    Icon(
                        Icons.Filled.SyncProblem,
                        contentDescription = s.downloads.metadataMissing,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 4.dp, bottom = 8.dp)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                            .padding(2.dp),
                    )
                }
            }
            Text(
                entity.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            )
            Text(
                "${entity.donePages}/${entity.totalPages} · ${DownloadRepository.statusLabel(entity.status)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (entity.status == DOWNLOAD_STATUS_FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
            )
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            IconButton(
                onClick = { menu = true },
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), RoundedCornerShape(14.dp)),
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = s.moreActionsFor(entity.title),
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(s.downloads.read) },
                    onClick = {
                        menu = false
                        onClick()
                    },
                )
                if (entity.status != DOWNLOAD_STATUS_DONE) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (entity.status == DOWNLOAD_STATUS_RUNNING ||
                                    entity.status == DOWNLOAD_STATUS_QUEUED
                                ) {
                                    s.downloads.pause
                                } else {
                                    s.downloads.resume
                                },
                            )
                        },
                        onClick = {
                            menu = false
                            onToggle()
                        },
                    )
                }
                if (entity.isMissingMetadata) {
                    DropdownMenuItem(
                        text = { Text(s.downloads.reloadInfo) },
                        onClick = {
                            menu = false
                            onReloadInfo()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(s.common.delete) },
                    onClick = {
                        menu = false
                        onRemove()
                    },
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
