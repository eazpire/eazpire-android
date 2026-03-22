package com.eazpire.creator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors

/** Page size for Creations products tab and hero product picker (shop collection uses server paging). */
const val CREATIONS_PRODUCTS_PER_PAGE = 24

enum class PaginationDotsStyle {
    /** Collection / shop bottom bar */
    Light,
    /** Creator Creations + dark modals */
    Dark
}

/**
 * Dots + horizontal swipe (same behavior as [com.eazpire.creator.ui.CollectionScreen] pagination).
 */
@Composable
fun ProductPaginationDots(
    totalPages: Int,
    currentPage: Int,
    onPageClick: (Int) -> Unit,
    onSwipePrev: () -> Unit = {},
    onSwipeNext: () -> Unit = {},
    modifier: Modifier = Modifier,
    style: PaginationDotsStyle = PaginationDotsStyle.Dark,
    /** Optional line inside the same bar as the dots (e.g. swipe hint). */
    swipeHint: String? = null
) {
    if (totalPages <= 1) return
    val density = LocalDensity.current
    val barBg = when (style) {
        PaginationDotsStyle.Light -> Color(0xFFF5F5F5)
        PaginationDotsStyle.Dark -> Color(0xFF1C2434).copy(alpha = 0.95f)
    }
    val inactiveDot = when (style) {
        PaginationDotsStyle.Light -> EazColors.TextSecondary.copy(alpha = 0.3f)
        PaginationDotsStyle.Dark -> Color.White.copy(alpha = 0.28f)
    }
    val hintColor = when (style) {
        PaginationDotsStyle.Light -> EazColors.TextSecondary.copy(alpha = 0.75f)
        PaginationDotsStyle.Dark -> Color.White.copy(alpha = 0.6f)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBg)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .pointerInput(currentPage, totalPages) {
                var totalDrag = 0f
                val thresholdPx = with(density) { 60.dp.toPx() }
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        when {
                            totalDrag > thresholdPx -> onSwipePrev()
                            totalDrag < -thresholdPx -> onSwipeNext()
                        }
                    }
                )
            }
    ) {
        if (!swipeHint.isNullOrBlank()) {
            Text(
                text = swipeHint,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = hintColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            (1..totalPages).forEach { page ->
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (page == currentPage) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (page == currentPage) EazColors.Orange
                            else inactiveDot
                        )
                        .clickable { onPageClick(page) }
                )
            }
        }
    }
}
