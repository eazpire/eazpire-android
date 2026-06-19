package com.eazpire.creator.ui.modal

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import kotlin.math.roundToInt

enum class EazSideDrawerAlign {
    Start,
    End,
}

/**
 * Slide-in drawer overlay with system-bar safe panel (status + navigation insets).
 * Use [EazModalSheetLayout] inside [panel] for header / scroll body / sticky footer.
 */
@Composable
fun EazSideDrawer(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    align: EazSideDrawerAlign = EazSideDrawerAlign.End,
    widthFraction: Float = 0.85f,
    backdropColor: Color = Color.Black.copy(alpha = 0.3f),
    panelColor: Color = Color.White,
    dismissOnBackdropClick: Boolean = true,
    panel: @Composable ColumnScope.(dismissAnimated: () -> Unit) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = dismissOnBackdropClick,
        ),
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
            ViewCompat.requestApplyInsets(dialogView)
        }

        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            val density = LocalDensity.current
            val drawerWidthPx = with(density) { (maxWidth * widthFraction).toPx() }
            var isEntered by remember { mutableStateOf(false) }
            var isExiting by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) { isEntered = true }

            val hiddenOffset = when (align) {
                EazSideDrawerAlign.End -> drawerWidthPx
                EazSideDrawerAlign.Start -> -drawerWidthPx
            }
            val offsetXPx by animateFloatAsState(
                targetValue = when {
                    !isEntered -> hiddenOffset
                    isExiting -> hiddenOffset
                    else -> 0f
                },
                animationSpec = tween(300, easing = FastOutSlowInEasing),
                label = "eazSideDrawerSlide",
            )

            LaunchedEffect(isExiting, offsetXPx) {
                if (isExiting && kotlin.math.abs(offsetXPx - hiddenOffset) < 1f) {
                    onDismissRequest()
                }
            }

            fun dismissAnimated() {
                isExiting = true
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(backdropColor)
                        .clickable(enabled = dismissOnBackdropClick) { dismissAnimated() },
                )
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(widthFraction)
                        .align(
                            when (align) {
                                EazSideDrawerAlign.End -> Alignment.CenterEnd
                                EazSideDrawerAlign.Start -> Alignment.CenterStart
                            },
                        )
                        .offset { IntOffset(offsetXPx.roundToInt(), 0) }
                        .background(panelColor),
                ) {
                    panel(::dismissAnimated)
                }
            }
        }
    }
}
