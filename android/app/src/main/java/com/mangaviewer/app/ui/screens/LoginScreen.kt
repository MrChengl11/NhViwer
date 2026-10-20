package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mangaviewer.app.data.AppContainer
import kotlinx.coroutines.launch

/**
 * 登录页：只提供 API Key 一条通道。
 *
 * 另外两条理论上存在的通道都不可用，因此没有保留：
 * - 访问令牌：需要网页登录才能拿到，而网页登录在本机网络下走不通
 *   （DNS 污染 + Cloudflare Turnstile 与域名绑定）；
 * - 账号密码：服务端强制 Turnstile，返回 400 CAPTCHA solution is required，本应用不绕过验证码。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(container: AppContainer, navController: NavHostController) {
    val auth by container.account.auth.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var keyDraft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var weCreatedKey by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (auth.isLoggedIn) "账号" else "登录") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (auth.isLoggedIn) {
                Card {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("已登录", style = MaterialTheme.typography.titleMedium)
                        Text(
                            auth.username?.takeIf { it.isNotBlank() } ?: "（未返回用户名）",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            "认证方式：API Key" + if (auth.userId > 0L) " · UID " + auth.userId else "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { container.account.syncFavorites() }
                                .onSuccess { snackbar.showSnackbar("同步完成：拉取 " + it.pulled + " 条，推送 " + it.pushed + " 条") }
                                .onFailure { snackbar.showSnackbar(it.message ?: "同步失败") }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "同步中…" else "同步账号收藏") }
                Text(
                    "双向同步：云端收藏会补到本地（保留本地文件夹），本地多出来的收藏会推送到云端。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                weCreatedKey?.let { created ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("已生成 API Key（只显示一次）", style = MaterialTheme.typography.titleSmall)
                            Text(created, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        container.account.logout()
                        weCreatedKey = null
                        scope.launch { snackbar.showSnackbar("已退出登录，本地收藏保留") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("退出登录") }
                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { container.api(container.settings.current().effectiveBase).createApiKey("MangaViewer Android") }
                                .onSuccess {
                                    weCreatedKey = it.key
                                    snackbar.showSnackbar("已生成 API Key")
                                }
                                .onFailure { snackbar.showSnackbar("生成失败：" + (it.message ?: "")) }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("为当前账号生成新 API Key") }
                Text(
                    "生成的 Key 只显示这一次，建议复制保存；旧 Key 可继续使用，也可到网页端删除。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Text(
                "用 API Key 登录。网页端右上角头像 → 设置 → API Keys → 新建，把生成的字符串粘贴到下面。",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "为什么只有 API Key：账号密码登录被服务端强制 Cloudflare Turnstile 人机验证，" +
                    "而访问令牌需要网页登录才能拿到（本机网络下网页登录不可达）。API Key 长期有效，是官方给第三方客户端的凭证。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = keyDraft,
                onValueChange = { keyDraft = it },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching { container.account.loginWithApiKey(keyDraft) }
                            .onSuccess { snackbar.showSnackbar("登录成功：" + it.username) }
                            .onFailure { snackbar.showSnackbar(it.message ?: "登录失败") }
                        busy = false
                    }
                },
                enabled = !busy && keyDraft.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "校验中…" else "登录") }

            if (busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text("正在请求服务端…", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
