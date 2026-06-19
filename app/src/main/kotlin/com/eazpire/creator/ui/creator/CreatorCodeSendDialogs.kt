package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eazpire.creator.ui.modal.EazInsetDialog
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

data class CreatorCodeRecipient(
    val ownerId: String,
    val username: String,
    val generatedCount: Int,
    val uploadCount: Int,
    val profilePictureUrl: String?,
)

fun parseCreatorCodeRecipients(json: JSONObject): List<CreatorCodeRecipient> {
    if (!json.optBoolean("ok", false)) return emptyList()
    val arr = json.optJSONArray("users") ?: JSONArray()
    return buildList(arr.length()) {
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val oid = row.optString("owner_id", "").trim()
            if (oid.isBlank()) continue
            add(
                CreatorCodeRecipient(
                    ownerId = oid,
                    username = row.optString("username", "User").ifBlank { "User" },
                    generatedCount = row.optInt("generated_count", 0),
                    uploadCount = row.optInt("upload_count", 0),
                    profilePictureUrl = row.optString("profile_picture_url", null)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() },
                )
            )
        }
    }
}

private fun tpl(text: String, generated: Int, uploads: Int): String =
    text
        .replace("{{generated}}", generated.toString())
        .replace("{{uploads}}", uploads.toString())

@Composable
fun CreatorCodeUserPickerDialog(
    visible: Boolean,
    ownerId: String,
    api: CreatorApi,
    codeId: Long,
    isPermanentGift: Boolean,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
    onSent: () -> Unit,
) {
    if (!visible) return

    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var users by remember { mutableStateOf<List<CreatorCodeRecipient>>(emptyList()) }
    var selected by remember { mutableStateOf<CreatorCodeRecipient?>(null) }
    var confirmChecked by remember { mutableStateOf(!isPermanentGift) }
    var sending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun resetPicker() {
        selected = null
        searchQuery = ""
        debouncedQuery = ""
        confirmChecked = !isPermanentGift
        errorMessage = null
    }

    LaunchedEffect(searchQuery) {
        delay(350)
        debouncedQuery = searchQuery.trim()
    }

    LaunchedEffect(debouncedQuery, selected) {
        if (selected != null) return@LaunchedEffect
        loading = true
        try {
            val q = debouncedQuery.takeIf { it.length >= 2 }
            val resp = withContext(Dispatchers.IO) {
                api.listCreatorCodeRecipients(ownerId, q)
            }
            users = parseCreatorCodeRecipients(resp)
        } catch (_: Exception) {
            users = emptyList()
        }
        loading = false
    }

    fun confirmSendText(username: String): String {
        val key = if (isPermanentGift) {
            "creator.settings.creator_codes_send_confirm_permanent"
        } else {
            "creator.settings.creator_codes_send_confirm_share"
        }
        val fallback = if (isPermanentGift) {
            "Code goes to @$username — you will lose this code permanently."
        } else {
            "Code goes to @$username — it will be redeemed when they accept."
        }
        return translationStore.t(key, fallback).replace("{{username}}", username)
    }

    EazInsetDialog(onDismissRequest = {
        resetPicker()
        onDismiss()
    }) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0B1220))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Text(
                text = translationStore.t(
                    "creator.settings.creator_codes_picker_title",
                    "Send Creator Code",
                ),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )

            if (selected == null) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    placeholder = {
                        Text(
                            translationStore.t(
                                "creator.settings.creator_codes_picker_search_placeholder",
                                "Search by username",
                            ),
                            color = Color.White.copy(alpha = 0.45f),
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = EazColors.Orange,
                        focusedBorderColor = EazColors.Orange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                    ),
                )
                Text(
                    text = translationStore.t(
                        "creator.settings.creator_codes_picker_search_hint",
                        "Type at least 2 characters to search",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 8.dp),
                )

                if (loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.size(28.dp))
                    }
                } else if (users.isEmpty()) {
                    Text(
                        text = translationStore.t(
                            "creator.settings.creator_codes_picker_empty",
                            "No eligible users found",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(users, key = { it.ownerId }) { user ->
                            CreatorCodeRecipientRow(
                                user = user,
                                translationStore = translationStore,
                                onClick = {
                                    selected = user
                                    confirmChecked = !isPermanentGift
                                    errorMessage = null
                                },
                            )
                        }
                    }
                }

                TextButton(
                    onClick = {
                        resetPicker()
                        onDismiss()
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 12.dp),
                ) {
                    Text(
                        translationStore.t("creator.settings.creator_codes_gift_cancel_btn", "Cancel"),
                        color = Color(0xFFFCA5A5),
                    )
                }
            } else {
                val user = selected!!
                Text(
                    text = confirmSendText(user.username),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (isPermanentGift) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Checkbox(
                            checked = confirmChecked,
                            onCheckedChange = { confirmChecked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = EazColors.Orange,
                                uncheckedColor = Color.White.copy(alpha = 0.5f),
                            ),
                        )
                        Text(
                            translationStore.t(
                                "creator.settings.creator_codes_gift_confirm",
                                "I understand I will lose this code",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFCA5A5),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            if (isPermanentGift && !confirmChecked) return@Button
                            sending = true
                            scope.launch {
                                try {
                                    val resp = withContext(Dispatchers.IO) {
                                        api.giftCreatorCode(
                                            ownerId = ownerId,
                                            codeId = codeId,
                                            channel = "direct_user",
                                            target = user.ownerId,
                                        )
                                    }
                                    if (resp.optBoolean("ok", false)) {
                                        resetPicker()
                                        onSent()
                                        onDismiss()
                                    } else {
                                        errorMessage = resp.optString(
                                            "error",
                                            translationStore.t(
                                                "creator.settings.creator_codes_send_failed",
                                                "Send failed",
                                            ),
                                        )
                                    }
                                } catch (_: Exception) {
                                    errorMessage = translationStore.t(
                                        "creator.settings.creator_codes_connection_error",
                                        "Connection error. Please try again.",
                                    )
                                }
                                sending = false
                            }
                        },
                        enabled = !sending && (!isPermanentGift || confirmChecked),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                translationStore.t(
                                    "creator.settings.creator_codes_send_user_btn",
                                    "Send to user",
                                ),
                                color = Color.Black,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            selected = null
                            confirmChecked = !isPermanentGift
                            errorMessage = null
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            translationStore.t("creator.settings.creator_codes_gift_cancel_btn", "Cancel"),
                            color = Color(0xFFFCA5A5),
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CreatorCodeRecipientRow(
    user: CreatorCodeRecipient,
    translationStore: TranslationStore,
    onClick: () -> Unit,
) {
    val statsTpl = translationStore.t(
        "creator.settings.creator_codes_picker_stats",
        "{{generated}} designs · {{uploads}} uploads",
    )
    val statsText = tpl(statsTpl, user.generatedCount, user.uploadCount)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "@${user.username}",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Text(
                text = statsText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        if (user.profilePictureUrl != null) {
            AsyncImage(
                model = user.profilePictureUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
fun CreatorCodePoolConfirmDialog(
    visible: Boolean,
    ownerId: String,
    api: CreatorApi,
    codeId: Long,
    isPermanentGift: Boolean,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
    onSent: () -> Unit,
) {
    if (!visible) return

    var confirmChecked by remember { mutableStateOf(!isPermanentGift) }
    var sending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val confirmText = if (isPermanentGift) {
        translationStore.t(
            "creator.settings.creator_codes_pool_confirm_permanent",
            "Add this code to the Eazy gift pool? You will lose it permanently when someone claims it.",
        )
    } else {
        translationStore.t(
            "creator.settings.creator_codes_pool_confirm_share",
            "Add this code to the Eazy gift pool? It stays active until someone claims it.",
        )
    }

    EazInsetDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0B1220))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Text(
                text = translationStore.t(
                    "creator.settings.creator_codes_pool_confirm_title",
                    "Eazy pool",
                ),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = confirmText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 12.dp),
            )
            if (isPermanentGift) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Checkbox(
                        checked = confirmChecked,
                        onCheckedChange = { confirmChecked = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = EazColors.Orange,
                            uncheckedColor = Color.White.copy(alpha = 0.5f),
                        ),
                    )
                    Text(
                        translationStore.t(
                            "creator.settings.creator_codes_gift_confirm",
                            "I understand I will lose this code",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFCA5A5),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        if (isPermanentGift && !confirmChecked) return@Button
                        sending = true
                        scope.launch {
                            try {
                                val resp = withContext(Dispatchers.IO) {
                                    api.giftCreatorCode(
                                        ownerId = ownerId,
                                        codeId = codeId,
                                        channel = "eazy_pool",
                                    )
                                }
                                if (resp.optBoolean("ok", false)) {
                                    onSent()
                                    onDismiss()
                                } else {
                                    errorMessage = resp.optString(
                                        "error",
                                        translationStore.t(
                                            "creator.settings.creator_codes_pool_failed",
                                            "Could not add to pool",
                                        ),
                                    )
                                }
                            } catch (_: Exception) {
                                errorMessage = translationStore.t(
                                    "creator.settings.creator_codes_connection_error",
                                    "Connection error. Please try again.",
                                )
                            }
                            sending = false
                        }
                    },
                    enabled = !sending && (!isPermanentGift || confirmChecked),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            translationStore.t(
                                "creator.settings.creator_codes_eazy_pool_btn",
                                "Eazy pool",
                            ),
                            color = Color.Black,
                        )
                    }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        translationStore.t("creator.settings.creator_codes_gift_cancel_btn", "Cancel"),
                        color = Color(0xFFFCA5A5),
                    )
                }
            }
        }
        }
    }
}
