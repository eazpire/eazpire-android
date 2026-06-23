package com.eazpire.creator.auth

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.Browser
import androidx.browser.customtabs.CustomTabsIntent
import com.eazpire.creator.debug.AuthDebugLog

/**
 * Opens Shopify Customer Account OAuth in a normal Chrome Custom Tab so device Google
 * accounts and browser SSO cookies are available (account picker on the Google step).
 */
object AuthBrowserLauncher {

    fun launchOAuth(context: Context, url: String) {
        AuthDebugLog.d("[CUSTOM TAB] launch url=$url")
        val tabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        tabsIntent.intent.putExtra(
            Browser.EXTRA_HEADERS,
            Bundle().apply { putString("Accept", AuthConfig.SHOPIFY_HTML_ACCEPT) },
        )
        tabsIntent.launchUrl(context, Uri.parse(url))
    }
}
