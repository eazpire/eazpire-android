package com.eazpire.creator.chat

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

private val GuideOrange = Color(0xFFF97316)
private val GuideBg = Color(0xEB0F0A1E)
private val BubbleTop = Color(0xFA22183A)
private val BubbleBottom = Color(0xFA0F0B1E)
private val BubbleBorder = Color(0x73F97316)

@Composable
fun EazyGuideOverlay(
    creatorApi: CreatorApi,
    pagePath: String,
    locale: String = "en",
    modifier: Modifier = Modifier
) {
    val active by EazyGuideModeStore.active.collectAsState()
    if (!active) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toolClick by EazyGuideModeStore.toolClick.collectAsState()
    val toolScreenshot by EazyGuideModeStore.toolScreenshot.collectAsState()
    val bubblePages by EazyGuideModeStore.bubblePages.collectAsState()
    val loading by EazyGuideModeStore.loading.collectAsState()
    val promptText by EazyGuideModeStore.promptText.collectAsState()
    val view = LocalView.current

    fun requestExplain() {
        scope.launch {
            val key = EazyGuideModeStore.elementContext?.guideKey
            val prompt = EazyGuideModeStore.promptText.value.trim()
            val screenshot = EazyGuideModeStore.screenshotContext
            if (key != null && screenshot == null && prompt.isEmpty()) {
                val pages = EazyGuideRegistry.pagesFor(context, key)
                if (!pages.isNullOrEmpty()) {
                    EazyGuideModeStore.setBubblePages(pages)
                    return@launch
                }
            }
            EazyGuideModeStore.setBubble("Let me look at that…", loading = true)
            try {
                val elementJson = EazyGuideModeStore.elementContext?.let {
                    JSONObject()
                        .put("guide_key", it.guideKey ?: JSONObject.NULL)
                        .put("label", it.label ?: JSONObject.NULL)
                }
                val screenshotJson = screenshot?.let {
                    JSONObject()
                        .put("base64", it.base64)
                        .put("mime", it.mime)
                }
                val body = JSONObject()
                    .put("page", pagePath)
                    .put("locale", locale)
                    .put("prompt", prompt)
                    .put("chat_ui_only", EazyGuideModeStore.chatUiOnly)
                    .put("element", elementJson ?: JSONObject.NULL)
                    .put("screenshot", screenshotJson ?: JSONObject.NULL)
                val res = withContext(Dispatchers.IO) { creatorApi.guideExplain(body) }
                val text = res.optString("text")
                if (res.optBoolean("ok") && text.isNotBlank()) {
                    EazyGuideModeStore.setBubble(text)
                } else {
                    EazyGuideModeStore.setBubble(res.optString("message", "Sorry, I could not explain that right now."))
                }
            } catch (_: Exception) {
                EazyGuideModeStore.setBubble("Sorry, I could not explain that right now.")
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (toolClick) {
            EazyGuideLongPressLayer(
                onLongPress = { key, label ->
                    EazyGuideModeStore.setElementContext(EazyGuideElementContext(key, label))
                    requestExplain()
                }
            )
        }

        if (toolScreenshot) {
            EazyScreenshotSelector(
                onCaptured = { b64 ->
                    EazyGuideModeStore.setScreenshotContext(EazyGuideScreenshotContext(b64))
                    requestExplain()
                },
                captureBitmap = {
                    withContext(Dispatchers.Main) {
                        val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        view.draw(canvas)
                        bmp
                    }
                }
            )
        }

        Text(
            text = "Guide Mode — tap Eazy to exit",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .background(GuideBg, RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )

        if (bubblePages.isNotEmpty() || loading) {
            GuideSpeechBubble(
                pages = bubblePages,
                loading = loading,
                onClose = { EazyGuideModeStore.clearBubble() },
                onPageChange = { EazyGuideModeStore.setBubblePageIndex(it) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 52.dp, end = 52.dp, start = 16.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.92f)
                .background(GuideBg, RoundedCornerShape(16.dp))
                .border(1.dp, GuideOrange.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when {
                        EazyGuideModeStore.screenshotContext != null -> "Screenshot"
                        !EazyGuideModeStore.elementContext?.label.isNullOrBlank() ->
                            EazyGuideModeStore.elementContext?.label.orEmpty()
                        else -> "Select something, then ask…"
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                GuideChip("Click", toolClick) { EazyGuideModeStore.toggleTool("click") }
                GuideChip("Screenshot", toolScreenshot) { EazyGuideModeStore.toggleTool("screenshot") }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicTextField(
                    value = promptText,
                    onValueChange = { EazyGuideModeStore.setPrompt(it) },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (promptText.isEmpty()) {
                            Text("Ask about what you see…", color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp)
                        }
                        inner()
                    }
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GuideOrange)
                        .pointerInput(Unit) {
                            detectTapGestures { requestExplain() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("➤", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideSpeechBubble(
    pages: List<EazyGuidePage>,
    loading: Boolean,
    onClose: () -> Unit,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val safePages = if (pages.isEmpty() && loading) {
        listOf(EazyGuidePage("", "Let me look at that…"))
    } else {
        pages
    }
    val pagerState = rememberPagerState(pageCount = { safePages.size.coerceAtLeast(1) })
    val scope = rememberCoroutineScope()

    LaunchedEffect(safePages) {
        pagerState.scrollToPage(0)
        onPageChange(0)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { onPageChange(it) }
    }

    Column(modifier = modifier.widthIn(max = 320.dp)) {
        // Tail pointing up toward Eazy (top-right)
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp)
                    .size(16.dp, 10.dp)
            ) {
                val path = Path().apply {
                    moveTo(size.width / 2f, 0f)
                    lineTo(0f, size.height)
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(path, color = BubbleTop)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(BubbleTop, BubbleBottom)),
                    RoundedCornerShape(18.dp)
                )
                .border(1.dp, BubbleBorder, RoundedCornerShape(18.dp))
                .padding(top = 10.dp, start = 12.dp, end = 12.dp, bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Text("×", color = Color.White.copy(alpha = 0.9f), fontSize = 18.sp)
            }

            Column(modifier = Modifier.padding(end = 22.dp)) {
                if (loading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = GuideOrange,
                            strokeWidth = 2.dp
                        )
                        Text(
                            safePages.firstOrNull()?.body.orEmpty(),
                            color = Color.White.copy(alpha = 0.94f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp, max = 180.dp)
                    ) { page ->
                        val item = safePages.getOrNull(page) ?: return@HorizontalPager
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(end = 4.dp)
                        ) {
                            if (item.category.isNotBlank()) {
                                Text(
                                    item.category.uppercase(),
                                    color = Color(0xFFFB923C),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.4.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            Text(
                                item.body,
                                color = Color.White.copy(alpha = 0.94f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    if (safePages.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            GuidePagerNav("‹", enabled = pagerState.currentPage > 0) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                repeat(safePages.size) { i ->
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (i == pagerState.currentPage) GuideOrange
                                                else Color.White.copy(alpha = 0.28f)
                                            )
                                            .clickable {
                                                scope.launch { pagerState.animateScrollToPage(i) }
                                            }
                                    )
                                }
                            }
                            GuidePagerNav("›", enabled = pagerState.currentPage < safePages.lastIndex) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidePagerNav(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.08f else 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.35f), fontSize = 18.sp)
    }
}

@Composable
private fun GuideChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) GuideOrange.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (active) GuideOrange.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.15f), RoundedCornerShape(999.dp))
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun Modifier.eazyGuideTarget(guideKey: String, label: String? = null): Modifier {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    return this
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        .then(
            if (EazyGuideModeStore.active.value) Modifier else Modifier
        )
}

private val guideTargetKeys = mutableMapOf<Rect, Pair<String?, String?>>()

@Composable
private fun EazyGuideLongPressLayer(
    onLongPress: (guideKey: String?, label: String?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val hit = guideTargetKeys.entries.firstOrNull { it.key.contains(offset) }
                        onLongPress(hit?.value?.first, hit?.value?.second)
                    },
                    onLongPress = { offset ->
                        val hit = guideTargetKeys.entries.firstOrNull { it.key.contains(offset) }
                        onLongPress(hit?.value?.first, hit?.value?.second)
                    }
                )
            }
    )
}

fun registerEazyGuideTarget(bounds: Rect, guideKey: String?, label: String?) {
    guideTargetKeys[bounds] = guideKey to label
}

@Composable
fun EazyGuideTarget(
    guideKey: String,
    label: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            if (EazyGuideModeStore.active.value) {
                registerEazyGuideTarget(coords.boundsInRoot(), guideKey, label)
            }
        }
    ) {
        content()
    }
}

@Composable
private fun EazyScreenshotSelector(
    onCaptured: (String) -> Unit,
    captureBitmap: suspend () -> Bitmap
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragStart = it; dragEnd = it },
                    onDrag = { change, _ -> dragEnd = change.position },
                    onDragEnd = {
                        val s = dragStart
                        val e = dragEnd
                        dragStart = null
                        dragEnd = null
                        if (s == null || e == null) return@detectDragGestures
                        val w = kotlin.math.abs(e.x - s.x)
                        val h = kotlin.math.abs(e.y - s.y)
                        if (w < 24f || h < 24f) return@detectDragGestures
                        scope.launch {
                            try {
                                val full = captureBitmap()
                                val left = minOf(s.x, e.x).roundToInt().coerceAtLeast(0)
                                val top = minOf(s.y, e.y).roundToInt().coerceAtLeast(0)
                                val cw = w.roundToInt().coerceAtMost(full.width - left)
                                val ch = h.roundToInt().coerceAtMost(full.height - top)
                                if (cw <= 0 || ch <= 0) return@launch
                                val cropped = Bitmap.createBitmap(full, left, top, cw, ch)
                                onCaptured(bitmapToBase64(cropped))
                            } catch (_: Exception) {}
                        }
                    }
                )
            }
    ) {
        val s = dragStart
        val e = dragEnd
        if (s != null && e != null) {
            val left = minOf(s.x, e.x)
            val top = minOf(s.y, e.y)
            val w = kotlin.math.abs(e.x - s.x)
            val h = kotlin.math.abs(e.y - s.y)
            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { w.toDp() }, with(density) { h.toDp() })
                    .border(2.dp, GuideOrange, RoundedCornerShape(4.dp))
                    .background(GuideOrange.copy(alpha = 0.12f))
            )
        }
    }
}

private fun bitmapToBase64(bitmap: Bitmap): String {
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
