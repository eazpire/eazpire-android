package com.eazpire.creator.ui

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.MutableState
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
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.auth.OAuthPkceStore
import com.eazpire.creator.cart.StorefrontCartStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.eazpire.creator.auth.AuthException
import com.eazpire.creator.auth.PkceUtils
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.auth.ShopifyAuthService
import com.eazpire.creator.notifications.NotificationPreferencesRepository
import com.eazpire.creator.push.PushTokenRegistrar
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    tokenStore: SecureTokenStore,
    onAuthSuccess: () -> Unit,
    onDismiss: () -> Unit = {},
    /** True when user chose „Mit Shopify fortfahren“ — opens OAuth tab immediately (no extra tap). */
    autoStartOAuth: Boolean = false,
    onAutoStartConsumed: () -> Unit = {},
    onCheckUpdate: (() -> Unit)? = null,
    /** Set when MainActivity receives shop.*://callback (e.g. after OAuth in Chrome Custom Tab) */
    oauthCallbackUri: MutableState<String?>? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val authService = remember { ShopifyAuthService() }
    val storefrontCartStore = remember { StorefrontCartStore(context) }
    val storefrontCartApi = remember { ShopifyStorefrontCartApi() }
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
                OAuthPkceStore.save(appCtx, state, verifier)
                val url = authService.buildAuthorizationUrl(
                    endpoints.authorizationEndpoint,
                    verifier,
                    state
                )
                // Full flow in Custom Tab — WebView + Custom Tab for Google broke Shopify OAuth state (t.eazpire.com "invalid state").
                // Prefer Chrome: Edge Custom Tabs can send Accept */* only → Shopify 406 → blank/black page (mitigate + Worker Accept patch).
                val tabs = CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .build()
                val chrome = "com.android.chrome"
                if (context.packageManager.getLaunchIntentForPackage(chrome) != null) {
                    tabs.intent.setPackage(chrome)
                }
                tabs.launchUrl(context, Uri.parse(url))
            } catch (e: Exception) {
                error = e.message ?: "Unknown error"
            } finally {
                isLoading = false
            }
        }
    }

    fun handleCallback(url: String) {
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (code == null || state == null) return
        val verifier = when {
            state == savedState && codeVerifier != null -> {
                OAuthPkceStore.clear(appCtx)
                codeVerifier!!
            }
            else -> OAuthPkceStore.consume(appCtx, state)
        } ?: run {
            error = "Invalid state"
            return
        }
        scope.launch {
            isLoading = true
            error = null
            try {
                val tokens = authService.exchangeCodeForTokens(code, verifier)
                val bearer =
                    tokens.accessToken.ifBlank { null } ?: tokens.idToken.ifBlank { null }
                        ?: throw AuthException("No access_token or id_token")
                val result = authService.exchangeShopifyTokenForJwt(bearer, tokens.idToken.ifBlank { null })
                val at = tokens.accessToken.ifBlank { null } ?: tokens.idToken.ifBlank { null }
                val shopifyExpiresAt =
                    System.currentTimeMillis() + tokens.expiresInSeconds * 1000L
                val rt = tokens.refreshToken?.takeIf { it.isNotBlank() }
                // sync=true: EncryptedSharedPreferences.apply() kann einen Frame verzögern → isLoggedIn() sonst false
                tokenStore.saveTokens(
                    result.jwt,
                    result.ownerId,
                    at,
                    shopifyExpiresAt,
                    refreshToken = rt,
                    clearRefreshTokenIfNull = rt == null,
                    sync = true
                )
                withContext(Dispatchers.IO) {
                    NotificationPreferencesRepository(context).syncFromServer(
                        CreatorApi(jwt = tokenStore.getJwt())
                    )
                }
                PushTokenRegistrar.syncIfLoggedIn(context)
                val cartId = storefrontCartStore.cartId
                if (cartId != null && tokens.accessToken.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        storefrontCartApi.updateBuyerIdentity(cartId, tokens.accessToken)
                    }
                }
                codeVerifier = null
                savedState = null
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

    LaunchedEffect(oauthCallbackUri?.value) {
        val holder = oauthCallbackUri ?: return@LaunchedEffect
        val url = holder.value ?: return@LaunchedEffect
        holder.value = null
        handleCallback(url)
    }

    LaunchedEffect(autoStartOAuth) {
        if (autoStartOAuth) {
            onAutoStartConsumed()
            startLogin()
        }
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
                    Text(if (error != null) "Erneut versuchen" else "Anmelden")
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
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    }
}
