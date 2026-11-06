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
import com.mangaviewer.app.i18n.AppLanguage
import com.mangaviewer.app.i18n.LocalStrings
import com.mangaviewer.app.i18n.UiStrings
import com.mangaviewer.app.i18n.concurrentDownloads
import com.mangaviewer.app.i18n.customDir
import com.mangaviewer.app.i18n.directNote
import com.mangaviewer.app.i18n.loggedInAs
import com.mangaviewer.app.i18n.preloadPages
import com.mangaviewer.app.i18n.syncDone
import com.mangaviewer.app.i18n.syncPushFailed
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
    val cfg = settings ?: return
    val s = LocalStrings.current
    var apiBaseDraft by remember(cfg.apiBase) { mutableStateOf(cfg.apiBase) }
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
                snackbar.showSnackbar(s.settings.dirUpdated)
            } else {
                snackbar.showSnackbar(s.settings.dirNoPermission)
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(s.settings.title) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 语言：默认英语；切到简体中文后立即生效并持久化。
            SectionTitle(s.settings.languageSection)
            Text(
                s.settings.languageDesc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = cfg.language == AppLanguage.ENGLISH,
                    onClick = { scope.launch { settingsRepo.setLanguage(AppLanguage.ENGLISH) } },
                    label = { Text(s.settings.languageEnglish) },
                )
                FilterChip(
                    selected = cfg.language == AppLanguage.CHINESE_SIMPLIFIED,
                    onClick = { scope.launch { settingsRepo.setLanguage(AppLanguage.CHINESE_SIMPLIFIED) } },
                    label = { Text(s.settings.languageChinese) },
                )
            }

            SectionTitle(s.settings.sectionAccount)
            Text(
                if (auth.isLoggedIn) {
                    s.loggedInAs(auth.username?.takeIf { it.isNotBlank() } ?: s.account.noUsername)
                } else {
                    s.settings.loggedOut
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { navController.navigate("login") }) {
                    Text(if (auth.isLoggedIn) s.settings.accountDetails else s.settings.goLogin)
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
                                            s.syncDone(r.pulled, r.pushed) +
                                                if (r.failedPush > 0) s.syncPushFailed(r.failedPush) else "",
                                        )
                                    }
                                    .onFailure { snackbar.showSnackbar(it.message ?: s.account.syncFailed) }
                                syncing = false
                            }
                        },
                    ) { Text(if (syncing) s.account.syncing else s.account.syncFavorites) }
                }
            }

            SectionTitle(s.settings.sectionNetwork)
            Text(
                s.settings.networkHelp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !cfg.directMode,
                    onClick = { scope.launch { container.settings.setDirectMode(false) } },
                    label = { Text(s.settings.localProxy) },
                )
                FilterChip(
                    selected = cfg.directMode,
                    onClick = { scope.launch { container.settings.setDirectMode(true) } },
                    label = { Text(s.settings.directProxy) },
                )
            }
            if (cfg.directMode) {
                Text(
                    s.directNote(Settings.DIRECT_API_BASE),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                OutlinedTextField(
                    value = apiBaseDraft,
                    onValueChange = { apiBaseDraft = it },
                    label = { Text(s.settings.proxyAddress) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    s.settings.proxyHelp,
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
                if (!cfg.directMode) {
                    Button(onClick = {
                        scope.launch {
                            container.settings.setApiBase(apiBaseDraft)
                            snackbar.showSnackbar(s.settings.saved)
                        }
                    }) { Text(s.settings.save) }
                }
                Button(
                    enabled = !checking,
                    onClick = {
                        scope.launch {
                            checking = true
                            val direct = cfg.directMode
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
                                    !result -> s.settings.connectFailed
                                    direct -> s.settings.directOk
                                    else -> s.settings.proxyOk
                                },
                            )
                            checking = false
                        }
                    },
                ) { Text(if (checking) s.settings.checking else s.settings.checkConnection) }
            }

            SectionTitle(s.settings.sectionReading)
            Text(s.settings.direction, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = cfg.readerRtl, onClick = { scope.launch { container.settings.setRtl(true) } }, label = { Text(s.settings.rtl) })
                FilterChip(selected = !cfg.readerRtl, onClick = { scope.launch { container.settings.setRtl(false) } }, label = { Text(s.settings.ltr) })
            }
            Text(s.settings.layout, style = MaterialTheme.typography.labelMedium)
            // FlowRow：4 个选项在窄屏会超出屏幕宽度，用 FlowRow 自动换行而不是压扁最后一个。
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReaderLayout.entries.forEach { layout ->
                    FilterChip(
                        selected = cfg.readerLayout == layout,
                        onClick = { scope.launch { container.settings.setLayout(layout) } },
                        label = { Text(layoutLabel(layout, s)) },
                    )
                }
            }
            Text(s.preloadPages(cfg.preloadCount), style = MaterialTheme.typography.labelMedium)
            Slider(
                value = cfg.preloadCount.toFloat(),
                onValueChange = { v -> scope.launch { container.settings.setPreload(v.toInt()) } },
                valueRange = 1f..8f,
                steps = 6,
            )
            Text(s.settings.quality, style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageQuality.entries.forEach { quality ->
                    FilterChip(
                        selected = cfg.imageQuality == quality,
                        onClick = { scope.launch { container.settings.setQuality(quality) } },
                        label = { Text(qualityLabel(quality, s)) },
                    )
                }
            }

            SectionTitle(s.settings.sectionAppearance)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = cfg.darkMode, onClick = { scope.launch { container.settings.setDarkMode(!cfg.darkMode) } }, label = { Text(s.settings.darkMode) })
                FilterChip(
                    selected = cfg.dynamicColor,
                    onClick = { scope.launch { container.settings.setDynamicColor(!cfg.dynamicColor) } },
                    label = { Text(s.settings.dynamicColor) },
                )
            }

            SectionTitle(s.settings.sectionDownloads)
            val customDir = cfg.downloadTree.isNotBlank()
            Text(s.settings.saveLocation, style = MaterialTheme.typography.labelMedium)
            Text(
                text = if (customDir) s.customDir(treeLabel(cfg.downloadTree, s)) else s.settings.privateDir,
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
                s.settings.downloadDirHelp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { downloadDirLauncher.launch(null) }) {
                    Text(if (customDir) s.settings.changeDir else s.settings.chooseDir)
                }
                if (customDir) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                settingsRepo.setDownloadTree("")
                                snackbar.showSnackbar(s.settings.defaultRestored)
                            }
                        },
                    ) { Text(s.settings.restoreDefault) }
                }
            }
            Text(s.concurrentDownloads(cfg.concurrentDownloads), style = MaterialTheme.typography.labelMedium)
            Slider(
                value = cfg.concurrentDownloads.toFloat(),
                onValueChange = { v -> scope.launch { container.settings.setConcurrency(v.toInt()) } },
                valueRange = 1f..6f,
                steps = 4,
            )

            SectionTitle(s.settings.sectionAbout)
            Text(
                s.settings.aboutText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { scope.launch { container.settings.setAdultConfirmed(false) } }) { Text(s.settings.showGate) }
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
private fun treeLabel(raw: String, s: UiStrings): String {
    val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(raw)) }.getOrNull().orEmpty()
    val rel = id.substringAfter(':', id)
    return if (rel.isBlank() || rel == "/") s.settings.storageRoot else rel
}

private fun layoutLabel(layout: ReaderLayout, s: UiStrings): String = when (layout) {
    ReaderLayout.PAGED_HORIZONTAL -> s.settings.layoutH
    ReaderLayout.PAGED_DOUBLE -> s.settings.layoutDouble
    ReaderLayout.PAGED_VERTICAL -> s.settings.layoutV
    ReaderLayout.CONTINUOUS -> s.settings.layoutContinuous
}

private fun qualityLabel(quality: ImageQuality, s: UiStrings): String = when (quality) {
    ImageQuality.ORIGINAL -> s.settings.qualityOriginal
    ImageQuality.HIGH -> s.settings.qualityHigh
    ImageQuality.MEDIUM -> s.settings.qualityMedium
    ImageQuality.LOW -> s.settings.qualityLow
}
