package com.mangaviewer.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.db.ALL_FOLDER
import com.mangaviewer.app.data.db.DEFAULT_FOLDER
import com.mangaviewer.app.data.db.FavoriteEntity
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.download.DownloadService
import com.mangaviewer.app.i18n.LocalStrings
import com.mangaviewer.app.i18n.UiStrings
import com.mangaviewer.app.i18n.allCount
import com.mangaviewer.app.i18n.bulkFailed
import com.mangaviewer.app.i18n.bulkMixed
import com.mangaviewer.app.i18n.bulkOk
import com.mangaviewer.app.i18n.confirmRemove
import com.mangaviewer.app.i18n.favoritesTitle
import com.mangaviewer.app.i18n.removedCount
import com.mangaviewer.app.i18n.selectedCount
import com.mangaviewer.app.i18n.syncDone
import com.mangaviewer.app.i18n.syncPushFailed
import com.mangaviewer.app.ui.components.EmptyBox
import com.mangaviewer.app.ui.components.GalleryCard
import kotlinx.coroutines.launch

/**
 * 收藏页：长按任意一本进入多选，选中的卡片显示勾选蒙层；右上角（同步按钮旁）提供
 * 下载 / 删除两个批量操作按钮，卡片下方不再放单独的删除按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(container: AppContainer, navController: NavController) {
    val favorites by container.favorites.observeAll().collectAsState(initial = emptyList())
    val auth by container.account.auth.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var syncing by remember { mutableStateOf(false) }
    val counts by container.favorites.observeFolderCounts().collectAsState(initial = emptyMap())
    var folder by remember { mutableStateOf(ALL_FOLDER) }
    var selection by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val s = LocalStrings.current

    val shown = if (folder == ALL_FOLDER) favorites else favorites.filter { it.folder == folder }
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
                    IconButton(
                        enabled = selecting && !downloading,
                        onClick = { downloadSelected() },
                    ) { Icon(Icons.Filled.Download, contentDescription = s.favorites.bulkDownloadSelected) }
                    IconButton(
                        enabled = selecting,
                        onClick = { confirmDelete = true },
                    ) { Icon(Icons.Filled.Delete, contentDescription = s.favorites.bulkDeleteSelected) }
                    IconButton(onClick = { navController.navigate("login") }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = if (auth.isLoggedIn) s.account.title else s.account.login)
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
            if (shown.isEmpty()) {
                EmptyBox(s.favorites.empty)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.galleryId }) { entity ->
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
            }
        }
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
