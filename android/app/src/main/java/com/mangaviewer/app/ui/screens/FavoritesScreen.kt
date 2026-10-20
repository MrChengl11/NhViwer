package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.db.ALL_FOLDER
import com.mangaviewer.app.data.db.DEFAULT_FOLDER
import com.mangaviewer.app.data.db.FavoriteEntity
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.ui.components.EmptyBox
import com.mangaviewer.app.ui.components.GalleryCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(container: AppContainer, navController: NavController) {
    val favorites by container.favorites.observeAll().collectAsState(initial = emptyList())
    val auth by container.account.auth.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var syncing by remember { mutableStateOf(false) }
    val counts by container.favorites.observeFolderCounts().collectAsState(initial = emptyMap())
    var folder by remember { mutableStateOf(ALL_FOLDER) }
    var pendingDelete by remember { mutableStateOf<FavoriteEntity?>(null) }
    val scope = rememberCoroutineScope()

    val shown = if (folder == ALL_FOLDER) favorites else favorites.filter { it.folder == folder }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收藏（${favorites.size}）") },
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                item {
                    FilterChip(
                        selected = folder == ALL_FOLDER,
                        onClick = { folder = ALL_FOLDER },
                        label = { Text("全部 ${favorites.size}") },
                    )
                }
                itemsIndexed(counts.keys.sorted()) { _, key ->
                    FilterChip(
                        selected = folder == key,
                        onClick = { folder = key },
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.galleryId }) { entity ->
                        Column {
                            GalleryCard(
                                item = entity.toUi(),
                                onClick = { navController.navigate("detail/${entity.galleryId}") },
                                favorite = true,
                            )
                            IconButton(onClick = { pendingDelete = entity }) {
                                Icon(Icons.Filled.Delete, contentDescription = "取消收藏")
                            }
                        }
                    }
                }
            }
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("取消收藏？") },
            text = { Text(target.title) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        container.favorites.remove(target.galleryId)
                        // 登录后同步删除到云端，失败不阻断本地操作
                        runCatching { container.account.pushFavorite(target.galleryId, false) }
                    }
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
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