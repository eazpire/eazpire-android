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

/** Shared chat palette — matches web `--chat-bg` / `--chat-accent` for shop and creator. */
private val ChatPalette = EazyModalPalette(
    bg = Color(0xFF1F2937),
    header = Color(0xFF111827),
    headerSecondary = Color(0xFF111827),
    accent = Color(0xFFF97316),
    text = Color(0xFFE5E7EB),
    muted = Color(0xFF9CA3AF),
    userBubble = Color(0xFF374151),
    assistantBubble = Color(0xFF4B5563)
)

fun eazyPaletteFor(@Suppress("UNUSED_PARAMETER") context: EazyChatContext): EazyModalPalette = ChatPalette

val LocalEazyModalPalette = staticCompositionLocalOf { ChatPalette }
