package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.ui.components.EmptyBox
import com.mangaviewer.app.ui.components.ErrorBox
import com.mangaviewer.app.ui.components.GalleryCard
import com.mangaviewer.app.ui.components.LoadingBox
import kotlinx.coroutines.launch

private enum class Feed(val label: String, val sort: String) {
    LATEST("最新", "date"),
    POPULAR("最多收藏", "popular"),
}

/**
 * 首页 = 发现流 + 内嵌搜索。
 * 搜索入口放在顶部标题栏（放大镜），展开后是一行输入框；提交后就地切换成搜索结果，
 * 顶部出现「返回发现」按钮恢复原来的 feed。不再单独占一个底部 Tab。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer, navController: NavController) {
    var feed by remember { mutableStateOf(Feed.LATEST) }
    var page by remember { mutableIntStateOf(1) }
    var items by remember { mutableStateOf<List<GalleryUi>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val favorites by container.favorites.observeIds().collectAsState(initial = emptySet())

    // ---- 内嵌搜索状态 ----
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    /** null = 非搜索模式；非 null = 正在展示该关键词的结果 */
    var activeQuery by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val density = LocalDensity.current
    /** 下拉手势阈值：约 72dp。 */
    val pullThresholdPx = with(density) { 72.dp.toPx() }
    val openSearchState by rememberUpdatedState { searchOpen = true }
    val canPullState by rememberUpdatedState {
        !searchOpen && activeQuery == null && !gridState.canScrollBackward
    }

    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true
        error = null
        val targetPage = if (reset) 1 else page + 1
        runCatching {
            when (feed) {
                Feed.LATEST -> container.galleries.latest(targetPage)
                Feed.POPULAR -> container.galleries.popular(targetPage)
            }
        }.onSuccess { list ->
            items = if (reset) list else items + list
            page = targetPage
        }.onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    LaunchedEffect(feed) {
        page = 1
        items = emptyList()
        load(true)
    }

    fun submitSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        searching = true
        searchError = null
        scope.launch {
            runCatching { container.galleries.search(q, 1, "date") }
                .onSuccess {
                    items = it
                    activeQuery = q
                    page = 1
                    searchOpen = false
                }
                .onFailure { searchError = it.message ?: "搜索失败" }
            searching = false
        }
    }

    fun exitSearch() {
        activeQuery = null
        query = ""
        searchError = null
        page = 1
        items = emptyList()
        scope.launch { load(true) }
    }

    val inSearch = activeQuery != null

    Scaffold(
        topBar = {
            if (searchOpen) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("搜索标签 / 作者 / 关键词", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "清空")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    )
                    IconButton(onClick = { searchOpen = false }) {
                        Icon(Icons.Filled.Clear, contentDescription = "关闭搜索")
                    }
                }
            } else {
                TopAppBar(
                    title = { Text(if (inSearch) "搜索：${activeQuery.orEmpty()}" else "发现漫画", maxLines = 1) },
                    actions = {
                        if (inSearch) {
                            IconButton(onClick = { exitSearch() }) {
                                Icon(Icons.Filled.Clear, contentDescription = "退出搜索")
                            }
                        } else {
                            IconButton(onClick = { searchOpen = true }) {
                                Icon(Icons.Filled.Search, contentDescription = "搜索")
                            }
                            IconButton(onClick = { navController.navigate("history") }) {
                                Icon(Icons.Filled.History, contentDescription = "历史")
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    runCatching { container.galleries.random() }
                                        .onSuccess { navController.navigate("detail/${it.id}") }
                                        .onFailure { error = it.message }
                                }
                            }) {
                                Icon(Icons.Filled.Casino, contentDescription = "随机")
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!canPullState()) return@awaitEachGesture
                        var pulled = 0f
                        var triggered = false
                        drag(down.id) { change ->
                            val dy = change.position.y - down.position.y
                            if (dy > 0f) {
                                pulled = dy
                                if (!triggered && pulled > pullThresholdPx) {
                                    triggered = true
                                    openSearchState()
                                }
                            }
                            change.consume()
                        }
                    }
                },
        ) {
            if (!inSearch && !searchOpen) {
                TabRow(selectedTabIndex = feed.ordinal) {
                    Feed.entries.forEach { f ->
                        Tab(
                            selected = feed == f,
                            onClick = { if (feed != f) feed = f },
                            text = { Text(f.label) },
                        )
                    }
                }
            }
            when {
                searching -> LoadingBox()
                searchError != null -> ErrorBox(searchError!!, onRetry = { submitSearch() })
                items.isEmpty() && loading -> LoadingBox()
                items.isEmpty() && error != null -> ErrorBox(error!!, onRetry = { scope.launch { load(true) } })
                items.isEmpty() && inSearch -> EmptyBox("没有找到「${activeQuery.orEmpty()}」，试试别的关键词")
                items.isEmpty() -> EmptyBox("暂无内容，确认反代服务已启动")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        GalleryCard(
                            item = item,
                            onClick = { navController.navigate("detail/${item.id}") },
                            favorite = item.id in favorites,
                        )
                    }
                    if (!inSearch) {
                        item {
                            Box(Modifier.fillMaxWidth().aspectRatio(0.72f)) {
                                FilledTonalButton(
                                    onClick = { scope.launch { load(false) } },
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                ) {
                                    if (loading) {
                                        CircularProgressIndicator(Modifier.padding(4.dp))
                                    } else {
                                        Text("加载更多")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
