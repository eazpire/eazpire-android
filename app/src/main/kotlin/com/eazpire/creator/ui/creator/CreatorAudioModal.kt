package com.eazpire.creator.ui.creator

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.audio.CreatorAudioItem
import com.eazpire.creator.audio.CreatorAudioStore
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorAudioModal(
    store: CreatorAudioStore,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    val list by store.list.collectAsState()
    val selectedId by store.selectedId.collectAsState()
    val isPlaying by store.isPlaying.collectAsState()
    val currentPlaybackId by store.currentPlaybackId.collectAsState()
    val volume by store.volume.collectAsState()
    val muted by store.muted.collectAsState()
    val isLoading by store.isLoading.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf<CreatorAudioItem?>(null) }

    LaunchedEffect(Unit) {
        store.isLoading.value = true
        try {
            val res = api.listAudioFiles()
            val arr = res.optJSONArray("files") ?: org.json.JSONArray()
            store.list.value = (0 until arr.length()).mapNotNull { i ->
                CreatorAudioStore.parseItem(arr.getJSONObject(i))
            }
        } catch (_: Exception) {
            store.list.value = emptyList()
        }
        store.isLoading.value = false
    }

    fun useSelected() {
        val id = selectedId ?: return
        if (ownerId.isBlank()) return
        scope.launch {
            try {
                val res = api.setCreatorAudio(ownerId, id)
                if (res.optBoolean("ok", false)) {
                    val item = store.getItem(id) ?: return@launch
                    store.play(item)
                    onDismiss()
                }
            } catch (_: Exception) {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF070B14),
        modifier = modifier.fillMaxSize(),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF070B14))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = translationStore.t("creator.audio.title", "Audio Library"),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0B1220))
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                if (list.isEmpty() && !isLoading) {
                    Text(
                        text = translationStore.t("creator.audio.empty", "No audio files yet. Add one to get started."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    list.forEach { item: CreatorAudioItem ->
                        val isSelected = selectedId == item.id
                        val isItemPlaying = currentPlaybackId == item.id && isPlaying
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(
                                    if (isSelected) EazColors.Orange.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) { store.select(item.id) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.coverUrl != null) {
                                    AsyncImage(
                                        model = item.coverUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = formatTime(0) + " / " + formatTime(item.durationSec),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(
                                onClick = { store.togglePlay(item) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    if (isItemPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isItemPlaying) "Pause" else "Play",
                                    tint = EazColors.Orange
                                )
                            }
                            if (ownerId == item.ownerId) {
                                IconButton(
                                    onClick = { showDeleteConfirm = item },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        tint = Color.White.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF070B14))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = { store.toggleMute() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (muted) "Unmute" else "Mute",
                            tint = if (muted) Color.White.copy(alpha = 0.5f) else Color.White
                        )
                    }
                    Slider(
                        value = volume,
                        onValueChange = { store.setVolume(it) },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = EazColors.Orange,
                            activeTrackColor = EazColors.Orange,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CreatorAudioAddButton(
                        ownerId = ownerId,
                        api = api,
                        store = store,
                        translationStore = translationStore
                    )
                    androidx.compose.material3.Button(
                        onClick = { useSelected() },
                        enabled = selectedId != null,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = EazColors.Orange,
                            disabledContainerColor = Color.White.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            translationStore.t("creator.audio.use", "Use"),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    showDeleteConfirm?.let { item ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(translationStore.t("creator.audio.remove_confirm", "Remove?")) },
            text = { Text(translationStore.t("creator.audio.remove_confirm_desc", "This cannot be undone.")) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                api.deleteAudioFile(ownerId, item.id)
                                if (store.currentPlaybackId.value == item.id) store.stop()
                                store.list.value = store.list.value.filter { it.id != item.id }
                                if (selectedId == item.id) store.select(null)
                            } catch (_: Exception) {}
                            showDeleteConfirm = null
                        }
                    }
                ) {
                    Text(translationStore.t("creator.audio.remove", "Remove"), color = EazColors.Orange)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = null }) {
                    Text(translationStore.t("creator.audio.cancel", "Cancel"), color = Color.White)
                }
            },
            containerColor = Color(0xFF0B1220),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun CreatorAudioAddButton(
    ownerId: String,
    api: CreatorApi,
    store: CreatorAudioStore,
    translationStore: TranslationStore
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { u ->
            scope.launch {
                try {
                    store.isLoading.value = true
                    val bytes = ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: return@launch
                    val mime = ctx.contentResolver.getType(u) ?: "audio/mpeg"
                    val res = api.uploadAudioFile(ownerId, bytes, mime, null)
                    if (res.optBoolean("ok", false)) {
                        val arr = api.listAudioFiles().optJSONArray("files") ?: org.json.JSONArray()
                        store.list.value = (0 until arr.length()).mapNotNull { i ->
                            CreatorAudioStore.parseItem(arr.getJSONObject(i))
                        }
                    }
                } catch (_: Exception) {}
                store.isLoading.value = false
            }
        }
    }

    androidx.compose.material3.OutlinedButton(
        onClick = { launcher.launch("audio/*") },
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = EazColors.Orange
        )
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            translationStore.t("creator.audio.add", "Add"),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private fun formatTime(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}
