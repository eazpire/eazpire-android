package com.eazpire.creator.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.ui.header.MainHeader

private const val SHOP_BASE_URL = "https://www.eazpire.com"

/**
 * Shop-Screen: WebView mit Shop, Sprache aus LocaleStore.
 * Sprachwechsel lädt Shop mit korrektem Locale-Prefix (/de/, /fr/, ...).
 */
@Composable
fun ShopScreen(
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val localeStore = remember { LocaleStore(context) }
    val languageCode by localeStore.languageCode.collectAsState(initial = localeStore.getLanguageCodeSync())
    var lastLoadedUrl by remember { mutableStateOf<String?>(null) }

    val shopUrl = remember(languageCode) {
        val lang = languageCode.lowercase().trim()
        if (lang.isBlank() || lang == "en") SHOP_BASE_URL
        else "$SHOP_BASE_URL/$lang/"
    }

    androidx.compose.material3.Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MainHeader(localeStore = localeStore)
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }
            },
            update = { webView ->
                if (lastLoadedUrl != shopUrl) {
                    lastLoadedUrl = shopUrl
                    webView.loadUrl(shopUrl)
                }
            }
        )
    }
}
