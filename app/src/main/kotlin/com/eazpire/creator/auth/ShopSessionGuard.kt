package com.eazpire.creator.auth

import android.content.Context
import com.eazpire.creator.api.ShopifyCustomerAccountApi
import com.eazpire.creator.push.PushTokenRegistrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hält App-Session und Shopify Customer Account OAuth synchron:
 * JWT allein reicht nicht – der Shopify access_token läuft früher ab.
 */
object ShopSessionGuard {

    /** Nach erfolgreicher Legacy-Validierung: konservativer Puffer bis zum nächsten App-Start. */
    private const val LEGACY_VALIDATION_EXTEND_MS = 50L * 60L * 1000L

    /**
     * Synchron, ohne Netzwerk: abgelaufenes OAuth-Fenster oder inkonsistenter Zustand (JWT ohne Shopify-Token).
     * Vor [setContent] aufrufen, damit die UI nicht kurz „eingeloggt“ zeigt.
     */
    fun shouldLogoutSync(tokenStore: SecureTokenStore): Boolean {
        if (tokenStore.getJwt().isNullOrBlank()) return false
        if (tokenStore.getAccessToken().isNullOrBlank()) return true
        val exp = tokenStore.getShopifyAccessExpiresAtEpochMs()
        if (exp <= 0L) return false
        return System.currentTimeMillis() >= exp
    }

    fun performFullLogout(context: Context, tokenStore: SecureTokenStore) {
        PushTokenRegistrar.unregisterBeforeLogout(context, tokenStore)
        tokenStore.clear()
        SecureTokenStore.clearAuthCookies()
    }

    /**
     * Alte Installs ohne gespeichertes Ablaufdatum: einmalig Customer Account API prüfen.
     * Bei Netzwerkfehler: Session behalten (Offline).
     */
    suspend fun validateLegacyShopifySessionIfNeeded(context: Context, tokenStore: SecureTokenStore) =
        withContext(Dispatchers.IO) {
            if (tokenStore.getJwt().isNullOrBlank()) return@withContext
            val access = tokenStore.getAccessToken() ?: return@withContext
            if (access.isBlank()) return@withContext
            val exp = tokenStore.getShopifyAccessExpiresAtEpochMs()
            if (exp > 0L) return@withContext
            try {
                val api = ShopifyCustomerAccountApi(access)
                val customer = api.getCustomer()
                if (customer == null) {
                    performFullLogout(context, tokenStore)
                } else {
                    tokenStore.setShopifyAccessExpiresAtEpochMs(
                        System.currentTimeMillis() + LEGACY_VALIDATION_EXTEND_MS
                    )
                }
            } catch (_: Exception) {
                // Offline o.ä. – nicht ausloggen
            }
        }
}
