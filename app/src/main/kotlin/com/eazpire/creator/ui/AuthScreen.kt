package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.auth.AuthException
import com.eazpire.creator.auth.AuthLoginMethod
import com.eazpire.creator.auth.OAuthPkceStore
import com.eazpire.creator.auth.PkceUtils
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.auth.ShopifyAuthService
import com.eazpire.creator.cart.StorefrontCartStore
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
 * - **Google** → Chrome Custom Tab (Google blocks embedded WebView).
 * - **Shop / Email** → in-app WebView.
 * OAuth on account.eazpire.com — Custom Tabs must send Accept: text/html (not */*).
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
    var awaitingOAuthCallback by remember { mutableStateOf(false) }
    var callbackHandled by remember { mutableStateOf(false) }
    var oauthWebViewLoadDone by remember(oauthWebViewUrl) { mutableStateOf(false) }
    var emailInput by remember(loginMethod) { mutableStateOf("") }
    var showEmailStep by remember(loginMethod) {
        mutableStateOf(loginMethod == AuthLoginMethod.EMAIL)
    }

    fun isShopCallbackUri(uri: Uri?): Boolean {
        val sch = uri?.scheme ?: return false
        return sch.startsWith("shop.") && uri.host == "callback"
    }

    fun isGoogleOAuthUri(uri: Uri): Boolean {
        val host = uri.host?.lowercase().orEmpty()
        return host.contains("accounts.google.com") ||
            (host.contains("google.com") && uri.path?.contains("oauth", ignoreCase = true) == true)
    }

    fun isShopAppUri(uri: Uri): Boolean {
        val host = uri.host?.lowercase().orEmpty()
        return host == "shop.app" || host.endsWith(".shop.app") || uri.scheme == "intent"
    }

    suspend fun clearCookiesForLogin() = suspendCancellableCoroutine { cont ->
        try {
            CookieManager.getInstance().removeAllCookies { cont.resume(Unit) }
        } catch (_: Exception) {
            cont.resume(Unit)
        }
    }

    fun oauthRequestHeaders(): Map<String, String> =
        mapOf("Accept" to AuthConfig.SHOPIFY_HTML_ACCEPT)

    fun launchOAuthCustomTab(url: String) {
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

    fun handleCallback(url: String) {
        if (callbackHandled) return
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        if (code == null || state == null) return
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
                WearAuthSync.push(context, tokenStore)
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

    fun startLogin(emailHint: String? = null) {
        scope.launch {
            isLoading = true
            error = null
            callbackHandled = false
            awaitingOAuthCallback = false
            showEmailStep = false
            try {
                clearCookiesForLogin()
                val endpoints = authService.discoverEndpoints()
                val verifier = PkceUtils.generateCodeVerifier()
                val state = PkceUtils.generateState()
                codeVerifier = verifier
                savedState = state
                OAuthPkceStore.save(appCtx, state, verifier)
                val hint = emailHint?.trim()?.takeIf { it.isNotBlank() }
                    ?: emailInput.trim().takeIf { it.isNotBlank() }
                val url = authService.buildAuthorizationUrl(
                    endpoints.authorizationEndpoint,
                    verifier,
                    state,
                    loginHint = if (loginMethod == AuthLoginMethod.EMAIL) hint else null
                )
                when (loginMethod) {
                    AuthLoginMethod.GOOGLE -> launchOAuthCustomTab(url)
                    AuthLoginMethod.SHOP, AuthLoginMethod.EMAIL -> {
                        oauthWebViewUrl = url
                        webViewProgress = 0
                    }
                }
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

    LaunchedEffect(autoStartOAuth, loginMethod) {
        if (!autoStartOAuth) return@LaunchedEffect
        onAutoStartConsumed()
        when (loginMethod) {
            AuthLoginMethod.EMAIL -> showEmailStep = true
            AuthLoginMethod.GOOGLE, AuthLoginMethod.SHOP -> startLogin()
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
                                settings.databaseEnabled = true
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
                                        if (isShopCallbackUri(u)) {
                                            view?.stopLoading()
                                            handleCallback(u.toString())
                                            return true
                                        }
                                        if (loginMethod != AuthLoginMethod.GOOGLE && isGoogleOAuthUri(u)) {
                                            launchOAuthCustomTab(u.toString())
                                            return true
                                        }
                                        if (loginMethod == AuthLoginMethod.SHOP && isShopAppUri(u)) {
                                            try {
                                                val intent = if (u.scheme == "intent") {
                                                    Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME)
                                                } else {
                                                    Intent(Intent.ACTION_VIEW, u)
                                                }
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                ctx.startActivity(intent)
                                            } catch (_: Exception) {
                                                return false
                                            }
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
                                        favicon: android.graphics.Bitmap?
                                    ) {
                                        url ?: return
                                        try {
                                            val u = Uri.parse(url)
                                            if (isShopCallbackUri(u)) {
                                                view?.stopLoading()
                                                handleCallback(url)
                                            }
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            }
                        },
                        update = { wv ->
                            val target = oauthWebViewUrl
                            if (target != null && !oauthWebViewLoadDone && !callbackHandled) {
                                wv.loadUrl(target, oauthRequestHeaders())
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
                    Text("Cancel")
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
                        AuthLoginMethod.SHOP -> "Sign in with the Shop app"
                        AuthLoginMethod.GOOGLE -> "Sign in with Google"
                        AuthLoginMethod.EMAIL -> "Sign in with email"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(24.dp))
                when {
                    isLoading -> CircularProgressIndicator()
                    showEmailStep && loginMethod == AuthLoginMethod.EMAIL -> {
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { startLogin(emailInput.trim()) },
                            enabled = emailInput.trim().contains("@"),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Continue")
                        }
                    }
                    awaitingOAuthCallback -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Complete sign-in in the browser tab, then return to the app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EazColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { startLogin() }) {
                            Text("Open sign-in again")
                        }
                    }
                    else -> {
                        Button(onClick = { startLogin() }) {
                            Text(if (error != null) "Try again" else "Sign in")
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
                        Text("Check for updates")
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(
                    onClick = {
                        awaitingOAuthCallback = false
                        oauthWebViewUrl = null
                        OAuthPkceStore.clear(appCtx)
                        onDismiss()
                    }
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
