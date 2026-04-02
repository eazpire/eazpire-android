package com.eazpire.creator.ui

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.auth.AuthException
import com.eazpire.creator.auth.OAuthPkceStore
import com.eazpire.creator.auth.PkceUtils
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.auth.ShopifyAuthService
import com.eazpire.creator.cart.StorefrontCartStore
import com.eazpire.creator.notifications.NotificationPreferencesRepository
import com.eazpire.creator.push.PushTokenRegistrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Shopify Customer Account OAuth (PKCE) — runs **inside an in-app WebView** so users see the
 * hosted login (Shop / Google / email) without Custom Tabs “open in browser / external app” prompts.
 * Cookies are cleared before starting so an existing session does not skip straight to callback.
 */
@Composable
fun AuthScreen(
    tokenStore: SecureTokenStore,
    onAuthSuccess: () -> Unit,
    onDismiss: () -> Unit = {},
    /** True after user picked Shop / Google / Email — opens OAuth WebView immediately. */
    autoStartOAuth: Boolean = false,
    onAutoStartConsumed: () -> Unit = {},
    onCheckUpdate: (() -> Unit)? = null,
    /** Deep link shop.*://callback when returning from external browser (legacy). */
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
    /** Non-null → show fullscreen WebView with this OAuth authorize URL. */
    var oauthWebViewUrl by remember { mutableStateOf<String?>(null) }
    var webViewProgress by remember { mutableStateOf(0) }
    var callbackHandled by remember { mutableStateOf(false) }
    /** Load OAuth URL once per [oauthWebViewUrl] — do not reload after user navigates inside WebView. */
    var oauthWebViewLoadDone by remember(oauthWebViewUrl) { mutableStateOf(false) }

    fun isShopCallbackUri(uri: Uri?): Boolean {
        val sch = uri?.scheme ?: return false
        return sch.startsWith("shop.") && uri.host == "callback"
    }

    suspend fun clearCookiesForLogin() = suspendCancellableCoroutine { cont ->
        try {
            CookieManager.getInstance().removeAllCookies { cont.resume(Unit) }
        } catch (_: Exception) {
            cont.resume(Unit)
        }
    }

    fun handleCallback(url: String) {
        if (callbackHandled) return
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (code == null || state == null) return
        callbackHandled = true
        oauthWebViewUrl = null
        val verifier = when {
            state == savedState && codeVerifier != null -> {
                OAuthPkceStore.clear(appCtx)
                codeVerifier!!
            }
            else -> OAuthPkceStore.consume(appCtx, state)
        } ?: run {
            error = "Invalid state"
            callbackHandled = false
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
                callbackHandled = false
                onAuthSuccess()
            } catch (e: AuthException) {
                callbackHandled = false
                error = e.message
            } catch (e: Exception) {
                callbackHandled = false
                error = e.message ?: "Token exchange failed"
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
                oauthWebViewUrl = url
                webViewProgress = 0
            } catch (e: Exception) {
                error = e.message ?: "Unknown error"
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

    if (oauthWebViewUrl != null) {
        Dialog(
            onDismissRequest = {
                oauthWebViewUrl = null
                callbackHandled = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
            ) {
                if (webViewProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = webViewProgress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setBackgroundColor(android.graphics.Color.WHITE)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                // Avoid *//* Accept-only issues on Shopify HTML; mimic Chrome mobile.
                                val def = WebSettings.getDefaultUserAgent(ctx)
                                settings.userAgentString = def.replace("; wv", "") + " Chrome/120.0.0.0 Mobile Safari/537.36"
                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        view?.post { webViewProgress = newProgress }
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val u = request?.url ?: return false
                                        if (isShopCallbackUri(u)) {
                                            view?.stopLoading()
                                            handleCallback(u.toString())
                                            return true
                                        }
                                        return false
                                    }

                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        val u = url?.let { Uri.parse(it) } ?: return false
                                        if (isShopCallbackUri(u)) {
                                            view?.stopLoading()
                                            handleCallback(u.toString())
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        url ?: return
                                        try {
                                            val u = Uri.parse(url)
                                            if (isShopCallbackUri(u)) {
                                                view?.stopLoading()
                                                handleCallback(url)
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        },
                        update = { wv ->
                            val target = oauthWebViewUrl
                            if (target != null && !oauthWebViewLoadDone && !callbackHandled) {
                                wv.loadUrl(target)
                                oauthWebViewLoadDone = true
                            }
                        }
                    )
                }
                TextButton(
                    onClick = {
                        oauthWebViewUrl = null
                        callbackHandled = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Text("Abbrechen")
                }
            }
        }
    } else {
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
            if (isLoading && oauthWebViewUrl == null) {
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
}
