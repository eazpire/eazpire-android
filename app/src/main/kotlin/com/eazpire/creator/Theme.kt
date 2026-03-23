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

    /** Creator Settings modal / Shop dark panels (aligned with CreatorSettingsModal sheet) */
    object CreatorModal {
        val SheetBg = Color(0xFF070B14)
        val ContentBg = Color(0xFF0B1220)
        val Elevated = Color(0xFF121A2E)
        val Border = Color.White.copy(alpha = 0.14f)
        val TextPrimary = Color.White
        val TextSecondary = Color.White.copy(alpha = 0.65f)
    }
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

/**
 * Shop bottom sheets (Create product, native studio): same brand tokens as [EazpireColorScheme],
 * with explicit containers for orange-tint surfaces and readable on-surface text.
 */
val EazShopSheetColorScheme = lightColorScheme(
    primary = EazColors.Orange,
    onPrimary = Color.White,
    secondary = EazColors.OrangeDark,
    onSecondary = Color.White,
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = EazColors.TextPrimary,
    surface = Color.White,
    onSurface = EazColors.TextPrimary,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = EazColors.TextSecondary,
    background = Color(0xFFFAFAFA),
    onBackground = EazColors.TextPrimary,
    outline = Color(0xFFE5E7EB),
    error = Color(0xFFB91C1C),
    onError = Color.White
)

@Composable
fun EazpireCreatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EazpireColorScheme,
        content = content
    )
}
