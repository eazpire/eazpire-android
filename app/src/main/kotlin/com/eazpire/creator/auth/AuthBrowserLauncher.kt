package com.eazpire.creator.auth

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import androidx.browser.customtabs.CustomTabsIntent
import com.eazpire.creator.debug.AuthDebugLog

/**
 * Opens Shopify Customer Account OAuth in an **ephemeral** Custom Tab when Chrome supports it,
 * so the normal browser profile cookies (Google / Shopify SSO) are not reused.
 *
 * Uses the intent extra from androidx.browser 1.9+ without requiring compileSdk 36.
 */
object AuthBrowserLauncher {

    /** @see androidx.browser.customtabs.CustomTabsIntent.EXTRA_ENABLE_EPHEMERAL_BROWSING */
    private const val EXTRA_ENABLE_EPHEMERAL_BROWSING =
        "androidx.browser.customtabs.extra.ENABLE_EPHEMERAL_BROWSING"

    fun launchOAuth(context: Context, url: String) {
        AuthDebugLog.d("[CUSTOM TAB] ephemeral launch url=$url")
        val tabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        tabsIntent.intent.putExtra(EXTRA_ENABLE_EPHEMERAL_BROWSING, true)
        tabsIntent.intent.putExtra(
            Browser.EXTRA_HEADERS,
            Bundle().apply { putString("Accept", AuthConfig.SHOPIFY_HTML_ACCEPT) },
        )
        tabsIntent.launchUrl(context, Uri.parse(url))
    }
}
