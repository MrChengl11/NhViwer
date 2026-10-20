package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.ui.components.EmptyBox
import com.mangaviewer.app.ui.components.GalleryThumb
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(container: AppContainer, navController: NavController) {
    val records by container.history.observeRecent(200).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("阅读历史") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = { TextButton(onClick = { scope.launch { container.history.clear() } }) { Text("清空") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (records.isEmpty()) {
                EmptyBox("还没有阅读记录")
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(records, key = { it.galleryId }) { record ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GalleryThumb(
                                path = record.thumbPath,
                                modifier = Modifier.size(width = 48.dp, height = 66.dp).clip(RoundedCornerShape(8.dp)),
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(record.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                Text(
                                    "看到第 ${record.lastPage + 1}/${record.numPages} 页 · ${fmt.format(Date(record.updatedAt))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = {
                                navController.navigate("reader/${record.galleryId}?page=${record.lastPage + 1}")
                            }) { Text("继续") }
                        }
                    }
                }
            }
        }
    }
}
