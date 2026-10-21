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
                    ok == 0 -> "加入下载队列失败（$failed 本）"
                    failed == 0 -> "已加入下载队列：$ok 本"
                    else -> "已加入 $ok 本，$failed 本失败"
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
                            Icon(Icons.Filled.Close, contentDescription = "退出多选")
                        }
                    }
                },
                title = { Text(if (selecting) "已选 ${selectedEntries.size}" else "收藏（${favorites.size}）") },
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
                                                "同步完成：拉取 ${r.pulled} 条，推送 ${r.pushed} 条" +
                                                    if (r.failedPush > 0) "（${r.failedPush} 条推送失败）" else "",
                                            )
                                        }
                                        .onFailure { snackbar.showSnackbar(it.message ?: "同步失败") }
                                    syncing = false
                                }
                            },
                        ) { Icon(Icons.Filled.Sync, contentDescription = "同步账号收藏") }
                    }
                    IconButton(
                        enabled = selecting && !downloading,
                        onClick = { downloadSelected() },
                    ) { Icon(Icons.Filled.Download, contentDescription = "下载选中") }
                    IconButton(
                        enabled = selecting,
                        onClick = { confirmDelete = true },
                    ) { Icon(Icons.Filled.Delete, contentDescription = "删除选中") }
                    IconButton(onClick = { navController.navigate("login") }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = if (auth.isLoggedIn) "账号" else "登录")
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
                        label = { Text("全部 ${favorites.size}") },
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
                        label = { Text("${folderLabel(key)} ${counts[key] ?: 0}") },
                    )
                }
            }
            if (shown.isEmpty()) {
                EmptyBox("还没有收藏，去详情页点心形")
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
                title = { Text("取消收藏 ${targets.size} 本？") },
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
                            snackbar.showSnackbar("已取消收藏 ${ids.size} 本")
                        }
                    }) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
            )
        } else {
            confirmDelete = false
        }
    }
}

private fun folderLabel(key: String): String = if (key == DEFAULT_FOLDER) "默认" else key

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
