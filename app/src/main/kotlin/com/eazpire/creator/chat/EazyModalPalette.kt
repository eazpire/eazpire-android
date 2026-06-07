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
    val headerSecondary: Color,
    val border: Color = Color(0xFF374151),
)

/** Creator dashboard — dark chat panel (body.creator-mode on web). */
private val CreatorPalette = EazyModalPalette(
    bg = Color(0xFF1F2937),
    header = Color(0xFF111827),
    headerSecondary = Color(0xFF111827),
    accent = Color(0xFFF97316),
    text = Color(0xFFE5E7EB),
    muted = Color(0xFF9CA3AF),
    userBubble = Color(0xFFF97316),
    assistantBubble = Color(0xFF111827),
    border = Color(0xFF374151),
)

/** Shop — light panel (body:not(.creator-mode) on web). */
private val ShopPalette = EazyModalPalette(
    bg = Color(0xFFFFFFFF),
    header = Color(0xFFF9FAFB),
    headerSecondary = Color(0xFFF9FAFB),
    accent = Color(0xFFF97316),
    text = Color(0xFF111827),
    muted = Color(0xFF6B7280),
    userBubble = Color(0xFFF97316),
    assistantBubble = Color(0xFFF3F4F6),
    border = Color(0xFFE5E7EB),
)

fun eazyPaletteFor(context: EazyChatContext): EazyModalPalette = when (context) {
    EazyChatContext.Creator -> CreatorPalette
    EazyChatContext.Shop -> ShopPalette
}

val LocalEazyModalPalette = staticCompositionLocalOf { CreatorPalette }
