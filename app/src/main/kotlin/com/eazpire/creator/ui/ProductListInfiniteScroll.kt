package com.eazpire.creator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.ui.home.HOME_LAZY_BATCH
import com.eazpire.creator.ui.home.HOME_MAX_PRODUCTS

/** Max products auto-loaded while scrolling before showing Load more. */
const val PRODUCT_LIST_MAX_AUTO = HOME_MAX_PRODUCTS

/** Batch size for lazy product list pagination. */
const val PRODUCT_LIST_BATCH = HOME_LAZY_BATCH

@Composable
fun rememberLazyListNearEnd(listState: LazyListState, threshold: Int = 2): Boolean {
    val nearEnd by remember(listState) {
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
fun rememberProductListNearEnd(gridState: LazyGridState, threshold: Int = 4): Boolean {
    val nearEnd by remember(gridState) {
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
    visible: Boolean,
    loading: Boolean,
    showLoadMore: Boolean,
    loadMoreLabel: String,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible && !showLoadMore) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            showLoadMore -> {
                Button(
                    onClick = onLoadMore,
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EazColors.Orange,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(loadMoreLabel)
                }
            }
            loading && visible -> {
                CircularProgressIndicator(color = EazColors.Orange)
            }
        }
    }
}

@Composable
fun ProductListAutoLoadEffect(
    enabled: Boolean,
    nearEnd: Boolean,
    autoLoadPaused: Boolean,
    hasMore: Boolean,
    loading: Boolean,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(enabled, nearEnd, autoLoadPaused, hasMore, loading) {
        if (!enabled || !nearEnd || autoLoadPaused || !hasMore || loading) return@LaunchedEffect
        onLoadMore()
    }
}
