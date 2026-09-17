package com.eazpire.shared.switcher

/**
 * Routing helpers for the IDEA-093 dual Play-app split.
 *
 * Creator (`com.eazpire.creator`) must not host shop catalog/cart/checkout/favorites
 * as in-app destinations. Those paths belong to Shop (`com.eazpire.shop`).
 */
object AppSwitchRouting {

    /**
     * True when a storefront URL path should open the Shop app (or Play Store),
     * not Creator tools. Creator dashboard/generator pages stay in Creator.
     */
    fun isShopStorefrontPath(path: String): Boolean {
        val raw = path.substringBefore('#').substringBefore('?').trim()
        val withSlash = if (raw.startsWith("/")) raw else "/$raw"
        val normalized = if (withSlash.length > 1) withSlash.trimEnd('/') else withSlash.ifBlank { "/" }
        if (
            normalized.startsWith("/pages/creator-dashboard") ||
            normalized.startsWith("/pages/design-generator")
        ) {
            return false
        }
        return normalized == "/" ||
            normalized == "/cart" ||
            normalized.startsWith("/cart/") ||
            normalized == "/search" ||
            normalized.startsWith("/search/") ||
            normalized.startsWith("/products/") ||
            normalized.startsWith("/collections/") ||
            normalized == "/creator" ||
            normalized.startsWith("/creator/") ||
            normalized.startsWith("/pages/ask-team") ||
            normalized.startsWith("/pages/thankyou") ||
            normalized.startsWith("/q/thankyou")
    }

    /** Logged-in switch uses the auth-handoff deep link; guests launch the sibling activity. */
    fun usesAuthHandoff(exchangeToken: String?): Boolean =
        !exchangeToken.isNullOrBlank()
}
