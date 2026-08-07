package com.eazpire.creator.admin.cursoragent

import android.app.Activity
import android.util.Log
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val TAG = "AdminCursorAgent"

/** Comfortable margin from content edges (matches web ~18px FAB inset). */
private val FabEdgeMargin = 16.dp

/**
 * Shop [CollapsibleShopFooter] / [GlobalFooter] sits in Scaffold bottomBar.
 * Keep the FAB above that chrome (similar to web mascot clearance).
 */
private val FabFooterClearance = 40.dp

private val PanelBg = Color(0xE6121418)
private val PanelBorder = Color(0x66FFFFFF)
private val FabBg = Color(0xF01A1D24)
private val BubbleUser = Color(0xFF2A3340)
private val BubbleAssistant = Color(0xCC1E2430)

/**
 * Admin-only Cursor Agent FAB + translucent panel.
 *
 * Uses Compose [Popup] / [Dialog] (lifecycle-safe windows) instead of a raw
 * WindowManager [ComposeView]. The previous TYPE_APPLICATION_SUB_PANEL path could
 * report attach success while composition never painted — and then disabled the
 * in-tree fallback, leaving admins with no icon.
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

    // Re-check when JWT appears after login (token store has no Flow).
    LaunchedEffect(tokenStore) {
        var lastJwt: String? = null
        while (true) {
            val jwt = tokenStore.getJwt()
            if (jwt != lastJwt) {
                lastJwt = jwt
                Log.i(TAG, "jwt changed present=${!jwt.isNullOrBlank()} — refresh admin gate")
                vm.refreshAdminGate()
            }
            delay(1_500)
        }
    }

    LaunchedEffect(vm.isAdmin, vm.gateChecked, vm.hideForScreenshot) {
        Log.i(
            TAG,
            "FAB visibility: isAdmin=${vm.isAdmin} gateChecked=${vm.gateChecked} " +
                "hideForScreenshot=${vm.hideForScreenshot}",
        )
    }

    if (!vm.isAdmin || vm.hideForScreenshot) {
        return
    }

    val density = LocalDensity.current
    // Host lives inside MainActivity's systemBarsPadding Surface. Measure that
    // content box — do NOT use screenWidth/HeightDp (those include system bars and
    // pushed the FAB into the nav/gesture area with clippingEnabled=false).
    BoxWithConstraints(modifier = Modifier.fillMaxSize().zIndex(100_000f)) {
        val containerW = constraints.maxWidth.toFloat().coerceAtLeast(0f)
        val containerH = constraints.maxHeight.toFloat().coerceAtLeast(0f)
        val fabSizePx = with(density) { 56.dp.toPx() }
        val marginPx = with(density) { FabEdgeMargin.toPx() }
        val footerClearancePx = with(density) { FabFooterClearance.toPx() }

        // Content-safe paddings inside the already systemBars-padded host:
        // margin on all sides + extra bottom clearance for Scaffold bottomBar footer.
        val padLeft = marginPx
        val padTop = marginPx
        val padRight = marginPx
        val padBottom = marginPx + footerClearancePx

        val effectivePos =
            vm.fabPos
                ?: AdminCursorFabGeometry.defaultBottomRightPct(
                    containerW,
                    containerH,
                    fabSizePx,
                    insetPx = marginPx,
                    paddingBottomExtraPx = footerClearancePx,
                )
        val (baseLeft, baseTop) =
            AdminCursorFabGeometry.offsetFromPct(
                effectivePos.xPct,
                effectivePos.yPct,
                containerW,
                containerH,
                fabSizePx,
                paddingLeft = padLeft,
                paddingTop = padTop,
                paddingRight = padRight,
                paddingBottom = padBottom,
            )

        var dragLeft by remember { mutableFloatStateOf(baseLeft) }
        var dragTop by remember { mutableFloatStateOf(baseTop) }
        LaunchedEffect(baseLeft, baseTop, vm.fabDragging) {
            if (!vm.fabDragging) {
                dragLeft = baseLeft
                dragTop = baseTop
            }
        }

        // Popup creates a small lifecycle-bound window (FAB-sized), above normal UI.
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(dragLeft.roundToInt(), dragTop.roundToInt()),
            properties =
                PopupProperties(
                    focusable = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    clippingEnabled = false,
                ),
        ) {
            AdminCursorFab(
                onTap = { vm.togglePanel() },
                onDoubleTap = { vm.resetFabPos() },
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
                            paddingLeft = padLeft,
                            paddingTop = padTop,
                            paddingRight = padRight,
                            paddingBottom = padBottom,
                        )
                    vm.onFabPosChanged(pos, persist = true)
                },
                onDragCancel = { vm.fabDragging = false },
                onDrag = { dx, dy ->
                    val clamped =
                        AdminCursorFabGeometry.clampOffset(
                            dragLeft + dx,
                            dragTop + dy,
                            containerW,
                            containerH,
                            fabSizePx,
                            paddingLeft = padLeft,
                            paddingTop = padTop,
                            paddingRight = padRight,
                            paddingBottom = padBottom,
                        )
                    dragLeft = clamped.first
                    dragTop = clamped.second
                },
            )
        }

        if (vm.panelOpen) {
            Dialog(
                onDismissRequest = { vm.closePanel() },
                properties =
                    DialogProperties(
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true,
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = true,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.28f))
                            .clickable { vm.closePanel() },
                ) {
                    AdminCursorAgentPanel(
                        vm = vm,
                        activity = activity,
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth(0.96f)
                                .fillMaxHeight(0.88f)
                                .clickable(enabled = false) { },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminCursorFab(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
) {
    LaunchedEffect(Unit) {
        Log.i(TAG, "FAB composable entered composition")
    }
    Box(
        modifier =
            Modifier
                .size(56.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(FabBg)
                .border(1.5.dp, EazColors.Orange.copy(alpha = 0.85f), CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleTap() },
                        onTap = { onTap() },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragCancel() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
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
                Spacer(modifier.width(6.dp))
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
                        Spacer(modifier.height(4.dp))
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

            Spacer(modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = vm.includeScreenshot,
                    onCheckedChange = { vm.includeScreenshot = it },
                )
                Text("Screenshot", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                Spacer(modifier.weight(1f))
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
