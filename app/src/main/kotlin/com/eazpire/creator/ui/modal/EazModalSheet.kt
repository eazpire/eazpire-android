package com.eazpire.creator.ui.modal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat

/**
 * Unified modal inset + layout helpers.
 *
 * Rule: never use [Modifier.fillMaxSize] inside sheet/dialog content — it reclaims the full
 * parent slot and ignores inset-aware sizing. Use [EazModalSheetLayout], [eazModalBody], or
 * [fillMaxWidth] + [ColumnScope.weight].
 */
object EazModalInsets {
    /** Scrollable / main body inside a bounded modal column. */
    fun body(): Modifier = Modifier
        .weight(1f, fill = false)
        .fillMaxWidth()

    /** Full-screen dialog column root (width + height, below status bar). */
    fun dialogRoot(): Modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .statusBarsPadding()

    /** Sticky footer — always apply on bottom action rows. */
    fun stickyFooter(): Modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .imePadding()
}

/**
 * Standard modal column: optional header, weighted body, optional inset-aware footer.
 * Use inside [EazBottomSheet] or [EazFullScreenDialog] instead of nested [fillMaxSize] trees.
 */
@Composable
fun EazModalSheetLayout(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        header?.invoke()
        Box(modifier = EazModalInsets.body()) {
            body()
        }
        footer?.invoke()
    }
}

/**
 * Material3 bottom sheet with consistent safe-area handling.
 *
 * - Disables platform sheet insets ([WindowInsets(0)]).
 * - Bounds expanded content so inner [fillMaxSize] cannot escape inset-aware height.
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
    val cappedHeight: Dp? = when {
        fullscreen -> null
        maxHeightFraction != null -> {
            val screenDp = LocalConfiguration.current.screenHeightDp
            (screenDp * maxHeightFraction).dp
        }
        else -> null
    }
    val sheetModifier = modifier.then(
        when {
            fullscreen -> Modifier.fillMaxHeight()
            cappedHeight != null -> Modifier.heightIn(max = cappedHeight)
            else -> Modifier.fillMaxWidth()
        }
    )
    val expandContent = fullscreen || maxHeightFraction != null
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = sheetModifier,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        dragHandle = dragHandle,
        windowInsets = WindowInsets(0),
    ) {
        val rootModifier = if (applyRootInsets) {
            Modifier
                .fillMaxWidth()
                .then(if (expandContent) Modifier.fillMaxHeight() else Modifier.wrapContentHeight())
                .navigationBarsPadding()
                .imePadding()
        } else {
            Modifier
                .fillMaxWidth()
                .then(if (expandContent) Modifier.fillMaxHeight() else Modifier.wrapContentHeight())
        }
        Column(modifier = rootModifier) {
            if (expandContent) {
                Box(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                    content()
                }
            } else {
                content()
            }
        }
    }
}

/** Full-screen dialog with edge-to-edge window + inset-aware root column. */
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(modifier = Modifier.weight(1f, fill = true).fillMaxWidth()) {
                content()
            }
        }
    }
}

/** Bottom action row that stays above gesture/3-button navigation and the keyboard. */
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

/** Surface wrapper for modal footers (shadow optional via elevation param on call site). */
@Composable
fun EazModalFooterSurface(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.then(EazModalInsets.stickyFooter()),
        color = color,
        content = content,
    )
}
