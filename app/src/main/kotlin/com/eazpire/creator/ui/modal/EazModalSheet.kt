package com.eazpire.creator.ui.modal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
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
 * Unified modal inset helpers — use these instead of ad-hoc bottom padding / fillMaxHeight(0.95f).
 */
object EazModalInsets {
    /** Standard bottom-sheet content root (above nav bar + keyboard). */
    fun sheetRoot(fullHeight: Boolean = false, includeStatusBar: Boolean = false): Modifier = Modifier
        .fillMaxWidth()
        .then(if (fullHeight) Modifier.fillMaxHeight() else Modifier)
        .then(if (includeStatusBar) Modifier.statusBarsPadding() else Modifier)
        .navigationBarsPadding()
        .imePadding()

    /** Full-screen [EazFullScreenDialog] body (below status bar). */
    fun dialogRoot(): Modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()

    /** Sticky footer when the sheet root does NOT already apply bottom insets. */
    fun stickyFooter(): Modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars)
        .imePadding()
}

/**
 * Material3 bottom sheet with consistent safe-area handling.
 *
 * - Disables platform sheet insets ([WindowInsets(0)]) and applies nav/IME padding on content.
 * - Prefer [maxHeightFraction] or [fullscreen] over [Modifier.fillMaxHeight](0.95f).
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
            EazModalInsets.sheetRoot(
                fullHeight = expandContent,
                includeStatusBar = fullscreen,
            )
        } else {
            Modifier
                .fillMaxWidth()
                .then(if (expandContent) Modifier.fillMaxHeight() else Modifier)
        }
        Column(modifier = rootModifier) {
            content()
        }
    }
}

/** Full-screen dialog with edge-to-edge window + reliable inset dispatch. */
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
        content()
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
