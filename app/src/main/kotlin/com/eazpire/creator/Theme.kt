package com.eazpire.creator

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val EazpireColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF6366F1),
    secondary = androidx.compose.ui.graphics.Color(0xFF8B5CF6),
)

@Composable
fun EazpireCreatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EazpireColorScheme,
        content = content
    )
}
