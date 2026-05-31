package com.eazpire.creator.auth

import android.net.Uri

/**
 * Konfiguration für Shopify Customer Account API OAuth.
 * Sync: creator-worker → eazpire-android (GitHub Actions)
 *
 * WICHTIG: In Shopify Admin → Settings → Customer accounts → Customer Account API
 * einen "Public mobile client" mit Callback-URL registrieren:
 *   shop.73952035098.eazpire://callback (shop_id numerisch — Pflichtformat)
 *
 * Die Client ID des Mobile Clients ggf. hier eintragen (falls anders als Admin-App).
 */
object AuthConfig {
    const val SHOP_DOMAIN = "allyoucanpink.myshopify.com"
    /** Numeric Shopify shop id (from redirect URI shop.{id}.eazpire). */
    const val SHOP_ID = "73952035098"
    /**
     * Prefer shopify.com discovery — myshopify discovery returns account.eazpire.com, which sits
     * behind Cloudflare bot checks and shows a blank "Verifying your connection" page in the app.
     */
    const val OIDC_DISCOVERY_URL =
        "https://shopify.com/authentication/$SHOP_ID/.well-known/openid-configuration"
    /** Shop für get-product-image (Storefront API) – Produkte sind auf www.eazpire.com */
    const val STOREFRONT_SHOP = "eazpire.myshopify.com"
    const val REDIRECT_URI = "shop.73952035098.eazpire://callback"
    /**
     * Chrome Custom Tabs send generic Accept (star-slash-star) for HTML navigations;
     * Shopify may respond 406 (empty body).
     * Must match worker patch in accountEazpireRedirectFix.js.
     */
    const val SHOPIFY_HTML_ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
    /**
     * Nur von Shopify unterstützte Scopes für Customer Account API (OAuth).
     * `offline_access` führt zu „ungültiger Scope“-Fehler und darf nicht verwendet werden.
     * Optional kann die Token-Antwort trotzdem ein refresh_token enthalten (Shopify-Verhalten prüfen).
     */
    const val SCOPE = "openid email customer-account-api:full"

    /**
     * Customer Account API Client ID.
     * MUSS aus Shopify Admin → Sales channels → Headless/Hydrogen →
     * Customer Account API settings → Credentials kommen.
     * NICHT die Admin-App Client ID verwenden – das sind getrennte Credentials!
     */
    const val CLIENT_ID = "82087087-a2cc-40a8-91ff-70e29ce275dd"

    const val CREATOR_ENGINE_URL = "https://creator-engine.eazpire.workers.dev"

    /** Rewrite custom account host → shopify.com (avoids Cloudflare blank page on account.*). */
    fun normalizeOAuthEndpoint(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return trimmed
        val prefix = "https://account.eazpire.com/authentication/"
        if (!trimmed.startsWith(prefix, ignoreCase = true)) return trimmed
        val rest = trimmed.substring(prefix.length).trimStart('/')
        return "https://shopify.com/authentication/$SHOP_ID/$rest"
    }

    fun rewriteAccountHostUri(uri: Uri): Uri? {
        if (uri.host?.equals("account.eazpire.com", ignoreCase = true) != true) return null
        val path = uri.path?.trimStart('/') ?: return null
        val rest = path.removePrefix("authentication/").trimStart('/')
        return uri.buildUpon()
            .scheme("https")
            .authority("shopify.com")
            .path("/authentication/$SHOP_ID/$rest")
            .build()
    }
}
