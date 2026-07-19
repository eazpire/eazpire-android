package com.eazpire.creator.ui.creator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.modal.EazFullScreenDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Motion Control video generation — native parity shell for IDEA-038 (Video Generator / Motion
 * Control). Upload a motion-reference video + a character image, pick orientation / sound
 * options, generate, then review results and save them to the library.
 *
 * NOTE: [CreatorApi.videoGenerateMotionControl], [CreatorApi.uploadVideoMotionRef],
 * [CreatorApi.videoGeneratorResults], [CreatorApi.videoSaveToLibrary] and
 * [CreatorApi.videoStudioLinkIngest] are expected to be added to `CreatorApi` — see method
 * assumptions documented at each call site below.
 */

private data class MotionResultItem(
    val id: String,
    val videoUrl: String?,
    val thumbnailUrl: String?,
    val status: String,
    val saved: Boolean
)

@Composable
fun VideoGeneratorScreen(
    visible: Boolean,
    onDismiss: () -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onVideoJobStarted: (jobId: String, summary: String) -> Unit = { _, _ -> },
) {
    if (!visible) return

    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }

    var motionVideoUri by remember { mutableStateOf<Uri?>(null) }
    var motionVideoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var motionVideoMime by remember { mutableStateOf("video/mp4") }
    var motionVideoLinkUrl by remember { mutableStateOf<String?>(null) }

    var characterImageUri by remember { mutableStateOf<Uri?>(null) }
    var characterImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var characterImageMime by remember { mutableStateOf("image/jpeg") }
    var characterImageLinkUrl by remember { mutableStateOf<String?>(null) }

    var characterOrientation by remember { mutableStateOf("portrait") } // portrait | landscape
    var keepOriginalSound by remember { mutableStateOf(true) }
    var prompt by remember { mutableStateOf("") }

    var generating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusError by remember { mutableStateOf(false) }

    var results by remember { mutableStateOf<List<MotionResultItem>>(emptyList()) }
    var loadingResults by remember { mutableStateOf(false) }

    var addSourceTarget by remember { mutableStateOf<String?>(null) } // "motion" | "character" | null

    val motionPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            motionVideoUri = it
            motionVideoLinkUrl = null
            motionVideoMime = context.contentResolver.getType(it) ?: "video/mp4"
            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        motionVideoBytes = stream.readBytes()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val characterPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            characterImageUri = it
            characterImageLinkUrl = null
            characterImageMime = context.contentResolver.getType(it) ?: "image/jpeg"
            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        characterImageBytes = stream.readBytes()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun loadResults() {
        if (ownerId.isBlank()) return
        scope.launch {
            loadingResults = true
            try {
                // Assumed: GET ?op=video-generator-results&owner_id= -> { ok, items:[{id,video_url,thumbnail_url,status,saved}] }
                val resp = withContext(Dispatchers.IO) { api.videoGeneratorResults(ownerId) }
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("items") ?: org.json.JSONArray()
                    results = (0 until arr.length()).mapNotNull { i ->
                        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                        MotionResultItem(
                            id = obj.optString("id", ""),
                            videoUrl = obj.optString("video_url", "").takeIf { it.isNotBlank() },
                            thumbnailUrl = obj.optString("thumbnail_url", "").takeIf { it.isNotBlank() },
                            status = obj.optString("status", "done"),
                            saved = obj.optBoolean("saved", false)
                        )
                    }
                }
            } catch (_: Exception) {}
            loadingResults = false
        }
    }

    LaunchedEffect(visible, ownerId) {
        if (visible && ownerId.isNotBlank()) loadResults()
    }

    fun runGenerate() {
        if (ownerId.isBlank() || generating) return
        val hasMotion = motionVideoBytes != null || !motionVideoLinkUrl.isNullOrBlank()
        val hasCharacter = characterImageBytes != null || !characterImageLinkUrl.isNullOrBlank()
        if (!hasMotion || !hasCharacter) {
            statusMessage = t(
                "creator.video_generator.select_sources_first",
                "Add a motion video and a character image first."
            )
            statusError = true
            return
        }
        scope.launch {
            generating = true
            statusMessage = null
            statusError = false
            try {
                var motionUrl = motionVideoLinkUrl
                motionVideoBytes?.let { bytes ->
                    // Assumed: POST multipart ?op=upload-video-motion-ref&owner_id= -> { ok, url }
                    val up = api.uploadVideoMotionRef(ownerId, bytes, "motion.mp4", motionVideoMime)
                    if (up.optBoolean("ok", false)) {
                        motionUrl = up.optString("url", "").takeIf { it.isNotBlank() } ?: motionUrl
                    }
                }
                var characterUrl = characterImageLinkUrl
                characterImageBytes?.let { bytes ->
                    val up = api.uploadHeroImage(ownerId, "video_character", bytes, characterImageMime)
                    if (up.optBoolean("ok", false)) {
                        characterUrl = up.optString("image_url", "").takeIf { it.isNotBlank() } ?: characterUrl
                    }
                }
                if (motionUrl.isNullOrBlank() || characterUrl.isNullOrBlank()) {
                    statusMessage = t("creator.video_generator.upload_failed", "Upload failed. Please try again.")
                    statusError = true
                    return@launch
                }
                // Assumed: POST ?op=video-generate-motion-control – Body: owner_id, motion_video_url,
                // source_image_url, prompt, character_orientation, keep_original_sound
                val resp = api.videoGenerateMotionControl(
                    ownerId = ownerId,
                    motionVideoUrl = motionUrl!!,
                    sourceImageUrl = characterUrl!!,
                    prompt = prompt.trim(),
                    characterOrientation = characterOrientation,
                    keepOriginalSound = keepOriginalSound
                )
                if (resp.optBoolean("ok", false) && resp.optString("job_id", "").isNotBlank()) {
                    val jobId = resp.optString("job_id", "")
                    onVideoJobStarted(
                        jobId,
                        t("creator.video_generator.job_summary_title", "Motion control video generation")
                    )
                    // Poll a few times for a quick UI refresh; job also surfaces via Eazy chat jobs tab.
                    scope.launch {
                        repeat(10) {
                            delay(3000)
                            try {
                                val poll = withContext(Dispatchers.IO) { api.pollJob(jobId) }
                                val status = poll.optString("status", "")
                                if (status == "done" || status == "failed" || status == "error") {
                                    loadResults()
                                    return@launch
                                }
                            } catch (_: Exception) {}
                        }
                        loadResults()
                    }
                    statusMessage = null
                    statusError = false
                } else {
                    statusMessage = resp.optString("error", "")
                        .ifBlank { t("creator.video_generator.generation_failed", "Generation failed.") }
                    statusError = true
                }
            } catch (e: Exception) {
                statusMessage = e.message?.take(200)
                    ?: t("creator.video_generator.network_error", "Network error.")
                statusError = true
            } finally {
                generating = false
            }
        }
    }

    fun saveResult(item: MotionResultItem) {
        if (ownerId.isBlank()) return
        scope.launch {
            try {
                // Assumed: POST ?op=video-save-to-library – Body: owner_id, result_id
                val resp = withContext(Dispatchers.IO) { api.videoSaveToLibrary(ownerId, item.id) }
                if (resp.optBoolean("ok", false)) {
                    results = results.map { if (it.id == item.id) it.copy(saved = true) else it }
                }
            } catch (_: Exception) {}
        }
    }

    EazFullScreenDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xFF0B1220))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = t("creator.video_generator.title", "Video Generator"),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(
                    text = t("creator.video_generator.subtitle", "Motion Control"),
                    style = MaterialTheme.typography.labelLarge,
                    color = EazColors.Orange
                )
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VideoGenSourceCard(
                        label = t("creator.video_generator.motion_video", "Motion video"),
                        icon = Icons.Default.Videocam,
                        imageUri = null,
                        isVideo = true,
                        hasSource = motionVideoUri != null || !motionVideoLinkUrl.isNullOrBlank(),
                        helperText = t(
                            "creator.video_generator.motion_video_helper",
                            "Reference clip that drives the motion"
                        ),
                        onPick = { addSourceTarget = "motion" },
                        onClear = {
                            motionVideoUri = null
                            motionVideoBytes = null
                            motionVideoLinkUrl = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                    VideoGenSourceCard(
                        label = t("creator.video_generator.character_image", "Character image"),
                        icon = Icons.Default.Image,
                        imageUri = characterImageUri,
                        isVideo = false,
                        hasSource = characterImageUri != null || !characterImageLinkUrl.isNullOrBlank(),
                        helperText = t(
                            "creator.video_generator.character_image_helper",
                            "Character or product to animate"
                        ),
                        onPick = { addSourceTarget = "character" },
                        onClear = {
                            characterImageUri = null
                            characterImageBytes = null
                            characterImageLinkUrl = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = t("creator.video_generator.orientation", "Orientation"),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "portrait" to t("creator.video_generator.orientation_portrait", "Portrait"),
                        "landscape" to t("creator.video_generator.orientation_landscape", "Landscape")
                    ).forEach { (value, label) ->
                        val active = characterOrientation == value
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) Color.White else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .border(
                                    1.dp,
                                    if (active) EazColors.Orange else Color.White.copy(alpha = 0.2f),
                                    RoundedCornerShape(999.dp)
                                )
                                .background(if (active) EazColors.Orange.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { characterOrientation = value }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = t("creator.video_generator.keep_original_sound", "Keep original sound"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Switch(
                        checked = keepOriginalSound,
                        onCheckedChange = { keepOriginalSound = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = EazColors.Orange)
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            t(
                                "creator.video_generator.prompt_placeholder",
                                "Describe the motion or scene you want..."
                            )
                        )
                    },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.18f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        cursorColor = EazColors.Orange,
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.42f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.42f),
                        focusedContainerColor = Color(0x99312937),
                        unfocusedContainerColor = Color(0x99312937)
                    )
                )

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { runGenerate() },
                    enabled = !generating && ownerId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange, contentColor = Color.White)
                ) {
                    if (generating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(t("creator.video_generator.generate", "Generate"))
                }

                statusMessage?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (statusError) EazColors.Orange else Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    text = t("creator.video_generator.results", "Results"),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(Modifier.height(10.dp))
                when {
                    loadingResults -> Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = EazColors.Orange) }
                    results.isEmpty() -> Text(
                        text = t("creator.video_generator.no_results_yet", "No results yet."),
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(results, key = { it.id }) { item ->
                            Column(
                                modifier = Modifier.width(140.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(9f / 16f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                                ) {
                                    val thumb = item.thumbnailUrl ?: item.videoUrl
                                    if (!thumb.isNullOrBlank()) {
                                        AsyncImage(
                                            model = thumb,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            "▶",
                                            color = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                TextButton(
                                    onClick = { saveResult(item) },
                                    enabled = !item.saved
                                ) {
                                    Text(
                                        if (item.saved) {
                                            t("creator.video_generator.saved", "Saved")
                                        } else {
                                            t("creator.video_generator.save", "Save")
                                        },
                                        color = if (item.saved) Color.White.copy(alpha = 0.5f) else EazColors.Orange,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    val currentAddSourceTarget = addSourceTarget
    if (currentAddSourceTarget != null) {
        val isMotion = currentAddSourceTarget == "motion"
        AddSourceSheet(
            title = if (isMotion) {
                t("creator.video_generator.add_motion_video", "Add motion video")
            } else {
                t("creator.video_generator.add_character_image", "Add character image")
            },
            translationStore = translationStore,
            isMotionTarget = isMotion,
            onDismiss = { addSourceTarget = null },
            onPickDeviceMotion = {
                addSourceTarget = null
                motionPicker.launch("video/*")
            },
            onPickDeviceCharacter = {
                addSourceTarget = null
                characterPicker.launch("image/*")
            },
            onLinkSubmit = { url ->
                addSourceTarget = null
                if (url.isNotBlank()) {
                    scope.launch {
                        try {
                            val kind = if (isMotion) "motion_video" else "character_image"
                            // Assumed: POST ?op=video-studio-link-ingest – Body: owner_id, url, kind -> { ok, url }
                            val resp = withContext(Dispatchers.IO) {
                                api.videoStudioLinkIngest(ownerId, url.trim(), kind)
                            }
                            val ingestedUrl = if (resp.optBoolean("ok", false)) {
                                resp.optString("url", "").takeIf { it.isNotBlank() } ?: url.trim()
                            } else {
                                url.trim()
                            }
                            if (isMotion) {
                                motionVideoUri = null
                                motionVideoBytes = null
                                motionVideoLinkUrl = ingestedUrl
                            } else {
                                characterImageUri = null
                                characterImageBytes = null
                                characterImageLinkUrl = ingestedUrl
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        )
    }
}

@Composable
private fun VideoGenSourceCard(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    imageUri: Uri?,
    isVideo: Boolean,
    hasSource: Boolean,
    helperText: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                2.dp,
                if (hasSource) EazColors.Orange.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                RoundedCornerShape(12.dp)
            )
            .background(if (hasSource) EazColors.Orange.copy(alpha = 0.1f) else Color(0x99111827))
            .clickable(onClick = onPick)
    ) {
        if (hasSource) {
            if (!isVideo && imageUri != null) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(icon, null, tint = EazColors.Orange, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/**
 * Simple "Add source" chooser — Device (file picker) or Link URL (ingested via
 * [CreatorApi.videoStudioLinkIngest] at the call site).
 */
@Composable
private fun AddSourceSheet(
    title: String,
    translationStore: TranslationStore,
    isMotionTarget: Boolean,
    onDismiss: () -> Unit,
    onPickDeviceMotion: () -> Unit,
    onPickDeviceCharacter: () -> Unit,
    onLinkSubmit: (String) -> Unit,
) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)
    var linkMode by remember { mutableStateOf(false) }
    var linkValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1F2937),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.88f),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!linkMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF111827))
                            .clickable { if (isMotionTarget) onPickDeviceMotion() else onPickDeviceCharacter() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null, tint = EazColors.Orange)
                        Text(t("creator.video_generator.source_device", "Choose from device"), color = Color.White)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF111827))
                            .clickable { linkMode = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Link, null, tint = EazColors.Orange)
                        Text(t("creator.video_generator.source_link", "Paste link URL"), color = Color.White)
                    }
                } else {
                    OutlinedTextField(
                        value = linkValue,
                        onValueChange = { linkValue = it },
                        singleLine = true,
                        placeholder = { Text("https://…") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = EazColors.Orange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            if (linkMode) {
                TextButton(onClick = { onLinkSubmit(linkValue) }, enabled = linkValue.isNotBlank()) {
                    Text(t("creator.common.confirm", "Confirm"), color = EazColors.Orange)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(t("creator.common.cancel", "Cancel"), color = Color.White.copy(alpha = 0.85f))
                }
            }
        },
        dismissButton = if (linkMode) {
            {
                TextButton(onClick = { linkMode = false }) {
                    Text(t("creator.common.back", "Back"), color = Color.White.copy(alpha = 0.85f))
                }
            }
        } else null
    )
}
