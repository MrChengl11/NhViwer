package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mangaviewer.app.data.AppContainer
import com.mangaviewer.app.data.model.CommentUi
import com.mangaviewer.app.data.repo.CommentRepository
import com.mangaviewer.app.i18n.LocalStrings
import com.mangaviewer.app.i18n.commentsTitle
import com.mangaviewer.app.i18n.writeComment
import kotlinx.coroutines.launch

@Composable
fun CommentSection(
    container: AppContainer,
    galleryId: Long,
    comments: List<CommentUi>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onPosted: () -> Unit,
    snackbar: SnackbarHostState,
) {
    var posting by remember { mutableStateOf(false) }
    var captcha by remember { mutableStateOf("") }
    var showCaptcha by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val s = LocalStrings.current

    LaunchedEffect(galleryId) {
        val saved = container.comments.draft(galleryId)
        if (!saved.isNullOrBlank() && draft.isBlank()) onDraftChange(saved)
    }
    LaunchedEffect(draft) {
        container.comments.saveDraft(galleryId, draft)
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        HorizontalDivider()
        Text(
            s.commentsTitle(comments.size),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 12.dp),
        )
        comments.forEach { comment ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    "${comment.author} · ${comment.postedAt ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(comment.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (comments.isEmpty()) {
            Text(
                s.comments.empty,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        OutlinedTextField(
            value = draft,
            onValueChange = { if (it.length <= CommentRepository.MAX_LENGTH) onDraftChange(it) },
            label = { Text(s.writeComment(CommentRepository.MIN_LENGTH, CommentRepository.MAX_LENGTH)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { showCaptcha = !showCaptcha },
                label = { Text(if (showCaptcha) s.comments.hideCaptcha else s.comments.showCaptcha) },
            )
            Text(
                "${draft.length}/${CommentRepository.MAX_LENGTH}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showCaptcha) {
            OutlinedTextField(
                value = captcha,
                onValueChange = { captcha = it },
                label = { Text(s.comments.tokenLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                s.comments.tokenNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Button(
            onClick = {
                scope.launch {
                    posting = true
                    runCatching { container.comments.post(galleryId, draft, captcha.ifBlank { null }) }
                        .onSuccess {
                            onDraftChange("")
                            snackbar.showSnackbar(s.comments.posted)
                            onPosted()
                        }
                        .onFailure { snackbar.showSnackbar(it.message ?: s.comments.postFailed) }
                    posting = false
                }
            },
            enabled = !posting && draft.trim().length >= CommentRepository.MIN_LENGTH,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            if (posting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(s.comments.solvingPow, modifier = Modifier.padding(start = 8.dp))
            } else {
                Text(s.comments.post)
            }
        }
    }
}
