package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.ui.components.EmptyBox
import com.mangaviewer.app.ui.components.ErrorBox
import com.mangaviewer.app.ui.components.GalleryCard
import com.mangaviewer.app.ui.components.LoadingBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal enum class Feed(val label: String) {
    LATEST("最新"),
    POPULAR("最多收藏"),
}

/**
 * 首页数据状态。放在 ViewModel 里，这样从详情页/阅读器返回时列表内容还在，
 * 滚动位置由 rememberSaveable 的 LazyGridState 恢复，不会回到顶部。
 */
internal class HomeViewModel(private val container: AppContainer) : ViewModel() {

    var feed by mutableStateOf(Feed.LATEST)
        private set
    var items by mutableStateOf<List<GalleryUi>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var endReached by mutableStateOf(false)
        private set
    /** 加载更多失败时的提示；上一次成功的列表保持不变。 */
    var loadMoreError by mutableStateOf<String?>(null)
        private set
    /** null = 发现流；非 null = 正在展示该关键词的搜索结果 */
    var activeQuery by mutableStateOf<String?>(null)
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set

    private var page = 0
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val inSearch: Boolean get() = activeQuery != null

    fun ensureLoaded() {
        if (items.isEmpty() && !loading && error == null) load(reset = true)
    }

    fun switchFeed(target: Feed) {
        if (feed == target) return
        feed = target
        searchError = null
        activeQuery = null
        reload()
    }

    fun retry() {
        if (inSearch) {
            searchError = null
            activeQuery?.let { search(it) }
        } else {
            reload()
        }
    }

    /** 滑到列表末尾附近时自动续页（原「加载更多」按钮已移除）。 */
    fun loadMore() {
        if (inSearch || endReached || loading) return
        if (loadMoreError != null) return
        load(reset = false)
    }

    fun retryLoadMore() {
        loadMoreError = null
        load(reset = false)
    }

    private fun reload() {
        job?.cancel()
        loading = false
        loadMoreError = null
        page = 0
        endReached = false
        error = null
        items = emptyList()
        load(reset = true)
    }

    fun load(reset: Boolean) {
        if (loading) return
        if (!reset && (endReached || inSearch)) return
        val targetPage = if (reset) 1 else page + 1
        loading = true
        if (reset) error = null
        loadMoreError = null
        job = scope.launch {
            runCatching {
                when {
                    inSearch -> container.galleries.search(activeQuery!!, 1, "date")
                    feed == Feed.LATEST -> container.galleries.latest(targetPage)
                    else -> container.galleries.popular(targetPage)
                }
            }.onSuccess { list ->
                val before = items.size
                // 按 id 去重：popular 榜两次请求之间排序会变，直接拼接会出现重复 key，
                // LazyGrid 抛 "Key was already used" 导致崩溃。
                val merged = if (reset) list else items + list
                items = merged.distinctBy { it.id }
                page = targetPage
                if (inSearch) {
                    endReached = true
                } else if (list.isEmpty() || list.size < PAGE_SIZE) {
                    endReached = true
                } else {
                    endReached = false
                    // 极端情况：整页都是上一页已有的条目，去重后没有增长，继续取下一页。
                    if (!reset && items.size == before) load(false)
                }
            }.onFailure {
                val msg = it.message ?: "加载失败"
                if (reset || items.isEmpty()) error = msg else loadMoreError = msg
            }
            loading = false
        }
    }

    fun search(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.isEmpty()) return
        job?.cancel()
        searching = true
        searchError = null
        activeQuery = null
        job = scope.launch {
            runCatching { container.galleries.search(q, 1, "date") }
                .onSuccess { list ->
                    activeQuery = q
                    items = list.distinctBy { it.id }
                    page = 1
                    endReached = true
                }
                .onFailure { searchError = it.message ?: "搜索失败" }
            searching = false
        }
    }

    fun exitSearch() {
        searchError = null
        activeQuery = null
        reload()
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }

    companion object {
        /** 与 GalleryRepository 的默认 perPage 保持一致。 */
        const val PAGE_SIZE = 25
    }
}

/**
 * 首页 = 发现流 + 内嵌搜索。
 * 搜索入口放在顶部标题栏（放大镜），展开后是一行输入框；提交后就地切换成搜索结果，
 * 顶部出现「退出搜索」按钮恢复原来的 feed。不再单独占一个底部 Tab。
 * 上滑直接滚动列表（原来的下拉唤起搜索手势会把上滑吃掉，已移除），滑到末尾自动加载下一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer, navController: NavController) {
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val favorites by container.favorites.observeIds().collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // ---- 仅 UI 态的搜索框展开状态；查询词与结果由 ViewModel 持有 ----
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val inSearch = vm.activeQuery != null

    LaunchedEffect(Unit) { vm.ensureLoaded() }

    // 滑到距末尾 4 个条目以内时自动续页；items 增长后 condition 会重新变 false，
    // 因此一次「接近末尾」只会触发一次加载。
    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            vm.activeQuery == null &&
                vm.items.isNotEmpty() &&
                !vm.loading &&
                !vm.endReached &&
                vm.loadMoreError == null &&
                lastVisible >= vm.items.size - 4
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) vm.loadMore() }
    }

    fun submitSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        searchOpen = false
        vm.search(q)
    }

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
                    title = {
                        Text(
                            if (inSearch) "搜索：${vm.activeQuery.orEmpty()}" else "发现漫画",
                            maxLines = 1,
                        )
                    },
                    actions = {
                        if (inSearch) {
                            IconButton(onClick = { vm.exitSearch() }) {
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!inSearch && !searchOpen) {
                TabRow(selectedTabIndex = vm.feed.ordinal) {
                    Feed.entries.forEach { f ->
                        Tab(
                            selected = vm.feed == f,
                            onClick = { vm.switchFeed(f) },
                            text = { Text(f.label) },
                        )
                    }
                }
            }
            when {
                vm.searching -> LoadingBox()
                vm.searchError != null -> ErrorBox(vm.searchError!!, onRetry = { vm.retry() })
                vm.items.isEmpty() && vm.loading -> LoadingBox()
                vm.items.isEmpty() && vm.error != null -> ErrorBox(vm.error!!, onRetry = { vm.retry() })
                vm.items.isEmpty() && inSearch -> EmptyBox("没有找到「${vm.activeQuery.orEmpty()}」，试试别的关键词")
                vm.items.isEmpty() -> EmptyBox("暂无内容，确认反代服务已启动")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(vm.items, key = { it.id }) { item ->
                        GalleryCard(
                            item = item,
                            onClick = { navController.navigate("detail/${item.id}") },
                            favorite = item.id in favorites,
                        )
                    }
                    if (!inSearch) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    vm.loading -> CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    vm.loadMoreError != null -> TextButton(onClick = { vm.retryLoadMore() }) {
                                        Text("加载失败，点击重试")
                                    }
                                    vm.endReached -> Text(
                                        "已经到底了",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    else -> Text(
                                        "上滑继续加载",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
