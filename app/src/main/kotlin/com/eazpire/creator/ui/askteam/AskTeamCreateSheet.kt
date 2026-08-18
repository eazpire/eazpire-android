package com.eazpire.creator.ui.askteam

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.ui.modal.EazBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AskTeamSeedProduct(
    val id: String = "p1",
    val title: String,
    val variantId: String,
    val handle: String,
    val imageUrl: String,
    val versionLabel: String,
    val sizes: List<String>,
    val views: List<Pair<String, String>>,
    val productKey: String = "",
    val shopifyProductId: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskTeamCreateSheet(
    tokenStore: SecureTokenStore,
    seeds: List<AskTeamSeedProduct>,
    onDismiss: () -> Unit,
    lockAskProduct: Boolean = false,
    initialAskProduct: Boolean = true,
    initialCollectTeamData: Boolean = true,
) {
    val resolvedSeeds = seeds.ifEmpty {
        listOf(
            AskTeamSeedProduct(
                title = "Ask Team",
                variantId = "",
                handle = "",
                imageUrl = "",
                versionLabel = "Version A",
                sizes = emptyList(),
                views = emptyList(),
            )
        )
    }
    val primary = resolvedSeeds.first()
    val jwt = tokenStore.getJwt()
    val api = remember(jwt) { CreatorApi(jwt = jwt) }
    val tStore = LocalTranslationStore.current
    fun t(key: String, fallback: String) = tStore?.t(key, fallback) ?: fallback
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var title by remember { mutableStateOf(primary.title) }
    var description by remember { mutableStateOf("") }
    var emails by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var askProduct by remember(lockAskProduct, initialAskProduct) {
        mutableStateOf(if (lockAskProduct) initialAskProduct else initialAskProduct)
    }
    var askSize by remember(initialCollectTeamData) { mutableStateOf(initialCollectTeamData) }
    var askName by remember(initialCollectTeamData) { mutableStateOf(initialCollectTeamData) }
    var askNumber by remember { mutableStateOf(false) }
    var askQty by remember { mutableStateOf(false) }
    var askPhoto by remember { mutableStateOf(false) }
    var roster by remember(resolvedSeeds) {
        mutableStateOf(
            AskTeamRosterState(
                globalProductId = resolvedSeeds.first().id,
                globalSize = resolvedSeeds.first().sizes.firstOrNull().orEmpty(),
            )
        )
    }
    var shareUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val seedProducts = resolvedSeeds.map { seed ->
        AskTeamProductOption(
            id = seed.id,
            label = listOf(seed.title, seed.versionLabel).filter { it.isNotBlank() }.joinToString(" · "),
            variantId = seed.variantId,
            sizes = seed.sizes,
        )
    }

    LaunchedEffect(jwt) {
        val defaults = withContext(Dispatchers.IO) { api.askTeamDefaults() }
        if (subject.isBlank()) subject = defaults.optString("email_subject")
        if (body.isBlank()) body = defaults.optString("email_body")
    }

    EazBottomSheet(onDismissRequest = onDismiss, fullscreen = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(t("eaz.ask_team.title", "Ask Team"), style = MaterialTheme.typography.titleLarge)
            Text(t("eaz.ask_team.create_lead", "Collect sizes, names, and product votes from your team."), color = EazColors.TextSecondary)
            if (shareUrl == null) {
                OutlinedTextField(title, { title = it }, label = { Text(t("eaz.ask_team.campaign_title", "Title")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text(t("eaz.ask_team.description", "Description")) }, modifier = Modifier.fillMaxWidth())
                if (!lockAskProduct) {
                    CheckRow(t("eaz.ask_team.ask_product", "Product / design"), askProduct) { askProduct = it }
                }
                CheckRow(t("eaz.ask_team.ask_size", "Size"), askSize) { askSize = it }
                CheckRow(t("eaz.ask_team.ask_name", "Name"), askName) { askName = it }
                CheckRow(t("eaz.ask_team.ask_number", "Number"), askNumber) { askNumber = it }
                CheckRow(t("eaz.ask_team.ask_quantity", "Quantity"), askQty) { askQty = it }
                CheckRow(t("eaz.ask_team.ask_photo", "Ask Photo"), askPhoto) { askPhoto = it }
                AskTeamRosterEditor(
                    products = seedProducts,
                    state = roster,
                    onState = { roster = it },
                    t = ::t,
                )
                Text(t("eaz.ask_team.split_pay", "Split payment") + " · " + t("eaz.ask_team.coming_soon", "Coming soon"), color = EazColors.TextSecondary)
                OutlinedTextField(emails, { emails = it }, label = { Text(t("eaz.ask_team.emails", "Email list (optional)")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subject, { subject = it }, label = { Text(t("eaz.ask_team.subject", "Email subject")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text(t("eaz.ask_team.body", "Email text")) }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                error?.let { Text(it, color = EazColors.Orange) }
                Button(onClick = {
                    if (jwt.isNullOrBlank()) {
                        error = t("eaz.ask_team.login_needed", "Sign in to manage Ask Team.")
                        return@Button
                    }
                    scope.launch {
                        val products = JSONArray()
                        resolvedSeeds.forEach { seed ->
                            products.put(
                                JSONObject()
                                    .put("id", seed.id)
                                    .put("title", seed.title)
                                    .put("shopify_product_id", seed.shopifyProductId)
                                    .put("shopify_variant_id", seed.variantId)
                                    .put("handle", seed.handle)
                                    .put("image_url", seed.imageUrl)
                                    .put("version_label", seed.versionLabel)
                                    .put("product_key", seed.productKey)
                                    .put("sizes", JSONArray(seed.sizes))
                                    .put(
                                        "views",
                                        JSONArray(
                                            seed.views.map { (view, url) ->
                                                JSONObject().put("view", view).put("url", url)
                                            }
                                        )
                                    )
                            )
                        }
                        val rosterJson = roster.toPayload()
                        val payload = JSONObject()
                            .put("title", title)
                            .put("description", description)
                            .put("emails", emails)
                            .put("email_subject", subject)
                            .put("email_body", body)
                            .put("ask_product", askProduct)
                            .put("ask_size", askSize)
                            .put("ask_name", askName)
                            .put("ask_number", askNumber)
                            .put("ask_quantity", askQty)
                            .put("ask_photo", askPhoto)
                            .put("payment_mode", "captain")
                            .put("products", products)
                            .put("member_count", rosterJson.optInt("member_count"))
                            .put("individual", rosterJson.optJSONObject("individual"))
                            .put("global", rosterJson.optJSONObject("global"))
                            .put("rows", rosterJson.optJSONArray("rows"))
                        val res = withContext(Dispatchers.IO) { api.askTeamCreate(payload) }
                        if (res.optBoolean("ok")) {
                            shareUrl = res.optString("share_url")
                            if (emails.isNotBlank()) {
                                withContext(Dispatchers.IO) { api.askTeamInvite(res.optString("campaign_id")) }
                            }
                        } else {
                            error = res.optString("error").ifBlank { t("eaz.ask_team.create_error", "Could not create Ask Team.") }
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(t("eaz.ask_team.create", "Create Ask Team"))
                }
            } else {
                Text(t("eaz.ask_team.share_title", "Share with your team"))
                Text(shareUrl.orEmpty())
                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                    })
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(t("eaz.ask_team.share", "Share"))
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}
