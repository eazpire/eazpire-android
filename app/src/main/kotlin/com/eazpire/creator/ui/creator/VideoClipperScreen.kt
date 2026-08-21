package com.eazpire.creator.ui.creator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    var showSourcePicker by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkValue by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        videoUri = uri
        clips = emptyList()
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
                if (!ingest.optBoolean("ok") && assetId.isBlank()) {
                    throw IllegalStateException(ingest.optString("error", "youtube_failed"))
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
                durationS = VideoClipperMedia.durationSeconds(context, videoUri!!)
                status = t("creator.video_clipper.link_ready", "YouTube video loaded.")
            } catch (e: Exception) {
                status = t("creator.video_clipper.link_error_youtube_failed", "Could not load that YouTube video.") +
                    " " + (e.message ?: "")
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
                val chunkSec = 8 * 60.0
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
                        val resp = api.videoClipperTranscribe(
                            ownerId = ownerId,
                            audioBytes = out.readBytes(),
                            filename = out.name,
                            offsetS = start,
                        )
                        out.delete()
                        if (!resp.optBoolean("ok")) {
                            throw IllegalStateException(resp.optString("error", "transcribe_failed"))
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
                status = t("creator.video_clipper.plan_ready", "Plan ready — edit times if you want, then export.")
            } catch (e: Exception) {
                status = t("creator.video_clipper.analyze_failed", "Analyze failed.") + " " + (e.message ?: "")
            }
            busy = false
        }
    }

    fun exportSelected() {
        val uri = videoUri
        if (uri == null || ownerId.isBlank() || busy) return
        val chosen = clips.filter { it.selected }
        if (chosen.isEmpty()) {
            status = t("creator.video_clipper.need_selection", "Select at least one clip.")
            return
        }
        busy = true
        scope.launch {
            try {
                chosen.forEachIndexed { idx, clip ->
                    status = t("creator.video_clipper.exporting", "Exporting Short…") + " ${idx + 1}/${chosen.size}"
                    withContext(Dispatchers.IO) {
                        val out = File(context.cacheDir, "cvcl-clip-${clip.id}.mp4")
                        val ok = VideoClipperMedia.extractVideoRange(
                            context,
                            uri,
                            (clip.start * 1_000_000).toLong(),
                            (clip.end * 1_000_000).toLong(),
                            out,
                        )
                        if (!ok || !out.exists()) throw IllegalStateException("cut_failed")
                        val resp = api.videoClipperExport(
                            ownerId = ownerId,
                            videoBytes = out.readBytes(),
                            filename = "${clip.title.ifBlank { "short" }}.mp4",
                            title = clip.title.ifBlank { "Video Clipper short" },
                            durationS = (clip.end - clip.start).coerceAtLeast(0.1),
                        )
                        out.delete()
                        if (!resp.optBoolean("ok")) {
                            throw IllegalStateException(resp.optString("error", "export_failed"))
                        }
                    }
                }
                status = t(
                    "creator.video_clipper.android_export_done",
                    "Clips saved. 9:16 crop is available in the web Video Clipper.",
                )
            } catch (e: Exception) {
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
            Column(
                modifier = Modifier
                    .weight(1f)
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
                clips.forEachIndexed { idx, clip ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = clip.selected,
                                onCheckedChange = { checked ->
                                    clips = clips.toMutableList().also {
                                        it[idx] = clip.copy(selected = checked)
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = EazColors.Orange),
                            )
                            OutlinedTextField(
                                value = clip.title,
                                onValueChange = { value ->
                                    clips = clips.toMutableList().also { it[idx] = clip.copy(title = value) }
                                },
                                colors = fieldColors,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = clip.start.toString(),
                                onValueChange = { value ->
                                    clips = clips.toMutableList().also {
                                        it[idx] = clip.copy(start = value.toDoubleOrNull() ?: clip.start)
                                    }
                                },
                                label = { Text(t("creator.video_clipper.start", "Start")) },
                                colors = fieldColors,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = clip.end.toString(),
                                onValueChange = { value ->
                                    clips = clips.toMutableList().also {
                                        it[idx] = clip.copy(end = value.toDoubleOrNull() ?: clip.end)
                                    }
                                },
                                label = { Text(t("creator.video_clipper.end", "End")) },
                                colors = fieldColors,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (clip.reason.isNotBlank()) {
                            Text(clip.reason, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (status.isNotBlank()) {
                    Text(status, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { analyze() },
                    enabled = !busy && videoUri != null && ownerId.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(t("creator.video_clipper.analyze", "Analyze video"))
                }
                Button(
                    onClick = { exportSelected() },
                    enabled = !busy && clips.any { it.selected },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(t("creator.video_clipper.export_selected", "Export selected Shorts"))
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
