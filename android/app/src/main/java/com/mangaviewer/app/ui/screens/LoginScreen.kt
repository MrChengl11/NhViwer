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
import com.mangaviewer.app.i18n.LocalStrings
import com.mangaviewer.app.i18n.loginSuccess
import com.mangaviewer.app.i18n.syncDone
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
    val s = LocalStrings.current

    var keyDraft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var weCreatedKey by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (auth.isLoggedIn) s.account.title else s.account.login) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.common.back)
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
                        Text(s.account.loggedIn, style = MaterialTheme.typography.titleMedium)
                        Text(
                            auth.username?.takeIf { it.isNotBlank() } ?: s.account.noUsernameReturned,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            s.account.authApiKey + if (auth.userId > 0L) " · UID " + auth.userId else "",
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
                                .onSuccess { snackbar.showSnackbar(s.syncDone(it.pulled, it.pushed)) }
                                .onFailure { snackbar.showSnackbar(it.message ?: s.account.syncFailed) }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) s.account.syncing else s.account.syncFavorites) }
                Text(
                    s.account.syncDesc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                weCreatedKey?.let { created ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(s.account.keyGeneratedOnce, style = MaterialTheme.typography.titleSmall)
                            Text(created, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        container.account.logout()
                        weCreatedKey = null
                        scope.launch { snackbar.showSnackbar(s.account.loggedOutMsg) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(s.account.logout) }
                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching { container.api(container.settings.current().effectiveBase).createApiKey("MangaViewer Android") }
                                .onSuccess {
                                    weCreatedKey = it.key
                                    snackbar.showSnackbar(s.account.keyGenerated)
                                }
                                .onFailure { snackbar.showSnackbar(s.account.generateFailed + (it.message ?: "")) }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(s.account.generateKey) }
                Text(
                    s.account.keyNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Text(
                s.account.help1,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                s.account.help2,
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
                            .onSuccess { snackbar.showSnackbar(s.loginSuccess(it.username)) }
                            .onFailure { snackbar.showSnackbar(it.message ?: s.account.loginFailed) }
                        busy = false
                    }
                },
                enabled = !busy && keyDraft.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) s.account.verifying else s.account.login) }

            if (busy) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text(s.account.submitting, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
