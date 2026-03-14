package com.eazpire.creator

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Shop-Orange wie im Theme (--admin-accent-warm, #f97316) */
private val EazpireOrange = Color(0xFFF97316)
private val EazpireOrangeDark = Color(0xFFEA580C)

private val EazpireColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = EazpireOrange,
    secondary = EazpireOrangeDark,
    onSecondary = Color.White,
    surface = EazpireOrange,
    onSurface = Color.White,
    background = EazpireOrange,
    onBackground = Color.White,
)

@Composable
fun EazpireCreatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EazpireColorScheme,
        content = content
    )
}
