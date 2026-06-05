package com.eazpire.creator.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazpire.creator.ui.nav.EazNavTablerIconByName

/** @deprecated use [com.eazpire.creator.ui.nav.EazNavTablerIcon] — kept for home category strip */
@Composable
fun HomeCategoryTablerIcon(
    categoryId: String,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFF97316),
    iconSize: Dp = 22.dp,
) {
    EazNavTablerIconByName(
        iconName = categoryId,
        modifier = modifier,
        tint = tint,
        iconSize = iconSize,
    )
}
