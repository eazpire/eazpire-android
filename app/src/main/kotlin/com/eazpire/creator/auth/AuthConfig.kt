package com.eazpire.creator.auth

/**
 * Konfiguration für Shopify Customer Account API OAuth.
 * Sync: creator-worker → eazpire-android (GitHub Actions)
 *
 * WICHTIG: In Shopify Admin → Settings → Customer accounts → Customer Account API
 * einen "Public mobile client" mit Callback-URL registrieren:
 *   shop.73952035098.eazpire://callback (shop_id numerisch!)
 *
 * Die Client ID des Mobile Clients ggf. hier eintragen (falls anders als Admin-App).
 */
object AuthConfig {
    const val SHOP_DOMAIN = "allyoucanpink.myshopify.com"
    /** Numeric Shopify shop id (from redirect URI shop.{id}.eazpire). */
    const val SHOP_ID = "73952035098"
    /** Fallback if myshopify discovery fails. */
    const val OIDC_DISCOVERY_URL_FALLBACK =
        "https://shopify.com/authentication/$SHOP_ID/.well-known/openid-configuration"
    /** Shop für get-product-image (Storefront API) – Produkte sind auf www.eazpire.com */
    const val STOREFRONT_SHOP = "eazpire.myshopify.com"
    const val REDIRECT_URI = "shop.73952035098.eazpire://callback"
    /**
     * Chrome Custom Tabs send Accept */* for HTML navigations; Shopify may respond 406 (empty body).
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
}
