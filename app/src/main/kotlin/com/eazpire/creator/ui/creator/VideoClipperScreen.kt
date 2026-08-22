package com.eazpire.creator.ui.creator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.modal.EazFullScreenDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class VideoClipperClip(
    val id: String,
    val start: Double,
    val end: Double,
    val title: String,
    val reason: String,
    val selected: Boolean = true,
)

data class VideoClipperExport(
    val id: String,
    val title: String,
    val uri: Uri,
    val durationS: Double,
)

/**
 * Video Clipper (IDEA-077) — transcribe + plan + trim export.
 * V1 Android: same plan API as web; 9:16 crop remains web-first (PARTIAL).
 */
@Composable
fun VideoClipperScreen(
    visible: Boolean,
    onDismiss: () -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }

    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var durationS by remember { mutableStateOf(0.0) }
    var autoCount by remember { mutableStateOf(true) }
    var autoDuration by remember { mutableStateOf(true) }
    var clipCount by remember { mutableStateOf("5") }
    var minS by remember { mutableStateOf("15") }
    var maxS by remember { mutableStateOf("45") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var clips by remember { mutableStateOf(listOf<VideoClipperClip>()) }
    var exported by remember { mutableStateOf(listOf<VideoClipperExport>()) }
    var sidebarCollapsed by remember { mutableStateOf(false) }
    var fullscreenUri by remember { mutableStateOf<Uri?>(null) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkValue by remember { mutableStateOf("") }
    var transcriptWords by remember { mutableStateOf(listOf<ClipperWord>()) }
    var captionStyle by remember { mutableStateOf(ClipperCaptionStyle()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        videoUri = uri
        clips = emptyList()
        exported = emptyList()
        transcriptWords = emptyList()
        durationS = if (uri != null) VideoClipperMedia.durationSeconds(context, uri) else 0.0
        status = if (uri == null) "" else t("creator.video_clipper.pick_hint", "Device upload or YouTube. 30–60 minutes with speech works best. Max 500 MB.")
    }

    fun loadYouTube(url: String) {
        if (ownerId.isBlank() || busy) return
        if (!isYouTubeUrl(url)) {
            status = t("creator.video_clipper.link_error_youtube_only", "Please paste a YouTube URL.")
            return
        }
        busy = true
        status = t("creator.video_clipper.link_downloading", "Starting YouTube download…")
        scope.launch {
            try {
                val ingest = withContext(Dispatchers.IO) { api.videoStudioLinkIngestMp4(ownerId, url.trim()) }
                var asset = ingest.optJSONObject("asset")
                val assetId = ingest.optString("asset_id", "")
                val ingestStatus = ingest.optString("status", "")
                val ingestError = ingest.optString("error", ingest.optString("error_code", ""))
                if (!ingest.optBoolean("ok") && assetId.isBlank()) {
                    if (ingestError == "youtube_bot" || ingestError == "youtube_failed") {
                        status = t("creator.video_clipper.link_processing", "Downloading from YouTube…")
                        val dest = File(context.cacheDir, "cvcl-youtube.mp4")
                        val ok = withContext(Dispatchers.IO) {
                            YouTubeOnDeviceResolver.downloadProgressiveMp4(url.trim(), dest)
                        }
                        if (!ok) {
                            throw IllegalStateException(ingestError.ifBlank { "youtube_failed" })
                        }
                        videoUri = Uri.fromFile(dest)
                        clips = emptyList()
                        exported = emptyList()
                        transcriptWords = emptyList()
                        durationS = VideoClipperMedia.durationSeconds(context, videoUri!!)
                        status = t("creator.video_clipper.link_ready", "YouTube video loaded.")
                        return@launch
                    }
                    throw IllegalStateException(ingestError.ifBlank { "youtube_failed" })
                }
                if (asset == null && assetId.isNotBlank() && ingestStatus != "ready") {
                    status = t("creator.video_clipper.link_queued", "Import queued — preparing YouTube video…")
                    var attempts = 0
                    while (asset == null && attempts < 120) {
                        delay(2000)
                        attempts++
                        if (attempts % 3 == 0) {
                            status = t("creator.video_clipper.link_processing", "Downloading from YouTube…")
                        }
                        val polled = withContext(Dispatchers.IO) {
                            api.videoStudioLinkIngestStatus(ownerId, assetId)
                        }
                        when (polled.optString("status")) {
                            "ready" -> asset = polled.optJSONObject("asset")
                            "failed" -> throw IllegalStateException(polled.optString("error", "youtube_failed"))
                        }
                    }
                }
                val remote = asset?.optString("url").orEmpty()
                if (remote.isBlank()) throw IllegalStateException("youtube_failed")
                status = t("creator.video_clipper.link_loading_player", "Loading video into Clipper…")
                val dest = File(context.cacheDir, "cvcl-youtube.mp4")
                val ok = withContext(Dispatchers.IO) { api.downloadUrlToFile(remote, dest) }
                if (!ok) throw IllegalStateException("download_failed")
                videoUri = Uri.fromFile(dest)
                clips = emptyList()
                exported = emptyList()
                transcriptWords = emptyList()
                durationS = VideoClipperMedia.durationSeconds(context, videoUri!!)
                status = t("creator.video_clipper.link_ready", "YouTube video loaded.")
            } catch (e: Exception) {
                val code = e.message.orEmpty()
                status = if (code == "youtube_bot") {
                    t(
                        "creator.video_clipper.link_error_youtube_bot",
                        "YouTube blocked the download from our servers. Save the video on your device and use Device instead.",
                    )
                } else {
                    t("creator.video_clipper.link_error_youtube_failed", "Could not load that YouTube video.") +
                        " " + code
                }
            }
            busy = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = EazColors.Orange,
        unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
        focusedLabelColor = Color.White.copy(alpha = 0.8f),
        unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
        cursorColor = EazColors.Orange,
    )

    fun analyze() {
        val uri = videoUri
        if (uri == null || ownerId.isBlank() || busy) return
        busy = true
        status = t("creator.video_clipper.extracting_audio", "Extracting speech… this can take a minute on long videos.")
        scope.launch {
            try {
                val cues = JSONArray()
                val words = JSONArray()
                val texts = mutableListOf<String>()
                val chunkSec = 25.0
                val total = durationS.coerceAtLeast(1.0)
                val chunks = kotlin.math.ceil(total / chunkSec).toInt().coerceAtLeast(1)
                withContext(Dispatchers.IO) {
                    for (i in 0 until chunks) {
                        val start = i * chunkSec
                        val end = kotlin.math.min(total, start + chunkSec)
                        val out = File(context.cacheDir, "cvcl-audio-$i.m4a")
                        val ok = VideoClipperMedia.extractAudioRange(
                            context,
                            uri,
                            (start * 1_000_000).toLong(),
                            (end * 1_000_000).toLong(),
                            out,
                        )
                        if (!ok || !out.exists() || out.length() <= 0L) continue
                        val audioBytes = out.readBytes()
                        var resp = api.videoClipperTranscribe(
                            ownerId = ownerId,
                            audioBytes = audioBytes,
                            filename = out.name,
                            offsetS = start,
                        )
                        if (!resp.optBoolean("ok")) {
                            resp = api.videoClipperTranscribe(
                                ownerId = ownerId,
                                audioBytes = audioBytes,
                                filename = out.name,
                                offsetS = start,
                            )
                        }
                        out.delete()
                        if (!resp.optBoolean("ok")) {
                            val code = resp.optString("error", "transcribe_failed")
                            throw IllegalStateException(
                                if (code == "chunk_too_long") {
                                    t(
                                        "creator.video_clipper.chunk_too_long",
                                        "That audio piece was too long. Refresh the page and try Analyze again.",
                                    )
                                } else {
                                    t(
                                        "creator.video_clipper.transcribe_failed",
                                        "Speech recognition failed. Try Analyze again.",
                                    )
                                },
                            )
                        }
                        if (resp.optString("text").isNotBlank()) texts += resp.optString("text")
                        mergeJsonArray(cues, resp.optJSONArray("cues"))
                        mergeJsonArray(words, resp.optJSONArray("words"))
                    }
                }
                status = t("creator.video_clipper.planning", "Building clip plan…")
                val payload = JSONObject()
                    .put("cues", cues)
                    .put("words", words)
                    .put("text", texts.joinToString(" "))
                    .put("duration_s", durationS)
                    .put("clip_count", if (autoCount) "auto" else clipCount.toIntOrNull() ?: 5)
                    .put("min_s", if (autoDuration) "auto" else minS.toIntOrNull() ?: 15)
                    .put("max_s", if (autoDuration) "auto" else maxS.toIntOrNull() ?: 45)
                val plan = withContext(Dispatchers.IO) { api.videoClipperPlan(ownerId, payload) }
                if (!plan.optBoolean("ok")) {
                    throw IllegalStateException(plan.optString("error", "plan_failed"))
                }
                val items = plan.optJSONArray("clips") ?: JSONArray()
                transcriptWords = (0 until words.length()).mapNotNull { idx ->
                    val o = words.optJSONObject(idx) ?: return@mapNotNull null
                    val text = o.optString("text", o.optString("word")).trim()
                    if (text.isBlank()) return@mapNotNull null
                    ClipperWord(
                        text = text,
                        start = o.optDouble("start"),
                        end = o.optDouble("end", o.optDouble("start") + 0.28),
                    )
                }
                clips = (0 until items.length()).map { idx ->
                    val o = items.getJSONObject(idx)
                    VideoClipperClip(
                        id = o.optString("id", "clip_${idx + 1}"),
                        start = o.optDouble("start"),
                        end = o.optDouble("end"),
                        title = o.optString("title", "Clip ${idx + 1}"),
                        reason = o.optString("reason"),
                    )
                }
                exported = emptyList()
                status = t(
                    "creator.video_clipper.plan_ready",
                    "Analysis ready — export to split the video into Shorts.",
                ) + " (${clips.size})"
            } catch (e: Exception) {
                status = t("creator.video_clipper.analyze_failed", "Analyze failed.") + " " + (e.message ?: "")
            }
            busy = false
        }
    }

    fun exportSelected() {
        val uri = videoUri
        if (uri == null || ownerId.isBlank() || busy) return
        val chosen = clips
        if (chosen.isEmpty()) {
            status = t("creator.video_clipper.need_selection", "Analyze the video first, then export.")
            return
        }
        busy = true
        scope.launch {
            val made = mutableListOf<VideoClipperExport>()
            try {
                chosen.forEachIndexed { idx, clip ->
                    status = t("creator.video_clipper.exporting", "Splitting Short…") + " ${idx + 1}/${chosen.size}"
                    val item = withContext(Dispatchers.IO) {
                        val out = File(context.cacheDir, "cvcl-out-${clip.id}.mp4")
                        val burned = captionStyle.enabled && VideoClipperCaptionBurn.exportClip(
                            context = context,
                            uri = uri,
                            startS = clip.start,
                            endS = clip.end,
                            outFile = out,
                            words = transcriptWords,
                            style = captionStyle,
                        )
                        val ok = burned || VideoClipperMedia.extractVideoRange(
                            context,
                            uri,
                            (clip.start * 1_000_000).toLong(),
                            (clip.end * 1_000_000).toLong(),
                            out,
                        )
                        if (!ok || !out.exists()) throw IllegalStateException("cut_failed")
                        runCatching {
                            api.videoClipperExport(
                                ownerId = ownerId,
                                videoBytes = out.readBytes(),
                                filename = "${clip.title.ifBlank { "short" }}.mp4",
                                title = clip.title.ifBlank { "Video Clipper short" },
                                durationS = (clip.end - clip.start).coerceAtLeast(0.1),
                            )
                        }
                        VideoClipperExport(
                            id = clip.id,
                            title = clip.title.ifBlank { "Short ${idx + 1}" },
                            uri = Uri.fromFile(out),
                            durationS = (clip.end - clip.start).coerceAtLeast(0.1),
                        )
                    }
                    made += item
                    exported = made.toList()
                }
                sidebarCollapsed = true
                status = t(
                    "creator.video_clipper.export_done",
                    "Shorts are ready on the right. Saved in Assets → Videos → Video Clipper.",
                )
            } catch (e: Exception) {
                if (made.isNotEmpty()) exported = made.toList()
                status = t("creator.video_clipper.export_failed", "Export failed.") + " " + (e.message ?: "")
            }
            busy = false
        }
    }

    EazFullScreenDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0B0714))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t("creator.video_clipper.title", "Video Clipper"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = t("creator.video_clipper.close", "Close"),
                        tint = Color.White,
                    )
                }
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box {
                    if (!sidebarCollapsed) {
                        Column(
                            modifier = Modifier
                                .width(280.dp)
                                .fillMaxHeight()
                                .background(Color.Black.copy(alpha = 0.22f))
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                t(
                                    "creator.video_clipper.lead",
                                    "Turn a long talking video into Shorts. We transcribe the speech, propose clip times, then cut 9:16 clips.",
                                ),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                if (videoUri == null) t("creator.video_clipper.pick_video", "Choose a long-form video")
                                else t("creator.video_clipper.pick_hint", "Device upload or YouTube. 30–60 minutes with speech works best. Max 500 MB."),
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .clickable(enabled = !busy) { showSourcePicker = true }
                                    .padding(16.dp),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = autoCount,
                                    onCheckedChange = { autoCount = it },
                                    colors = SwitchDefaults.colors(checkedTrackColor = EazColors.Orange),
                                )
                                Text(t("creator.video_clipper.auto_count", "Auto clip count"), color = Color.White)
                            }
                            if (!autoCount) {
                                OutlinedTextField(
                                    value = clipCount,
                                    onValueChange = { clipCount = it.filter { ch -> ch.isDigit() }.take(2) },
                                    label = { Text(t("creator.video_clipper.clip_count", "Clips")) },
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = autoDuration,
                                    onCheckedChange = { autoDuration = it },
                                    colors = SwitchDefaults.colors(checkedTrackColor = EazColors.Orange),
                                )
                                Text(t("creator.video_clipper.auto_duration", "Auto duration"), color = Color.White)
                            }
                            if (!autoDuration) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = minS,
                                        onValueChange = { minS = it.filter { ch -> ch.isDigit() }.take(2) },
                                        label = { Text(t("creator.video_clipper.min_s", "Min seconds")) },
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = maxS,
                                        onValueChange = { maxS = it.filter { ch -> ch.isDigit() }.take(2) },
                                        label = { Text(t("creator.video_clipper.max_s", "Max seconds")) },
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = captionStyle.enabled,
                                    onCheckedChange = { captionStyle = captionStyle.copy(enabled = it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = EazColors.Orange),
                                )
                                Text(t("creator.video_clipper.add_subtitle", "Add subtitle"), color = Color.White)
                            }
                            if (captionStyle.enabled) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = captionStyle.wordsPer.toString(),
                                        onValueChange = { value ->
                                            captionStyle = captionStyle.copy(
                                                wordsPer = value.filter { ch -> ch.isDigit() }.toIntOrNull()?.coerceIn(1, 16)
                                                    ?: captionStyle.wordsPer,
                                            )
                                        },
                                        label = { Text(t("creator.video_clipper.sub_words", "Words")) },
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = captionStyle.lines.toString(),
                                        onValueChange = { value ->
                                            captionStyle = captionStyle.copy(
                                                lines = value.filter { ch -> ch.isDigit() }.toIntOrNull()?.coerceIn(1, 4)
                                                    ?: captionStyle.lines,
                                            )
                                        },
                                        label = { Text(t("creator.video_clipper.sub_lines", "Lines")) },
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                Text(t("creator.video_clipper.sub_font", "Font"), color = Color.White.copy(alpha = 0.7f))
                                ClipperChipRow(
                                    options = listOf("Arial", "Georgia", "Impact", "Verdana", "Trebuchet MS", "Courier New"),
                                    selected = captionStyle.font,
                                    onSelect = { captionStyle = captionStyle.copy(font = it) },
                                )
                                Text(t("creator.video_clipper.sub_color", "Color"), color = Color.White.copy(alpha = 0.7f))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(0xFFFFFFFF, 0xFFFFFF00, 0xFFFF9800, 0xFF00E5FF, 0xFF000000).forEach { color ->
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(Color(color))
                                                .border(
                                                    2.dp,
                                                    if (captionStyle.color == color) EazColors.Orange else Color.White.copy(alpha = 0.28f),
                                                    CircleShape,
                                                )
                                                .clickable { captionStyle = captionStyle.copy(color = color) },
                                        )
                                    }
                                }
                                Text(t("creator.video_clipper.sub_background", "Background"), color = Color.White.copy(alpha = 0.7f))
                                ClipperChipRow(
                                    options = listOf("transparent", "color"),
                                    labels = mapOf(
                                        "transparent" to t("creator.video_clipper.sub_bg_transparent", "Transparent"),
                                        "color" to t("creator.video_clipper.sub_bg_color", "Color"),
                                    ),
                                    selected = captionStyle.bgMode,
                                    onSelect = { captionStyle = captionStyle.copy(bgMode = it) },
                                )
                                if (captionStyle.bgMode == "color") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(0xFF000000, 0xCC000000, 0xFFFFFFFF, 0xFFFF9800).forEach { color ->
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(color))
                                                    .border(
                                                        2.dp,
                                                        if (captionStyle.bgColor == color) EazColors.Orange else Color.White.copy(alpha = 0.28f),
                                                        CircleShape,
                                                    )
                                                    .clickable { captionStyle = captionStyle.copy(bgColor = color) },
                                            )
                                        }
                                    }
                                }
                                Text(t("creator.video_clipper.sub_animation", "Animation"), color = Color.White.copy(alpha = 0.7f))
                                ClipperChipRow(
                                    options = listOf("fade", "slide_up", "pop", "typewriter", "none"),
                                    labels = mapOf(
                                        "fade" to t("creator.video_clipper.sub_anim_fade", "Fade"),
                                        "slide_up" to t("creator.video_clipper.sub_anim_slide", "Slide up"),
                                        "pop" to t("creator.video_clipper.sub_anim_pop", "Pop"),
                                        "typewriter" to t("creator.video_clipper.sub_anim_type", "Typewriter"),
                                        "none" to t("creator.video_clipper.sub_anim_none", "None"),
                                    ),
                                    selected = captionStyle.animation,
                                    onSelect = { captionStyle = captionStyle.copy(animation = it) },
                                )
                                Text(t("creator.video_clipper.sub_viewer", "Subtitle viewer"), color = Color.White.copy(alpha = 0.7f))
                                ClipperSubtitleViewer(
                                    videoUri = videoUri,
                                    words = transcriptWords,
                                    clips = clips,
                                    style = captionStyle,
                                    onStyleChange = { captionStyle = it },
                                )
                            }
                            if (status.isNotBlank()) {
                                Text(status, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                            }
                            Button(
                                onClick = { analyze() },
                                enabled = !busy && videoUri != null && ownerId.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(t("creator.video_clipper.analyze", "Analyze video"))
                            }
                            Button(
                                onClick = { exportSelected() },
                                enabled = !busy && clips.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.16f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(t("creator.video_clipper.export_shorts", "Export Shorts"))
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    } else {
                        Spacer(Modifier.width(28.dp).fillMaxHeight())
                    }
                    IconButton(
                        onClick = { sidebarCollapsed = !sidebarCollapsed },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(
                            imageVector = if (sidebarCollapsed) {
                                Icons.Default.KeyboardArrowRight
                            } else {
                                Icons.Default.KeyboardArrowLeft
                            },
                            contentDescription = t("creator.video_clipper.toggle_sidebar", "Toggle sidebar"),
                            tint = Color.White,
                        )
                    }
                }

                if (exported.isEmpty()) {
                    Text(
                        t(
                            "creator.video_clipper.results_empty",
                            "Analyze the video, then export to split it into Shorts.",
                        ),
                        color = Color.White.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(20.dp),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(exported, key = { it.id }) { item ->
                            ClipperResultCard(
                                item = item,
                                fullscreenLabel = t("creator.video_clipper.fullscreen", "Fullscreen"),
                                onFullscreen = { fullscreenUri = item.uri },
                            )
                        }
                    }
                }
            }
        }
    }

    fullscreenUri?.let { playUri ->
        Dialog(
            onDismissRequest = { fullscreenUri = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val frameH = minOf(maxHeight, maxWidth * 16f / 9f)
                val frameW = frameH * 9f / 16f
                ClipperPlayerView(
                    uri = playUri,
                    modifier = Modifier
                        .width(frameW)
                        .height(frameH),
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                )
                IconButton(
                    onClick = { fullscreenUri = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = t("creator.video_clipper.close", "Close"),
                        tint = Color.White,
                    )
                }
            }
        }
    }

    if (showSourcePicker) {
        AlertDialog(
            onDismissRequest = { showSourcePicker = false },
            containerColor = Color(0xFF1F2937),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.88f),
            title = { Text(t("creator.video_clipper.add_source_title", "Choose video source")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClipperSourceRow(
                        icon = Icons.Default.PhotoLibrary,
                        label = t("creator.video_clipper.add_source_device", "Device"),
                        hint = t("creator.video_clipper.add_source_device_hint", "Upload a video from this device"),
                    ) {
                        showSourcePicker = false
                        picker.launch("video/*")
                    }
                    ClipperSourceRow(
                        icon = Icons.Default.Link,
                        label = t("creator.video_clipper.add_source_link", "Link"),
                        hint = t("creator.video_clipper.add_source_link_hint", "Paste a YouTube URL"),
                    ) {
                        showSourcePicker = false
                        linkValue = ""
                        showLinkDialog = true
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourcePicker = false }) {
                    Text(t("creator.video_clipper.cancel", "Cancel"), color = Color.White.copy(alpha = 0.85f))
                }
            },
        )
    }

    if (showLinkDialog) {
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            containerColor = Color(0xFF1F2937),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.88f),
            title = { Text(t("creator.video_clipper.link_title", "Add from YouTube")) },
            text = {
                OutlinedTextField(
                    value = linkValue,
                    onValueChange = { linkValue = it },
                    singleLine = true,
                    label = { Text(t("creator.video_clipper.link_url_label", "YouTube URL")) },
                    placeholder = { Text(t("creator.video_clipper.link_url_placeholder", "https://www.youtube.com/watch?v=…")) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLinkDialog = false
                        loadYouTube(linkValue)
                    },
                    enabled = linkValue.isNotBlank(),
                ) {
                    Text(t("creator.video_clipper.link_load", "Load video"), color = EazColors.Orange)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLinkDialog = false }) {
                    Text(t("creator.video_clipper.cancel", "Cancel"), color = Color.White.copy(alpha = 0.85f))
                }
            },
        )
    }
}

@Composable
private fun ClipperChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    labels: Map<String, String> = emptyMap(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    val on = option == selected
                    Text(
                        text = labels[option] ?: option,
                        color = if (on) Color.Black else Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) Color.White else Color.White.copy(alpha = 0.12f))
                            .clickable { onSelect(option) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipperSubtitleViewer(
    videoUri: Uri?,
    words: List<ClipperWord>,
    clips: List<VideoClipperClip>,
    style: ClipperCaptionStyle,
    onStyleChange: (ClipperCaptionStyle) -> Unit,
) {
    val rangeStart = clips.firstOrNull()?.start ?: 0.0
    val rangeEnd = clips.firstOrNull()?.end ?: (rangeStart + 20.0)
    val blocks = remember(words, style.wordsPer, style.lines, rangeStart, rangeEnd) {
        VideoClipperCaptions.buildBlocks(words, style.wordsPer, style.lines, rangeStart, rangeEnd)
    }
    var nowS by remember { mutableStateOf(rangeStart) }
    val fontFamily = when (style.font) {
        "Georgia" -> FontFamily.Serif
        "Courier New" -> FontFamily.Monospace
        else -> FontFamily.SansSerif
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
    ) {
        val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        if (videoUri != null) {
            ClipperLoopPreview(
                uri = videoUri,
                startS = rangeStart,
                endS = rangeEnd,
                onTime = { nowS = it },
            )
        }
        val block = VideoClipperCaptions.atTime(blocks, nowS) ?: blocks.firstOrNull()
        val progress = VideoClipperCaptions.animProgress(block, nowS, style.animation)
        val shown = VideoClipperCaptions.visibleText(block, nowS, style.animation)
            .ifBlank { "Add subtitle" }
        var extraDy = 0f
        var extraScale = 1f
        var alpha = 1f
        when (style.animation) {
            "fade" -> alpha = progress
            "slide_up" -> {
                alpha = progress
                extraDy = (1f - progress) * boxH * 0.035f
            }
            "pop" -> {
                alpha = progress
                extraScale = 0.86f + 0.14f * progress
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        (boxW * style.x).roundToInt(),
                        (boxH * style.y + extraDy).roundToInt(),
                    )
                }
                .graphicsLayer {
                    translationX = -size.width / 2f
                    translationY = -size.height / 2f
                    rotationZ = style.rotation
                    scaleX = style.scale * extraScale
                    scaleY = style.scale * extraScale
                    this.alpha = alpha
                }
                .border(1.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                .pointerInput(style.x, style.y, boxW, boxH) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        onStyleChange(
                            style.copy(
                                x = (style.x + drag.x / boxW).coerceIn(0.08f, 0.92f),
                                y = (style.y + drag.y / boxH).coerceIn(0.08f, 0.92f),
                            ),
                        )
                    }
                }
                .padding(6.dp),
        ) {
            Text(
                text = shown,
                color = Color(style.color),
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier
                    .background(
                        if (style.bgMode == "color") Color(style.bgColor) else Color.Transparent,
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = if (style.bgMode == "color") 6.dp else 0.dp, vertical = 2.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 7.dp, y = 7.dp)
                    .size(14.dp)
                    .background(Color.White, RoundedCornerShape(3.dp))
                    .pointerInput(style.scale) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val delta = (drag.x + drag.y) / boxW
                            onStyleChange(style.copy(scale = (style.scale + delta * 2.4f).coerceIn(0.45f, 2.4f)))
                        }
                    },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 28.dp)
                    .size(22.dp)
                    .background(Color.White, CircleShape)
                    .pointerInput(style.rotation) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onStyleChange(style.copy(rotation = style.rotation + drag.x * 0.6f))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun ClipperLoopPreview(
    uri: Uri,
    startS: Double,
    endS: Double,
    onTime: (Double) -> Unit,
) {
    val context = LocalContext.current
    val exoPlayer = remember(uri, startS, endS) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            setMediaItem(
                MediaItem.Builder()
                    .setUri(uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs((startS * 1000).toLong().coerceAtLeast(0L))
                            .setEndPositionMs((endS * 1000).toLong().coerceAtLeast(1000L))
                            .build(),
                    )
                    .build(),
            )
            prepare()
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    LaunchedEffect(exoPlayer, startS) {
        while (true) {
            delay(80)
            onTime(startS + exoPlayer.currentPosition / 1000.0)
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            PlayerView(context).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                player = exoPlayer
            }
        },
        update = { it.player = exoPlayer },
    )
}

@Composable
private fun ClipperSourceRow(
    icon: ImageVector,
    label: String,
    hint: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111827))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = EazColors.Orange, modifier = Modifier.size(22.dp))
        Column {
            Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(hint, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ClipperResultCard(
    item: VideoClipperExport,
    fullscreenLabel: String,
    onFullscreen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        ) {
            ClipperPlayerView(
                uri = item.uri,
                modifier = Modifier.fillMaxSize(),
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
            )
            IconButton(
                onClick = onFullscreen,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    imageVector = Icons.Filled.Fullscreen,
                    contentDescription = fullscreenLabel,
                    tint = Color.White,
                )
            }
        }
        Text(
            item.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ClipperPlayerView(
    uri: Uri,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        modifier = modifier,
        factory = {
            PlayerView(context).apply {
                useController = true
                this.resizeMode = resizeMode
                player = exoPlayer
            }
        },
        update = {
            it.player = exoPlayer
            it.resizeMode = resizeMode
        },
    )
}

private fun isYouTubeUrl(raw: String): Boolean {
    return try {
        val host = (Uri.parse(raw.trim()).host ?: "").removePrefix("www.").lowercase()
        host == "youtu.be" || host == "youtube.com" || host == "m.youtube.com" || host == "music.youtube.com"
    } catch (_: Exception) {
        false
    }
}

private fun mergeJsonArray(target: JSONArray, extra: JSONArray?) {
    if (extra == null) return
    for (i in 0 until extra.length()) target.put(extra.get(i))
}
