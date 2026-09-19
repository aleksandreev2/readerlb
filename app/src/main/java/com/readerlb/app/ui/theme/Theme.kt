package com.readerlb.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val Scheme = lightColorScheme(
    primary = Blue,
    secondary = Cyan,
    background = Canvas,
    surface = Card,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = Ink,
    onSurface = Ink,
    outline = Line
)

@Composable
fun ReaderLBTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
