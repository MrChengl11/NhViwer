package com.mangaviewer.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.db.ALL_FOLDER
import com.mangaviewer.app.data.db.DEFAULT_FOLDER
import com.mangaviewer.app.data.db.FavoriteEntity
import com.mangaviewer.app.data.local.FavoritesViewMode
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.download.DownloadService
import com.mangaviewer.app.i18n.LocalStrings
import com.mangaviewer.app.i18n.UiStrings
import com.mangaviewer.app.i18n.addedOn
import com.mangaviewer.app.i18n.allCount
import com.mangaviewer.app.i18n.bulkFailed
import com.mangaviewer.app.i18n.bulkMixed
import com.mangaviewer.app.i18n.bulkOk
import com.mangaviewer.app.i18n.confirmRemove
import com.mangaviewer.app.i18n.favoritesTitle
import com.mangaviewer.app.i18n.filterDateRange
import com.mangaviewer.app.i18n.removedCount
import com.mangaviewer.app.i18n.selectedCount
import com.mangaviewer.app.i18n.statsPages
import com.mangaviewer.app.i18n.syncDone
import com.mangaviewer.app.i18n.syncPushFailed
import com.mangaviewer.app.ui.components.EmptyBox
import com.mangaviewer.app.ui.components.GalleryCard
import com.mangaviewer.app.ui.components.GalleryThumb
import kotlinx.coroutines.launch

/**
 * 收藏页：长按任意一本进入多选，选中的卡片显示勾选蒙层；非多选状态下右上角提供
 * 筛选 / 列表-网格切换；多选时只保留批量下载 / 批量取消收藏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(container: AppContainer, navController: NavController) {
    val favorites by container.favorites.observeAll().collectAsState(initial = emptyList())
    val auth by container.account.auth.collectAsState()
    val settings by container.settings.settingsFlow.collectAsState(initial = null)
    val snackbar = remember { SnackbarHostState() }
    var syncing by remember { mutableStateOf(false) }
    val counts by container.favorites.observeFolderCounts().collectAsState(initial = emptyMap())
    var folder by rememberSaveable { mutableStateOf(ALL_FOLDER) }
    var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var filter by rememberSaveable(stateSaver = FavoriteFilterSaver) {
        mutableStateOf(FavoriteFilter())
    }
    var showFilter by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val s = LocalStrings.current

    val grid = (settings?.favoritesViewMode ?: FavoritesViewMode.GRID) == FavoritesViewMode.GRID
    val shown = remember(favorites, folder, filter) {
        favorites.filter { (folder == ALL_FOLDER || it.folder == folder) && filter.matches(it) }
    }
    val selectedEntries = favorites.filter { it.galleryId in selection }
    val selecting = selectedEntries.isNotEmpty()

    // 返回键先退出多选，不离开页面。
    BackHandler(enabled = selecting) { selection = emptySet() }

    fun toggle(favorite: FavoriteEntity) {
        val id = favorite.galleryId
        selection = if (id in selection) selection - id else selection + id
    }

    fun downloadSelected() {
        val targets = selectedEntries
        if (targets.isEmpty() || downloading) return
        downloading = true
        scope.launch {
            var ok = 0
            var failed = 0
            for (entity in targets) {
                val detail = runCatching { container.galleries.detail(entity.galleryId) }.getOrNull()
                if (detail == null) {
                    failed++
                    continue
                }
                val enqueued = runCatching {
                    container.downloads.enqueue(detail.ui, detail.pages.map { it.path })
                }
                if (enqueued.isSuccess) ok++ else failed++
            }
            if (ok > 0 && runCatching { container.settings.current().foregroundDownloads }.getOrDefault(false)) {
                DownloadService.start(context)
            }
            downloading = false
            selection = emptySet()
            snackbar.showSnackbar(
                when {
                    ok == 0 -> s.bulkFailed(failed)
                    failed == 0 -> s.bulkOk(ok)
                    else -> s.bulkMixed(ok, failed)
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = s.favorites.exitSelection)
                        }
                    }
                },
                title = { Text(if (selecting) s.selectedCount(selectedEntries.size) else s.favoritesTitle(favorites.size)) },
                actions = {
                    if (selecting) {
                        IconButton(
                            enabled = !downloading,
                            onClick = { downloadSelected() },
                        ) { Icon(Icons.Filled.Download, contentDescription = s.favorites.bulkDownloadSelected) }
                        IconButton(
                            onClick = { confirmDelete = true },
                        ) { Icon(Icons.Filled.Delete, contentDescription = s.favorites.bulkDeleteSelected) }
                    } else {
                        if (auth.isLoggedIn) {
                            IconButton(
                                enabled = !syncing,
                                onClick = {
                                    syncing = true
                                    scope.launch {
                                        runCatching { container.account.syncFavorites() }
                                            .onSuccess { r ->
                                                snackbar.showSnackbar(
                                                    s.syncDone(r.pulled, r.pushed) +
                                                        if (r.failedPush > 0) s.syncPushFailed(r.failedPush) else "",
                                                )
                                            }
                                            .onFailure { snackbar.showSnackbar(it.message ?: s.account.syncFailed) }
                                        syncing = false
                                    }
                                },
                            ) { Icon(Icons.Filled.Sync, contentDescription = s.account.syncFavorites) }
                        }
                        IconButton(onClick = { showFilter = true }) {
                            BadgedBox(
                                badge = {
                                    if (filter.activeCount > 0) Badge { Text(filter.activeCount.toString()) }
                                },
                            ) {
                                Icon(Icons.Filled.Tune, contentDescription = s.favorites.filter)
                            }
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    container.settings.setFavoritesViewMode(
                                        if (grid) FavoritesViewMode.LIST else FavoritesViewMode.GRID,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (grid) Icons.Filled.ViewList else Icons.Filled.GridView,
                                contentDescription = if (grid) s.favorites.viewList else s.favorites.viewGrid,
                            )
                        }
                        IconButton(onClick = { navController.navigate("login") }) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = if (auth.isLoggedIn) s.account.title else s.account.login)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                item {
                    FilterChip(
                        selected = folder == ALL_FOLDER,
                        onClick = {
                            folder = ALL_FOLDER
                            selection = emptySet()
                        },
                        label = { Text(s.allCount(favorites.size)) },
                    )
                }
                itemsIndexed(counts.keys.sorted()) { _, key ->
                    FilterChip(
                        selected = folder == key,
                        onClick = {
                            folder = key
                            selection = emptySet()
                        },
                        leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        label = { Text("${folderLabel(key, s)} ${counts[key] ?: 0}") },
                    )
                }
            }
            if (filter.isActive) {
                ActiveFavoriteFilterBar(filter = filter, onFilterChange = { filter = it })
            }
            when {
                favorites.isEmpty() -> EmptyBox(s.favorites.empty)
                shown.isEmpty() -> Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        s.favorites.noFilterResult,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = {
                            filter = FavoriteFilter()
                            folder = ALL_FOLDER
                            selection = emptySet()
                        },
                    ) { Text(s.favorites.filterClear) }
                }
                grid -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(shown, key = { it.galleryId }) { entity ->
                        GalleryCard(
                            item = entity.toUi(),
                            onClick = {
                                if (selection.isNotEmpty()) toggle(entity)
                                else navController.navigate("detail/${entity.galleryId}")
                            },
                            onLongClick = { selection = selection + entity.galleryId },
                            selected = entity.galleryId in selection,
                            favorite = true,
                        )
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(shown, key = { it.galleryId }) { entity ->
                        FavoriteRow(
                            entity = entity,
                            selectionActive = selecting,
                            selected = entity.galleryId in selection,
                            onClick = {
                                if (selection.isNotEmpty()) toggle(entity)
                                else navController.navigate("detail/${entity.galleryId}")
                            },
                            onLongClick = { selection = selection + entity.galleryId },
                        )
                    }
                }
            }
        }
    }

    if (showFilter) {
        FavoriteFilterSheet(
            filter = filter,
            favorites = favorites,
            onFilterChange = { filter = it },
            onDismiss = { showFilter = false },
        )
    }

    if (confirmDelete) {
        val targets = selectedEntries
        if (targets.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(s.confirmRemove(targets.size)) },
                text = {
                    Text(
                        buildString {
                            append(targets.take(5).joinToString("\n") { it.title.ifBlank { "#${it.galleryId}" } })
                            if (targets.size > 5) append("\n…")
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val ids = targets.map { it.galleryId }
                        confirmDelete = false
                        selection = emptySet()
                        scope.launch {
                            ids.forEach { id ->
                                container.favorites.remove(id)
                                // 登录后同步删除到云端，失败不阻断本地操作
                                runCatching { container.account.pushFavorite(id, false) }
                            }
                            snackbar.showSnackbar(s.removedCount(ids.size))
                        }
                    }) { Text(s.common.delete) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(s.common.cancel) } },
            )
        } else {
            confirmDelete = false
        }
    }
}

/** 列表模式的一行：封面 + 标题 / 原名 + 页数收藏数 + 收藏时间与所属收藏夹。 */
@Composable
private fun FavoriteRow(
    entity: FavoriteEntity,
    selectionActive: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val s = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GalleryThumb(
            path = entity.thumbPath,
            modifier = Modifier.size(width = 56.dp, height = 78.dp).clip(RoundedCornerShape(8.dp)),
            contentDescription = entity.title,
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                entity.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = entity.prettyTitle?.takeIf { it.isNotBlank() && it != entity.title }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                s.statsPages(entity.numPages, entity.numFavorites),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "${s.addedOn(formatLocalDate(entity.addedAt))} · ${folderLabel(entity.folder, s)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selectionActive) {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (selected) s.common.selected else null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** 活动筛选条：每个生效条件一枚可单独取消的 chip，最后一键清除。 */
@Composable
private fun ActiveFavoriteFilterBar(
    filter: FavoriteFilter,
    onFilterChange: (FavoriteFilter) -> Unit,
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
                    label = { Text("${s.favorites.filterDate} · ${favoriteDateLabel(filter, s)}") },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        filter.initial?.let { letter ->
            item(key = "initial") {
                InputChip(
                    selected = true,
                    onClick = { onFilterChange(filter.copy(initial = null)) },
                    label = { Text("${s.favorites.filterInitial} · $letter") },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        if (filter.artists.isNotEmpty()) {
            item(key = "artist") {
                InputChip(
                    selected = true,
                    onClick = { onFilterChange(filter.copy(artists = emptySet())) },
                    label = { Text("${s.favorites.filterArtist} · ${filter.artists.size}") },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        if (filter.categories.isNotEmpty()) {
            item(key = "category") {
                InputChip(
                    selected = true,
                    onClick = { onFilterChange(filter.copy(categories = emptySet())) },
                    label = { Text("${s.favorites.filterCategory} · ${filter.categories.size}") },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
        item(key = "clear") {
            TextButton(onClick = { onFilterChange(FavoriteFilter()) }) {
                Text(s.favorites.filterClear)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FavoriteFilterSheet(
    filter: FavoriteFilter,
    favorites: List<FavoriteEntity>,
    onFilterChange: (FavoriteFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }
    // 可选的作者 / 分类来自收藏记录里已补全的标签；旧收藏没补全前不出现。
    val artists = remember(favorites) {
        favorites.flatMap { it.artists }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
    }
    val categories = remember(favorites) {
        favorites.flatMap { it.categories }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
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
                    s.favorites.filterTitle,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onFilterChange(FavoriteFilter()) }) {
                    Text(s.favorites.reset)
                }
            }

            FilterSectionTitle(s.favorites.filterDate)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FilterChip(
                    selected = filter.fromMillis == null && filter.toMillis == null,
                    onClick = { onFilterChange(filter.copy(fromMillis = null, toMillis = null)) },
                    label = { Text(s.favorites.dateAll) },
                )
                DatePresetChip(
                    selected = filter.fromMillis == today.from && filter.toMillis == today.to,
                    label = s.favorites.dateToday,
                    onClick = { onFilterChange(filter.copy(fromMillis = today.from, toMillis = today.to)) },
                )
                DatePresetChip(
                    selected = filter.fromMillis == last7.from && filter.toMillis == last7.to,
                    label = s.favorites.date7,
                    onClick = { onFilterChange(filter.copy(fromMillis = last7.from, toMillis = last7.to)) },
                )
                DatePresetChip(
                    selected = filter.fromMillis == last30.from && filter.toMillis == last30.to,
                    label = s.favorites.date30,
                    onClick = { onFilterChange(filter.copy(fromMillis = last30.from, toMillis = last30.to)) },
                )
                DatePresetChip(
                    selected = filter.fromMillis == thisYear.from && filter.toMillis == thisYear.to,
                    label = s.favorites.dateYear,
                    onClick = { onFilterChange(filter.copy(fromMillis = thisYear.from, toMillis = thisYear.to)) },
                )
                FilterChip(
                    selected = customSelected,
                    onClick = { showDatePicker = true },
                    label = { Text(s.favorites.dateCustom) },
                    leadingIcon = {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                )
            }
            if (filter.fromMillis != null || filter.toMillis != null) {
                Text(
                    favoriteDateLabel(filter, s),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            FilterDivider()
            FilterSectionTitle(s.favorites.filterInitial)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item(key = "all") {
                    FilterChip(
                        selected = filter.initial == null,
                        onClick = { onFilterChange(filter.copy(initial = null)) },
                        label = { Text(s.favorites.filterAll) },
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
            FilterSectionTitle(s.favorites.filterArtist)
            if (artists.isEmpty()) {
                FilterHint(s.favorites.filterNoArtists)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    artists.forEach { artist ->
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
            FilterSectionTitle(s.favorites.filterCategory)
            if (categories.isEmpty()) {
                FilterHint(s.favorites.filterNoCategories)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    categories.forEach { category ->
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

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                Text(s.favorites.sheetDone)
            }
        }
    }

    if (showDatePicker) {
        FilterDateRangeDialog(
            title = s.favorites.filterDate,
            rangeTitle = s.favorites.dateRangeTitle,
            doneText = s.favorites.sheetDone,
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

/**
 * 收藏页筛选条件。日期基于 addedAt，首字母取标题第一个字母 / 数字（中日文与符号统一归到 "#"）。
 * 收藏夹的切换仍由顶部文件夹 chip 负责，不重复放进面板。
 */
private data class FavoriteFilter(
    val fromMillis: Long? = null,
    val toMillis: Long? = null,
    val initial: String? = null,
    val artists: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
) {
    val activeCount: Int
        get() = (if (fromMillis != null || toMillis != null) 1 else 0) +
            (if (initial != null) 1 else 0) +
            (if (artists.isNotEmpty()) 1 else 0) +
            (if (categories.isNotEmpty()) 1 else 0)

    val isActive: Boolean get() = activeCount > 0

    fun matches(entity: FavoriteEntity): Boolean {
        if (fromMillis != null && entity.addedAt < fromMillis) return false
        if (toMillis != null && entity.addedAt >= toMillis) return false
        if (initial != null && titleInitial(entity.title) != initial) return false
        if (artists.isNotEmpty() && entity.artists.none { it in artists }) return false
        if (categories.isNotEmpty() && entity.categories.none { it in categories }) return false
        return true
    }
}

private val FavoriteFilterSaver = listSaver<FavoriteFilter, Any?>(
    save = { f -> listOf(f.fromMillis, f.toMillis, f.initial, ArrayList(f.artists), ArrayList(f.categories)) },
    restore = { v ->
        FavoriteFilter(
            fromMillis = v.getOrNull(0) as? Long,
            toMillis = v.getOrNull(1) as? Long,
            initial = v.getOrNull(2) as? String,
            artists = (v.getOrNull(3) as? ArrayList<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
            categories = (v.getOrNull(4) as? ArrayList<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
        )
    },
)

private fun favoriteDateLabels(s: UiStrings) = DateFilterLabels(
    all = s.favorites.dateAll,
    today = s.favorites.dateToday,
    days7 = s.favorites.date7,
    days30 = s.favorites.date30,
    year = s.favorites.dateYear,
    rangeLine = { from, to -> s.filterDateRange(from, to) },
)

private fun favoriteDateLabel(filter: FavoriteFilter, s: UiStrings): String =
    dateFilterLabel(filter.fromMillis, filter.toMillis, favoriteDateLabels(s))

private fun folderLabel(key: String, s: UiStrings): String = if (key == DEFAULT_FOLDER) s.favorites.defaultLabel else key

private fun FavoriteEntity.toUi(): GalleryUi = GalleryUi(
    id = galleryId,
    mediaId = 0L,
    title = title,
    subtitle = prettyTitle,
    thumbPath = thumbPath,
    numPages = numPages,
    numFavorites = numFavorites,
    uploadDate = null,
    tags = emptyList(),
)
