package com.eazpire.creator.admin.cursoragent

import android.app.Activity
import android.graphics.BitmapFactory
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val TAG = "AdminCursorAgent"

/** Comfortable margin from content edges (matches web ~18px FAB inset). */
private val FabEdgeMargin = 16.dp

/**
 * Shop [CollapsibleShopFooter] / [GlobalFooter] sits in Scaffold bottomBar.
 * Keep the FAB above that chrome (similar to web mascot clearance).
 */
private val FabFooterClearance = 40.dp

private val PanelBg = Color(0xF0121418)
private val PanelBorder = Color(0x66FFFFFF)
private val FabBg = Color(0xF01A1D24)
private val BubbleUser = Color(0xFF2A3340)
private val BubbleAssistant = Color(0xCC1E2430)
private val DrawerBg = Color(0xFF161A20)

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
                        // Back is handled inside the panel (drawer closes first).
                        dismissOnBackPress = false,
                        dismissOnClickOutside = false,
                        usePlatformDefaultWidth = false,
                        // Apply safeDrawing insets ourselves so content clears status + nav bars.
                        decorFitsSystemWindows = false,
                    ),
            ) {
                AdminCursorAgentPanel(
                    vm = vm,
                    activity = activity,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(PanelBg)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .imePadding(),
                )
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) {
            listState.animateScrollToItem(vm.messages.lastIndex)
        }
    }

    BackHandler {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            vm.closePanel()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = DrawerBg,
                modifier = Modifier.width(280.dp).fillMaxHeight(),
            ) {
                ChatDrawerContent(
                    chats = vm.chats,
                    selectedId = vm.chatId,
                    onNewChat = {
                        vm.newChat()
                        scope.launch { drawerState.close() }
                    },
                    onSelect = { id ->
                        vm.selectChat(id)
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Header: menu | title | Ask/Agent | model | close
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Chats", tint = Color.White)
                }
                Text(
                    "Admin Agent",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                ModeToggle(mode = vm.mode, onChange = { vm.mode = it })
                Spacer(modifier = Modifier.width(4.dp))
                ModelChip(models = vm.models, selected = vm.modelId, onSelect = { vm.modelId = it })
                IconButton(onClick = { vm.closePanel() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            if (vm.statusText.isNotBlank()) {
                Text(
                    vm.statusText,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
                )
            }

            // Transcript — full width
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        .padding(10.dp),
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
                        Spacer(modifier = Modifier.height(4.dp))
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

            if (vm.running) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { vm.cancelRun() }) {
                        Text("Cancel", color = Color(0xFFFF8A80), fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer composer: [camera] [thumb chip] [text] [send]
            ComposerBar(
                promptText = vm.promptText,
                onPromptChange = { vm.promptText = it },
                mode = vm.mode,
                pendingScreenshotPng = vm.pendingScreenshotPng,
                capturingScreenshot = vm.capturingScreenshot,
                busy = vm.sending || vm.running,
                onCaptureScreenshot = { vm.captureScreenshot(activity) },
                onClearScreenshot = { vm.clearPendingScreenshot() },
                onSend = { vm.send() },
            )
        }
    }
}

@Composable
private fun ChatDrawerContent(
    chats: List<AdminCursorChatSummary>,
    selectedId: String?,
    onNewChat: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Chats",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNewChat, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Add, contentDescription = "New chat", tint = EazColors.Orange)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(chats, key = { it.id }) { chat ->
                val selected = chat.id == selectedId
                Text(
                    text = chat.title.ifBlank { "Chat" }.take(48),
                    color = if (selected) EazColors.Orange else Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable { onSelect(chat.id) }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ComposerBar(
    promptText: String,
    onPromptChange: (String) -> Unit,
    mode: AdminCursorMode,
    pendingScreenshotPng: ByteArray?,
    capturingScreenshot: Boolean,
    busy: Boolean,
    onCaptureScreenshot: () -> Unit,
    onClearScreenshot: () -> Unit,
    onSend: () -> Unit,
) {
    val previewBitmap =
        remember(pendingScreenshotPng) {
            pendingScreenshotPng?.let { bytes ->
                runCatching {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }.getOrNull()
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, PanelBorder.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (previewBitmap != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
            ) {
                Box {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = "Screenshot preview",
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, EazColors.Orange.copy(alpha = 0.7f), RoundedCornerShape(8.dp)),
                    )
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.75f))
                                .clickable(enabled = !busy) { onClearScreenshot() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove screenshot",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Screenshot attached",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
        ) {
            IconButton(
                onClick = onCaptureScreenshot,
                enabled = !busy && !capturingScreenshot,
                modifier = Modifier.size(40.dp),
            ) {
                if (capturingScreenshot) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = EazColors.Orange,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = "Take screenshot",
                        tint =
                            if (previewBitmap != null) {
                                EazColors.Orange
                            } else {
                                Color.White.copy(alpha = 0.85f)
                            },
                    )
                }
            }
            BasicTextField(
                value = promptText,
                onValueChange = onPromptChange,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 4.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = SolidColor(EazColors.Orange),
                maxLines = 5,
                decorationBox = { inner ->
                    if (promptText.isEmpty()) {
                        Text(
                            if (mode == AdminCursorMode.ASK) "Ask (read-only)…" else "Agent prompt…",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 14.sp,
                        )
                    }
                    inner()
                },
            )
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp).padding(end = 8.dp),
                    color = EazColors.Orange,
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = onSend, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = EazColors.Orange)
                }
            }
        }
    }
}

@Composable
private fun ModeToggle(mode: AdminCursorMode, onChange: (AdminCursorMode) -> Unit) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, PanelBorder.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
    ) {
        AdminCursorMode.entries.forEach { m ->
            val selected = m == mode
            Text(
                text = m.label,
                color = if (selected) EazColors.Orange else Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (selected) EazColors.Orange.copy(alpha = 0.22f) else Color.Transparent,
                        )
                        .clickable { onChange(m) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ModelChip(models: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(selected.take(14), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
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
