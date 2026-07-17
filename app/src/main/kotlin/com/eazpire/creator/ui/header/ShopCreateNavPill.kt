package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors

/**
 * Charcoal Create nav pill (matches web). No pulse animation.
 */
@Composable
fun ShopCreateNavPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RectangleShape)
            .background(EazColors.ShopCreateNavGradient)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
