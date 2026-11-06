package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.i18n.LocalStrings
import com.mangaviewer.app.i18n.UiStrings
import com.mangaviewer.app.ui.components.EmptyBox
import com.mangaviewer.app.ui.components.ErrorBox
import com.mangaviewer.app.ui.components.GalleryCard
import com.mangaviewer.app.ui.components.LoadingBox
import kotlinx.coroutines.launch

/** 快捷筛选：按命名空间补全搜索语法。 */
private val QUICK_FILTERS = listOf("language:", "tag:", "artist:", "parody:", "character:", "group:")

/** 快捷筛选按钮上的类型名，来自字符串表。 */
private fun quickFilterLabel(s: UiStrings, prefix: String): String = when (prefix) {
    "language:" -> s.tagTypes.language
    "tag:" -> s.tagTypes.tag
    "artist:" -> s.tagTypes.artist
    "parody:" -> s.tagTypes.parody
    "character:" -> s.tagTypes.character
    else -> s.tagTypes.group
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(container: AppContainer, navController: NavController) {
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<GalleryUi>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current
    val favorites by container.favorites.observeIds().collectAsState(initial = emptySet())

    fun run(page: Int = 1) {
        scope.launch {
            loading = true
            error = null
            submitted = true
            runCatching { container.galleries.search(query.trim(), page, "date") }
                .onSuccess { items = it }
                .onFailure { error = it.message ?: s.search.searchFailed }
            loading = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(s.search.title) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(s.search.hint) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(QUICK_FILTERS.size) { index ->
                    val prefix = QUICK_FILTERS[index]
                    AssistChip(
                        onClick = { query = (query.trimEnd() + " " + prefix).trimStart() },
                        label = { Text(quickFilterLabel(s, prefix)) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { run() }, modifier = Modifier.weight(1f)) { Text(s.home.search) }
            }
            when {
                loading -> LoadingBox()
                error != null -> ErrorBox(error!!, onRetry = { run() })
                submitted && items.isEmpty() -> EmptyBox(s.common.noResults)
                !submitted -> EmptyBox(s.home.enterKeyword)
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
                }
            }
        }
    }
}
