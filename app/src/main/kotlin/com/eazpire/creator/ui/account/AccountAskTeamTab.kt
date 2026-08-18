package com.eazpire.creator.ui.account

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.ui.askteam.AskTeamRosterEditor
import com.eazpire.creator.ui.askteam.AskTeamRosterState
import com.eazpire.creator.ui.askteam.parseAskTeamProducts
import com.eazpire.creator.ui.askteam.rosterStateFromCampaign
import com.eazpire.creator.ui.askteam.toPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun AccountAskTeamTab(
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier,
) {
    val jwt = tokenStore.getJwt()
    val api = remember(jwt) { CreatorApi(jwt = jwt) }
    val tStore = LocalTranslationStore.current
    fun t(key: String, fallback: String) = tStore?.t(key, fallback) ?: fallback
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var campaigns by remember { mutableStateOf(listOf<JSONObject>()) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    var roster by remember { mutableStateOf(AskTeamRosterState()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            val res = withContext(Dispatchers.IO) { api.askTeamList() }
            val arr = res.optJSONArray("campaigns") ?: JSONArray()
            campaigns = (0 until arr.length()).map { arr.getJSONObject(it) }
            loading = false
        }
    }

    LaunchedEffect(jwt) { reload() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t("eaz.ask_team.title", "Ask Team"), style = MaterialTheme.typography.titleLarge)
        Text(
            t("eaz.ask_team.account_lead", "Open campaigns, see who answered, and add the team order to your cart."),
            color = EazColors.TextSecondary,
        )
        if (loading) CircularProgressIndicator()
        error?.let { Text(it, color = EazColors.Orange) }
        if (!loading && campaigns.isEmpty() && detail == null) {
            Text(t("eaz.ask_team.empty", "No Ask Team campaigns yet. Start one from a product page."))
        }
        campaigns.forEach { c ->
            val stats = c.optJSONObject("stats")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            val id = c.optString("id")
                            val res = withContext(Dispatchers.IO) { api.askTeamGet(id) }
                            val camp = res.optJSONObject("campaign")
                            detail = camp
                            if (camp != null) roster = rosterStateFromCampaign(camp)
                        }
                    }
                    .padding(vertical = 8.dp),
            ) {
                Text(c.optString("title"), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${stats?.optString("team_members").orEmpty()} · ${c.optString("status")}",
                    color = EazColors.TextSecondary,
                )
            }
        }
        detail?.let { camp ->
            val stats = camp.optJSONObject("stats")
            Text(camp.optString("title"), style = MaterialTheme.typography.titleMedium)
            Text(t("eaz.ask_team.team_members", "Team members") + ": " + stats?.optString("team_members").orEmpty())
            val votes = stats?.optJSONArray("product_votes") ?: JSONArray()
            Text(t("eaz.ask_team.product_votes", "Product choice"), style = MaterialTheme.typography.titleSmall)
            (0 until votes.length()).forEach { i ->
                val v = votes.getJSONObject(i)
                Text("${v.optString("label")} — ${v.optInt("votes")} ${t("eaz.ask_team.votes", "votes")}")
            }
            val sizes = stats?.optJSONArray("sizes") ?: JSONArray()
            Text(t("eaz.ask_team.team_details", "Team details"), style = MaterialTheme.typography.titleSmall)
            (0 until sizes.length()).forEach { i ->
                val s = sizes.getJSONObject(i)
                Text("${s.optString("size")} = ${s.optInt("count")}x")
            }
            AskTeamRosterEditor(
                products = parseAskTeamProducts(camp.optJSONArray("products")),
                state = roster,
                onState = { roster = it },
                t = ::t,
                showSave = true,
                onSave = {
                    scope.launch {
                        val payload = roster.toPayload().put("campaign_id", camp.optString("id"))
                        val res = withContext(Dispatchers.IO) { api.askTeamRosterSave(payload) }
                        if (res.optBoolean("ok")) {
                            val next = res.optJSONObject("campaign")
                            detail = next
                            if (next != null) roster = rosterStateFromCampaign(next)
                            error = null
                        } else {
                            error = res.optString("error").ifBlank { t("eaz.ask_team.roster_error", "Please fill the individual fields for each person.") }
                        }
                    }
                },
            )
            val responses = camp.optJSONArray("responses") ?: JSONArray()
            (0 until responses.length()).forEach { i ->
                AskTeamResponseEditor(
                    campaignId = camp.optString("id"),
                    response = responses.getJSONObject(i),
                    api = api,
                    t = ::t,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val url = camp.optString("share_url")
                    context.startActivity(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    })
                }) { Text(t("eaz.ask_team.share", "Share")) }
                OutlinedButton(onClick = {
                    scope.launch { withContext(Dispatchers.IO) { api.askTeamRemind(camp.optString("id")) } }
                }) { Text(t("eaz.ask_team.remind", "Remind missing")) }
                OutlinedButton(onClick = {
                    scope.launch { withContext(Dispatchers.IO) { api.askTeamGenerateMocks(camp.optString("id")) } }
                }) { Text(t("eaz.ask_team.on_you", "On-You Preview")) }
            }
            Button(onClick = {
                scope.launch {
                    val res = withContext(Dispatchers.IO) { api.askTeamAddToCart(camp.optString("id")) }
                    if (!res.optBoolean("ok")) {
                        error = res.optString("error").ifBlank { t("eaz.ask_team.no_lines", "No completed answers to add yet.") }
                        return@launch
                    }
                    val lines = res.optJSONArray("lines") ?: JSONArray()
                    val pairs = (0 until lines.length()).mapNotNull { i ->
                        val o = lines.getJSONObject(i)
                        val id = o.optLong("id")
                        if (id <= 0L) null else id to o.optInt("quantity", 1)
                    }
                    val cartApi = com.eazpire.creator.api.ShopifyStorefrontCartApi()
                    val store = com.eazpire.creator.cart.StorefrontCartStore(context)
                    withContext(Dispatchers.IO) {
                        val existing = store.cartId
                        if (existing != null) {
                            pairs.forEach { (vid, qty) -> cartApi.addLine(existing, vid, qty, null) }
                        } else {
                            val created = cartApi.createCart(pairs, null, null)
                            if (created.ok && created.cartId != null) store.cartId = created.cartId
                        }
                    }
                    error = null
                }
            }) { Text(t("eaz.ask_team.add_cart", "Add all to cart")) }
        }
    }
}

@Composable
private fun AskTeamResponseEditor(
    campaignId: String,
    response: JSONObject,
    api: CreatorApi,
    t: (String, String) -> String,
) {
    val scope = rememberCoroutineScope()
    var name by remember(response.optString("id")) { mutableStateOf(response.optString("jersey_name")) }
    var number by remember(response.optString("id")) { mutableStateOf(response.optString("jersey_number")) }
    var size by remember(response.optString("id")) { mutableStateOf(response.optString("size")) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(response.optString("respondent_name").ifBlank { t("eaz.ask_team.member", "Member") })
        OutlinedTextField(name, { name = it }, label = { Text(t("eaz.ask_team.name", "Name")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(number, { number = it }, label = { Text(t("eaz.ask_team.number", "Number")) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(size, { size = it }, label = { Text(t("eaz.ask_team.size", "Size")) }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    api.askTeamUpdateResponse(
                        JSONObject()
                            .put("campaign_id", campaignId)
                            .put("response_id", response.optString("id"))
                            .put("jersey_name", name)
                            .put("jersey_number", number)
                            .put("size", size),
                    )
                }
            }
        }) { Text(t("eaz.ask_team.save", "Save")) }
    }
}
