package com.eazpire.creator.ui.modal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat

/**
 * Modal layout helpers.
 *
 * Never use [Modifier.fillMaxSize] / [Modifier.fillMaxHeight] on sheet/dialog ROOT content.
 * Use [EazModalSheetLayout] (header + weighted body + inset footer) instead.
 */
object EazModalInsets {
    /** Fullscreen sheet header — status bar + display cutout safe area (top only). */
    fun stickyHeader(): Modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()

    /** Sticky footer — navigation + keyboard safe area. */
    fun stickyFooter(): Modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .imePadding()
}

/**
 * Standard modal column: optional header, weighted scrollable body, optional inset-aware footer.
 */
@Composable
fun EazModalSheetLayout(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        header?.invoke()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            body()
        }
        footer?.invoke()
    }
}

/** Body slot inside [EazModalSheetLayout] — width only, height from parent weight. */
fun Modifier.eazModalBody(): Modifier = fillMaxWidth()

/**
 * Hides a bottom sheet before running [onComplete].
 * Avoids tearing down [Modifier.verticalScroll] children in the same frame (logout crash).
 */
@OptIn(ExperimentalMaterial3Api::class)
fun CoroutineScope.dismissBottomSheetThen(sheetState: SheetState, onComplete: () -> Unit) {
    launch {
        sheetState.hide()
    }.invokeOnCompletion {
        if (!sheetState.isVisible) onComplete()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberSheetContentMaxHeight(
    fullscreen: Boolean,
    maxHeightFraction: Float?,
    constraintsMaxHeight: Dp,
): Dp? {
    return when {
        fullscreen -> constraintsMaxHeight
        maxHeightFraction != null -> constraintsMaxHeight * maxHeightFraction
        else -> null
    }
}

/**
 * Material3 bottom sheet — outer height is inset-aware; inner content must use [EazModalSheetLayout].
 *
 * Fullscreen sheets apply [EazModalInsets.stickyHeader] on the content root (status bar / cutout).
 * Footers use [EazModalInsets.stickyFooter] (nav bar + IME). Bottom sheets render outside
 * [MainActivity]'s [systemBarsPadding], so insets must be applied here per overlay window.
 *
 * Do NOT put [Modifier.fillMaxSize] / [Modifier.fillMaxHeight] on sheet content roots.
 * [maxHeightFraction] is applied to inset-reduced height, not raw screen height.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EazBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    maxHeightFraction: Float? = null,
    fullscreen: Boolean = false,
    applyRootInsets: Boolean = true,
    content: @Composable () -> Unit,
) {
    val useExpandedLayout = fullscreen || maxHeightFraction != null
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.fillMaxWidth(),
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        dragHandle = dragHandle,
        windowInsets = WindowInsets(0),
    ) {
        val constraintsInsetModifier = when {
            applyRootInsets && fullscreen ->
                Modifier.statusBarsPadding().navigationBarsPadding()
            applyRootInsets && maxHeightFraction != null ->
                Modifier.navigationBarsPadding()
            else -> Modifier
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .then(constraintsInsetModifier),
        ) {
            val heightCap = rememberSheetContentMaxHeight(
                fullscreen = fullscreen,
                maxHeightFraction = maxHeightFraction,
                constraintsMaxHeight = maxHeight,
            )
            val columnModifier = Modifier
                .fillMaxWidth()
                .then(
                    when {
                        heightCap != null -> Modifier.height(heightCap)
                        else -> Modifier.wrapContentHeight()
                    },
                )
            if (useExpandedLayout) {
                Column(modifier = columnModifier.clipToBounds()) {
                    content()
                }
            } else {
                content()
            }
        }
    }
}

/** Alias — full-screen modal with system-bar safe bounds. */
@Composable
fun EazStandardDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    ),
    content: @Composable () -> Unit,
) = EazFullScreenDialog(onDismissRequest, properties, content)

/**
 * Generic inset-aware dialog shell (centered cards, pickers, nested overlays).
 * Prefer [EazFullScreenDialog] when content should fill the safe area.
 */
@Composable
fun EazInsetDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    ),
    applySystemBarInsets: Boolean = true,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
            ViewCompat.requestApplyInsets(dialogView)
        }
        val insetModifier = if (applySystemBarInsets) {
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        } else {
            Modifier.fillMaxSize()
        }
        Box(modifier = insetModifier) {
            content()
        }
    }
}

/** Full-screen dialog — content bounded by constraints; footers use [EazModalInsets.stickyFooter]. */
@Composable
fun EazFullScreenDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    ),
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
            ViewCompat.requestApplyInsets(dialogView)
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxHeight)
                    .clipToBounds(),
            ) {
                content()
            }
        }
    }
}

@Composable
fun EazModalStickyFooter(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.then(EazModalInsets.stickyFooter()),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

@Composable
fun EazModalFooterSurface(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.then(EazModalInsets.stickyFooter()),
        color = color,
        shadowElevation = shadowElevation,
        content = content,
    )
}
