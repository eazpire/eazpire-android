package com.eazpire.creator.ui

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.cart.StorefrontCartStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.eazpire.creator.auth.AuthException
import com.eazpire.creator.auth.PkceUtils
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.auth.ShopifyAuthService
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    tokenStore: SecureTokenStore,
    onAuthSuccess: () -> Unit,
    onCheckUpdate: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val authService = remember { ShopifyAuthService() }
    val storefrontCartStore = remember { StorefrontCartStore(context) }
    val storefrontCartApi = remember { ShopifyStorefrontCartApi() }
    var showWebView by remember { mutableStateOf(false) }
    var authUrl by remember { mutableStateOf<String?>(null) }
    var codeVerifier by remember { mutableStateOf<String?>(null) }
    var savedState by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    fun startLogin() {
        scope.launch {
            isLoading = true
            error = null
            try {
                val endpoints = authService.discoverEndpoints()
                val verifier = PkceUtils.generateCodeVerifier()
                val state = PkceUtils.generateState()
                codeVerifier = verifier
                savedState = state
                authUrl = authService.buildAuthorizationUrl(
                    endpoints.authorizationEndpoint,
                    verifier,
                    state
                )
                showWebView = true
            } catch (e: Exception) {
                error = e.message ?: "Unknown error"
            } finally {
                isLoading = false
            }
        }
    }

    fun handleCallback(url: String) {
        val uri = android.net.Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (code == null || state == null) return
        if (state != savedState) {
            error = "Invalid state"
            return
        }
        val verifier = codeVerifier ?: return
        showWebView = false  // WebView schließen, Loading anzeigen
        scope.launch {
            isLoading = true
            error = null
            try {
                val tokens = authService.exchangeCodeForTokens(code, verifier)
                val result = authService.exchangeShopifyTokenForJwt(tokens.accessToken, tokens.idToken.ifBlank { null })
                tokenStore.saveTokens(result.jwt, result.ownerId, tokens.accessToken.ifBlank { null })
                // Link guest cart to customer for address prefill at checkout
                val cartId = storefrontCartStore.cartId
                if (cartId != null && tokens.accessToken.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        storefrontCartApi.updateBuyerIdentity(cartId, tokens.accessToken)
                    }
                }
                onAuthSuccess()
            } catch (e: AuthException) {
                error = e.message
            } catch (e: Exception) {
                error = e.message ?: "Token exchange failed"
            } finally {
                isLoading = false
            }
        }
    }

    if (showWebView && authUrl != null) {
        WebViewAuth(
            url = authUrl!!,
            redirectUri = AuthConfig.REDIRECT_URI,
            onRedirect = { handleCallback(it) },
            onDismiss = { showWebView = false }
        )
        return
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "eazpire",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Mit deinem Shopify-Konto anmelden",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(onClick = { startLogin() }) {
                    Text("Anmelden")
                }
            }
            error?.let { msg ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            onCheckUpdate?.let { check ->
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = check) {
                    Text("Nach Updates suchen")
                }
            }
        }
    }
}

@Composable
private fun WebViewAuth(
    url: String,
    redirectUri: String,
    onRedirect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val prefix = redirectUri.substringBefore("?")
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val reqUrl = request?.url?.toString() ?: return false
                        if (reqUrl.startsWith(prefix)) {
                            onRedirect(reqUrl)
                            return true
                        }
                        return false
                    }
                }
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    // Back-Button würde WebView schließen – Nutzer kann mit System-Back raus
}
