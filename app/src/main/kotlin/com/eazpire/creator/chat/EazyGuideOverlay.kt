package com.eazpire.creator.chat

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

private val GuideOrange = Color(0xFFF97316)
private val GuideBg = Color(0xEB0F0A1E)

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
    val toolPrompt by EazyGuideModeStore.toolPrompt.collectAsState()
    val bubbleText by EazyGuideModeStore.bubbleText.collectAsState()
    val loading by EazyGuideModeStore.loading.collectAsState()
    val promptText by EazyGuideModeStore.promptText.collectAsState()
    val view = LocalView.current

    fun requestExplain() {
        scope.launch {
            val key = EazyGuideModeStore.elementContext?.guideKey
            val prompt = EazyGuideModeStore.promptText.value.trim()
            val screenshot = EazyGuideModeStore.screenshotContext
            if (key != null && screenshot == null && prompt.isEmpty()) {
                val local = EazyGuideRegistry.textFor(context, key)
                if (local != null) {
                    EazyGuideModeStore.setBubble(local)
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
            text = "Guide Mode — click Eazy to exit",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .background(GuideBg, RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        )

        bubbleText?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 320.dp)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = GuideOrange, strokeWidth = 2.dp)
                        Text(msg, color = Color(0xFF1E293B), fontSize = 13.sp)
                    }
                } else {
                    Text(msg, color = Color(0xFF1E293B), fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (toolPrompt) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth(0.92f)
                        .background(GuideBg, RoundedCornerShape(14.dp))
                        .border(1.dp, GuideOrange.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(GuideBg, RoundedCornerShape(999.dp))
                    .border(1.dp, GuideOrange.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                GuideChip("Click", toolClick) { EazyGuideModeStore.toggleTool("click") }
                GuideChip("Screenshot", toolScreenshot) { EazyGuideModeStore.toggleTool("screenshot") }
                GuideChip("Prompt", toolPrompt) { EazyGuideModeStore.toggleTool("prompt") }
                GuideChip("Explain", true) { requestExplain() }
            }
        }
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
