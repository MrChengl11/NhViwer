package com.mangaviewer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mangaviewer.app.data.model.GalleryUi
import com.mangaviewer.app.data.remote.ImageProxyInterceptor

/** 缩略图：走图片候选列表（t1..t4 优先），反代由 Coil 拦截器完成。 */
@Composable
fun GalleryThumb(
    path: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val model = ImageProxyInterceptor.candidates(path, thumb = true).firstOrNull()
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Text("无图", style = MaterialTheme.typography.labelSmall)
        } else {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun GalleryCard(
    item: GalleryUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box {
            GalleryThumb(
                path = item.thumbPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(12.dp)),
                contentDescription = item.title,
            )
            if (favorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            text = "${item.numPages} 页 · ${item.numFavorites} 收藏",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.padding(top = 12.dp)) {
            androidx.compose.material3.Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
fun EmptyBox(message: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
