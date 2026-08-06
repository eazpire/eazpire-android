@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eazpire.creator.ui.creator

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayOutputStream
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.modal.EazBottomSheet
import com.eazpire.creator.ui.modal.EazFullScreenDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Native Video Studio shell — projects, assets, a simplified multi-track timeline editor and
 * export. Parity shell for the marketing web "Video Studio" module.
 */

private const val MAX_TRACKS = 12
private const val PX_PER_SECOND = 60f
private val ASPECT_PRESETS = listOf("16:9", "9:16", "1:1", "4:5")

private data class VideoStudioProject(val id: String, val name: String, val aspectRatio: String)

private data class VideoStudioAsset(
    val id: String,
    val url: String?,
    val thumbnailUrl: String?,
    val type: String, // video | image | audio
    val durationMs: Long
)

private data class TimelineClip(
    val id: String,
    val assetId: String,
    val trackIndex: Int,
    val startMs: Long,
    val durationMs: Long,
    val label: String
)

@Composable
fun VideoStudioScreen(
    visible: Boolean,
    onDismiss: () -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
) {
    if (!visible) return

    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)

    val scope = rememberCoroutineScope()
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }

    var projects by remember { mutableStateOf<List<VideoStudioProject>>(emptyList()) }
    var projectsLoading by remember { mutableStateOf(true) }
    var currentProject by remember { mutableStateOf<VideoStudioProject?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf<String?>(null) }
    var showRenderInfo by remember { mutableStateOf(false) }

    fun loadProjects() {
        if (ownerId.isBlank()) {
            projectsLoading = false
            return
        }
        scope.launch {
            projectsLoading = true
            try {
                // Assumed: GET ?op=video-studio-projects-list&owner_id= -> { ok, items:[{id,name,aspect_ratio}] }
                val resp = withContext(Dispatchers.IO) { api.videoStudioProjectsList(ownerId) }
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("items") ?: JSONArray()
                    projects = (0 until arr.length()).mapNotNull { i ->
                        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                        VideoStudioProject(
                            id = obj.optString("id", ""),
                            name = obj.optString("name", "Untitled project"),
                            aspectRatio = obj.optString("aspect_ratio", "16:9")
                        )
                    }
                }
            } catch (_: Exception) {}
            projectsLoading = false
        }
    }

    LaunchedEffect(visible, ownerId) {
        if (visible && ownerId.isNotBlank()) loadProjects()
    }

    fun createProject(name: String, aspectRatio: String) {
        if (ownerId.isBlank()) return
        scope.launch {
            try {
                // Assumed: POST ?op=video-studio-project-create – Body: owner_id, name, aspect_ratio -> { ok, project:{id,name,aspect_ratio} }
                val resp = withContext(Dispatchers.IO) { api.videoStudioProjectCreate(ownerId, name, aspectRatio) }
                if (resp.optBoolean("ok", false)) {
                    val obj = resp.optJSONObject("project")
                    val project = VideoStudioProject(
                        id = obj?.optString("id", "") ?: "",
                        name = obj?.optString("name", name) ?: name,
                        aspectRatio = obj?.optString("aspect_ratio", aspectRatio) ?: aspectRatio
                    )
                    if (project.id.isNotBlank()) {
                        projects = projects + project
                        currentProject = project
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun deleteProject(project: VideoStudioProject) {
        if (ownerId.isBlank()) return
        scope.launch {
            try {
                // Assumed: DELETE/POST ?op=video-studio-project-delete – Body: owner_id, project_id
                withContext(Dispatchers.IO) { api.videoStudioProjectDelete(ownerId, project.id) }
                projects = projects.filter { it.id != project.id }
                if (currentProject?.id == project.id) currentProject = null
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
                IconButton(
                    onClick = { if (currentProject != null) currentProject = null else onDismiss() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        text = t("creator.video_studio.title", "Video Studio"),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    currentProject?.let {
                        Text(it.name, style = MaterialTheme.typography.labelSmall, color = EazColors.Orange)
                    }
                }
                if (currentProject != null) {
                    TextButton(
                        onClick = { showRenderInfo = true },
                        enabled = !exporting
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = t("creator.video_studio.render", "Render"),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            t("creator.video_studio.render", "Render"),
                            color = Color.White
                        )
                    }
                    Button(
                        onClick = {
                            val project = currentProject ?: return@Button
                            scope.launch {
                                exporting = true
                                exportStatus = null
                                try {
                                    // Assumed: POST ?op=video-studio-export – Body: owner_id, project_id -> { ok, job_id }
                                    val resp = withContext(Dispatchers.IO) { api.videoStudioExport(ownerId, project.id) }
                                    exportStatus = if (resp.optBoolean("ok", false)) {
                                        t("creator.video_studio.export_started", "Export started.")
                                    } else {
                                        resp.optString("error", "").ifBlank {
                                            t("creator.video_studio.export_failed", "Export failed.")
                                        }
                                    }
                                } catch (e: Exception) {
                                    exportStatus = e.message?.take(160)
                                } finally {
                                    exporting = false
                                }
                            }
                        },
                        enabled = !exporting,
                        colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange, contentColor = Color.White)
                    ) {
                        if (exporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(t("creator.video_studio.export", "Export"))
                    }
                }
            }
            if (showRenderInfo) {
                AlertDialog(
                    onDismissRequest = { showRenderInfo = false },
                    title = { Text(t("creator.video_studio.render", "Render")) },
                    text = {
                        Text(
                            t(
                                "creator.video_studio.render_android_pending",
                                "Fullscreen timeline render is available in the web Video Studio. Native compose preview is coming next."
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showRenderInfo = false }) {
                            Text(t("creator.common.close", "Close"))
                        }
                    }
                )
            }
            exportStatus?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            val project = currentProject
            if (project == null) {
                VideoStudioProjectListPanel(
                    projects = projects,
                    loading = projectsLoading,
                    translationStore = translationStore,
                    onSelect = { currentProject = it },
                    onCreate = { name, aspect -> createProject(name, aspect) },
                    onDelete = { deleteProject(it) },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                VideoStudioEditorPanel(
                    ownerId = ownerId,
                    project = project,
                    api = api,
                    translationStore = translationStore,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onAspectRatioChange = { newAspect ->
                        currentProject = project.copy(aspectRatio = newAspect)
                        scope.launch {
                            try {
                                // Assumed: POST ?op=video-studio-project-update – Body: owner_id, project_id, aspect_ratio
                                withContext(Dispatchers.IO) {
                                    api.videoStudioProjectUpdate(
                                        ownerId,
                                        project.id,
                                        JSONObject().put("aspect_ratio", newAspect)
                                    )
                                }
                            } catch (_: Exception) {}
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun VideoStudioProjectListPanel(
    projects: List<VideoStudioProject>,
    loading: Boolean,
    translationStore: TranslationStore,
    onSelect: (VideoStudioProject) -> Unit,
    onCreate: (name: String, aspectRatio: String) -> Unit,
    onDelete: (VideoStudioProject) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)
    var showCreateDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<VideoStudioProject?>(null) }

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = t("creator.video_studio.projects", "Projects"),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange, contentColor = Color.White)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("creator.video_studio.new_project", "New project"))
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EazColors.Orange)
            }
            projects.isEmpty() -> Text(
                text = t("creator.video_studio.no_projects_yet", "No projects yet — create one to get started."),
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(projects, key = { it.id }) { project ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x99111827))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .clickable { onSelect(project) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Text(
                                project.aspectRatio,
                                style = MaterialTheme.typography.labelSmall,
                                color = EazColors.Orange
                            )
                        }
                        IconButton(onClick = { confirmDelete = project }) {
                            Icon(Icons.Default.Delete, null, tint = Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        var aspect by remember { mutableStateOf(ASPECT_PRESETS.first()) }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color(0xFF1F2937),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.88f),
            title = { Text(t("creator.video_studio.new_project", "New project")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        placeholder = { Text(t("creator.video_studio.project_name", "Project name")) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = EazColors.Orange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ASPECT_PRESETS.forEach { preset ->
                            val active = aspect == preset
                            Text(
                                text = preset,
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
                                    .clickable { aspect = preset }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCreateDialog = false
                        onCreate(name.trim().ifBlank { "Untitled project" }, aspect)
                    }
                ) { Text(t("creator.common.confirm", "Confirm"), color = EazColors.Orange) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text(t("creator.common.cancel", "Cancel"), color = Color.White.copy(alpha = 0.85f))
                }
            }
        )
    }

    confirmDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = Color(0xFF1F2937),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.88f),
            title = { Text(t("creator.video_studio.delete_project_title", "Delete project?")) },
            text = { Text(project.name) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(project)
                    confirmDelete = null
                }) { Text(t("creator.common.confirm", "Confirm"), color = EazColors.Orange) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(t("creator.common.cancel", "Cancel"), color = Color.White.copy(alpha = 0.85f))
                }
            }
        )
    }
}

@Composable
private fun VideoStudioEditorPanel(
    ownerId: String,
    project: VideoStudioProject,
    api: CreatorApi,
    translationStore: TranslationStore,
    onAspectRatioChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var assets by remember(project.id) { mutableStateOf<List<VideoStudioAsset>>(emptyList()) }
    var assetsLoading by remember(project.id) { mutableStateOf(true) }
    var selectedAsset by remember(project.id) { mutableStateOf<VideoStudioAsset?>(null) }
    var clips by remember(project.id) { mutableStateOf<List<TimelineClip>>(emptyList()) }
    var undoStack by remember(project.id) { mutableStateOf<List<List<TimelineClip>>>(emptyList()) }
    var redoStack by remember(project.id) { mutableStateOf<List<List<TimelineClip>>>(emptyList()) }
    var playheadMs by remember(project.id) { mutableLongStateOf(0L) }
    var toolsClip by remember { mutableStateOf<TimelineClip?>(null) }
    var removeObjectClip by remember { mutableStateOf<TimelineClip?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun commitClips(next: List<TimelineClip>) {
        undoStack = undoStack + listOf(clips)
        redoStack = emptyList()
        clips = next
    }

    fun undo() {
        val prev = undoStack.lastOrNull() ?: return
        redoStack = listOf(clips) + redoStack
        undoStack = undoStack.dropLast(1)
        clips = prev
    }

    fun redo() {
        val next = redoStack.firstOrNull() ?: return
        undoStack = undoStack + listOf(clips)
        redoStack = redoStack.drop(1)
        clips = next
    }

    fun loadAssets() {
        if (ownerId.isBlank()) {
            assetsLoading = false
            return
        }
        scope.launch {
            assetsLoading = true
            try {
                // Assumed: GET ?op=video-studio-assets-list&owner_id=&project_id= -> { ok, items:[{id,url,thumbnail_url,type,duration_ms}] }
                val resp = withContext(Dispatchers.IO) { api.videoStudioAssetsList(ownerId, project.id) }
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("items") ?: JSONArray()
                    assets = (0 until arr.length()).mapNotNull { i ->
                        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                        VideoStudioAsset(
                            id = obj.optString("id", ""),
                            url = obj.optString("url", "").takeIf { it.isNotBlank() },
                            thumbnailUrl = obj.optString("thumbnail_url", "").takeIf { it.isNotBlank() },
                            type = obj.optString("type", "video"),
                            durationMs = obj.optLong("duration_ms", 4000L)
                        )
                    }
                }
            } catch (_: Exception) {}
            assetsLoading = false
        }
    }

    LaunchedEffect(project.id) { loadAssets() }

    val assetPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@launch
                val mime = context.contentResolver.getType(uri) ?: "video/mp4"
                val ext = if (mime.contains("image")) "jpg" else "mp4"
                // Assumed: POST multipart ?op=video-studio-asset-upload&owner_id=&project_id= -> { ok, asset:{id,url,thumbnail_url,type} }
                val resp = withContext(Dispatchers.IO) {
                    api.videoStudioAssetUpload(ownerId, project.id, bytes, "upload.$ext", mime)
                }
                if (resp.optBoolean("ok", false)) {
                    loadAssets()
                }
            } catch (_: Exception) {}
        }
    }

    fun addClipForAsset(asset: VideoStudioAsset) {
        val usedTracks = clips.map { it.trackIndex }.toSet()
        val trackIndex = (0 until MAX_TRACKS).firstOrNull { it !in usedTracks } ?: 0
        val startMs = clips.filter { it.trackIndex == trackIndex }.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
        val clip = TimelineClip(
            id = "clip_${System.currentTimeMillis()}_${clips.size}",
            assetId = asset.id,
            trackIndex = trackIndex,
            startMs = startMs,
            durationMs = asset.durationMs.coerceAtLeast(500L),
            label = asset.type
        )
        commitClips(clips + clip)
    }

    Row(modifier = modifier) {
        VideoStudioAssetSidebar(
            assets = assets,
            loading = assetsLoading,
            selectedAssetId = selectedAsset?.id,
            translationStore = translationStore,
            onUploadClick = { assetPicker.launch("*/*") },
            onSelect = { selectedAsset = it },
            onAddToTimeline = { addClipForAsset(it) },
            onDelete = { asset ->
                scope.launch {
                    try {
                        // Assumed: DELETE/POST ?op=video-studio-asset-delete – Body: owner_id, project_id, asset_id
                        withContext(Dispatchers.IO) { api.videoStudioAssetDelete(ownerId, project.id, asset.id) }
                        assets = assets.filter { it.id != asset.id }
                        if (selectedAsset?.id == asset.id) selectedAsset = null
                    } catch (_: Exception) {}
                }
            }
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ASPECT_PRESETS.forEach { preset ->
                    val active = project.aspectRatio == preset
                    Text(
                        text = preset,
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
                            .clickable { onAspectRatioChange(preset) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                VideoStudioPreviewPlayer(
                    url = selectedAsset?.url,
                    aspectRatio = project.aspectRatio,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) {
                    Icon(
                        Icons.Default.Undo,
                        null,
                        tint = if (undoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.3f)
                    )
                }
                IconButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) {
                    Icon(
                        Icons.Default.Redo,
                        null,
                        tint = if (redoStack.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.3f)
                    )
                }
                Text(
                    text = t("creator.video_studio.timeline", "Timeline"),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${playheadMs / 1000}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = EazColors.Orange
                )
            }

            TimelineEditor(
                clips = clips,
                playheadMs = playheadMs,
                onPlayheadChange = { playheadMs = it },
                onClipsChange = { commitClips(it) },
                onClipLongPress = { toolsClip = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }

    val toolsTarget = toolsClip
    if (toolsTarget != null) {
        EazBottomSheet(onDismissRequest = { toolsClip = null }, containerColor = Color(0xFF1F2937)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    text = t("creator.video_studio.asset_tools", "Clip tools"),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                Spacer(Modifier.height(12.dp))
                VideoStudioToolRow(
                    icon = Icons.Default.ContentCut,
                    label = t("creator.video_studio.cut", "Cut at playhead"),
                    onClick = {
                        val clip = toolsTarget
                        val cutAt = playheadMs
                        if (cutAt > clip.startMs && cutAt < clip.startMs + clip.durationMs) {
                            val firstPart = clip.copy(durationMs = cutAt - clip.startMs)
                            val secondPart = clip.copy(
                                id = "clip_${System.currentTimeMillis()}_split",
                                startMs = cutAt,
                                durationMs = (clip.startMs + clip.durationMs) - cutAt
                            )
                            commitClips(clips.filter { it.id != clip.id } + firstPart + secondPart)
                        }
                        scope.launch {
                            try {
                                // Assumed: POST ?op=video-studio-asset-cut – Body: owner_id, project_id, asset_id, at_ms
                                withContext(Dispatchers.IO) {
                                    api.videoStudioAssetCut(ownerId, project.id, clip.assetId, playheadMs)
                                }
                            } catch (_: Exception) {}
                        }
                        toolsClip = null
                    }
                )
                VideoStudioToolRow(
                    icon = Icons.Default.AutoFixHigh,
                    label = t("creator.video_studio.tool_remove_object", "Remove Object"),
                    onClick = {
                        removeObjectClip = toolsTarget
                        toolsClip = null
                    }
                )
                VideoStudioToolRow(
                    icon = Icons.Default.MusicOff,
                    label = t("creator.video_studio.remove_audio", "Remove audio"),
                    onClick = {
                        val clip = toolsTarget
                        scope.launch {
                            try {
                                // Assumed: POST ?op=video-studio-asset-remove-audio – Body: owner_id, project_id, asset_id
                                withContext(Dispatchers.IO) {
                                    api.videoStudioAssetRemoveAudio(ownerId, project.id, clip.assetId)
                                }
                            } catch (_: Exception) {}
                        }
                        toolsClip = null
                    }
                )
                VideoStudioToolRow(
                    icon = Icons.Default.CopyAll,
                    label = t("creator.video_studio.duplicate", "Duplicate"),
                    onClick = {
                        val clip = toolsTarget
                        commitClips(
                            clips + clip.copy(
                                id = "clip_${System.currentTimeMillis()}_dup",
                                startMs = clip.startMs + clip.durationMs
                            )
                        )
                        scope.launch {
                            try {
                                // Assumed: POST ?op=video-studio-asset-duplicate – Body: owner_id, project_id, asset_id
                                withContext(Dispatchers.IO) {
                                    api.videoStudioAssetDuplicate(ownerId, project.id, clip.assetId)
                                }
                            } catch (_: Exception) {}
                        }
                        toolsClip = null
                    }
                )
                VideoStudioToolRow(
                    icon = Icons.Default.Delete,
                    label = t("creator.video_studio.remove_clip", "Remove from timeline"),
                    onClick = {
                        commitClips(clips.filter { it.id != toolsTarget.id })
                        toolsClip = null
                    }
                )
            }
        }
    }

    val removeTarget = removeObjectClip
    if (removeTarget != null) {
        VideoStudioRemoveObjectSheet(
            asset = assets.find { it.id == removeTarget.assetId },
            translationStore = translationStore,
            onDismiss = { removeObjectClip = null },
            onSubmit = { maskDataUrl, quality ->
                scope.launch {
                    try {
                        statusMessage = t(
                            "creator.video_studio.remove_object_processing",
                            "Removing with standard quality (ProPainter)…"
                        )
                        val bytes = withContext(Dispatchers.IO) {
                            api.videoStudioRemoveObject(
                                ownerId = ownerId,
                                assetId = removeTarget.assetId,
                                maskPngBase64 = maskDataUrl,
                                quality = quality,
                            )
                        }
                        withContext(Dispatchers.IO) {
                            api.videoStudioAssetUpload(
                                ownerId = ownerId,
                                projectId = project.id,
                                bytes = bytes,
                                filename = "cleaned-${removeTarget.assetId}.mp4",
                                mime = "video/mp4",
                            )
                        }
                        loadAssets()
                        statusMessage = t(
                            "creator.video_studio.remove_object_ready",
                            "Preview ready — play it, then save as a new asset."
                        )
                        removeObjectClip = null
                    } catch (e: Exception) {
                        statusMessage = e.message?.takeIf { it.isNotBlank() }
                            ?: t("creator.video_studio.remove_object_failed", "Could not remove the object. Try again or use Standard quality.")
                    }
                }
            },
        )
    }

    statusMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3500)
            if (statusMessage == msg) statusMessage = null
        }
    }
}

@Composable
private fun VideoStudioRemoveObjectSheet(
    asset: VideoStudioAsset?,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
    onSubmit: (maskPngBase64: String, quality: String) -> Unit,
) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)
    var quality by remember { mutableStateOf("standard") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    fun exportMaskDataUrl(): String? {
        val w = canvasSize.width.roundToInt().coerceAtLeast(2)
        val h = canvasSize.height.roundToInt().coerceAtLeast(2)
        if (strokes.isEmpty() && currentStroke.isEmpty()) return null
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = AndroidCanvas(bmp)
        c.drawColor(android.graphics.Color.BLACK)
        val paint = AndroidPaint().apply {
            color = android.graphics.Color.WHITE
            style = AndroidPaint.Style.STROKE
            strokeWidth = (w / 28f).coerceIn(8f, 48f)
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
            isAntiAlias = true
        }
        val fillPaint = AndroidPaint().apply {
            color = android.graphics.Color.WHITE
            style = AndroidPaint.Style.FILL
            isAntiAlias = true
        }
        fun drawStroke(pts: List<Offset>) {
            if (pts.isEmpty()) return
            if (pts.size == 1) {
                c.drawCircle(pts[0].x, pts[0].y, paint.strokeWidth / 2f, fillPaint)
                return
            }
            for (i in 1 until pts.size) {
                c.drawLine(pts[i - 1].x, pts[i - 1].y, pts[i].x, pts[i].y, paint)
            }
        }
        strokes.forEach(::drawStroke)
        drawStroke(currentStroke)
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return "data:image/png;base64,$b64"
    }

    EazFlowBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = t("creator.video_studio.tool_remove_object", "Remove Object"),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Text(
                text = t(
                    "creator.video_studio.remove_object_hint",
                    "Pause the video and paint over logos, text, or watermarks. That area is used for the full video: we detect logo/text/watermark inside it and clean every frame (like generative fill for video). Standard works on any length; High quality is limited to 5 seconds."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = quality == "standard",
                    onClick = { quality = "standard" },
                    colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange),
                )
                Text(
                    t("creator.video_studio.remove_object_quality_standard", "Standard (full video)"),
                    color = Color.White,
                    modifier = Modifier.clickable { quality = "standard" },
                )
                Spacer(Modifier.width(12.dp))
                RadioButton(
                    selected = quality == "high",
                    onClick = { quality = "high" },
                    colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange),
                )
                Text(
                    t("creator.video_studio.remove_object_quality_high", "High quality (≤5s)"),
                    color = Color.White,
                    modifier = Modifier.clickable { quality = "high" },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B0B10))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            ) {
                if (!asset?.url.isNullOrBlank()) {
                    AsyncImage(
                        model = asset?.thumbnailUrl ?: asset?.url,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset -> currentStroke = listOf(offset) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke + change.position
                                },
                                onDragEnd = {
                                    if (currentStroke.isNotEmpty()) strokes.add(currentStroke)
                                    currentStroke = emptyList()
                                },
                                onDragCancel = { currentStroke = emptyList() },
                            )
                        },
                ) {
                    canvasSize = size
                    val strokeWidth = (size.minDimension / 28f).coerceIn(8f, 48f)
                    fun drawPts(pts: List<Offset>) {
                        if (pts.isEmpty()) return
                        if (pts.size == 1) {
                            drawCircle(EazColors.Orange.copy(alpha = 0.85f), strokeWidth / 2f, pts[0])
                            return
                        }
                        val path = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                        }
                        drawPath(
                            path,
                            color = EazColors.Orange.copy(alpha = 0.85f),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )
                    }
                    strokes.forEach(::drawPts)
                    drawPts(currentStroke)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    strokes.clear()
                    currentStroke = emptyList()
                    error = null
                }) {
                    Text(t("creator.video_studio.remove_object_clear", "Clear mask"), color = Color.White)
                }
                Button(
                    onClick = {
                        val mask = exportMaskDataUrl()
                        if (mask == null) {
                            error = t(
                                "creator.video_studio.remove_object_paint_first",
                                "Paint over the object or watermark to remove, then try again."
                            )
                            return@Button
                        }
                        busy = true
                        error = null
                        onSubmit(mask, quality)
                    },
                    enabled = !busy && asset != null,
                    colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Text(t("creator.video_studio.remove_object_preview", "Generate cleaned preview"))
                    }
                }
            }
            error?.let {
                Text(it, color = Color(0xFFF87171), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EazFlowBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    EazBottomSheet(onDismissRequest = onDismissRequest, containerColor = Color(0xFF1F2937), content = content)
}

@Composable
private fun VideoStudioToolRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = EazColors.Orange)
        Text(label, color = Color.White)
    }
}

@Composable
private fun VideoStudioAssetSidebar(
    assets: List<VideoStudioAsset>,
    loading: Boolean,
    selectedAssetId: String?,
    translationStore: TranslationStore,
    onUploadClick: () -> Unit,
    onSelect: (VideoStudioAsset) -> Unit,
    onAddToTimeline: (VideoStudioAsset) -> Unit,
    onDelete: (VideoStudioAsset) -> Unit,
) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)
    Column(
        modifier = Modifier
            .width(150.dp)
            .fillMaxHeight()
            .background(Color(0xFF0F0C1C).copy(alpha = 0.85f))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = t("creator.video_studio.assets", "Assets"),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
            IconButton(onClick = onUploadClick, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Upload, null, tint = EazColors.Orange, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.size(20.dp))
            }
            assets.isEmpty() -> Text(
                text = t("creator.video_studio.no_assets", "No assets."),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(assets, key = { it.id }) { asset ->
                    val selected = selectedAssetId == asset.id
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .border(
                                2.dp,
                                if (selected) EazColors.Orange else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(6.dp)
                            )
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { onSelect(asset) }
                    ) {
                        val thumb = asset.thumbnailUrl ?: asset.url
                        if (!thumb.isNullOrBlank()) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                        ) {
                            IconButton(
                                onClick = { onAddToTimeline(asset) },
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            ) {
                                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                        IconButton(
                            onClick = { onDelete(asset) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        ) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoStudioPreviewPlayer(
    url: String?,
    aspectRatio: String,
    modifier: Modifier = Modifier,
) {
    val ratio = when (aspectRatio) {
        "9:16" -> 9f / 16f
        "1:1" -> 1f
        "4:5" -> 4f / 5f
        else -> 16f / 9f
    }
    Box(
        modifier = modifier
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black)
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNullOrBlank()) {
            Text(
                text = "▶",
                color = Color.White.copy(alpha = 0.4f),
                style = MaterialTheme.typography.headlineMedium
            )
            return@Box
        }
        val context = LocalContext.current
        val exoPlayer = remember(url) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
            }
        }
        DisposableEffect(exoPlayer) {
            onDispose { exoPlayer.release() }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(context).apply {
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exoPlayer
                }
            },
            update = { it.player = exoPlayer }
        )
    }
}

/**
 * Simplified multi-track timeline: up to [MAX_TRACKS] horizontal lanes, clips as draggable bars,
 * a playhead line. Tap the ruler area to move the playhead; drag a clip horizontally to move it
 * (snapped to whole seconds); long-press a clip to open asset tools.
 */
@Composable
private fun TimelineEditor(
    clips: List<TimelineClip>,
    playheadMs: Long,
    onPlayheadChange: (Long) -> Unit,
    onClipsChange: (List<TimelineClip>) -> Unit,
    onClipLongPress: (TimelineClip) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackHeight = 40.dp
    val tracksToShow = ((clips.maxOfOrNull { it.trackIndex } ?: 0) + 2).coerceIn(3, MAX_TRACKS)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val pxPerMs = with(density) { (PX_PER_SECOND.dp.toPx()) / 1000f }

    Row(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
        ) {
            val timelineWidthMs = (clips.maxOfOrNull { it.startMs + it.durationMs } ?: 8000L) + 4000L
            val timelineWidthPx = (timelineWidthMs * pxPerMs)
            val timelineWidthDp = with(density) { timelineWidthPx.toDp() }

            Box(
                modifier = Modifier
                    .width(timelineWidthDp.coerceAtLeast(320.dp))
                    .fillMaxHeight()
                    .background(Color(0xFF111827))
                    .pointerInput(pxPerMs) {
                        detectTapGestures(onTap = { offset ->
                            val ms = (offset.x / pxPerMs).roundToInt().toLong().coerceAtLeast(0L)
                            onPlayheadChange(ms)
                        })
                    }
            ) {
                Column(Modifier.fillMaxSize()) {
                    repeat(tracksToShow) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(trackHeight)
                                .border(0.5.dp, Color.White.copy(alpha = 0.06f))
                        )
                    }
                }

                clips.forEach { clip ->
                    TimelineClipBar(
                        clip = clip,
                        pxPerMs = pxPerMs,
                        trackHeight = trackHeight,
                        allClips = clips,
                        onClipsChange = onClipsChange,
                        onLongPress = onClipLongPress
                    )
                }

                val playheadXDp = with(density) { (playheadMs * pxPerMs).toDp() }
                Canvas(
                    modifier = Modifier
                        .offset(x = playheadXDp)
                        .fillMaxHeight()
                        .width(2.dp)
                ) {
                    drawLine(
                        color = Color.White,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 2f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineClipBar(
    clip: TimelineClip,
    pxPerMs: Float,
    trackHeight: androidx.compose.ui.unit.Dp,
    allClips: List<TimelineClip>,
    onClipsChange: (List<TimelineClip>) -> Unit,
    onLongPress: (TimelineClip) -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var dragStartMs by remember(clip.id) { mutableLongStateOf(clip.startMs) }
    val xPx = clip.startMs * pxPerMs
    val widthPx = (clip.durationMs * pxPerMs).coerceAtLeast(with(density) { 24.dp.toPx() })
    val xDp = with(density) { xPx.toDp() }
    val widthDp = with(density) { widthPx.toDp() }
    val yDp = trackHeight * clip.trackIndex

    Box(
        modifier = Modifier
            .offset(x = xDp, y = yDp)
            .width(widthDp)
            .height(trackHeight - 4.dp)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(EazColors.Orange.copy(alpha = 0.75f))
            .border(1.dp, EazColors.Orange, RoundedCornerShape(6.dp))
            .pointerInput(clip.id, pxPerMs) {
                detectDragGestures(
                    onDragStart = { dragStartMs = clip.startMs },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val deltaMs = (dragAmount.x / pxPerMs).toLong()
                        dragStartMs = (dragStartMs + deltaMs).coerceAtLeast(0L)
                        onClipsChange(
                            allClips.map { if (it.id == clip.id) it.copy(startMs = dragStartMs) else it }
                        )
                    }
                )
            }
            .pointerInput(clip.id) {
                detectTapGestures(onLongPress = { onLongPress(clip) })
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = clip.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1
        )
    }
}
