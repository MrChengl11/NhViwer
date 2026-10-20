package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_DONE
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_FAILED
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_PAUSED
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_QUEUED
import com.mangaviewer.app.data.db.DOWNLOAD_STATUS_RUNNING
import com.mangaviewer.app.data.db.DownloadEntity
import com.mangaviewer.app.data.repo.DownloadRepository
import com.mangaviewer.app.download.DownloadService
import com.mangaviewer.app.ui.components.EmptyBox
import com.mangaviewer.app.ui.components.GalleryThumb
import kotlinx.coroutines.launch

private data class DownloadBucket(val key: String, val label: String, val items: List<DownloadEntity>)

private val BUCKET_ORDER = listOf(
    DOWNLOAD_STATUS_RUNNING to "正在下载",
    DOWNLOAD_STATUS_QUEUED to "排队中",
    DOWNLOAD_STATUS_PAUSED to "已暂停 / 待继续",
    DOWNLOAD_STATUS_FAILED to "失败",
    DOWNLOAD_STATUS_DONE to "已完成",
)

private fun bucketOf(list: List<DownloadEntity>): List<DownloadBucket> = BUCKET_ORDER.mapNotNull { (key, label) ->
    val items = list.filter { it.status == key }
    if (items.isEmpty()) null else DownloadBucket(key, label, items)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(container: AppContainer, navController: NavController) {
    val downloads by container.downloads.observeAll().collectAsState(initial = emptyList())
    val settings by container.settings.settingsFlow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val buckets = bucketOf(downloads)
    val active = downloads.count { it.status == DOWNLOAD_STATUS_RUNNING || it.status == DOWNLOAD_STATUS_QUEUED }
    val totalBytes = downloads.sumOf { it.bytes }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载（${downloads.size}）") },
                actions = {
                    if (active > 0) {
                        AssistChip(
                            onClick = { container.downloads.pauseAll(); DownloadService.stop(context) },
                            label = { Text("全部暂停") },
                        )
                    } else if (downloads.any { it.status == DOWNLOAD_STATUS_PAUSED }) {
                        AssistChip(
                            onClick = {
                                container.downloads.startPaused()
                                if (settings?.foregroundDownloads == true) DownloadService.start(context)
                            },
                            label = { Text("全部继续") },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (downloads.isEmpty()) {
                EmptyBox("还没有下载任务")
            } else {
                SummaryBar(downloads.size, active, totalBytes)
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    buckets.forEach { bucket ->
                        item(key = "h-${bucket.key}") {
                            Text(
                                "${bucket.label} · ${bucket.items.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                            )
                        }
                        items(bucket.items, key = { it.galleryId }) { entity ->
                            DownloadRow(
                                entity = entity,
                                onClick = { navController.navigate("reader/${entity.galleryId}?page=${maxOf(1, entity.donePages)}") },
                                onToggle = {
                                    when (entity.status) {
                                        DOWNLOAD_STATUS_RUNNING, DOWNLOAD_STATUS_QUEUED -> {
                                            container.downloads.pause(entity.galleryId)
                                        }
                                        else -> {
                                            container.downloads.start(entity.galleryId)
                                            if (settings?.foregroundDownloads == true) DownloadService.start(context)
                                        }
                                    }
                                },
                                onRemove = { scope.launch { container.downloads.remove(entity.galleryId) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBar(count: Int, active: Int, bytes: Long) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            InfoChip("任务", count.toString())
            InfoChip("进行中", active.toString())
            InfoChip("占用", formatBytes(bytes))
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DownloadRow(
    entity: DownloadEntity,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
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
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(entity.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
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
                )
            }
            if (entity.totalPages > 0) {
                LinearProgressIndicator(
                    progress = { entity.donePages.toFloat() / entity.totalPages },
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
                    DOWNLOAD_STATUS_DONE -> "阅读"
                    DOWNLOAD_STATUS_PAUSED, DOWNLOAD_STATUS_FAILED -> "继续"
                    else -> "暂停"
                },
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "删除")
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}
