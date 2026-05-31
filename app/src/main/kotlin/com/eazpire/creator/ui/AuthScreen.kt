package com.eazpire.creator.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabsIntent
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
import com.eazpire.creator.auth.AuthConfig
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
 * Shopify Customer Account OAuth (PKCE).
 * - account.eazpire.com login in WebView with browser Accept header (Custom Tab sends generic Accept → HTTP 406 blank page).
 * - Google OAuth in Chrome Custom Tab when WebView navigates to accounts.google.com.
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
    var oauthWebViewUrl by remember { mutableStateOf<String?>(null) }
    var webViewProgress by remember { mutableStateOf(0) }
    var callbackHandled by remember { mutableStateOf(false) }
    var oauthWebViewLoadDone by remember(oauthWebViewUrl) { mutableStateOf(false) }
    var loginAttemptId by remember { mutableStateOf(0) }
    var lastAuthUrl by remember { mutableStateOf<String?>(null) }
    var awaitingOAuthCallback by remember { mutableStateOf(false) }

    LaunchedEffect(loginMethod) {
        AuthDebugLog.d("[AUTHSCREEN] loginMethod=$loginMethod webViewForAccount=true")
    }

    fun openShopifyOAuthInWebView(url: String) {
        oauthWebViewUrl = url
        webViewProgress = 0
        oauthWebViewLoadDone = false
        awaitingOAuthCallback = false
    }

    fun isGoogleOAuthUri(uri: Uri): Boolean {
        val host = uri.host?.lowercase().orEmpty()
        return host.contains("accounts.google.com") ||
            (host.contains("google.com") && uri.path?.contains("oauth", ignoreCase = true) == true)
    }

    fun launchOAuthCustomTab(url: String) {
        AuthDebugLog.d("[CUSTOM TAB] launch url=$url attempt=$loginAttemptId")
        oauthWebViewUrl = null
        val tabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        tabsIntent.intent.putExtra(
            Browser.EXTRA_HEADERS,
            Bundle().apply { putString("Accept", AuthConfig.SHOPIFY_HTML_ACCEPT) }
        )
        tabsIntent.launchUrl(context, Uri.parse(url))
        awaitingOAuthCallback = true
    }

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
        AuthDebugLog.d("[CALLBACK] Received callback url=$url")
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (code == null || state == null) {
            AuthDebugLog.e("[CALLBACK] Missing code or state in url=$url")
            return
        }
        callbackHandled = true
        oauthWebViewUrl = null
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
            loginAttemptId += 1
            val attempt = loginAttemptId
            AuthDebugLog.d("[LOGIN#$attempt] START method=$loginMethod")
            try {
                clearCookiesForLogin()
                val endpoints = authService.discoverEndpoints()
                AuthDebugLog.d("[LOGIN#$attempt] DISCOVERY authorizationEndpoint=${endpoints.authorizationEndpoint} tokenEndpoint=${endpoints.tokenEndpoint}")
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
                lastAuthUrl = url
                AuthDebugLog.d("[LOGIN#$attempt] AUTH_URL $url")
                openShopifyOAuthInWebView(url)
            } catch (e: Exception) {
                error = e.message ?: "Unknown error"
                AuthDebugLog.e("[LOGIN#$attempt] Failed: ${e.message}", e)
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
            Column(modifier = Modifier.fillMaxSize()) {
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
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.cacheMode = WebSettings.LOAD_DEFAULT
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                val def = WebSettings.getDefaultUserAgent(ctx)
                                settings.userAgentString =
                                    def.replace("; wv", "") + " Chrome/120.0.0.0 Mobile Safari/537.36"
                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        view?.post { webViewProgress = newProgress }
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    private fun handleNavigation(view: WebView?, u: Uri): Boolean {
                                        AuthDebugLog.d("[WEBVIEW NAV] attempt=$loginAttemptId url=$u")
                                        if (isShopCallbackUri(u)) {
                                            view?.stopLoading()
                                            handleCallback(u.toString())
                                            return true
                                        }
                                        if (isGoogleOAuthUri(u)) {
                                            view?.stopLoading()
                                            launchOAuthCustomTab(u.toString())
                                            return true
                                        }
                                        return false
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val u = request?.url ?: return false
                                        return handleNavigation(view, u)
                                    }

                                    @Deprecated("Deprecated in Java")
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        val u = url?.let { Uri.parse(it) } ?: return false
                                        return handleNavigation(view, u)
                                    }

                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: Bitmap?
                                    ) {
                                        url ?: return
                                        try {
                                            val u = Uri.parse(url)
                                            if (isShopCallbackUri(u)) {
                                                view?.stopLoading()
                                                handleCallback(url)
                                                return
                                            }
                                            if (isGoogleOAuthUri(u)) {
                                                view?.stopLoading()
                                                launchOAuthCustomTab(url)
                                            }
                                        } catch (_: Exception) {
                                        }
                                    }

                                    override fun onReceivedHttpError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        errorResponse: WebResourceResponse?
                                    ) {
                                        super.onReceivedHttpError(view, request, errorResponse)
                                        if (request?.isForMainFrame == true) {
                                            val status = errorResponse?.statusCode ?: 0
                                            AuthDebugLog.e("[WEBVIEW HTTP ERROR] status=$status url=${request.url}")
                                            if (status == 406) {
                                                error = "Login page blocked (HTTP 406). Please update the app."
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        update = { wv ->
                            val target = oauthWebViewUrl
                            if (target != null && !oauthWebViewLoadDone && !callbackHandled) {
                                AuthDebugLog.d("[WEBVIEW LOAD] attempt=$loginAttemptId target=$target")
                                wv.loadUrl(
                                    target,
                                    mapOf("Accept" to AuthConfig.SHOPIFY_HTML_ACCEPT),
                                )
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
                    text = when (loginMethod) {
                        AuthLoginMethod.SHOP -> "Shop app Login"
                        AuthLoginMethod.GOOGLE -> "Google Login"
                        AuthLoginMethod.EMAIL -> "Email Login"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (loginMethod) {
                        AuthLoginMethod.GOOGLE ->
                            "Choose Google on the Shopify screen; Google opens in your browser."
                        else -> "Mit deinem Shopify-Konto anmelden"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(32.dp))
                when {
                    isLoading && oauthWebViewUrl == null && !awaitingOAuthCallback -> {
                        CircularProgressIndicator()
                    }
                    awaitingOAuthCallback -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Complete sign-in in the browser tab, then return to the app.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { startLogin() }) {
                            Text("Open sign-in again")
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
                        oauthWebViewUrl = null
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
}
