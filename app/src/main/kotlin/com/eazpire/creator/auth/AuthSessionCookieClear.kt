package com.eazpire.creator.auth

import android.webkit.CookieManager
import com.eazpire.creator.debug.AuthDebugLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Clears Shopify cookies in the WebView jar only (not Chrome Custom Tab cookies).
 * Google login clears the Shopify browser session via logout URL — see [ShopifyAuthService.buildBrowserLoginUrl].
 */
object AuthSessionCookieClear {

    private val SHOPIFY_AUTH_ORIGINS = listOf(
        "https://shopify.com",
        "https://account.eazpire.com",
        "https://www.eazpire.com",
    )

    suspend fun clearShopifyAuthCookies() = suspendCancellableCoroutine { cont ->
        try {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            for (origin in SHOPIFY_AUTH_ORIGINS) {
                clearCookiesForOrigin(cm, origin)
                clearCookiesForOrigin(cm, "$origin/authentication/")
            }
            cm.flush()
            AuthDebugLog.d("[AUTH COOKIES] cleared Shopify auth origins")
            cont.resume(Unit)
        } catch (e: Exception) {
            AuthDebugLog.e("[AUTH COOKIES] clear failed (non-fatal)", e)
            cont.resume(Unit)
        }
    }

    private fun clearCookiesForOrigin(cm: CookieManager, origin: String) {
        val cookies = cm.getCookie(origin) ?: return
        for (part in cookies.split(";")) {
            val name = part.trim().substringBefore("=").trim()
            if (name.isEmpty()) continue
            cm.setCookie(
                origin,
                "$name=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT",
            )
        }
    }
}
