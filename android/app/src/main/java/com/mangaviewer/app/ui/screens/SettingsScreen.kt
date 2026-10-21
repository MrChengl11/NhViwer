package com.mangaviewer.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.local.ImageQuality
import com.mangaviewer.app.data.local.ReaderLayout
import com.mangaviewer.app.data.local.Settings
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, navController: NavController) {
    val settingsRepo = container.settings
    val settings by settingsRepo.settingsFlow.collectAsState(initial = null)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val s = settings ?: return
    var apiBaseDraft by remember(s.apiBase) { mutableStateOf(s.apiBase) }
    var checking by remember { mutableStateOf(false) }
    val auth by container.account.auth.collectAsState()
    var syncing by remember { mutableStateOf(false) }

    // 选择自定义下载目录（SAF 目录树），并申请持久化读写权限。
    val downloadDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val granted = runCatching {
            container.appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)
        scope.launch {
            if (granted) {
                settingsRepo.setDownloadTree(uri.toString())
                snackbar.showSnackbar("下载目录已更新，仅影响之后的新下载")
            } else {
                snackbar.showSnackbar("没有获得该目录的读写权限，已保持原设置")
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("账号")
            Text(
                if (auth.isLoggedIn) {
                    "已登录：" + (auth.username?.takeIf { it.isNotBlank() } ?: "（无用户名）") + "（API Key）"
                } else {
                    "未登录。登录后可以把账号收藏同步到本地，收藏/取消收藏也会同步到账号。"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { navController.navigate("login") }) {
                    Text(if (auth.isLoggedIn) "账号详情" else "去登录")
                }
                if (auth.isLoggedIn) {
                    Button(
                        enabled = !syncing,
                        onClick = {
                            syncing = true
                            scope.launch {
                                runCatching { container.account.syncFavorites() }
                                    .onSuccess { r ->
                                        snackbar.showSnackbar(
                                            "同步完成：拉取 " + r.pulled + " 条，推送 " + r.pushed + " 条" +
                                                if (r.failedPush > 0) "（" + r.failedPush + " 条推送失败）" else "",
                                        )
                                    }
                                    .onFailure { snackbar.showSnackbar(it.message ?: "同步失败") }
                                syncing = false
                            }
                        },
                    ) { Text(if (syncing) "同步中…" else "同步收藏") }
                }
            }

            SectionTitle("网络连接")
            Text(
                "设备自己能打开 nhentai.net（手机/平板上挂着代理或 TUN）时选「直连」，不需要电脑开反代；" +
                    "设备自己连不上时才需要「本地反代」。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !s.directMode,
                    onClick = { scope.launch { container.settings.setDirectMode(false) } },
                    label = { Text("本地反代") },
                )
                FilterChip(
                    selected = s.directMode,
                    onClick = { scope.launch { container.settings.setDirectMode(true) } },
                    label = { Text("直连（已挂代理）") },
                )
            }
            if (s.directMode) {
                Text(
                    "直连模式：API 走 " + Settings.DIRECT_API_BASE + "，图片直接请求图床域名。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    value = apiBaseDraft,
                    onValueChange = { apiBaseDraft = it },
                    label = { Text("反代地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "先在宿主机运行 proxy（默认 8787），再执行 adb reverse tcp:8787 tcp:8787。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Settings.ALT_API_BASES.forEach { base ->
                        FilterChip(
                            selected = apiBaseDraft == base,
                            onClick = { apiBaseDraft = base },
                            label = { Text(base.removePrefix("http://")) },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!s.directMode) {
                    Button(onClick = {
                        scope.launch {
                            container.settings.setApiBase(apiBaseDraft)
                            snackbar.showSnackbar("已保存")
                        }
                    }) { Text("保存") }
                }
                Button(
                    enabled = !checking,
                    onClick = {
                        scope.launch {
                            checking = true
                            val direct = s.directMode
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val root = if (direct) Settings.DIRECT_API_BASE else apiBaseDraft.trimEnd('/')
                                    val path = if (direct) "/api/v2/galleries?page=1" else "/health"
                                    val request = Request.Builder().url(root + path).build()
                                    container.client.newCall(request).execute().use { it.isSuccessful }
                                }.getOrDefault(false)
                            }
                            snackbar.showSnackbar(
                                when {
                                    !result -> "连接失败"
                                    direct -> "直连正常"
                                    else -> "反代正常"
                                },
                            )
                            checking = false
                        }
                    },
                ) { Text(if (checking) "检测中…" else "检测连接") }
            }

            SectionTitle("阅读")
            Text("翻页方向", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.readerRtl, onClick = { scope.launch { container.settings.setRtl(true) } }, label = { Text("日漫（从右到左）") })
                FilterChip(selected = !s.readerRtl, onClick = { scope.launch { container.settings.setRtl(false) } }, label = { Text("从左到右") })
            }
            Text("布局", style = MaterialTheme.typography.labelMedium)
            // FlowRow：4 个选项在窄屏会超出屏幕宽度，用 FlowRow 自动换行而不是压扁最后一个。
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderLayout.entries.forEach { layout ->
                    FilterChip(
                        selected = s.readerLayout == layout,
                        onClick = { scope.launch { container.settings.setLayout(layout) } },
                        label = { Text(layoutLabel(layout)) },
                    )
                }
            }
            Text("预加载页数：${s.preloadCount}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = s.preloadCount.toFloat(),
                onValueChange = { v -> scope.launch { container.settings.setPreload(v.toInt()) } },
                valueRange = 1f..8f,
                steps = 6,
            )
            Text("画质", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageQuality.entries.forEach { quality ->
                    FilterChip(
                        selected = s.imageQuality == quality,
                        onClick = { scope.launch { container.settings.setQuality(quality) } },
                        label = { Text(qualityLabel(quality)) },
                    )
                }
            }

            SectionTitle("外观")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = s.darkMode, onClick = { scope.launch { container.settings.setDarkMode(!s.darkMode) } }, label = { Text("深色模式") })
                FilterChip(
                    selected = s.dynamicColor,
                    onClick = { scope.launch { container.settings.setDynamicColor(!s.dynamicColor) } },
                    label = { Text("动态取色（Android 12+）") },
                )
            }

            SectionTitle("下载")
            val customDir = s.downloadTree.isNotBlank()
            Text("保存位置", style = MaterialTheme.typography.labelMedium)
            Text(
                text = if (customDir) "自定义目录：${treeLabel(s.downloadTree)}" else "应用私有目录（默认）",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!customDir) {
                Text(
                    container.downloads.internalDirText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "每部漫画在所选目录下建一个以 ID 命名的子文件夹，图片按页号保存；" +
                    "切换目录只影响之后的新下载，已下载的内容仍可正常阅读；卸载 App 会删除应用私有目录里的内容。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { downloadDirLauncher.launch(null) }) {
                    Text(if (customDir) "更改下载目录" else "选择下载目录")
                }
                if (customDir) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                settingsRepo.setDownloadTree("")
                                snackbar.showSnackbar("已恢复默认下载目录")
                            }
                        },
                    ) { Text("恢复默认") }
                }
            }
            Text("并发下载：${s.concurrentDownloads}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = s.concurrentDownloads.toFloat(),
                onValueChange = { v -> scope.launch { container.settings.setConcurrency(v.toInt()) } },
                valueRange = 1f..6f,
                steps = 4,
            )

            SectionTitle("关于")
            Text(
                "Nhviwer 仅用于技术研究与学习，内容来自第三方站点，应用不托管任何内容；请遵守当地法律法规。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { scope.launch { container.settings.setAdultConfirmed(false) } }) { Text("重新显示成人确认页") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

/** 把 SAF tree URI 转成用户能看懂的路径，如 primary:Download/NhViwer -> Download/NhViwer。 */
private fun treeLabel(raw: String): String {
    val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(raw)) }.getOrNull().orEmpty()
    val rel = id.substringAfter(':', id)
    return if (rel.isBlank() || rel == "/") "存储根目录" else rel
}

private fun layoutLabel(layout: ReaderLayout): String = when (layout) {
    ReaderLayout.PAGED_HORIZONTAL -> "左右翻页"
    ReaderLayout.PAGED_DOUBLE -> "双页跨页"
    ReaderLayout.PAGED_VERTICAL -> "上下翻页"
    ReaderLayout.CONTINUOUS -> "连续滚动"
}

private fun qualityLabel(quality: ImageQuality): String = when (quality) {
    ImageQuality.ORIGINAL -> "原图"
    ImageQuality.HIGH -> "高清"
    ImageQuality.MEDIUM -> "中等"
    ImageQuality.LOW -> "省流"
}