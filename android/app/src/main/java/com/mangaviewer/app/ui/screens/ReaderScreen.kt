package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.local.ImageQuality
import com.mangaviewer.app.data.local.ReaderLayout
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.data.remote.ImageProxyInterceptor
import com.mangaviewer.app.ui.components.ErrorBox
import com.mangaviewer.app.ui.components.LoadingBox
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(container: AppContainer, galleryId: Long, initialPage: Int, navController: NavController) {
    val settingsState by container.settings.settingsFlow.collectAsState(initial = null)
    val settings = settingsState ?: return
    var paths by remember { mutableStateOf<List<String>?>(null) }
    var title by remember { mutableStateOf("") }
    // 保留画廊 UI 模型，用于在翻页/滚动时持续更新阅读进度。
    var galleryUi by remember { mutableStateOf<GalleryUi?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var overlay by remember { mutableStateOf(true) }
    // 阅读器缩放：双击放大到 2.5x，放大后可拖动查看，单击还原。
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    // 底部页数列表的显隐；阅读页中心的小正方形点击后唤出。
    var pagePickerOpen by remember { mutableStateOf(false) }
    // 「刷新当前页」：refreshTarget 记录被点名的页，refreshNonce 变化时强制该页重新请求图片。
    var refreshTarget by remember { mutableIntStateOf(-1) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    // 各布局把自己的翻页 / 跳页 / 当前页动作注册到这里，由统一手势层和页数列表调用。
    val stepRef = remember {
        object {
            var handler: (Int) -> Unit = {}
            var jump: (Int) -> Unit = {}
            var current: () -> Int = { 0 }
        }
    }

    LaunchedEffect(galleryId) {
        error = null
        // 优先离线：已下载的页面直接用本地文件 / 自定义目录里的 content:// URI。
        val localPages = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            container.downloads.localPages(galleryId)
        }
        if (localPages.isNotEmpty()) {
            paths = localPages
            title = title.ifBlank { "本地阅读" }
            return@LaunchedEffect
        }
        runCatching { container.galleries.detail(galleryId) }
            .onSuccess { d ->
                paths = d.pages.map { it.path }
                title = d.ui.title
                galleryUi = d.ui
                runCatching { container.history.record(d.ui, initialPage - 1, d.pages.size) }
            }
            .onFailure { error = it.message ?: "加载失败" }
    }

    // 手势挂在外层 Box（父级）：子级 Pager / LazyColumn 先拿到事件，拖动交给它们滚动；
    // 只有真正的“点击”才在这里处理。旧实现把 detectTapGestures 放在内容之上的全屏子 Box，
    // 命中测试会挡住下层，滑动全被当成点击（连续滚动布局完全滑不动）。
    val reverse = settings.readerRtl
    // 只有被点名的页随 refreshNonce 重建请求，其余页保持原状，避免整屏重新下载。
    val refreshKeyFor: (Int) -> Int = { index -> if (index == refreshTarget) refreshNonce else 0 }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(reverse, zoom) {
                val centerHalfPx = 36.dp.toPx()
                detectTapGestures(
                    onDoubleTap = { point ->
                        if (zoom > 1f) {
                            zoom = 1f
                            panX = 0f
                            panY = 0f
                        } else {
                            val target = 2.5f
                            zoom = target
                            panX = -(point.x - size.width / 2f) * (target - 1f)
                            panY = -(point.y - size.height / 2f) * (target - 1f)
                        }
                    },
                    onTap = { offset ->
                        if (zoom > 1f) {
                            zoom = 1f
                            panX = 0f
                            panY = 0f
                        } else {
                            // 屏幕正中心 72dp 的透明判定区：点中即唤出底部页数列表，不绘制任何视觉元素。
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val inCenter = offset.x >= cx - centerHalfPx &&
                                offset.x <= cx + centerHalfPx &&
                                offset.y >= cy - centerHalfPx &&
                                offset.y <= cy + centerHalfPx
                            val third = size.width / 3f
                            when {
                                inCenter -> pagePickerOpen = true
                                offset.x < third -> stepRef.handler(if (reverse) 1 else -1)
                                offset.x > third * 2 -> stepRef.handler(if (reverse) -1 else 1)
                                else -> overlay = !overlay
                            }
                        }
                    },
                )
            },
    ) {
        val list = paths
        when {
            error != null -> ErrorBox(error!!, onRetry = { scope.launch { error = null; paths = null } })
            list == null -> LoadingBox()
            list.isEmpty() -> Text("没有页面", color = Color.White, modifier = Modifier.align(Alignment.Center))
            else -> {
                val start = (initialPage - 1).coerceIn(0, list.lastIndex)
                val uiSnapshot = galleryUi
                // 翻页/滚动 400ms 防抖后写一次阅读进度，历史页即可显示真实进度。
                val recordPage: (Int) -> Unit = { pageIndex ->
                    if (uiSnapshot != null) {
                        scope.launch {
                            runCatching {
                                container.history.record(uiSnapshot, pageIndex, list.size)
                            }
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom
                            scaleY = zoom
                            translationX = panX
                            translationY = panY
                        },
                ) {
                when (settings.readerLayout) {
                    ReaderLayout.PAGED_DOUBLE -> {
                        // 双页跨页：每屏并排两页，索引按“跨页序号”推进。
                        val spreads = (list.size + 1) / 2
                        val pagerState = rememberPagerState(initialPage = (start / 2).coerceIn(0, (spreads - 1).coerceAtLeast(0))) { spreads }
                        LaunchedEffect(pagerState.currentPage) {
                            delay(400)
                            recordPage(pagerState.currentPage * 2)
                        }
                        val reverse = settings.readerRtl
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = reverse,
                            key = { it },
                            modifier = Modifier.fillMaxSize(),
                        ) { spread ->
                            val first = spread * 2
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    PageImage(
                                        path = list[first],
                                        container = container,
                                        quality = settings.imageQuality,
                                        isLocal = isLocalPath(list[first]),
                                        preload = settings.preloadCount,
                                        refreshKey = refreshKeyFor(first),
                                        index = first,
                                        total = list.size,
                                        pager = { pagerState.currentPage * 2 },
                                    )
                                }
                                if (first + 1 <= list.lastIndex) {
                                    Box(Modifier.weight(1f).fillMaxHeight()) {
                                        PageImage(
                                            path = list[first + 1],
                                            container = container,
                                            quality = settings.imageQuality,
                                            isLocal = isLocalPath(list[first + 1]),
                                            preload = settings.preloadCount,
                                            refreshKey = refreshKeyFor(first + 1),
                                            index = first + 1,
                                            total = list.size,
                                            pager = { pagerState.currentPage * 2 },
                                        )
                                    }
                                }
                            }
                        }
                        stepRef.handler = { step ->
                            val target = (pagerState.currentPage + step).coerceIn(0, spreads - 1)
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        stepRef.jump = { pageIndex ->
                            val target = (pageIndex / 2).coerceIn(0, spreads - 1)
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        stepRef.current = { (pagerState.currentPage * 2).coerceAtMost(list.lastIndex) }
                        if (settings.showPageNumber) {
                            val cur = (pagerState.currentPage * 2 + 1).coerceAtMost(list.size)
                            val end = (cur + 1).coerceAtMost(list.size)
                            val label = if (cur == end) "$cur / ${list.size}" else "$cur-$end / ${list.size}"
                            Text(
                                label,
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                            )
                        }
                    }
                    ReaderLayout.PAGED_HORIZONTAL -> {
                        val pagerState = rememberPagerState(initialPage = start) { list.size }
                        LaunchedEffect(pagerState.currentPage) {
                            delay(400)
                            recordPage(pagerState.currentPage)
                        }
                        val reverse = settings.readerRtl
                        HorizontalPager(
                            state = pagerState,
                            reverseLayout = reverse,
                            key = { it },
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            Box(Modifier.fillMaxSize()) {
                                PageImage(
                                    path = list[page],
                                    container = container,
                                    quality = settings.imageQuality,
                                    isLocal = isLocalPath(list[page]),
                                    preload = settings.preloadCount,
                                    refreshKey = refreshKeyFor(page),
                                    index = page,
                                    total = list.size,
                                    pager = { pagerState.currentPage },
                                )
                            }
                        }
                        // 点击分区：左 1/3 上一页、右 1/3 下一页、中间切换工具栏。
                        // 单独叠一层 Box，避免 tap 检测器抢走 Pager 的横向拖拽手势。
                        stepRef.handler = { step ->
                            val target = (pagerState.currentPage + step).coerceIn(0, list.lastIndex)
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        stepRef.jump = { pageIndex ->
                            val target = pageIndex.coerceIn(0, list.lastIndex)
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        stepRef.current = { pagerState.currentPage }
                        if (settings.showPageNumber) {
                            Text(
                                "${pagerState.currentPage + 1} / ${list.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                            )
                        }
                    }
                    ReaderLayout.PAGED_VERTICAL -> {
                        val pagerState = rememberPagerState(initialPage = start) { list.size }
                        LaunchedEffect(pagerState.currentPage) {
                            delay(400)
                            recordPage(pagerState.currentPage)
                        }
                        VerticalPager(state = pagerState, key = { it }, modifier = Modifier.fillMaxSize()) { page ->
                            PageImage(
                                path = list[page],
                                container = container,
                                quality = settings.imageQuality,
                                isLocal = isLocalPath(list[page]),
                                preload = settings.preloadCount,
                                refreshKey = refreshKeyFor(page),
                                index = page,
                                total = list.size,
                                pager = { pagerState.currentPage },
                            )
                        }
                        stepRef.handler = { step ->
                            val target = (pagerState.currentPage + step).coerceIn(0, list.lastIndex)
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        stepRef.jump = { pageIndex ->
                            val target = pageIndex.coerceIn(0, list.lastIndex)
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        stepRef.current = { pagerState.currentPage }
                        if (settings.showPageNumber) {
                            Text(
                                "${pagerState.currentPage + 1} / ${list.size}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                            )
                        }
                    }
                    ReaderLayout.CONTINUOUS -> {
                        val state = rememberLazyListState(initialFirstVisibleItemIndex = start)
                        LaunchedEffect(state.firstVisibleItemIndex) {
                            delay(400)
                            recordPage(state.firstVisibleItemIndex)
                        }
                        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(list) { index, path ->
                                PageImage(
                                    path = path,
                                    container = container,
                                    quality = settings.imageQuality,
                                    isLocal = isLocalPath(path),
                                    preload = settings.preloadCount,
                                    refreshKey = refreshKeyFor(index),
                                    index = index,
                                    total = list.size,
                                    pager = { state.firstVisibleItemIndex },
                                )
                            }
                        }
                        stepRef.jump = { pageIndex ->
                            scope.launch { state.scrollToItem(pageIndex.coerceIn(0, list.lastIndex)) }
                        }
                        stepRef.current = { state.firstVisibleItemIndex }
                    }
                }
                }

                // 放大后才挂拖动层，避免吃掉了 Pager 的翻页手势。
                if (zoom > 1f) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectTransformGestures { _, pan, gestureZoom, _ ->
                                val next = (zoom * gestureZoom).coerceIn(1f, 5f)
                                if (next <= 1.01f) {
                                    zoom = 1f
                                    panX = 0f
                                    panY = 0f
                                } else {
                                    zoom = next
                                    panX += pan.x
                                    panY += pan.y
                                }
                            }
                        },
                    )
                }
            }
        }

        if (overlay) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
                }
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                IconButton(onClick = {
                    if (zoom > 1f) {
                        zoom = 1f
                        panX = 0f
                        panY = 0f
                    } else {
                        zoom = 2.5f
                        panX = 0f
                        panY = 0f
                    }
                }) {
                    Icon(
                        if (zoom > 1f) Icons.Filled.ZoomOut else Icons.Filled.ZoomIn,
                        contentDescription = if (zoom > 1f) "缩小" else "放大",
                        tint = Color.White,
                    )
                }
                // 刷新当前页：只让当前页重新请求图片（失败重试 / 图片更新），其余页不受影响。
                IconButton(onClick = {
                    refreshTarget = stepRef.current()
                    refreshNonce++
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新当前页", tint = Color.White)
                }
                // 换排版单独一个按钮：按 双页 → 横滑 → 竖滑 → 连续 循环切换。
                IconButton(onClick = {
                    val next = when (settings.readerLayout) {
                        ReaderLayout.PAGED_HORIZONTAL -> ReaderLayout.PAGED_DOUBLE
                        ReaderLayout.PAGED_DOUBLE -> ReaderLayout.PAGED_VERTICAL
                        ReaderLayout.PAGED_VERTICAL -> ReaderLayout.CONTINUOUS
                        ReaderLayout.CONTINUOUS -> ReaderLayout.PAGED_HORIZONTAL
                    }
                    scope.launch { container.settings.setLayout(next) }
                }) {
                    Icon(Icons.Filled.Autorenew, contentDescription = "切换布局", tint = Color.White)
                }
            }
        }

        val pageList = paths
        if (pagePickerOpen && pageList != null && pageList.isNotEmpty()) {
            PagePickerSheet(
                total = pageList.size,
                currentPage = stepRef.current().coerceIn(0, pageList.lastIndex),
                onPick = { index ->
                    pagePickerOpen = false
                    stepRef.jump(index)
                },
                onDismiss = { pagePickerOpen = false },
            )
        }
    }
}

/** 本地页面：应用私有目录的绝对路径，或自定义下载目录的 content:// URI。 */
private fun isLocalPath(path: String): Boolean =
    path.startsWith("/") || path.startsWith("content://")

@Composable
private fun PageImage(
    path: String,
    container: AppContainer,
    quality: ImageQuality,
    isLocal: Boolean,
    preload: Int,
    index: Int,
    total: Int,
    pager: () -> Int,
    refreshKey: Int = 0,
) {
    // 在线页有多个图床候选：i* 在本机常年 520，失败时自动换到 t*，避免整屏黑。
    // refreshKey 变化时重建候选与失败计数，让「刷新当前页」从第一个图床重新开始。
    val candidates = remember(path, isLocal, quality, refreshKey) {
        if (isLocal) listOf<String>(path)
        else ImageProxyInterceptor.viewerCandidates(path) { x -> x + quality.query }
    }
    var hostIndex by remember(path, refreshKey) { mutableIntStateOf(0) }
    val model = remember(candidates, hostIndex, isLocal) {
        val current = candidates.getOrNull(hostIndex.coerceIn(0, candidates.lastIndex))
        // 本地页可能是应用私有路径（File）或自定义下载目录的 content:// URI（Coil 可直接加载）。
        if (isLocal) current?.let { if (it.startsWith("content://")) it else File(it) } else current
    }
    // 刷新时给请求叠加 nonce 缓存键，绕过 Coil 内存/磁盘缓存重新解码或重新下载；
    // 本地页（私有目录 / content://）只重建解码，不改写来源。
    val context = LocalContext.current
    val request = remember(model, refreshKey, hostIndex, isLocal) {
        if (refreshKey <= 0) {
            model
        } else {
            val token = "$refreshKey-$hostIndex-" + path.hashCode()
            ImageRequest.Builder(context).data(model).apply {
                memoryCacheKey("reader-refresh-$token")
                if (!isLocal) diskCacheKey("reader-refresh-$token")
            }.build()
        }
    }
    // 刷新时给一个小转圈，否则远程图重新下载期间整屏纯黑，看起来像刷新坏了。
    var refreshing by remember(refreshKey) { mutableStateOf(refreshKey > 0) }
    LaunchedEffect(refreshKey) { refreshing = refreshKey > 0 }
    Box(Modifier.fillMaxSize().fillMaxHeight(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            contentDescription = "第 ${index + 1} 页",
            contentScale = ContentScale.Fit,
            onLoading = { if (refreshKey > 0) refreshing = true },
            onSuccess = { refreshing = false },
            onError = {
                refreshing = false
                if (!isLocal && hostIndex < candidates.lastIndex) hostIndex++
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (refreshing) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
        }
        // 预加载窗口由 Pager/Lazy 的相邻项组合决定；preload 仅用于潜在的自定义预热。
        @Suppress("UNUSED_EXPRESSION")
        (preload >= 0 && kotlin.math.abs(pager() - index) <= preload)
    }
}

/** 底部页数列表：九宫格式页码，点一下跳到对应页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagePickerSheet(
    total: Int,
    currentPage: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "跳转到页面",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "共 $total 页 · 当前 ${currentPage + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(56.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(total, key = { it }) { index ->
                    val selected = index == currentPage
                    Box(
                        Modifier
                            .size(52.dp)
                            .background(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(10.dp),
                            )
                            .pointerInput(index) {
                                detectTapGestures(onTap = { onPick(index) })
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${index + 1}",
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
