package com.eazpire.creator.chat

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class EazyModalPalette(
    val bg: Color,
    val header: Color,
    val accent: Color,
    val text: Color,
    val muted: Color,
    val userBubble: Color,
    val assistantBubble: Color,
    /** Slightly different secondary header (e.g. tab strip) */
    val headerSecondary: Color
)

private val CreatorPalette = EazyModalPalette(
    bg = Color(0xFF1F2937),
    header = Color(0xFF111827),
    headerSecondary = Color(0xFF111827),
    accent = Color(0xFFF97316),
    text = Color(0xFFE5E7EB),
    muted = Color(0xFF9CA3AF),
    userBubble = Color(0xFF374151),
    assistantBubble = Color(0xFF4B5563)
)

private val ShopPalette = EazyModalPalette(
    bg = Color(0xFF0B1220),
    header = Color(0xFF060B14),
    headerSecondary = Color(0xFF0D1626),
    accent = Color(0xFF38BDF8),
    text = Color(0xFFF1F5F9),
    muted = Color(0xFF94A3B8),
    userBubble = Color(0xFF1E3A5F),
    assistantBubble = Color(0xFF162238)
)

fun eazyPaletteFor(context: EazyChatContext): EazyModalPalette = when (context) {
    EazyChatContext.Shop -> ShopPalette
    EazyChatContext.Creator -> CreatorPalette
}

val LocalEazyModalPalette = staticCompositionLocalOf { CreatorPalette }
