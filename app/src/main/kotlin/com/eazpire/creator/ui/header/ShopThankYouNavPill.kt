package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors

/**
 * Ice-blue Thank You nav tile (matches web `#eazThankyouNavBtn`).
 * Flush against Create — no orange header strip between the tiles.
 */
@Composable
fun ShopThankYouNavPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RectangleShape)
            .background(EazColors.ShopThankYouNavBg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
