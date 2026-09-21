package com.readerlb.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF16314A),
    onPrimaryContainer = Color(0xFFDCEFFF),
    secondary = Cyan,
    background = Canvas,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Color(0xFF1D2128),
    onSurfaceVariant = Muted,
    outline = Color(0xFF3B414B),
    outlineVariant = Line,
    tertiary = Success,
    tertiaryContainer = Color(0xFF16352B),
    onTertiaryContainer = Color(0xFFB8F2DE),
    error = Color(0xFFFF7B7B),
    errorContainer = Color(0xFF3A2022),
    onErrorContainer = Color(0xFFFFD5D5)
)

@Composable
fun ReaderLBTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
