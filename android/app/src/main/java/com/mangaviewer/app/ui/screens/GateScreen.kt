package com.mangaviewer.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangaviewer.app.i18n.LocalStrings

/** 18+ 门控：首次启动必须确认成年。 */
@Composable
fun GateScreen(onConfirm: () -> Unit) {
    val s = LocalStrings.current
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(s.gate.title, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = s.gate.body1 + s.gate.body2,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            ) { Text(s.gate.confirm) }
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(s.gate.leave) }
        }
    }
}
