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

/** 18+ 门控：首次启动必须确认成年。 */
@Composable
fun GateScreen(onConfirm: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("成人内容提醒", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "本应用仅用于技术研究与学习，内容来自第三方站点，应用本身不托管任何内容。\n\n" +
                    "继续使用即表示你已年满 18 周岁，并自行承担查看相关内容的风险与法律责任。",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            ) { Text("我已年满 18 周岁") }
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("未满 18 周岁请卸载") }
        }
    }
}
