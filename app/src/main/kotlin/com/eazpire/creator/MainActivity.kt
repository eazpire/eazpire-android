package com.eazpire.creator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.debug.initDebugLog
import com.eazpire.creator.debug.initLangSwitchDebug
import com.eazpire.creator.ui.ShopScreen

class MainActivity : ComponentActivity() {
    val pendingDeepLink = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initDebugLog(this)
        initLangSwitchDebug(this)
        val tokenStore = SecureTokenStore(this)
        pendingDeepLink.value = intent?.data
        setContent {
            EazpireCreatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 0.dp
                ) {
                    ShopScreen(tokenStore = tokenStore, pendingDeepLink = pendingDeepLink)
                }
            }
        }
        handleOAuthCallback(intent, tokenStore)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { pendingDeepLink.value = it }
        handleOAuthCallback(intent, SecureTokenStore(this))
    }

    private fun handleOAuthCallback(intent: Intent?, tokenStore: SecureTokenStore) {
        val data = intent?.data ?: return
        if (data.scheme?.startsWith("shop.") == true && data.host == "callback") {
            // OAuth-Callback via Deep Link (falls CustomTabs statt WebView genutzt wird)
            val code = data.getQueryParameter("code")
            val state = data.getQueryParameter("state")
            if (code != null && state != null) {
                // State/Verifier müssten hier aus einem temporären Store kommen
            }
        }
    }
}
