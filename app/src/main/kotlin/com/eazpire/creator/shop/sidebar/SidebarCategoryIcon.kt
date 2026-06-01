package com.eazpire.creator.shop.sidebar

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eazpire.creator.EazColors

@Composable
fun SidebarCategoryIcon(
    handle: String,
    modifier: Modifier = Modifier,
) {
    Icon(
        imageVector = SidebarNavVisuals.vectorForHandle(handle),
        contentDescription = null,
        tint = EazColors.Orange,
        modifier = modifier,
    )
}
