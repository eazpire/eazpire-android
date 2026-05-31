package com.eazpire.creator.ui

import android.net.Uri
import android.webkit.CookieManager
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import com.eazpire.creator.auth.AuthLoginMethod
import com.eazpire.creator.auth.AuthException
import com.eazpire.creator.auth.OAuthPkceStore
import com.eazpire.creator.auth.PkceUtils
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.auth.ShopifyAuthService
import com.eazpire.creator.cart.StorefrontCartStore
import com.eazpire.creator.debug.AuthDebugLog
import com.eazpire.creator.notifications.NotificationPreferencesRepository
import com.eazpire.creator.push.PushTokenRegistrar
import com.eazpire.creator.wear.sync.WearAuthSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Shopify Customer Account OAuth (PKCE) via Chrome Custom Tab.
 * account.eazpire.com (myshopify discovery) — required for Google OAuth and reliable
 * shop.{id}.eazpire://callback deep links after email OTP.
 */
@Composable
fun AuthScreen(
    tokenStore: SecureTokenStore,
    onAuthSuccess: () -> Unit,
    onDismiss: () -> Unit = {},
    loginMethod: AuthLoginMethod = AuthLoginMethod.EMAIL,
    autoStartOAuth: Boolean = false,
    onAutoStartConsumed: () -> Unit = {},
    onCheckUpdate: (() -> Unit)? = null,
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
    var awaitingOAuthCallback by remember { mutableStateOf(false) }
    var callbackHandled by remember { mutableStateOf(false) }

    LaunchedEffect(loginMethod) {
        AuthDebugLog.d("[AUTHSCREEN] loginMethod=$loginMethod usesCustomTab=true")
    }

    suspend fun clearCookiesForLogin() = suspendCancellableCoroutine { cont ->
        try {
            CookieManager.getInstance().removeAllCookies { cont.resume(Unit) }
        } catch (_: Exception) {
            cont.resume(Unit)
        }
    }

    fun launchOAuthCustomTab(url: String) {
        AuthDebugLog.d("[CUSTOM TAB] launch url=$url")
        val tabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        tabsIntent.launchUrl(context, Uri.parse(url))
        awaitingOAuthCallback = true
    }

    fun handleCallback(url: String) {
        if (callbackHandled) return
        AuthDebugLog.d("[CALLBACK] Received callback url=$url")
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (code == null || state == null) {
            AuthDebugLog.e("[CALLBACK] Missing code or state in url=$url")
            return
        }
        callbackHandled = true
        awaitingOAuthCallback = false
        val verifier = when {
            state == savedState && codeVerifier != null -> {
                OAuthPkceStore.clear(appCtx)
                codeVerifier!!
            }
            else -> OAuthPkceStore.consume(appCtx, state)
        } ?: run {
            error = "Invalid state"
            callbackHandled = false
            AuthDebugLog.e("[CALLBACK] Invalid state saved=$savedState callback=$state")
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
                tokenStore.saveTokens(
                    result.jwt,
                    result.ownerId,
                    at,
                    shopifyExpiresAt,
                    refreshToken = rt,
                    clearRefreshTokenIfNull = false,
                    sync = true
                )
                AuthDebugLog.d("[CALLBACK] Login success; saved jwt ownerId=${result.ownerId}")
                withContext(Dispatchers.IO) {
                    runCatching {
                        NotificationPreferencesRepository(context).syncFromServer(
                            CreatorApi(jwt = tokenStore.getJwt())
                        )
                    }.onFailure { AuthDebugLog.e("[CALLBACK] syncFromServer failed (non-fatal)", it) }
                    runCatching { PushTokenRegistrar.syncIfLoggedIn(context) }
                        .onFailure { AuthDebugLog.e("[CALLBACK] push sync failed (non-fatal)", it) }
                    runCatching { WearAuthSync.push(context, tokenStore) }
                        .onFailure { AuthDebugLog.e("[CALLBACK] wear sync failed (non-fatal)", it) }
                    val cartId = storefrontCartStore.cartId
                    if (cartId != null && tokens.accessToken.isNotBlank()) {
                        runCatching {
                            storefrontCartApi.updateBuyerIdentity(cartId, tokens.accessToken)
                        }.onFailure { AuthDebugLog.e("[CALLBACK] cart buyer identity failed (non-fatal)", it) }
                    }
                }
                codeVerifier = null
                savedState = null
                callbackHandled = false
                onAuthSuccess()
            } catch (e: AuthException) {
                callbackHandled = false
                error = e.message
                AuthDebugLog.e("[CALLBACK] AuthException: ${e.message}", e)
            } catch (e: Exception) {
                callbackHandled = false
                error = e.message ?: "Token exchange failed"
                AuthDebugLog.e("[CALLBACK] Exception: ${e.message}", e)
            } finally {
                isLoading = false
            }
        }
    }

    fun startLogin() {
        scope.launch {
            isLoading = true
            error = null
            callbackHandled = false
            awaitingOAuthCallback = false
            try {
                clearCookiesForLogin()
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
                AuthDebugLog.d("[START LOGIN] Custom Tab OAuth url=$url method=$loginMethod")
                launchOAuthCustomTab(url)
            } catch (e: Exception) {
                error = e.message ?: "Unknown error"
                AuthDebugLog.e("[START LOGIN] Failed: ${e.message}", e)
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
                text = when (loginMethod) {
                    AuthLoginMethod.SHOP -> "Shop app Login"
                    AuthLoginMethod.GOOGLE -> "Google Login"
                    AuthLoginMethod.EMAIL -> "Email Login"
                },
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Mit deinem Shopify-Konto anmelden",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            when {
                isLoading -> CircularProgressIndicator()
                awaitingOAuthCallback -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Melde dich im Browser-Tab an und kehre dann zur App zurück.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { startLogin() }) {
                        Text("Anmeldung erneut öffnen")
                    }
                }
                else -> {
                    Button(onClick = { startLogin() }) {
                        Text(if (error != null) "Erneut versuchen" else "Anmelden")
                    }
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
            TextButton(
                onClick = {
                    awaitingOAuthCallback = false
                    OAuthPkceStore.clear(appCtx)
                    onDismiss()
                }
            ) {
                Text("Abbrechen")
            }
        }
    }
}
