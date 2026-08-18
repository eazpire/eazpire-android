package com.eazpire.creator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.ui.modal.EazBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopTeamSettingsSheet(
    api: CreatorApi,
    productKey: String,
    studioSessionId: String,
    mockUrl: String,
    sizes: List<String>,
    nameOn: Boolean,
    numberOn: Boolean,
    t: (String, String) -> String,
    onDismiss: () -> Unit,
    onNeedField: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var campaignId by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("manual") }
    var shareMode by remember { mutableStateOf("link") }
    var title by remember { mutableStateOf(t("design_studio.shop.team_settings_auto_title", "Team") + " · " + productKey.replace("-", " ")) }
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var askName by remember { mutableStateOf(nameOn) }
    var askNumber by remember { mutableStateOf(numberOn) }
    var emails by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var memberCount by remember { mutableStateOf("1") }
    var members by remember { mutableStateOf(listOf(TeamMemberDraft())) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun resize(count: Int) {
        val n = count.coerceIn(1, 50)
        val next = members.toMutableList()
        while (next.size < n) next.add(TeamMemberDraft())
        members = next.take(n)
        memberCount = n.toString()
    }

    fun payload(statusValue: String): JSONObject {
        val rows = JSONArray()
        members.take(50).forEachIndexed { i, m ->
            val sizeLines = JSONArray().put(
                JSONObject().put("size", m.size).put("qty", m.qty.toIntOrNull() ?: 1)
            )
            rows.put(
                JSONObject()
                    .put("display_name", m.name.ifBlank { "Member ${i + 1}" })
                    .put("jersey_name", if (nameOn) m.name else "")
                    .put("jersey_number", if (numberOn) m.number.filter { it.isDigit() } else "")
                    .put("size", m.size)
                    .put("quantity", m.qty.toIntOrNull() ?: 1)
                    .put("size_lines", sizeLines),
            )
        }
        val products = JSONArray().put(
            JSONObject()
                .put("id", "studio")
                .put("title", title.ifBlank { "Team design" })
                .put("product_key", productKey)
                .put("image_url", mockUrl)
                .put("sizes", JSONArray(sizes.ifEmpty { listOf("S", "M", "L", "XL") })),
        )
        return JSONObject()
            .put("campaign_id", campaignId)
            .put("status", statusValue)
            .put("title", title)
            .put("description", description)
            .put("tags", tags)
            .put("share_password", password)
            .put("source_mode", mode)
            .put("studio_product_key", productKey)
            .put("studio_session_id", studioSessionId)
            .put("ask_product", 0)
            .put("ask_size", if (mode == "ask") 1 else 0)
            .put("ask_name", if (mode == "ask") if (askName) 1 else 0 else 1)
            .put("ask_number", if (mode == "ask") if (askNumber) 1 else 0 else 1)
            .put("ask_quantity", 0)
            .put("products", products)
            .put("member_count", members.size)
            .put("individual", JSONObject().put("name", true).put("number", true).put("size", true).put("quantity", true).put("product", false))
            .put("global", JSONObject())
            .put("rows", rows)
            .put("emails", emails)
            .put("email_subject", subject)
            .put("email_body", body)
    }

    suspend fun persist(statusValue: String): JSONObject {
        val bodyJson = payload(statusValue)
        val res = if (campaignId.isBlank()) api.askTeamCreate(bodyJson) else api.askTeamUpdate(bodyJson)
        if (res.optBoolean("ok")) {
            campaignId = res.optString("campaign_id").ifBlank { campaignId }
            status = t("design_studio.shop.team_settings_draft_saved", "Draft saved.")
        }
        return res
    }

    EazBottomSheet(onDismissRequest = onDismiss, fullscreen = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(t("design_studio.shop.team_settings_modal_title", "Team Settings"), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(title, { title = it }, label = { Text(t("eaz.ask_team.campaign_title", "Title")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text(t("eaz.ask_team.description", "Description")) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tags, { tags = it }, label = { Text(t("design_studio.shop.team_settings_tags", "Tags")) }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "manual", onClick = { mode = "manual" }, label = { Text(t("design_studio.shop.team_settings_mode_manual", "Set Manual")) })
                FilterChip(selected = mode == "ask", onClick = { mode = "ask" }, label = { Text(t("design_studio.shop.team_settings_mode_ask", "Ask Team")) })
            }
            if (mode == "manual") {
                OutlinedTextField(
                    memberCount,
                    {
                        memberCount = it.filter { ch -> ch.isDigit() }
                        resize(memberCount.toIntOrNull() ?: 1)
                    },
                    label = { Text(t("design_studio.shop.team_settings_member_count", "Team members")) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(t("design_studio.shop.team_settings_member_hint", "Typical teams are around 25. Maximum 50."), color = EazColors.TextSecondary)
                members.forEachIndexed { i, m ->
                    OutlinedTextField(
                        value = m.name,
                        onValueChange = { next -> members = members.toMutableList().also { list -> list[i] = m.copy(name = next) } },
                        label = { Text(t("design_studio.shop.team_settings_name", "Name")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = m.number,
                        onValueChange = { next -> members = members.toMutableList().also { list -> list[i] = m.copy(number = next.filter { ch -> ch.isDigit() }) } },
                        label = { Text(t("design_studio.shop.team_settings_number", "Number")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = m.size,
                        onValueChange = { next -> members = members.toMutableList().also { list -> list[i] = m.copy(size = next) } },
                        label = { Text(t("eaz.ask_team.size", "Size")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = m.qty,
                        onValueChange = { next -> members = members.toMutableList().also { list -> list[i] = m.copy(qty = next.filter { ch -> ch.isDigit() }.ifBlank { "1" }) } },
                        label = { Text(t("eaz.ask_team.quantity", "Quantity")) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(askName, { askName = it })
                    Text(t("design_studio.shop.team_settings_name", "Name"))
                    Checkbox(askNumber, { askNumber = it })
                    Text(t("design_studio.shop.team_settings_number", "Number"))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = shareMode == "link", onClick = { shareMode = "link" }, label = { Text(t("design_studio.shop.team_settings_share_link", "Copy link / QR")) })
                    FilterChip(selected = shareMode == "email", onClick = { shareMode = "email" }, label = { Text(t("design_studio.shop.team_settings_share_email", "Send email")) })
                }
                if (shareMode == "link") {
                    OutlinedTextField(password, { password = it }, label = { Text(t("design_studio.shop.team_settings_password", "Optional password")) }, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(emails, { emails = it }, label = { Text(t("design_studio.shop.team_settings_add_email", "Add another person")) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    OutlinedTextField(subject, { subject = it }, label = { Text(t("eaz.ask_team.subject", "Email subject")) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(body, { body = it }, label = { Text(t("eaz.ask_team.body", "Email")) }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                }
            }
            if (status.isNotBlank()) Text(status, color = EazColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            withContext(Dispatchers.IO) { persist("draft") }
                            busy = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(t("design_studio.shop.team_settings_save_draft", "Save draft")) }
                Button(
                    enabled = !busy,
                    onClick = {
                        if (!nameOn && !numberOn) {
                            onNeedField()
                            return@Button
                        }
                        if (mode == "ask" && !askName && !askNumber) {
                            onNeedField()
                            return@Button
                        }
                        scope.launch {
                            busy = true
                            val res = withContext(Dispatchers.IO) { persist("open") }
                            if (res.optBoolean("ok") && mode == "ask") {
                                if (shareMode == "email") {
                                    withContext(Dispatchers.IO) {
                                        api.askTeamInvite(campaignId.ifBlank { res.optString("campaign_id") }, emails, subject, body)
                                    }
                                    status = t("eaz.ask_team.send_email", "Send emails")
                                } else {
                                    val link = res.optString("share_url").ifBlank { res.optJSONObject("campaign")?.optString("share_url").orEmpty() }
                                    if (link.isNotBlank()) clipboard.setText(AnnotatedString(link))
                                    status = link.ifBlank { t("eaz.ask_team.copy_link", "Copy link") }
                                }
                            } else if (res.optBoolean("ok")) {
                                status = t("design_studio.shop.team_settings_create_products", "Create Products")
                            } else {
                                status = t("eaz.ask_team.create_error", "Could not save team settings.")
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (mode == "ask") {
                            if (shareMode == "email") t("design_studio.shop.team_settings_send", "Send")
                            else t("eaz.ask_team.copy_link", "Copy link")
                        } else t("design_studio.shop.team_settings_create_products", "Create Products")
                    )
                }
            }
        }
    }
}

private data class TeamMemberDraft(
    val name: String = "",
    val number: String = "",
    val size: String = "",
    val qty: String = "1",
)
