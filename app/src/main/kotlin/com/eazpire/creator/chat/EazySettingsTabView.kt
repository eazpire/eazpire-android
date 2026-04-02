@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eazpire.creator.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun EazySettingsTabView(
    eazySettingsStore: EazySettingsStore,
    api: CreatorApi,
    chatStore: EazyChatStore,
    ownerId: String?,
    scope: CoroutineScope,
    t: (String, String) -> String,
    onResetMascot: () -> Unit,
    onOpenFunctions: () -> Unit,
    onChatHistoryCleared: () -> Unit
) {
    val settings by eazySettingsStore.settings.collectAsState()
    var showClearHistory by remember { mutableStateOf(false) }
    var showClearMemory by remember { mutableStateOf(false) }

    fun sync() {
        scope.launch {
            val u = chatStore.getUserId(ownerId)
            eazySettingsStore.scheduleSyncToServer(api, u)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            t("eazy_chat.settings_audio", "Eazy Audio"),
            style = MaterialTheme.typography.titleSmall,
            color = LocalEazyModalPalette.current.accent
        )
        val audioOn = settings.optBoolean("audio_enabled", true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(t("eazy_chat.settings_audio_enabled", "Audio"), color = LocalEazyModalPalette.current.text)
            Switch(
                checked = audioOn,
                onCheckedChange = {
                    eazySettingsStore.setBoolean("audio_enabled", it)
                    sync()
                }
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (audioOn) 1f else 0.45f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(t("eazy_chat.settings_volume", "Volume"), color = LocalEazyModalPalette.current.text, modifier = Modifier.weight(1f))
            Text("${settings.optInt("audio_volume", 75)}%", color = LocalEazyModalPalette.current.muted, style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = settings.optInt("audio_volume", 75).toFloat(),
            onValueChange = {
                eazySettingsStore.setInt("audio_volume", it.toInt().coerceIn(0, 100))
                sync()
            },
            valueRange = 0f..100f,
            enabled = audioOn,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth().alpha(if (audioOn) 1f else 0.45f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(t("eazy_chat.settings_autoplay", "Auto-play voice messages"), color = LocalEazyModalPalette.current.text)
            Switch(
                checked = settings.optBoolean("audio_autoplay", false),
                onCheckedChange = {
                    eazySettingsStore.setBoolean("audio_autoplay", it)
                    sync()
                },
                enabled = audioOn
            )
        }

        Divider(color = LocalEazyModalPalette.current.muted.copy(alpha = 0.25f))

        Text(
            t("eazy_chat.settings_messages", "Automatic Messages"),
            style = MaterialTheme.typography.titleSmall,
            color = LocalEazyModalPalette.current.accent
        )
        val msgMaster = settings.optBoolean("messages_enabled", true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(t("eazy_chat.settings_messages_master", "All automatic messages"), color = LocalEazyModalPalette.current.text)
            Switch(
                checked = msgMaster,
                onCheckedChange = {
                    eazySettingsStore.setBoolean("messages_enabled", it)
                    sync()
                }
            )
        }

        EazyMsgToggleRow(
            settings = settings,
            msgMaster = msgMaster,
            key = "messages_mascot_bubbles",
            labelKey = "eazy_chat.settings_msg_mascot",
            labelDefault = "Mascot Bubbles",
            hintKey = "eazy_chat.settings_msg_mascot_hint",
            hintDefault = "Speech bubbles on the Eazy icon on the page",
            showFreq = true,
            freqKey = "frequency_mascot_bubbles",
            t = t,
            onToggle = { k, v -> eazySettingsStore.setBoolean(k, v); sync() },
            onFreq = { k, v -> eazySettingsStore.setInt(k, v); sync() }
        )
        EazyMsgToggleRow(
            settings = settings,
            msgMaster = msgMaster,
            key = "messages_dream_bubbles",
            labelKey = "eazy_chat.settings_msg_dream",
            labelDefault = "Dream Bubbles",
            hintKey = "eazy_chat.settings_msg_dream_hint",
            hintDefault = "At night: Eazy dreams and mumbles",
            showFreq = true,
            freqKey = "frequency_dream_bubbles",
            t = t,
            onToggle = { k, v -> eazySettingsStore.setBoolean(k, v); sync() },
            onFreq = { k, v -> eazySettingsStore.setInt(k, v); sync() }
        )
        EazyMsgToggleRow(
            settings = settings,
            msgMaster = msgMaster,
            key = "messages_chat_greeting",
            labelKey = "eazy_chat.settings_msg_greeting",
            labelDefault = "Chat Greeting",
            hintKey = "eazy_chat.settings_msg_greeting_hint",
            hintDefault = "First message when opening the chat",
            t = t,
            onToggle = { k, v -> eazySettingsStore.setBoolean(k, v); sync() },
            onFreq = { k, v -> eazySettingsStore.setInt(k, v); sync() }
        )
        EazyMsgToggleRow(
            settings = settings,
            msgMaster = msgMaster,
            key = "messages_idle_tips_chat",
            labelKey = "eazy_chat.settings_msg_idle_chat",
            labelDefault = "Idle Tips (Chat)",
            hintKey = "eazy_chat.settings_msg_idle_chat_hint",
            hintDefault = "Tips during inactivity in the open chat",
            showFreq = true,
            freqKey = "frequency_idle_tips_chat",
            t = t,
            onToggle = { k, v -> eazySettingsStore.setBoolean(k, v); sync() },
            onFreq = { k, v -> eazySettingsStore.setInt(k, v); sync() }
        )
        EazyMsgToggleRow(
            settings = settings,
            msgMaster = msgMaster,
            key = "messages_idle_tips_page",
            labelKey = "eazy_chat.settings_msg_idle_page",
            labelDefault = "Idle Tips (Page)",
            hintKey = "eazy_chat.settings_msg_idle_page_hint",
            hintDefault = "Tips as speech bubble on the icon",
            showFreq = true,
            freqKey = "frequency_idle_tips_page",
            t = t,
            onToggle = { k, v -> eazySettingsStore.setBoolean(k, v); sync() },
            onFreq = { k, v -> eazySettingsStore.setInt(k, v); sync() }
        )
        EazyMsgToggleRow(
            settings = settings,
            msgMaster = msgMaster,
            key = "messages_function_tips",
            labelKey = "eazy_chat.settings_msg_fn_tips",
            labelDefault = "Function Tips",
            hintKey = "eazy_chat.settings_msg_fn_tips_hint",
            hintDefault = "Contextual tips within functions",
            t = t,
            onToggle = { k, v -> eazySettingsStore.setBoolean(k, v); sync() },
            onFreq = { k, v -> eazySettingsStore.setInt(k, v); sync() }
        )
        EazyMsgToggleRow(
            settings = settings,
            msgMaster = msgMaster,
            key = "messages_job_updates",
            labelKey = "eazy_chat.settings_msg_jobs",
            labelDefault = "Job Updates",
            hintKey = "eazy_chat.settings_msg_jobs_hint",
            hintDefault = "\"Design is ready!\" as chat message",
            t = t,
            onToggle = { k, v -> eazySettingsStore.setBoolean(k, v); sync() },
            onFreq = { k, v -> eazySettingsStore.setInt(k, v); sync() }
        )

        Divider(color = LocalEazyModalPalette.current.muted.copy(alpha = 0.25f))

        Text(
            t("eazy_chat.settings_mood", "Mood / Tone"),
            style = MaterialTheme.typography.titleSmall,
            color = LocalEazyModalPalette.current.accent
        )
        Text(
            t("eazy_chat.settings_mood_hint", "Choose which types of messages Eazy sends you."),
            style = MaterialTheme.typography.bodySmall,
            color = LocalEazyModalPalette.current.muted
        )
        val tags = listOf(
            "lustig" to ("eazy_chat.settings_tag_lustig" to "Funny"),
            "inspirierend" to ("eazy_chat.settings_tag_inspirierend" to "Inspiring"),
            "weisheit" to ("eazy_chat.settings_tag_weisheit" to "Wisdom"),
            "informativ" to ("eazy_chat.settings_tag_informativ" to "Informative"),
            "frech" to ("eazy_chat.settings_tag_frech" to "Cheeky"),
            "flirty" to ("eazy_chat.settings_tag_flirty" to "Flirty")
        )
        val active = eazySettingsStore.getMoodTags()
        tags.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (id, pair) ->
                    val (lk, def) = pair
                    FilterChip(
                        selected = active.contains(id),
                        onClick = {
                            eazySettingsStore.toggleMoodTag(id)
                            sync()
                        },
                        label = { Text(t(lk, def), style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }

        Divider(color = LocalEazyModalPalette.current.muted.copy(alpha = 0.25f))

        Text(
            t("eazy_chat.settings_placement", "Eazy Placement"),
            style = MaterialTheme.typography.titleSmall,
            color = LocalEazyModalPalette.current.accent
        )
        val placement = settings.optString("placement", "page").ifBlank { "page" }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = placement == "header",
                onClick = {
                    eazySettingsStore.setString("placement", "header")
                    sync()
                },
                label = { Text(t("eazy_chat.settings_placement_header", "Header")) }
            )
            FilterChip(
                selected = placement == "page",
                onClick = {
                    eazySettingsStore.setString("placement", "page")
                    sync()
                },
                label = { Text(t("eazy_chat.settings_placement_page", "Page (free)")) }
            )
        }
        TextButton(onClick = {
            onResetMascot()
            eazySettingsStore.setString("placement", "page")
            sync()
        }) {
            Text(t("eazy_chat.settings_reset_position", "Reset position"), color = LocalEazyModalPalette.current.accent)
        }

        Divider(color = LocalEazyModalPalette.current.muted.copy(alpha = 0.25f))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = LocalEazyModalPalette.current.accent, modifier = Modifier.size(20.dp))
            Text(
                t("eazy_chat.ui_functions_tab", "Functions"),
                style = MaterialTheme.typography.titleSmall,
                color = LocalEazyModalPalette.current.accent
            )
        }
        Text(
            t("eazy_fn.hint", "Eye: show or hide shortcuts in the chat carousel."),
            style = MaterialTheme.typography.bodySmall,
            color = LocalEazyModalPalette.current.muted
        )
        TextButton(onClick = onOpenFunctions) {
            Text(t("eazy_chat.settings_open_functions", "Manage function shortcuts"), color = LocalEazyModalPalette.current.accent)
        }

        Divider(color = LocalEazyModalPalette.current.muted.copy(alpha = 0.25f))

        Text(
            t("eazy_chat.settings_privacy", "Privacy"),
            style = MaterialTheme.typography.titleSmall,
            color = LocalEazyModalPalette.current.accent
        )
        TextButton(onClick = { showClearHistory = true }) {
            Text(t("eazy_chat.settings_clear_history", "Delete chat history"), color = Color(0xFFEF4444))
        }
        TextButton(onClick = { showClearMemory = true }) {
            Text(t("eazy_chat.settings_clear_memory", "Reset Eazy memory"), color = Color(0xFFF97316))
        }
    }

    if (showClearHistory) {
        AlertDialog(
            onDismissRequest = { showClearHistory = false },
            title = { Text(t("eazy_chat.settings_clear_history", "Delete chat history"), color = LocalEazyModalPalette.current.text) },
            text = {
                Text(
                    t("eazy_chat.settings_clear_history_confirm", "All chats and conversations will be permanently deleted. This cannot be undone."),
                    color = LocalEazyModalPalette.current.muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearHistory = false
                    scope.launch {
                        val u = chatStore.getUserId(ownerId)
                        withContext(Dispatchers.IO) { api.eazyConvDeleteAllChats(u) }
                        onChatHistoryCleared()
                    }
                }) { Text(t("eazy_chat.settings_confirm_ok", "Confirm"), color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistory = false }) {
                    Text(t("eazy_chat.settings_confirm_cancel", "Cancel"), color = LocalEazyModalPalette.current.muted)
                }
            },
            containerColor = LocalEazyModalPalette.current.header
        )
    }

    if (showClearMemory) {
        AlertDialog(
            onDismissRequest = { showClearMemory = false },
            title = { Text(t("eazy_chat.settings_clear_memory", "Reset Eazy memory"), color = LocalEazyModalPalette.current.text) },
            text = {
                Text(
                    t("eazy_chat.settings_clear_memory_confirm", "All preferences and Eazy's memories of you will be reset."),
                    color = LocalEazyModalPalette.current.muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearMemory = false
                    eazySettingsStore.resetToDefaults()
                    sync()
                }) { Text(t("eazy_chat.settings_confirm_ok", "Confirm"), color = LocalEazyModalPalette.current.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showClearMemory = false }) {
                    Text(t("eazy_chat.settings_confirm_cancel", "Cancel"), color = LocalEazyModalPalette.current.muted)
                }
            },
            containerColor = LocalEazyModalPalette.current.header
        )
    }
}

private fun freqLabel(v: Int, t: (String, String) -> String): String = when (v) {
    0 -> t("eazy_chat.settings_freq_rare", "Rare")
    1 -> t("eazy_chat.settings_freq_normal", "Normal")
    else -> t("eazy_chat.settings_freq_often", "Often")
}

@Composable
private fun EazyMsgToggleRow(
    settings: JSONObject,
    msgMaster: Boolean,
    key: String,
    labelKey: String,
    labelDefault: String,
    hintKey: String? = null,
    hintDefault: String? = null,
    showFreq: Boolean = false,
    freqKey: String? = null,
    t: (String, String) -> String,
    onToggle: (String, Boolean) -> Unit,
    onFreq: (String, Int) -> Unit
) {
    Column(Modifier.fillMaxWidth().alpha(if (msgMaster) 1f else 0.45f)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(t(labelKey, labelDefault), color = LocalEazyModalPalette.current.text, modifier = Modifier.weight(1f))
            Switch(
                checked = settings.optBoolean(key, true),
                onCheckedChange = { onToggle(key, it) },
                enabled = msgMaster
            )
        }
        hintKey?.let { hk ->
            Text(
                t(hk, hintDefault ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = LocalEazyModalPalette.current.muted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (showFreq && freqKey != null) {
            val fv = settings.optInt(freqKey, 1).coerceIn(0, 2)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("eazy_chat.settings_frequency", "Frequency"),
                    color = LocalEazyModalPalette.current.muted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(72.dp)
                )
                Slider(
                    value = fv.toFloat(),
                    onValueChange = { onFreq(freqKey, it.toInt().coerceIn(0, 2)) },
                    valueRange = 0f..2f,
                    steps = 1,
                    enabled = msgMaster && settings.optBoolean(key, true),
                    modifier = Modifier.weight(1f)
                )
                Text(freqLabel(fv, t), color = LocalEazyModalPalette.current.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
