package com.eazpire.creator.admin.cursoragent

import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazpire.creator.EazColors
import com.eazpire.creator.EazpireCreatorTheme
import com.eazpire.creator.auth.SecureTokenStore
import kotlin.math.roundToInt

private val PanelBg = Color(0xE6121418)
private val PanelBorder = Color(0x66FFFFFF)
private val FabBg = Color(0xF01A1D24)
private val BubbleUser = Color(0xFF2A3340)
private val BubbleAssistant = Color(0xCC1E2430)

/**
 * Full-screen host that only claims touches inside [hitRects] so the rest of the
 * TYPE_APPLICATION_SUB_PANEL window passes through to dialogs below.
 * (ComposeView is final — wrap it in a FrameLayout.)
 */
private class PassThroughOverlayHost(context: Context) : FrameLayout(context) {
    var hitRects: List<Rect> = emptyList()
    val composeView: ComposeView =
        ComposeView(context).also {
            addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val lx = ev.x.roundToInt()
        val ly = ev.y.roundToInt()
        val hit = hitRects.any { it.contains(lx, ly) }
        if (!hit) return false
        return super.dispatchTouchEvent(ev)
    }
}

/**
 * Admin-only Cursor Agent FAB + translucent panel.
 * Uses a sub-panel window so the icon sits above Compose Dialog modals.
 */
@Composable
fun AdminCursorAgentHost(
    activity: ComponentActivity,
    tokenStore: SecureTokenStore,
) {
    val vm: AdminCursorAgentViewModel =
        viewModel(
            viewModelStoreOwner = activity,
            factory = AdminCursorAgentViewModel.Factory(tokenStore),
        )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) vm.refreshAdminGate()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        vm.refreshAdminGate()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(activity, vm.isAdmin, vm.hideForScreenshot, vm.panelOpen) {
        if (!vm.isAdmin || vm.hideForScreenshot) {
            return@DisposableEffect onDispose { }
        }

        val host = PassThroughOverlayHost(activity)
        host.composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                EazpireCreatorTheme {
                    AdminCursorAgentOverlayContent(
                        activity = activity,
                        vm = vm,
                        onHitRects = { rects -> host.hitRects = rects },
                    )
                }
            }
        }

        var flags =
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!vm.panelOpen) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
                flags,
                PixelFormat.TRANSLUCENT,
            ).apply {
                token = activity.window.decorView.windowToken
                gravity = Gravity.TOP or Gravity.START
                title = "eazpire-admin-cursor-agent"
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }

        val wm = activity.windowManager
        try {
            wm.addView(host, params)
        } catch (_: Exception) {
            return@DisposableEffect onDispose { }
        }

        onDispose {
            try {
                wm.removeViewImmediate(host)
            } catch (_: Exception) {
                try {
                    wm.removeView(host)
                } catch (_: Exception) {
                    /* ignore */
                }
            }
        }
    }
}

@Composable
private fun AdminCursorAgentOverlayContent(
    activity: Activity,
    vm: AdminCursorAgentViewModel,
    onHitRects: (List<Rect>) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerW = with(density) { maxWidth.toPx() }
        val containerH = with(density) { maxHeight.toPx() }
        val fabSizePx = with(density) { 56.dp.toPx() }
        val insetPx = with(density) { 20.dp.toPx() }

        val effectivePos =
            vm.fabPos
                ?: AdminCursorFabGeometry.defaultBottomRightPct(containerW, containerH, fabSizePx, insetPx)
        val (leftPx, topPx) =
            AdminCursorFabGeometry.offsetFromPct(
                effectivePos.xPct,
                effectivePos.yPct,
                containerW,
                containerH,
                fabSizePx,
            )

        var dragLeft by remember(leftPx) { mutableStateOf(leftPx) }
        var dragTop by remember(topPx) { mutableStateOf(topPx) }
        LaunchedEffect(leftPx, topPx, vm.fabDragging) {
            if (!vm.fabDragging) {
                dragLeft = leftPx
                dragTop = topPx
            }
        }

        val fabRect =
            Rect(
                dragLeft.roundToInt(),
                dragTop.roundToInt(),
                (dragLeft + fabSizePx).roundToInt(),
                (dragTop + fabSizePx).roundToInt(),
            )

        if (vm.panelOpen) {
            val panelPad = with(density) { 8.dp.roundToPx() }
            val panelRect =
                Rect(
                    panelPad,
                    (containerH * 0.06f).roundToInt(),
                    (containerW - panelPad).roundToInt(),
                    (containerH * 0.94f).roundToInt(),
                )
            LaunchedEffect(fabRect, panelRect) {
                onHitRects(listOf(panelRect, fabRect))
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                        .clickable { vm.closePanel() },
            )
            AdminCursorAgentPanel(
                vm = vm,
                activity = activity,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.96f)
                        .fillMaxHeight(0.88f),
            )
        } else {
            LaunchedEffect(fabRect) {
                onHitRects(listOf(fabRect))
            }
        }

        Box(
            modifier =
                Modifier
                    .offset { IntOffset(dragLeft.roundToInt(), dragTop.roundToInt()) }
                    .size(56.dp)
                    .shadow(10.dp, CircleShape)
                    .clip(CircleShape)
                    .background(FabBg)
                    .border(1.5.dp, EazColors.Orange.copy(alpha = 0.85f), CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { vm.resetFabPos() },
                            onTap = { vm.togglePanel() },
                        )
                    }
                    .pointerInput(containerW, containerH, fabSizePx) {
                        detectDragGestures(
                            onDragStart = { vm.fabDragging = true },
                            onDragEnd = {
                                vm.fabDragging = false
                                val pos =
                                    AdminCursorFabGeometry.pctFromOffset(
                                        dragLeft,
                                        dragTop,
                                        containerW,
                                        containerH,
                                        fabSizePx,
                                    )
                                vm.onFabPosChanged(pos, persist = true)
                            },
                            onDragCancel = { vm.fabDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val maxX = (containerW - fabSizePx).coerceAtLeast(0f)
                                val maxY = (containerH - fabSizePx).coerceAtLeast(0f)
                                dragLeft = (dragLeft + dragAmount.x).coerceIn(0f, maxX)
                                dragTop = (dragTop + dragAmount.y).coerceIn(0f, maxY)
                                onHitRects(
                                    listOf(
                                        Rect(
                                            dragLeft.roundToInt(),
                                            dragTop.roundToInt(),
                                            (dragLeft + fabSizePx).roundToInt(),
                                            (dragTop + fabSizePx).roundToInt(),
                                        ),
                                    ),
                                )
                            },
                        )
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = "Admin Cursor Agent",
                tint = EazColors.Orange,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun AdminCursorAgentPanel(
    vm: AdminCursorAgentViewModel,
    activity: Activity,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) {
            listState.animateScrollToItem(vm.messages.lastIndex)
        }
    }

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(PanelBg)
                .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
                .clickable(enabled = false) { }
                .padding(10.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .padding(end = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Chats",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.newChat() }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "New chat", tint = EazColors.Orange, modifier = Modifier.size(18.dp))
                }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(vm.chats, key = { it.id }) { chat ->
                    val selected = chat.id == vm.chatId
                    Text(
                        text = chat.title.ifBlank { "Chat" }.take(40),
                        color = if (selected) EazColors.Orange else Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                                .clickable { vm.selectChat(chat.id) }
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Admin Agent",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                ModeChip(mode = vm.mode, onChange = { vm.mode = it })
                Spacer(Modifier.width(6.dp))
                ModelChip(models = vm.models, selected = vm.modelId, onSelect = { vm.modelId = it })
                IconButton(onClick = { vm.closePanel() }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            if (vm.statusText.isNotBlank()) {
                Text(
                    vm.statusText,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.25f))
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (vm.messages.isEmpty()) {
                    item {
                        Text(
                            if (vm.mode == AdminCursorMode.ASK) {
                                "Ask anything about the Android app — no code changes."
                            } else {
                                "Describe an Android change. Agent focuses on android/ (+ shared if needed) and deploys to main."
                            },
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                        )
                    }
                }
                items(vm.messages, key = { it.id }) { msg ->
                    val isUser = msg.role.equals("user", ignoreCase = true)
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isUser) BubbleUser else BubbleAssistant)
                                .padding(10.dp),
                    ) {
                        Text(
                            if (isUser) "You" else "Agent",
                            color = EazColors.Orange.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(msg.content, color = Color.White, fontSize = 13.sp)
                        if (msg.imageUrls.isNotEmpty()) {
                            Text(
                                "📎 ${msg.imageUrls.size} image(s)",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = vm.includeScreenshot,
                    onCheckedChange = { vm.includeScreenshot = it },
                )
                Text("Screenshot", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                if (vm.running) {
                    TextButton(onClick = { vm.cancelRun() }) {
                        Text("Cancel", color = Color(0xFFFF8A80), fontSize = 12.sp)
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                BasicTextField(
                    value = vm.promptText,
                    onValueChange = { vm.promptText = it },
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(EazColors.Orange),
                    decorationBox = { inner ->
                        if (vm.promptText.isEmpty()) {
                            Text(
                                if (vm.mode == AdminCursorMode.ASK) "Ask (read-only)…" else "Agent prompt…",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 14.sp,
                            )
                        }
                        inner()
                    },
                )
                if (vm.sending || vm.running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = EazColors.Orange,
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = { vm.send(activity) }) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = EazColors.Orange)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(mode: AdminCursorMode, onChange: (AdminCursorMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(mode.label, color = EazColors.Orange, fontSize = 12.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AdminCursorMode.entries.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m.label) },
                    onClick = {
                        onChange(m)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelChip(models: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(selected.take(18), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { m ->
                DropdownMenuItem(
                    text = { Text(m) },
                    onClick = {
                        onSelect(m)
                        expanded = false
                    },
                )
            }
        }
    }
}
