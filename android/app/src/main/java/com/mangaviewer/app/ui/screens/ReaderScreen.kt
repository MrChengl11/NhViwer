package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
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

    LaunchedEffect(galleryId) {
        error = null
        // 优先离线：本地已下载的目录直接用文件
        val localDir = container.downloads.galleryDir(galleryId)
        val localFiles = localDir.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") }?.sortedBy { it.name }
        if (!localFiles.isNullOrEmpty()) {
            paths = localFiles.map { it.absolutePath }
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
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
                // 各布局把自己的翻页动作注册到这里，由统一手势层调用，避免每个分支各挂一层 pointerInput。
                val stepRef = remember { object { var handler: (Int) -> Unit = {} } }
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
                                        isLocal = list[first].startsWith("/"),
                                        preload = settings.preloadCount,
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
                                            isLocal = list[first + 1].startsWith("/"),
                                            preload = settings.preloadCount,
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
                                    isLocal = list[page].startsWith("/"),
                                    preload = settings.preloadCount,
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
                                isLocal = list[page].startsWith("/"),
                                preload = settings.preloadCount,
                                index = page,
                                total = list.size,
                                pager = { pagerState.currentPage },
                            )
                        }
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
                                    isLocal = path.startsWith("/"),
                                    preload = settings.preloadCount,
                                    index = index,
                                    total = list.size,
                                    pager = { state.firstVisibleItemIndex },
                                )
                            }
                        }
                    }
                }
                }
                                // 统一手势层（永远在内容之上）：单击按分区翻页/中间切换工具栏；双击放大 2.5x；放大后单击还原。
                val reverse = settings.readerRtl
                Box(
                    Modifier.fillMaxSize().pointerInput(reverse, zoom) {
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
                                    val third = size.width / 3f
                                    val step = when {
                                        offset.x < third -> if (reverse) 1 else -1
                                        offset.x > third * 2 -> if (reverse) -1 else 1
                                        else -> 0
                                    }
                                    if (step == 0) overlay = !overlay else stepRef.handler(step)
                                }
                            },
                        )
                    },
                )
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
    }
}

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
) {
    // 在线页有多个图床候选：i* 在本机常年 520，失败时自动换到 t*，避免整屏黑。
    val candidates = remember(path, isLocal, quality) {
        if (isLocal) listOf<String>(path)
        else ImageProxyInterceptor.viewerCandidates(path) { x -> x + quality.query }
    }
    var hostIndex by remember(path) { mutableIntStateOf(0) }
    val model = remember(candidates, hostIndex, isLocal) {
        val current = candidates.getOrNull(hostIndex.coerceIn(0, candidates.lastIndex))
        if (isLocal) current?.let { File(it) } else current
    }
    Box(Modifier.fillMaxSize().fillMaxHeight(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = model,
            contentDescription = "第 ${index + 1} 页",
            contentScale = ContentScale.Fit,
            onError = {
                if (!isLocal && hostIndex < candidates.lastIndex) hostIndex++
            },
            modifier = Modifier.fillMaxSize(),
        )
        // 预加载窗口由 Pager/Lazy 的相邻项组合决定；preload 仅用于潜在的自定义预热。
        @Suppress("UNUSED_EXPRESSION")
        (preload >= 0 && kotlin.math.abs(pager() - index) <= preload)
    }
}