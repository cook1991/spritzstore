package net.spritzlive.spritzstore.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF4CD08A)

private val DarkColors = darkColorScheme(primary = Accent, secondary = Accent)
private val LightColors = lightColorScheme(primary = Accent, secondary = Accent)

@Composable
fun SpritzStoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
