package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.model.groupTags
import com.mangaviewer.app.data.remote.ImageProxyInterceptor
import com.mangaviewer.app.data.repo.GalleryDetail
import com.mangaviewer.app.download.DownloadService
import com.mangaviewer.app.ui.components.ErrorBox
import com.mangaviewer.app.ui.components.GalleryCard
import com.mangaviewer.app.ui.components.LoadingBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(container: AppContainer, galleryId: Long, navController: NavController) {
    var detail by remember { mutableStateOf<GalleryDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var favorite by remember { mutableStateOf(false) }
    var commentDraft by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settings by container.settings.settingsFlow.collectAsState(initial = null)
    val context = LocalContext.current

    suspend fun reload() {
        loading = true
        error = null
        runCatching { container.galleries.detail(galleryId) }
            .onSuccess { detail = it; favorite = container.favorites.isFavorite(galleryId) }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    LaunchedEffect(galleryId) { reload() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("详情", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val d = detail ?: return@launch
                            val now = container.favorites.toggle(d.ui)
                            favorite = now
                            // 登录后把收藏操作也推送到账号（失败只提示，不回滚本地）
                            val pushed = runCatching { container.account.pushFavorite(d.ui.id, now) }.getOrDefault(false)
                            snackbar.showSnackbar(
                                when {
                                    !container.authStore.state.value.isLoggedIn -> if (now) "已加入收藏（本地）" else "已取消收藏"
                                    pushed -> if (now) "已加入收藏并同步到账号" else "已取消收藏并同步到账号"
                                    else -> if (now) "已加入收藏（云端同步失败）" else "已取消收藏（云端同步失败）"
                                },
                            )
                        }
                    }) {
                        Icon(
                            if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "收藏",
                            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        val d = detail
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && d == null -> LoadingBox()
                error != null && d == null -> ErrorBox(error!!, onRetry = { scope.launch { reload() } })
                d == null -> LoadingBox()
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(16.dp)) {
                            val cover = ImageProxyInterceptor.candidates(d.ui.thumbPath, thumb = true).firstOrNull()
                            AsyncImage(
                                model = cover,
                                contentDescription = d.ui.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 120.dp, height = 168.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Column(Modifier.padding(start = 16.dp)) {
                                Text(d.ui.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (!d.ui.subtitle.isNullOrBlank() && d.ui.subtitle != d.ui.title) {
                                    Text(
                                        d.ui.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                                Text(
                                    "${d.ui.numPages} 页 · ${d.ui.numFavorites} 收藏 · ${d.commentCount} 评论",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { navController.navigate("reader/${d.ui.id}?page=1") },
                                modifier = Modifier.weight(1f),
                            ) { Text("开始阅读") }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        container.downloads.enqueue(d.ui, d.pages.map { it.path })
                                        if (container.settings.current().foregroundDownloads) {
                                            DownloadService.start(context)
                                        }
                                        snackbar.showSnackbar("已加入下载队列，可在「下载」页查看进度")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("下载", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                    val groups = groupTags(d.ui.tags)
                    groups.forEach { group ->
                        item(key = "tag-${group.type}") {
                            Column(Modifier.padding(top = 12.dp)) {
                                Text(
                                    group.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                                LazyRow(
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(group.tags, key = { it.id }) { tag ->
                                        AssistChip(
                                            onClick = {
                                                navController.navigate("tag/${tag.type}/${tag.id}?name=${tag.name}")
                                            },
                                            label = { Text("${tag.name} ${tag.count}") },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (d.related.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(top = 16.dp)) {
                                Text(
                                    "相关作品",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                                LazyRow(
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(d.related, key = { it.id }) { item ->
                                        Box(Modifier.width(120.dp)) {
                                            GalleryCard(item = item, onClick = { navController.navigate("detail/${item.id}") })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        CommentSection(
                            container = container,
                            galleryId = galleryId,
                            comments = d.comments,
                            draft = commentDraft,
                            onDraftChange = { commentDraft = it },
                            onPosted = { scope.launch { reload() } },
                            snackbar = snackbar,
                        )
                    }
                }
            }
        }
    }
}