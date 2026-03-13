package com.eazpire.creator.auth

/**
 * Konfiguration für Shopify Customer Account API OAuth.
 *
 * WICHTIG: In Shopify Admin → Settings → Customer accounts → Customer Account API
 * einen "Public mobile client" mit Callback-URL registrieren:
 *   shop.allyoucanpink.eazpire://callback
 *
 * Die Client ID des Mobile Clients ggf. hier eintragen (falls anders als Admin-App).
 */
object AuthConfig {
    const val SHOP_DOMAIN = "allyoucanpink.myshopify.com"
    const val REDIRECT_URI = "shop.allyoucanpink.eazpire://callback"
    const val SCOPE = "openid email customer-account-api:full"

    /**
     * Customer Account API Client ID.
     * Aus Shopify Admin beim Registrieren des Mobile Clients.
     * Fallback: Admin-App Client ID (kann funktionieren, wenn dieselbe App).
     */
    const val CLIENT_ID = "59af775fc259aa93c1deca6124fee2a6"

    const val CREATOR_ENGINE_URL = "https://creator-engine.eazpire.workers.dev"
}
