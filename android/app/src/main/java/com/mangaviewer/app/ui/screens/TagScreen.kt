package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.ui.components.EmptyBox
import com.mangaviewer.app.ui.components.ErrorBox
import com.mangaviewer.app.ui.components.GalleryCard
import com.mangaviewer.app.ui.components.LoadingBox
import kotlinx.coroutines.launch

private val SORTS = listOf("date" to "最新", "popular" to "热门")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagScreen(
    container: AppContainer,
    tagType: String,
    tagId: Long,
    tagName: String,
    navController: NavController,
) {
    var sort by remember { mutableStateOf("date") }
    var page by remember { mutableIntStateOf(1) }
    var items by remember { mutableStateOf<List<GalleryUi>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val favorites by container.favorites.observeIds().collectAsState(initial = emptySet())

    suspend fun load(reset: Boolean) {
        loading = true
        error = null
        val target = if (reset) 1 else page + 1
        runCatching { container.galleries.tagged(tagId, target, sort) }
            .onSuccess {
                items = if (reset) it else items + it
                page = target
            }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    LaunchedEffect(tagId, sort) {
        page = 1
        items = emptyList()
        load(true)
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
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SORTS.forEach { (value, label) ->
                    FilterChip(selected = sort == value, onClick = { sort = value }, label = { Text(label) })
                }
            }
            when {
                items.isEmpty() && loading -> LoadingBox()
                items.isEmpty() && error != null -> ErrorBox(error!!, onRetry = { scope.launch { load(true) } })
                items.isEmpty() -> EmptyBox("没有结果")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
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
                    item {
                        Box(Modifier.fillMaxWidth().aspectRatio(0.72f)) {
                            FilledTonalButton(
                                onClick = { scope.launch { load(false) } },
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                            ) { Text(if (loading) "加载中…" else "加载更多") }
                        }
                    }
                }
            }
        }
    }
}
