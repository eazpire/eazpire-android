package com.eazpire.creator.ui.askteam

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.ui.modal.EazBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskTeamFormSheet(
    token: String,
    invite: String = "",
    onDismiss: () -> Unit,
) {
    val api = remember { CreatorApi() }
    val tStore = LocalTranslationStore.current
    fun t(key: String, fallback: String) = tStore?.t(key, fallback) ?: fallback
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var campaign by remember { mutableStateOf<JSONObject?>(null) }
    var selectedId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(true) }
    var needsPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var emailBound by remember { mutableStateOf(false) }
    var unlockedPassword by remember { mutableStateOf("") }

    fun load(pass: String) {
        scope.launch {
            loading = true
            val res = withContext(Dispatchers.IO) { api.askTeamPublicGet(token, invite, pass) }
            loading = false
            open = res.optBoolean("open", true)
            needsPassword = res.optBoolean("needs_password", false)
            campaign = res.optJSONObject("campaign")
            if (needsPassword) return@launch
            unlockedPassword = pass
            val existing = res.optJSONObject("response")
            val member = res.optJSONObject("member")
            emailBound = member?.optBoolean("email_bound") == true
            name = existing?.optString("jersey_name").orEmpty()
                .ifBlank { member?.optString("display_name").orEmpty() }
            number = existing?.optString("jersey_number").orEmpty()
            size = existing?.optString("size").orEmpty()
            val products = campaign?.optJSONArray("products") ?: JSONArray()
            if (products.length() > 0) selectedId = products.getJSONObject(0).optString("id")
        }
    }

    LaunchedEffect(token, invite) { load("") }

    EazBottomSheet(onDismissRequest = onDismiss, fullscreen = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) CircularProgressIndicator()
            else if (needsPassword) {
                Text(t("eaz.ask_team.password_required", "This form is password protected."))
                OutlinedTextField(password, { password = it }, label = { Text(t("eaz.ask_team.share_password", "Optional password")) }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = EazColors.Orange) }
                Button(onClick = {
                    if (password.isBlank()) error = t("eaz.ask_team.password_wrong", "Wrong password.")
                    else load(password)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(t("eaz.ask_team.unlock", "Unlock"))
                }
            }
            else if (!open) Text(t("eaz.ask_team.closed", "This Ask Team form is closed."))
            else if (done) Text(t("eaz.ask_team.thanks", "Thanks — you’re on the list."), style = MaterialTheme.typography.titleLarge)
            else {
                val c = campaign
                Text(c?.optString("title").orEmpty().ifBlank { t("eaz.ask_team.title", "Ask Team") }, style = MaterialTheme.typography.titleLarge)
                if (!c?.optString("description").isNullOrBlank()) {
                    Text(c?.optString("description").orEmpty(), color = EazColors.TextSecondary)
                }
                val products = c?.optJSONArray("products") ?: JSONArray()
                if (c?.optBoolean("ask_product", true) == true) {
                    Text(t("eaz.ask_team.choose_product", "Choose a product"))
                    (0 until products.length()).forEach { i ->
                        val p = products.getJSONObject(i)
                        val id = p.optString("id")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    2.dp,
                                    if (id == selectedId) EazColors.Orange else EazColors.TopbarBorder,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { selectedId = id }
                                .padding(8.dp),
                        ) {
                            val img = p.optString("image_url")
                            if (img.isNotBlank()) {
                                AsyncImage(img, null, modifier = Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Fit)
                            }
                            Text("${p.optString("title")} · ${p.optString("version_label")}")
                        }
                    }
                }
                if (emailBound) {
                    OutlinedTextField(email, { email = it }, label = { Text(t("eaz.ask_team.email", "Email")) }, modifier = Modifier.fillMaxWidth())
                }
                if (c?.optBoolean("ask_name", true) == true) {
                    OutlinedTextField(name, { name = it }, label = { Text(t("eaz.ask_team.name", "Name")) }, modifier = Modifier.fillMaxWidth())
                }
                if (c?.optBoolean("ask_number", false) == true) {
                    OutlinedTextField(number, { number = it }, label = { Text(t("eaz.ask_team.number", "Number")) }, modifier = Modifier.fillMaxWidth())
                }
                if (c?.optBoolean("ask_size", true) == true) {
                    OutlinedTextField(size, { size = it }, label = { Text(t("eaz.ask_team.size", "Size")) }, modifier = Modifier.fillMaxWidth())
                }
                if (c?.optBoolean("ask_quantity", false) == true) {
                    OutlinedTextField(quantity, { quantity = it }, label = { Text(t("eaz.ask_team.quantity", "Quantity")) }, modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = EazColors.Orange) }
                Button(onClick = {
                    scope.launch {
                        val picked = (0 until products.length()).map { products.getJSONObject(it) }
                            .firstOrNull { it.optString("id") == selectedId }
                            ?: if (products.length() > 0) products.getJSONObject(0) else null
                        val res = withContext(Dispatchers.IO) {
                            api.askTeamPublicSubmit(
                                JSONObject()
                                    .put("t", token)
                                    .put("m", invite)
                                    .put("password", unlockedPassword)
                                    .put("email", email)
                                    .put("respondent_email", email)
                                    .put("name", name)
                                    .put("jersey_name", name)
                                    .put("jersey_number", number)
                                    .put("size", size)
                                    .put("quantity", quantity.toIntOrNull() ?: 1)
                                    .put("product_id", selectedId)
                                    .put("shopify_variant_id", picked?.optString("shopify_variant_id").orEmpty()),
                            )
                        }
                        if (res.optBoolean("ok")) done = true
                        else {
                            error = when (res.optString("error")) {
                                "email_mismatch" -> t("eaz.ask_team.email_mismatch", "Use the email address this invite was sent to.")
                                "password_required" -> t("eaz.ask_team.password_wrong", "Wrong password.")
                                else -> t("eaz.ask_team.submit_error", "Please check the required fields.")
                            }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(t("eaz.ask_team.submit", "Submit"))
                }
            }
        }
    }
}
