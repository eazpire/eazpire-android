package com.eazpire.creator.shop.sidebar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.eazpire.creator.EazColors
import com.eazpire.creator.ui.nav.EazNavTablerIcon

@Composable
fun SidebarCategoryIcon(
    handle: String,
    modifier: Modifier = Modifier,
    tint: Color = EazColors.Orange,
) {
    EazNavTablerIcon(
        handle = handle,
        modifier = modifier,
        tint = tint,
    )
}
