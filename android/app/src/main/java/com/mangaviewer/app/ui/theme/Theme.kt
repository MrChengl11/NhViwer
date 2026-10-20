package com.mangaviewer.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.mangaviewer.app.data.local.Settings

/**
 * Material 3 主题：
 * - Android 12+ 且开启动态取色时使用系统壁纸色（Material You）；
 * - 否则用手写的种子色方案（默认紫色 #6750A4，与 Web 版保持一致）。
 */
@Composable
fun MangaTheme(
    settings: Settings,
    content: @Composable () -> Unit,
) {
    val dark = settings.darkMode || isSystemInDarkTheme() && !settings.darkMode
    val darkTheme = if (settings.darkMode) true else isSystemInDarkTheme()
    val context = LocalContext.current
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        settings.dynamicColor && supportsDynamic && darkTheme -> dynamicDarkColorScheme(context)
        settings.dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        darkTheme -> seededDark(settings.seedColor)
        else -> seededLight(settings.seedColor)
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

private fun seededLight(seed: Long): androidx.compose.material3.ColorScheme {
    val primary = Color(seed)
    return lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        primaryContainer = primary.copy(alpha = 0.12f).compositeOver(Color.White),
        onPrimaryContainer = primary,
        secondaryContainer = Color(0xFFE8DEF8),
        onSecondaryContainer = Color(0xFF1D192B),
        surface = Color(0xFFFFFBFE),
        background = Color(0xFFFFFBFE),
    )
}

private fun seededDark(seed: Long): androidx.compose.material3.ColorScheme {
    val primary = Color(seed).lighten()
    return darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF371E73),
        primaryContainer = Color(0xFF4F378B),
        onPrimaryContainer = Color(0xFFEADDFF),
        secondaryContainer = Color(0xFF4A4458),
        onSecondaryContainer = Color(0xFFE8DEF8),
        surface = Color(0xFF141218),
        background = Color(0xFF141218),
    )
}

private fun Color.lighten(): Color = Color(
    red = red + (1f - red) * 0.45f,
    green = green + (1f - green) * 0.45f,
    blue = blue + (1f - blue) * 0.45f,
    alpha = alpha,
)

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}
