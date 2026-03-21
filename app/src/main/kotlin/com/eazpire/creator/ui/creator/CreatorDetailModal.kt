package com.eazpire.creator.ui.creator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val TAB_AVATAR = 0
private const val TAB_COVER = 1

/** Full-screen dialog: per-creator avatar + cover / hero (aligned with web creator-detail-modal). */
@Composable
fun CreatorDetailModal(
    creatorName: String,
    ownerId: String,
    api: CreatorApi,
    translationStore: TranslationStore,
    onDismiss: () -> Unit
) {
    val tr = { k: String, d: String -> translationStore.t(k, d) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mainTab by remember { mutableIntStateOf(TAB_AVATAR) }
    var displayMode by remember { mutableStateOf("cover") }
    var originalDisplayMode by remember { mutableStateOf("cover") }

    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var coverUrl by remember { mutableStateOf<String?>(null) }
    var avatarHasExisting by remember { mutableStateOf(false) }
    var coverHasExisting by remember { mutableStateOf(false) }
    var avatarPending by remember { mutableStateOf<JSONObject?>(null) }
    var coverPending by remember { mutableStateOf<JSONObject?>(null) }

    var heroItems by remember { mutableStateOf<List<HeroRow>>(emptyList()) }
    var heroLoading by remember { mutableStateOf(false) }

    var avatarPrompt by remember { mutableStateOf("") }
    var coverPrompt by remember { mutableStateOf("") }
    var showAvatarGen by remember { mutableStateOf(false) }
    var showCoverGen by remember { mutableStateOf(false) }
    var generatingAvatar by remember { mutableStateOf(false) }
    var generatingCover by remember { mutableStateOf(false) }
    var genProgress by remember { mutableStateOf(0f) }

    var statusMsg by remember { mutableStateOf<String?>(null) }
    var statusErr by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    var pickCategory by remember { mutableStateOf<String?>(null) }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val cat = pickCategory ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@launch
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val up = withContext(Dispatchers.IO) {
                    api.uploadCreatorImage(ownerId, creatorName, cat, bytes, mime)
                }
                if (up.optBoolean("ok", false)) {
                    val temp = up.optString("temp_url", "")
                    val r2 = up.optString("r2_key", "")
                    val pending = JSONObject()
                        .put("type", "upload")
                        .put("temp_url", temp)
                        .put("r2_key", r2)
                        .put("image_type", "custom")
                    if (cat == "avatar") {
                        avatarPending = pending
                        avatarUrl = temp
                    } else {
                        coverPending = pending
                        coverUrl = temp
                    }
                    statusMsg = tr("creator.detail_modal.upload_confirm", "Image uploaded. Tap Save to confirm.")
                    statusErr = false
                } else {
                    statusMsg = up.optString("error", tr("creator.common.error", "Error"))
                    statusErr = true
                }
            } catch (e: Exception) {
                statusMsg = e.message ?: tr("creator.common.error", "Error")
                statusErr = true
            }
        }
    }

    fun loadImages() {
        scope.launch {
            loading = true
            try {
                val av = withContext(Dispatchers.IO) { api.getCreatorImage(ownerId, creatorName, "avatar") }
                val imgA = av.optJSONObject("image")
                val uA = imgA?.optString("image_url")?.takeIf { it.isNotBlank() }
                avatarUrl = uA
                avatarHasExisting = uA != null
                avatarPending = null

                val cv = withContext(Dispatchers.IO) { api.getCreatorImage(ownerId, creatorName, "cover") }
                val imgC = cv.optJSONObject("image")
                val uC = imgC?.optString("image_url")?.takeIf { it.isNotBlank() }
                coverUrl = uC
                coverHasExisting = uC != null
                coverPending = null
                val dm = cv.optString("display_mode", "").ifBlank { "cover" }
                displayMode = dm
                originalDisplayMode = dm
            } catch (_: Exception) {
            }
            loading = false
        }
    }

    LaunchedEffect(creatorName, ownerId) {
        loadImages()
    }

    LaunchedEffect(ownerId, creatorName, mainTab, displayMode) {
        if (mainTab != TAB_COVER || displayMode != "hero") return@LaunchedEffect
        heroLoading = true
        try {
            val r = withContext(Dispatchers.IO) { api.heroList(ownerId, limit = 100, status = "active") }
            val arr = r.optJSONArray("items") ?: JSONArray()
            val list = mutableListOf<HeroRow>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pub = o.has("published_at") && !o.isNull("published_at")
                val status = o.optString("status", "")
                if (!pub && status != "published") continue
                val id = o.optString("id", "") ?: continue
                if (id.isBlank()) continue
                val thumb = o.optString("thumbnail_url", "").ifBlank { o.optString("image_url", "") }
                val title = o.optString("title", "").ifBlank { o.optString("gpt_prompt", "") }
                val enabled = o.optBoolean("creator_page_enabled", true)
                list.add(HeroRow(id, thumb, title, enabled))
            }
            heroItems = list
        } catch (_: Exception) {
            heroItems = emptyList()
        }
        heroLoading = false
    }

    fun heroEnabledCount() = heroItems.count { it.enabled }

    fun hasUnsaved(): Boolean {
        if (displayMode != originalDisplayMode) return true
        if (avatarPending != null) return true
        if (coverPending != null) return true
        return false
    }

    fun pollGeneration(category: String, predictionId: String) {
        scope.launch {
            var n = 0
            while (n < 90) {
                delay(2000)
                n++
                genProgress = (n / 90f).coerceAtMost(0.92f)
                val st = withContext(Dispatchers.IO) { api.getCreatorImageStatus(predictionId) }
                val status = st.optString("status", "")
                when (status) {
                    "succeeded" -> {
                        val out = st.opt("output")
                        val url = when (out) {
                            is JSONArray -> if (out.length() > 0) out.optString(0) else ""
                            is String -> out
                            else -> out?.toString()?.trim('"') ?: ""
                        }
                        if (url.isNotBlank()) {
                            val prompt = if (category == "avatar") avatarPrompt else coverPrompt
                            val pending = JSONObject()
                                .put("type", "generated")
                                .put("generated_url", url)
                                .put("prediction_id", predictionId)
                                .put("prompt", prompt)
                                .put("image_type", "generated")
                            if (category == "avatar") {
                                avatarPending = pending
                                avatarUrl = url
                            } else {
                                coverPending = pending
                                coverUrl = url
                            }
                            statusMsg = tr("creator.detail_modal.generate_confirm", "Generated. Tap Save to confirm.")
                            statusErr = false
                        }
                        generatingAvatar = false
                        generatingCover = false
                        genProgress = 0f
                        return@launch
                    }
                    "failed" -> {
                        statusMsg = st.optString("error", tr("creator.detail_modal.generate_failed", "Generation failed"))
                        statusErr = true
                        generatingAvatar = false
                        generatingCover = false
                        genProgress = 0f
                        return@launch
                    }
                }
            }
            statusMsg = tr("creator.detail_modal.timeout", "Timed out. Try again.")
            statusErr = true
            generatingAvatar = false
            generatingCover = false
            genProgress = 0f
        }
    }

    fun runGenerate(category: String) {
        val prompt = (if (category == "avatar") avatarPrompt else coverPrompt).trim()
        if (prompt.length < 3) {
            statusMsg = tr("creator.detail_modal.prompt_short", "Enter a longer description (min. 3 characters).")
            statusErr = true
            return
        }
        scope.launch {
            if (category == "avatar") generatingAvatar = true else generatingCover = true
            genProgress = 0.1f
            statusMsg = null
            val ref = if (category == "avatar") avatarUrl else coverUrl
            val (code, data) = withContext(Dispatchers.IO) {
                api.generateCreatorImageWithHttpCode(
                    ownerId, creatorName, category, prompt,
                    referenceImageUrl = ref?.takeIf { it.startsWith("http") }
                )
            }
            if (code == 402) {
                statusMsg = tr("creator.detail_modal.insufficient_eaz", "Not enough EAZ (5 required).")
                statusErr = true
                generatingAvatar = false
                generatingCover = false
                genProgress = 0f
                return@launch
            }
            if (data.optBoolean("ok", false) && data.has("prediction_id")) {
                pollGeneration(category, data.optString("prediction_id"))
            } else {
                if (data.optString("code", "") == "INSUFFICIENT_EAZ") {
                    statusMsg = tr("creator.detail_modal.insufficient_eaz", "Not enough EAZ (5 required).")
                } else {
                    statusMsg = data.optString("error", tr("creator.detail_modal.generate_failed", "Generation failed"))
                }
                statusErr = true
                generatingAvatar = false
                generatingCover = false
                genProgress = 0f
            }
        }
    }

    fun saveAll() {
        scope.launch {
            saving = true
            statusMsg = null
            try {
                if (displayMode == "hero" && displayMode != originalDisplayMode && heroEnabledCount() < 4) {
                    statusMsg = tr("creator.detail_modal.hero_required", "At least 4 hero images must be enabled.")
                    statusErr = true
                    saving = false
                    return@launch
                }
                if (displayMode != originalDisplayMode) {
                    val dr = withContext(Dispatchers.IO) {
                        api.saveCoverDisplayMode(ownerId, creatorName, displayMode)
                    }
                    if (!dr.optBoolean("ok", false)) {
                        statusMsg = dr.optString("error", tr("creator.common.error", "Error"))
                        statusErr = true
                        saving = false
                        return@launch
                    }
                    originalDisplayMode = displayMode
                }
                if (avatarPending != null) {
                    val r = withContext(Dispatchers.IO) {
                        if (avatarPending!!.optString("type", "") == "delete") {
                            api.deleteCreatorImage(ownerId, creatorName, "avatar")
                        } else {
                            api.saveCreatorImage(ownerId, creatorName, "avatar", avatarPending!!)
                        }
                    }
                    if (!r.optBoolean("ok", false)) {
                        statusMsg = r.optString("error", tr("creator.common.error", "Error"))
                        statusErr = true
                        saving = false
                        return@launch
                    }
                    val wasDelete = avatarPending!!.optString("type", "") == "delete"
                    avatarPending = null
                    if (wasDelete) {
                        avatarUrl = null
                        avatarHasExisting = false
                    } else {
                        r.optString("image_url", "").takeIf { it.isNotBlank() }?.let { avatarUrl = it }
                        avatarHasExisting = avatarUrl != null
                    }
                }
                if (coverPending != null) {
                    val r = withContext(Dispatchers.IO) {
                        if (coverPending!!.optString("type", "") == "delete") {
                            api.deleteCreatorImage(ownerId, creatorName, "cover")
                        } else {
                            api.saveCreatorImage(ownerId, creatorName, "cover", coverPending!!)
                        }
                    }
                    if (!r.optBoolean("ok", false)) {
                        statusMsg = r.optString("error", tr("creator.common.error", "Error"))
                        statusErr = true
                        saving = false
                        return@launch
                    }
                    val wasDelete = coverPending!!.optString("type", "") == "delete"
                    coverPending = null
                    if (wasDelete) {
                        coverUrl = null
                        coverHasExisting = false
                    } else {
                        r.optString("image_url", "").takeIf { it.isNotBlank() }?.let { coverUrl = it }
                        coverHasExisting = coverUrl != null
                    }
                }
                statusMsg = tr("creator.detail_modal.save_success", "Saved.")
                statusErr = false
                delay(600)
                onDismiss()
            } catch (e: Exception) {
                statusMsg = e.message ?: tr("creator.common.error", "Error")
                statusErr = true
            }
            saving = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF070B14)
        ) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(56.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF070B14))
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = tr("creator.detail_modal.tab_avatar", "Profile photo"),
                        tint = if (mainTab == TAB_AVATAR) EazColors.Orange else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (mainTab == TAB_AVATAR) EazColors.Orange.copy(alpha = 0.2f) else Color.Transparent
                            )
                            .clickable { mainTab = TAB_AVATAR }
                            .padding(8.dp)
                    )
                    Icon(
                        Icons.Default.Image,
                        contentDescription = tr("creator.detail_modal.tab_cover", "Cover"),
                        tint = if (mainTab == TAB_COVER) EazColors.Orange else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (mainTab == TAB_COVER) EazColors.Orange.copy(alpha = 0.2f) else Color.Transparent
                            )
                            .clickable { mainTab = TAB_COVER }
                            .padding(8.dp)
                    )
                }

                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null, tint = Color.White)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                tr("creator.detail_modal.title", "Creator profile"),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(creatorName, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                        }
                        Button(
                            onClick = { saveAll() },
                            enabled = hasUnsaved() && !saving,
                            colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                            Text(tr("creator.common.save", "Save"), modifier = Modifier.padding(start = 6.dp))
                        }
                    }

                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EazColors.Orange)
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            if (mainTab == TAB_AVATAR) {
                                ImageSection(
                                    label = tr("creator.detail_modal.tab_avatar", "Profile photo"),
                                    imageUrl = avatarUrl,
                                    onUpload = {
                                        pickCategory = "avatar"
                                        pickLauncher.launch("image/*")
                                    },
                                    onRemove = {
                                        if (!avatarHasExisting && avatarPending == null) return@ImageSection
                                        if (!avatarHasExisting) {
                                            avatarPending = null
                                            avatarUrl = null
                                            return@ImageSection
                                        }
                                        avatarPending = JSONObject().put("type", "delete").put("image_type", "none")
                                        avatarUrl = null
                                        statusMsg = tr("creator.detail_modal.remove_pending", "Removal applies when you save.")
                                        statusErr = false
                                    },
                                    showGenerate = showAvatarGen,
                                    onToggleGenerate = { showAvatarGen = !showAvatarGen },
                                    prompt = avatarPrompt,
                                    onPromptChange = { avatarPrompt = it },
                                    onGenerate = { runGenerate("avatar") },
                                    generating = generatingAvatar,
                                    genProgress = genProgress,
                                    tr = tr
                                )
                            } else {
                                Text(
                                    tr("creator.detail_modal.cover_mode_title", "Header display"),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = displayMode == "cover",
                                        onClick = { displayMode = "cover" },
                                        colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                                    )
                                    Text(tr("creator.detail_modal.cover_mode_image", "Cover image"), color = Color.White, modifier = Modifier.clickable { displayMode = "cover" })
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = displayMode == "hero",
                                        onClick = { displayMode = "hero" },
                                        colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                                    )
                                    Text(tr("creator.detail_modal.cover_mode_hero", "Hero grid"), color = Color.White, modifier = Modifier.clickable { displayMode = "hero" })
                                }
                                Text(
                                    tr("creator.detail_modal.hero_count_hint", "Enabled: %s / 4 min. for hero mode.")
                                        .replace("%s", heroEnabledCount().toString()),
                                    color = Color.White.copy(alpha = 0.65f),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                if (displayMode == "cover") {
                                    ImageSection(
                                        label = tr("creator.detail_modal.tab_cover", "Cover"),
                                        imageUrl = coverUrl,
                                        onUpload = {
                                            pickCategory = "cover"
                                            pickLauncher.launch("image/*")
                                        },
                                        onRemove = {
                                            if (!coverHasExisting && coverPending == null) return@ImageSection
                                            if (!coverHasExisting) {
                                                coverPending = null
                                                coverUrl = null
                                                return@ImageSection
                                            }
                                            coverPending = JSONObject().put("type", "delete").put("image_type", "none")
                                            coverUrl = null
                                            statusMsg = tr("creator.detail_modal.remove_pending", "Removal applies when you save.")
                                            statusErr = false
                                        },
                                        showGenerate = showCoverGen,
                                        onToggleGenerate = { showCoverGen = !showCoverGen },
                                        prompt = coverPrompt,
                                        onPromptChange = { coverPrompt = it },
                                        onGenerate = { runGenerate("cover") },
                                        generating = generatingCover,
                                        genProgress = genProgress,
                                        tallPreview = true,
                                        tr = tr
                                    )
                                } else {
                                    if (heroLoading) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = EazColors.Orange)
                                        }
                                    } else if (heroItems.isEmpty()) {
                                        Text(
                                            tr("creator.detail_modal.hero_empty", "No published hero images yet."),
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                    } else {
                                        heroItems.forEach { row ->
                                            HeroToggleRow(row, ownerId, api, scope) { enabled ->
                                                heroItems = heroItems.map {
                                                    if (it.id == row.id) it.copy(enabled = enabled) else it
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            statusMsg?.let {
                                Text(
                                    it,
                                    color = if (statusErr) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class HeroRow(val id: String, val thumb: String, val title: String, val enabled: Boolean)

@Composable
private fun HeroToggleRow(
    row: HeroRow,
    ownerId: String,
    api: CreatorApi,
    scope: kotlinx.coroutines.CoroutineScope,
    onLocalToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable {
                val newE = !row.enabled
                onLocalToggle(newE)
                scope.launch {
                    withContext(Dispatchers.IO) {
                        api.toggleHeroCreatorPage(ownerId, row.id, newE)
                    }
                }
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = row.thumb.takeIf { it.isNotBlank() },
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(row.title.ifBlank { "—" }, color = Color.White, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        }
        Text(
            if (row.enabled) "✓" else "○",
            color = if (row.enabled) EazColors.Orange else Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun ImageSection(
    label: String,
    imageUrl: String?,
    onUpload: () -> Unit,
    onRemove: () -> Unit,
    showGenerate: Boolean,
    onToggleGenerate: () -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    onGenerate: () -> Unit,
    generating: Boolean,
    genProgress: Float,
    tallPreview: Boolean = false,
    tr: (String, String) -> String
) {
    Text(label, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(if (tallPreview) 160.dp else 140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (tallPreview) ContentScale.Crop else ContentScale.Fit
            )
        } else {
            Text(tr("creator.detail_modal.no_image", "No image"), color = Color.White.copy(alpha = 0.45f))
        }
        if (generating) {
            LinearProgressIndicator(
                progress = genProgress,
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                color = EazColors.Orange,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
        }
    }
    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onUpload,
            enabled = !generating,
            colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange, contentColor = Color.Black)
        ) {
            Text(tr("creator.detail_modal.upload_image", "Upload"))
        }
        Button(
            onClick = onToggleGenerate,
            enabled = !generating,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A3142), contentColor = Color.White)
        ) {
            Text(tr("creator.detail_modal.generate_ai", "AI generate"))
        }
        Button(
            onClick = onRemove,
            enabled = !generating,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F2A2A), contentColor = Color(0xFFFCA5A5))
        ) {
            Text(tr("creator.detail_modal.remove_image", "Remove"))
        }
    }
    if (showGenerate) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            minLines = 2,
            label = { Text(tr("creator.detail_modal.prompt_label", "Prompt"), color = Color.White.copy(alpha = 0.8f)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = EazColors.Orange,
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                cursorColor = EazColors.Orange
            )
        )
        Button(
            onClick = onGenerate,
            enabled = !generating,
            modifier = Modifier.padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange, contentColor = Color.Black)
        ) {
            Text(tr("creator.detail_modal.start_generation", "Start") + " (5 EAZ)")
        }
    }
}
