package com.mangaviewer.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.i18n.AppLocale
import com.mangaviewer.app.i18n.LocalStrings
import com.mangaviewer.app.i18n.noResultFor
import com.mangaviewer.app.ui.components.EmptyBox
import com.mangaviewer.app.ui.components.ErrorBox
import com.mangaviewer.app.ui.components.GalleryCard
import com.mangaviewer.app.ui.components.LoadingBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal enum class Feed {
    LATEST,
    POPULAR,
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
    /**
     * 搜索页模式：true 时列表区展示「搜索提示 / 搜索结果」，而不是首页发现流。
     * 与 [activeQuery] 分开，是为了支持「清空搜索框但留在搜索页」这种中间态。
     */
    var searchMode by mutableStateOf(false)
        private set
    /** null = 还没提交过关键词；非 null = 正在展示该关键词的搜索结果 */
    var activeQuery by mutableStateOf<String?>(null)
        private set
    var searching by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set

    private var page = 0
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val inSearch: Boolean get() = searchMode

    fun ensureLoaded() {
        if (items.isEmpty() && !loading && error == null && !searchMode) load(reset = true)
    }

    fun switchFeed(target: Feed) {
        if (feed == target) return
        feed = target
        searchError = null
        activeQuery = null
        searchMode = false
        reload()
    }

    fun retry() {
        val q = activeQuery
        if (q != null) {
            searchError = null
            search(q)
        } else if (!searchMode) {
            reload()
        }
    }

    /** 滑到列表末尾附近时自动续页（原「加载更多」按钮已移除）。搜索页不分页。 */
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
                    activeQuery != null -> container.galleries.search(activeQuery!!, 1, "date")
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
                if (activeQuery != null) {
                    endReached = true
                } else if (list.isEmpty() || list.size < PAGE_SIZE) {
                    endReached = true
                } else {
                    endReached = false
                    // 极端情况：整页都是上一页已有的条目，去重后没有增长，继续取下一页。
                    if (!reset && items.size == before) load(false)
                }
            }.onFailure {
                val msg = it.message ?: AppLocale.strings().common.loadFailed
                if (reset || items.isEmpty()) error = msg else loadMoreError = msg
            }
            loading = false
        }
    }

    /** 提交搜索：进入搜索页并展示该关键词的结果。 */
    fun search(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.isEmpty()) return
        job?.cancel()
        searchMode = true
        searching = true
        searchError = null
        activeQuery = q
        items = emptyList()
        page = 0
        endReached = false
        job = scope.launch {
            runCatching { container.galleries.search(q, 1, "date") }
                .onSuccess { list ->
                    items = list.distinctBy { it.id }
                    page = 1
                    endReached = true
                }
                .onFailure { searchError = it.message ?: AppLocale.strings().home.searchFailed }
            searching = false
        }
    }

    /**
     * 清空搜索框：取消在途请求、清空结果，但**留在搜索页**等用户重新输入。
     * 注意不要退回首页发现流（那是 [exitSearch] 的语义）。
     */
    fun clearSearch() {
        job?.cancel()
        searchMode = true
        activeQuery = null
        searchError = null
        searching = false
        loading = false
        loadMoreError = null
        items = emptyList()
        page = 0
        endReached = false
    }

    /** 退出搜索：回到首页发现流（「取消」按钮 / 系统返回键）。 */
    fun exitSearch() {
        job?.cancel()
        searchError = null
        searching = false
        activeQuery = null
        searchMode = false
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
 * 输入框保持可见（内容就是当前关键词），点 × 只清空输入框并留在搜索页，
 * 点「取消」或按系统返回键才回到首页发现流。
 * 上滑直接滚动列表（原来的下拉唤起搜索手势会把上滑吃掉，已移除），滑到末尾自动加载下一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer, navController: NavController) {
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val s = LocalStrings.current
    val favorites by container.favorites.observeIds().collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    // ---- 仅 UI 态的搜索框展开状态；查询词与结果由 ViewModel 持有 ----
    // rememberSaveable：从详情页返回时输入框内容与展开状态还在。
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    // 只有用户主动唤起搜索时才抢焦点弹键盘，从详情页返回时不要弹。
    var wantFocus by remember { mutableStateOf(false) }

    // 搜索页（含已清空输入框的空态）也一直显示输入框，这样 × 才是「清空」而不是「退出」。
    val fieldVisible = searchOpen || vm.searchMode
    val inSearch = vm.searchMode

    LaunchedEffect(Unit) { vm.ensureLoaded() }

    LaunchedEffect(wantFocus) {
        if (wantFocus) {
            runCatching {
                delay(80)
                focusRequester.requestFocus()
            }
            wantFocus = false
        }
    }

    // 滑到距末尾 4 个条目以内时自动续页；items 增长后 condition 会重新变 false，
    // 因此一次「接近末尾」只会触发一次加载。
    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            !vm.searchMode &&
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
        searchOpen = true
        focusManager.clearFocus()
        vm.search(q)
    }

    /** × ：清空输入框与搜索结果，留在搜索页继续输入。 */
    fun clearSearchBox() {
        query = ""
        vm.clearSearch()
        wantFocus = true
    }

    /** 「取消」/ 返回键：真正退出搜索，回到首页发现流。 */
    fun closeSearch() {
        query = ""
        searchOpen = false
        focusManager.clearFocus()
        if (vm.searchMode) vm.exitSearch()
    }

    BackHandler(enabled = fieldVisible) { closeSearch() }

    Scaffold(
        topBar = {
            if (fieldVisible) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        singleLine = true,
                        placeholder = { Text(s.home.searchHint, maxLines = 1) },
                        leadingIcon = {
                            IconButton(onClick = { submitSearch() }) {
                                Icon(Icons.Filled.Search, contentDescription = s.home.search)
                            }
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { clearSearchBox() }) {
                                    Icon(Icons.Filled.Clear, contentDescription = s.home.clearSearch)
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                    )
                    TextButton(onClick = { closeSearch() }) { Text(s.common.cancel) }
                }
            } else {
                TopAppBar(
                    title = { Text(s.home.discover, maxLines = 1) },
                    actions = {
                        IconButton(onClick = {
                            searchOpen = true
                            wantFocus = true
                        }) {
                            Icon(Icons.Filled.Search, contentDescription = s.home.search)
                        }
                        IconButton(onClick = { navController.navigate("history") }) {
                            Icon(Icons.Filled.History, contentDescription = s.home.history)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { container.galleries.random() }
                                    .onSuccess { navController.navigate("detail/${it.id}") }
                            }
                        }) {
                            Icon(Icons.Filled.Casino, contentDescription = s.home.random)
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!inSearch && !fieldVisible) {
                TabRow(selectedTabIndex = vm.feed.ordinal) {
                    Feed.entries.forEach { f ->
                        Tab(
                            selected = vm.feed == f,
                            onClick = { vm.switchFeed(f) },
                            text = { Text(if (f == Feed.LATEST) s.home.latest else s.home.mostFavorites) },
                        )
                    }
                }
            }
            when {
                vm.searching -> LoadingBox()
                vm.searchError != null -> ErrorBox(vm.searchError!!, onRetry = { vm.retry() })
                inSearch && vm.activeQuery == null -> EmptyBox(s.home.enterKeyword)
                vm.items.isEmpty() && vm.loading -> LoadingBox()
                vm.items.isEmpty() && vm.error != null -> ErrorBox(vm.error!!, onRetry = { vm.retry() })
                vm.items.isEmpty() && inSearch -> EmptyBox(s.noResultFor(vm.activeQuery.orEmpty()))
                vm.items.isEmpty() -> EmptyBox(s.home.noContent)
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
                                        Text(s.common.loadFailedTapRetry)
                                    }
                                    vm.endReached -> Text(
                                        s.common.endOfList,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    else -> Text(
                                        s.common.swipeUpLoadMore,
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
