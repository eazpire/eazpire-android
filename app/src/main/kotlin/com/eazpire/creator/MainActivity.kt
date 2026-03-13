package com.eazpire.creator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.ui.AuthScreen
import com.eazpire.creator.update.UpdateChecker
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
                    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
                    val scope = rememberCoroutineScope()
                    val ctx = LocalContext.current
                    fun runUpdateCheck() {
                        scope.launch {
                            val info = UpdateChecker.checkForUpdate(BuildConfig.VERSION_CODE)
                            updateInfo = info
                            if (info == null) {
                                Toast.makeText(ctx, "Keine Updates verfügbar", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    LaunchedEffect(Unit) { runUpdateCheck() }
                    if (isLoggedIn) {
                        CreatorScreen(
                            tokenStore = tokenStore,
                            onLogout = {
                                tokenStore.clear()
                                isLoggedIn = false
                            },
                            onCheckUpdate = { runUpdateCheck() }
                        )
                    } else {
                        AuthScreen(
                            tokenStore = tokenStore,
                            onAuthSuccess = { isLoggedIn = true },
                            onCheckUpdate = { runUpdateCheck() }
                        )
                    }
                    updateInfo?.let { info ->
                        val ctx = LocalContext.current
                        AlertDialog(
                            onDismissRequest = { updateInfo = null },
                            title = { Text("Update verfügbar") },
                            text = { Text("${info.releaseName} ist verfügbar. Jetzt herunterladen?") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
                                        updateInfo = null
                                    }
                                ) {
                                    Text("Herunterladen")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { updateInfo = null }) {
                                    Text("Später")
                                }
                            }
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
    onLogout: () -> Unit,
    onCheckUpdate: (() -> Unit)? = null
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
        onCheckUpdate?.let { check ->
            Button(onClick = check, modifier = Modifier.padding(4.dp)) {
                Text("Nach Updates suchen")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
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
