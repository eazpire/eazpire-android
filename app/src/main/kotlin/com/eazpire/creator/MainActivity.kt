package com.eazpire.creator

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.ui.AuthScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenStore = SecureTokenStore(this)
        setContent {
            EazpireCreatorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var isLoggedIn by remember { mutableStateOf(tokenStore.isLoggedIn()) }
                    if (isLoggedIn) {
                        CreatorScreen(
                            tokenStore = tokenStore,
                            onLogout = {
                                tokenStore.clear()
                                isLoggedIn = false
                            }
                        )
                    } else {
                        AuthScreen(
                            tokenStore = tokenStore,
                            onAuthSuccess = { isLoggedIn = true }
                        )
                    }
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

@Composable
fun CreatorScreen(
    tokenStore: SecureTokenStore,
    onLogout: () -> Unit
) {
    val jwt = remember { tokenStore.getJwt() }
    val api = remember(jwt) { CreatorApi(baseUrl = AuthConfig.CREATOR_ENGINE_URL, jwt = jwt) }
    var balanceText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Eazpire Creator",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { api.getBalance() }
                        if (result.optBoolean("ok", false)) {
                            val bal = result.optDouble("balance_eaz", 0.0)
                            balanceText = "%.2f EAZ".format(bal)
                            error = null
                        } else {
                            error = result.optString("error", "Unbekannter Fehler")
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Fehler"
                    }
                }
            }
        ) {
            Text("Balance laden")
        }
        balanceText?.let { Text(it, modifier = Modifier.padding(8.dp)) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onLogout) {
            Text("Abmelden")
        }
    }
}
