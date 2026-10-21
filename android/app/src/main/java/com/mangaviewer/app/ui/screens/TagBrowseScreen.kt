package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.model.TagUi
import com.mangaviewer.app.data.repo.TagIndexRepository.Companion.LETTERS
import com.mangaviewer.app.ui.components.EmptyBox
import com.mangaviewer.app.ui.components.ErrorBox
import com.mangaviewer.app.ui.components.LoadingBox

/** 分类页只展示标签，其余类型都走同一个 tag 检索接口。 */
private const val TYPE_TAG = "tag"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagBrowseScreen(container: AppContainer, navController: NavController) {
    // 只保留「标签」：作者/原作/角色/社团这些分类实际都走 tag 接口，展示出来只会让人误以为有效。
    // 默认选 A
    var letter by remember { mutableStateOf("A") }
    var tagList by remember { mutableStateOf<List<TagUi>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var total by remember { mutableStateOf(0) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(letter, reloadKey) {
        loading = true
        error = null
        tagList = container.tagIndex.cached(TYPE_TAG, letter).orEmpty()
        runCatching {
            val t = container.tagIndex.totalTags(TYPE_TAG)
            val list = container.tagIndex.tagsForLetter(TYPE_TAG, letter)
            t to list
        }.onSuccess { pair ->
            total = pair.first
            tagList = pair.second
        }.onFailure {
            error = it.message ?: "加载失败"
        }
        loading = false
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("分类") }) },
        bottomBar = { LetterBar(selected = letter, onSelect = { letter = it }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (letter == "#") "数字 / 符号" else "$letter 开头",
                    style = MaterialTheme.typography.titleSmall,
                )
                Box(Modifier.weight(1f))
                Text(
                    text = if (loading && tagList.isEmpty()) "加载中…" else "${tagList.size} 个 · 共 $total 个",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                tagList.isEmpty() && loading -> LoadingBox()
                tagList.isEmpty() && error != null -> ErrorBox(error!!, onRetry = { reloadKey += 1 })
                tagList.isEmpty() -> EmptyBox("该字母下暂无标签")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(tagList, key = { it.id }) { tag ->
                        TagCell(tag) {
                            val encoded = java.net.URLEncoder.encode(tag.name, "UTF-8")
                            navController.navigate("tag/${tag.type}/${tag.id}?name=$encoded")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TagCell(tag: TagUi, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        border = AssistChipDefaults.assistChipBorder(enabled = true),
        label = {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${tag.count} 部",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** 底部固定的 # A..Z 字母条，可横向滑动。 */
@Composable
private fun LetterBar(selected: String, onSelect: (String) -> Unit) {
    Surface(tonalElevation = 3.dp) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(LETTERS, key = { it }) { l ->
                val isSel = l == selected
                Surface(
                    onClick = { onSelect(l) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp).height(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(l, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
