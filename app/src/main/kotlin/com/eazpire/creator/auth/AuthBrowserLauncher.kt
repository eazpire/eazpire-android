package com.eazpire.creator.auth

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import androidx.browser.customtabs.CustomTabsIntent
import com.eazpire.creator.debug.AuthDebugLog

/**
 * Opens Shopify Customer Account OAuth in an ephemeral Chrome Custom Tab (no shared cookies
 * with normal Chrome). Device Google accounts remain selectable on the Google step.
 */
object AuthBrowserLauncher {

    /** androidx.browser 1.9+ — set on intent so 1.8.0 compileSdk stays valid. */
    private const val EXTRA_ENABLE_EPHEMERAL_BROWSING =
        "androidx.browser.customtabs.extra.ENABLE_EPHEMERAL_BROWSING"

    fun launchOAuth(context: Context, url: String) {
        AuthDebugLog.d("[CUSTOM TAB] launch ephemeral url=$url")
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
