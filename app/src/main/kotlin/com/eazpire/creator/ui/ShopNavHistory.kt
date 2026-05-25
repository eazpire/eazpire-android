package com.eazpire.creator.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Snapshot of shop navigation state for back/forward swipe history.
 */
data class ShopNavSnapshot(
    val selectedCollection: Triple<String, String, String?>? = null,
    val shopSearchQuery: String? = null,
    val shopCreateActive: Boolean = false,
    val shopCreateStudioOpen: Boolean = false,
    val selectedProductHandle: String? = null,
    val selectedCreatorName: String? = null,
    val productModalHandle: String? = null,
) {
    companion object {
        fun home() = ShopNavSnapshot()
    }
}

class ShopNavHistoryController(initial: ShopNavSnapshot = ShopNavSnapshot.home()) {
    var entries by mutableStateOf(listOf(initial))
        private set
    var index by mutableIntStateOf(0)
        private set
    var isRestoring by mutableStateOf(false)
        private set

    val canGoBack: Boolean get() = index > 0
    val canGoForward: Boolean get() = index < entries.lastIndex

    fun push(snapshot: ShopNavSnapshot) {
        if (isRestoring) return
        if (entries.getOrNull(index) == snapshot) return
        entries = entries.take(index + 1) + snapshot
        index = entries.lastIndex
    }

    fun goBack(): ShopNavSnapshot? {
        if (!canGoBack) return null
        isRestoring = true
        index--
        return entries[index]
    }

    fun goForward(): ShopNavSnapshot? {
        if (!canGoForward) return null
        isRestoring = true
        index++
        return entries[index]
    }

    fun finishRestore() {
        isRestoring = false
    }
}

@Composable
fun rememberShopNavHistoryController(): ShopNavHistoryController =
    remember { ShopNavHistoryController() }

/**
 * Swipe from the left screen edge → previous page; from the right edge → next page.
 * Edge-only so horizontal carousels and collection paging keep working in the content area.
 */
fun Modifier.shopNavEdgeSwipe(
    enabled: Boolean,
    onSwipeBack: () -> Unit,
    onSwipeForward: () -> Unit,
): Modifier {
    if (!enabled) return this
    return this.then(
        Modifier.pointerInput(Unit) {
            val edgePx = with(density) { 40.dp.toPx() }
            val thresholdPx = with(density) { 72.dp.toPx() }
            var startX = 0f
            var totalDrag = 0f
            detectHorizontalDragGestures(
                onDragStart = { offset ->
                    startX = offset.x
                    totalDrag = 0f
                },
                onHorizontalDrag = { _, amount -> totalDrag += amount },
                onDragEnd = {
                    val width = size.width.toFloat()
                    when {
                        startX <= edgePx && totalDrag >= thresholdPx -> onSwipeBack()
                        startX >= width - edgePx && totalDrag <= -thresholdPx -> onSwipeForward()
                    }
                },
            )
        },
    )
}
