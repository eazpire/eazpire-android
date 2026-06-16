package com.eazpire.creator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.ui.home.HOME_LAZY_BATCH

/** Max products auto-loaded while scrolling (matches web `MAX_AUTO`). */
const val PRODUCT_LIST_MAX_AUTO = 200

/** Batch size for lazy product list pagination. */
const val PRODUCT_LIST_BATCH = HOME_LAZY_BATCH

/** How many items before list end to start prefetching the next batch. */
const val PRODUCT_LIST_PREFETCH_THRESHOLD = 12

@Composable
fun rememberLazyListNearEnd(
    listState: LazyListState,
    threshold: Int = PRODUCT_LIST_PREFETCH_THRESHOLD,
): Boolean {
    val nearEnd by remember(listState, threshold) {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - threshold
        }
    }
    return nearEnd
}

@Composable
fun rememberProductListNearEnd(
    gridState: LazyGridState,
    threshold: Int = PRODUCT_LIST_PREFETCH_THRESHOLD,
): Boolean {
    val nearEnd by remember(gridState, threshold) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - threshold
        }
    }
    return nearEnd
}

@Composable
fun ProductListInfiniteFooter(
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!loading) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = EazColors.Orange)
    }
}

@Composable
fun ProductListAutoLoadEffect(
    enabled: Boolean,
    nearEnd: Boolean,
    hasMore: Boolean,
    loading: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(enabled, nearEnd, hasMore, loading) {
        if (!enabled || !nearEnd || !hasMore || loading) return@LaunchedEffect
        onLoadMore()
    }
}
