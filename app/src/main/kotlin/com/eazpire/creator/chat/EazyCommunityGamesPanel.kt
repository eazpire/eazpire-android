package com.eazpire.creator.chat

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class CommunityGameItem(
    val slug: String,
    val title: String,
    val description: String,
    val bundleUrl: String,
)

@Composable
fun EazyCommunityGamesPanel(
    api: CreatorApi,
    ownerId: String?,
    shop: String,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    t: (String, String) -> String,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var games by remember { mutableStateOf<List<CommunityGameItem>>(emptyList()) }
    var playing by remember { mutableStateOf<Pair<String, String>?>(null) } // slug, sessionToken
    var bundleUrl by remember { mutableStateOf<String?>(null) }
    val palette = LocalEazyModalPalette.current

    val scope = rememberCoroutineScope()

    LaunchedEffect(ownerId, isLoggedIn) {
        if (!isLoggedIn || ownerId.isNullOrBlank()) {
            loading = false
            games = emptyList()
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            val j = withContext(Dispatchers.IO) { api.getCommunityGamesCatalog(shop, ownerId) }
            if (!j.optBoolean("ok", false)) {
                error = t("eazy_chat.games_community_error", "Could not load community games.")
                games = emptyList()
            } else {
                val arr = j.optJSONArray("games") ?: JSONArray()
                games = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        add(
                            CommunityGameItem(
                                slug = o.optString("slug"),
                                title = o.optString("title", o.optString("slug")),
                                description = o.optString("description", ""),
                                bundleUrl = o.optString("bundle_url", ""),
                            ),
                        )
                    }
                }
            }
        } catch (_: Exception) {
            error = t("eazy_chat.games_community_error", "Could not load community games.")
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (!isLoggedIn || ownerId.isNullOrBlank()) {
            Text(
                t("eazy_chat.games_login", "Sign in to play the daily game."),
                color = palette.muted,
                modifier = Modifier.padding(16.dp),
            )
            TextButton(onClick = onLoginClick) { Text(t("eazy_chat.games_play", "Play")) }
            return@Column
        }

        if (playing != null && bundleUrl != null) {
            TextButton(onClick = {
                playing = null
                bundleUrl = null
            }) {
                Text(t("eazy_chat.games_community_back", "Back to list"))
            }
            CommunityGameWebView(
                bundleUrl = bundleUrl!!,
                sessionToken = playing!!.second,
                gameSlug = playing!!.first,
            )
            return@Column
        }

        Text(
            t("eazy_chat.games_community_intro", "Games built by the eazpire community. No daily prizes — just play for fun."),
            fontSize = 13.sp,
            color = palette.muted,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        when {
            loading -> {
                CircularProgressIndicator(color = palette.accent, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            error != null -> {
                Text(error!!, color = palette.muted, modifier = Modifier.padding(16.dp))
            }
            games.isEmpty() -> {
                Text(
                    t("eazy_chat.games_community_empty", "No community games yet."),
                    color = palette.muted,
                    modifier = Modifier.padding(16.dp),
                )
            }
            else -> {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    games.forEach { game ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.assistantBubble.copy(alpha = 0.35f))
                                .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Text(
                                t("eazy_chat.games_community_badge", "Community"),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.accent,
                            )
                            Text(game.title, fontWeight = FontWeight.SemiBold, color = palette.text)
                            if (game.description.isNotBlank()) {
                                Text(game.description, fontSize = 12.sp, color = palette.muted, maxLines = 3)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                t("eazy_chat.games_community_play", "Play"),
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.accent)
                                    .clickable {
                                        scope.launch {
                                            try {
                                                val start = withContext(Dispatchers.IO) {
                                                    api.startCommunityGameSession(shop, ownerId!!, game.slug)
                                                }
                                                if (start.optBoolean("ok", false)) {
                                                    playing = game.slug to start.optString("session_token")
                                                    bundleUrl = start.optString("bundle_url", game.bundleUrl)
                                                } else {
                                                    error = t("eazy_chat.games_community_error", "Could not load community games.")
                                                }
                                            } catch (_: Exception) {
                                                error = t("eazy_chat.games_community_error", "Could not load community games.")
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CommunityGameWebView(bundleUrl: String, sessionToken: String, gameSlug: String) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(12.dp)),
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val js =
                            """
                            (function(){
                              window.dispatchEvent(new MessageEvent('message', {
                                data: { type: 'eazy:init', payload: {
                                  sessionToken: ${JSONObject.quote(sessionToken)},
                                  gameSlug: ${JSONObject.quote(gameSlug)},
                                  locale: 'en',
                                  theme: 'dark'
                                }}
                              }));
                            })();
                            """.trimIndent()
                        view?.evaluateJavascript(js, null)
                    }
                }
                loadUrl(bundleUrl)
            }
        },
    )
}
