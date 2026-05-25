package com.eazpire.creator.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.CreatorApi
import org.json.JSONArray

private enum class GamesHubSection { Play, Collection, Exchange }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EazyGamesHubPanel(
    api: CreatorApi,
    ownerId: String?,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onDismiss: () -> Unit,
    t: (String, String) -> String,
) {
    var section by remember { mutableStateOf(GamesHubSection.Play) }
    val scroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = section == GamesHubSection.Play,
                onClick = { section = GamesHubSection.Play },
                label = { Text(t("eazy_chat.games_section_play", "Play")) },
            )
            FilterChip(
                selected = section == GamesHubSection.Collection,
                onClick = { section = GamesHubSection.Collection },
                label = { Text(t("eazy_chat.games_section_collection", "Collection")) },
            )
            FilterChip(
                selected = section == GamesHubSection.Exchange,
                onClick = { section = GamesHubSection.Exchange },
                label = { Text(t("eazy_chat.games_section_exchange", "Exchange")) },
            )
        }

        when (section) {
            GamesHubSection.Play ->
                EazyDailyGamePanel(
                    api = api,
                    ownerId = ownerId,
                    isLoggedIn = isLoggedIn,
                    onLoginClick = onLoginClick,
                    onDismiss = onDismiss,
                    t = t,
                )
            GamesHubSection.Collection ->
                EazyGamesCollectionPanel(api = api, ownerId = ownerId, isLoggedIn = isLoggedIn, t = t)
            GamesHubSection.Exchange ->
                EazyGamesExchangePanel(api = api, ownerId = ownerId, isLoggedIn = isLoggedIn, t = t)
        }
    }
}

@Composable
private fun EazyGamesCollectionPanel(
    api: CreatorApi,
    ownerId: String?,
    isLoggedIn: Boolean,
    t: (String, String) -> String,
) {
    var items by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(ownerId, isLoggedIn) {
        if (!isLoggedIn || ownerId.isNullOrBlank()) {
            loading = false
            items = listOf(t("eazy_chat.games_login", "Sign in to play the daily game."))
            return@LaunchedEffect
        }
        loading = true
        try {
            val j = api.getPrizesInventoryList(ownerId)
            val arr = j.optJSONArray("items") ?: JSONArray()
            val lines = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                lines +=
                    "${o.optString("name")} (${o.optString("type")}) · ${o.optString("category")} · ${o.optString("rarity")}"
            }
            items =
                if (lines.isEmpty()) {
                    listOf(t("eazy_chat.prizes_collection_empty", "No prizes or cards yet."))
                } else {
                    lines
                }
        } catch (_: Exception) {
            items = listOf(t("eazy_chat.chat_error_unknown", "Something went wrong."))
        }
        loading = false
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = t("eazy_chat.games_motto", "Play Daily Games – Win Daily Prizes"),
            style = MaterialTheme.typography.titleSmall,
        )
        if (loading) {
            Text(t("eazy_chat.games_loading", "Loading…"))
        } else {
            items.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun EazyGamesExchangePanel(
    api: CreatorApi,
    ownerId: String?,
    isLoggedIn: Boolean,
    t: (String, String) -> String,
) {
    var tokenBalance by remember { mutableIntStateOf(0) }
    var listings by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(ownerId, isLoggedIn) {
        if (!isLoggedIn || ownerId.isNullOrBlank()) {
            loading = false
            listings = listOf(t("eazy_chat.games_login", "Sign in to play the daily game."))
            return@LaunchedEffect
        }
        loading = true
        try {
            val tok = api.getPrizesTradeTokens(ownerId)
            tokenBalance = tok.optInt("balance", 0)
            val j = api.getPrizesTradeListings()
            val arr = j.optJSONArray("listings") ?: JSONArray()
            val lines = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                lines += "#${o.optInt("id")} · seller ${o.optString("seller_id").take(8)}…"
            }
            listings =
                if (lines.isEmpty()) {
                    listOf(t("eazy_chat.exchange_no_listings", "No listings yet."))
                } else {
                    lines
                }
        } catch (_: Exception) {
            listings = listOf(t("eazy_chat.chat_error_unknown", "Something went wrong."))
        }
        loading = false
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${t("eazy_chat.exchange_token_balance", "Trade tokens")}: $tokenBalance",
            style = MaterialTheme.typography.titleSmall,
        )
        if (loading) {
            Text(t("eazy_chat.games_loading", "Loading…"))
        } else {
            listings.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
