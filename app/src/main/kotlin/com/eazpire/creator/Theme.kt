package com.eazpire.creator

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** eazpire Brand Colors (aligned with theme --orange, #f97316) */
object EazColors {
    val Orange = Color(0xFFF97316)
    val OrangeHover = Color(0xFFFB923C)
    val OrangeDark = Color(0xFFEA580C)
    val OrangeBg = Color(0x0FF97316) // 6% opacity
    val TopbarBorder = Color(0x0A000000)
    val TextPrimary = Color(0xCF000000)
    val TextSecondary = Color(0x66000000)
}

private val EazpireColorScheme = lightColorScheme(
    primary = EazColors.Orange,
    onPrimary = Color.White,
    secondary = EazColors.OrangeDark,
    onSecondary = Color.White,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = EazColors.TextSecondary,
    background = Color(0xFFFAFAFA),
    onBackground = EazColors.TextPrimary,
    outline = Color(0xFFE5E7EB),
)

@Composable
fun EazpireCreatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EazpireColorScheme,
        content = content
    )
}
