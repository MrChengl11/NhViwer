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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val SORTS = listOf("date" to "最新", "popular" to "热门")

/**
 * Tag 详情列表状态放在 ViewModel 里，从详情页返回时列表不会丢；
 * 分页改为滑到末尾自动续页，不再有「加载更多」按钮。
 */
internal class TagViewModel(
    private val container: AppContainer,
    private val tagId: Long,
) : ViewModel() {

    var sort by mutableStateOf("date")
        private set
    var items by mutableStateOf<List<GalleryUi>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var endReached by mutableStateOf(false)
        private set
    var loadMoreError by mutableStateOf<String?>(null)
        private set

    private var page = 0
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun ensureLoaded() {
        if (items.isEmpty() && !loading && error == null) load(reset = true)
    }

    fun changeSort(value: String) {
        if (sort == value) return
        sort = value
        reload()
    }

    fun retry() = reload()

    fun loadMore() {
        if (loading || endReached) return
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
        page = 0
        endReached = false
        error = null
        loadMoreError = null
        items = emptyList()
        load(reset = true)
    }

    fun load(reset: Boolean) {
        if (loading) return
        if (!reset && (endReached || loadMoreError != null)) return
        val targetPage = if (reset) 1 else page + 1
        loading = true
        if (reset) error = null
        loadMoreError = null
        job = scope.launch {
            runCatching { container.galleries.tagged(tagId, targetPage, sort) }
                .onSuccess { list ->
                    val before = items.size
                    // 按 id 去重：两次请求之间榜单排序会变化，直接拼接会出现重复 key，
                    // LazyGrid 抛 "Key was already used" 直接崩溃。
                    val merged = if (reset) list else items + list
                    items = merged.distinctBy { it.id }
                    page = targetPage
                    endReached = list.isEmpty() || list.size < PAGE_SIZE
                    // 整页都是已有条目时去重后没有增长，继续取下一页。
                    if (!reset && items.size == before) load(false)
                }
                .onFailure {
                    val msg = it.message ?: "加载失败"
                    if (reset || items.isEmpty()) error = msg else loadMoreError = msg
                }
            loading = false
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagScreen(
    container: AppContainer,
    tagType: String,
    tagId: Long,
    tagName: String,
    navController: NavController,
) {
    val vm: TagViewModel = viewModel(key = "tag_$tagId") { TagViewModel(container, tagId) }
    val gridState = rememberLazyGridState()
    val favorites by container.favorites.observeIds().collectAsState(initial = emptySet())

    LaunchedEffect(Unit) { vm.ensureLoaded() }

    // 滑到距末尾 4 个条目以内时自动续页；原「加载更多」按钮已移除。
    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            vm.items.isNotEmpty() &&
                !vm.loading &&
                !vm.endReached &&
                vm.loadMoreError == null &&
                lastVisible >= vm.items.size - 4
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) vm.loadMore() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tagName.isBlank()) "#$tagId" else tagName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SORTS.forEach { (value, label) ->
                    FilterChip(
                        selected = vm.sort == value,
                        onClick = { vm.changeSort(value) },
                        label = { Text(label) },
                    )
                }
            }
            when {
                vm.items.isEmpty() && vm.loading -> LoadingBox()
                vm.items.isEmpty() && vm.error != null -> ErrorBox(vm.error!!, onRetry = { vm.retry() })
                vm.items.isEmpty() -> EmptyBox("没有结果")
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
