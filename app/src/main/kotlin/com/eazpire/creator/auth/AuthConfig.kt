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
    /** Shop für get-product-image (Storefront API) – Produkte sind auf www.eazpire.com */
    const val STOREFRONT_SHOP = "eazpire.myshopify.com"
    const val REDIRECT_URI = "shop.73952035098.eazpire://callback"
    /** `offline_access` liefert refresh_token für lange Sessions (OAuth-Refresh ohne erneuten Login). */
    const val SCOPE = "openid email offline_access customer-account-api:full"

    /**
     * Customer Account API Client ID.
     * MUSS aus Shopify Admin → Sales channels → Headless/Hydrogen →
     * Customer Account API settings → Credentials kommen.
     * NICHT die Admin-App Client ID verwenden – das sind getrennte Credentials!
     */
    const val CLIENT_ID = "82087087-a2cc-40a8-91ff-70e29ce275dd"

    const val CREATOR_ENGINE_URL = "https://creator-engine.eazpire.workers.dev"
}
