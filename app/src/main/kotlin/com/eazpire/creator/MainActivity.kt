package com.eazpire.creator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.debug.initDebugLog
import com.eazpire.creator.ui.ShopScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initDebugLog(this)
        val tokenStore = SecureTokenStore(this)
        setContent {
            EazpireCreatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    ShopScreen(tokenStore = tokenStore)
                }
            }
        }
        handleDeepLink(intent, tokenStore)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent, SecureTokenStore(this))
    }

    private fun handleDeepLink(intent: Intent?, tokenStore: SecureTokenStore) {
        val data = intent?.data ?: return
        if (data.scheme == "shop.allyoucanpink.eazpire" && data.host == "callback") {
            // OAuth-Callback via Deep Link (falls CustomTabs statt WebView genutzt wird)
            // WebView fängt den Redirect ab; dieser Fall ist für zukünftige CustomTabs-Nutzung
            val code = data.getQueryParameter("code")
            val state = data.getQueryParameter("state")
            if (code != null && state != null) {
                // State/Verifier müssten hier aus einem temporären Store kommen
                // Aktuell nutzen wir WebView, daher wird dieser Pfad nicht benötigt
            }
        }
    }
}
