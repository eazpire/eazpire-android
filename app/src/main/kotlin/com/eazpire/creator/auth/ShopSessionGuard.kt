package com.eazpire.creator.auth

import android.content.Context
import com.eazpire.creator.api.ShopifyCustomerAccountApi
import com.eazpire.creator.push.PushTokenRegistrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Hält App-Session und Shopify Customer Account OAuth synchron.
 * Lange Sessions: OAuth-Refresh (refresh_token) + JWT-Erneuerung über creator-engine.
 */
object ShopSessionGuard {

    /** Vor Ablauf erneuern, damit Storefront/Cart nie abgelaufenen access_token sieht. */
    private const val REFRESH_BUFFER_MS = 5L * 60L * 1000L

    /** Legacy ohne expires_at: einmalig Customer prüfen. */
    private const val LEGACY_VALIDATION_EXTEND_MS = 50L * 60L * 1000L

    /**
     * Synchron: nur ausloggen, wenn OAuth wirklich tot ist (abgelaufen **und** kein refresh_token).
     */
    fun shouldLogoutSync(tokenStore: SecureTokenStore): Boolean {
        if (tokenStore.getJwt().isNullOrBlank()) return false
        if (tokenStore.getAccessToken().isNullOrBlank()) return true
        val exp = tokenStore.getShopifyAccessExpiresAtEpochMs()
        if (exp <= 0L) return false
        val expired = System.currentTimeMillis() >= exp
        return expired && tokenStore.getRefreshToken().isNullOrBlank()
    }

    fun performFullLogout(context: Context, tokenStore: SecureTokenStore) {
        PushTokenRegistrar.unregisterBeforeLogout(context, tokenStore)
        tokenStore.clear()
        SecureTokenStore.clearAuthCookies()
    }

    /**
     * Erneuert Shopify access_token + App-JWT, wenn refresh_token vorhanden und Refresh fällig.
     * Bei [IOException] (Server/Netz): Session behalten.
     * Bei [AuthException]: Refresh abgelehnt → vollständiger Logout.
     */
    suspend fun refreshAccessTokenIfNeeded(context: Context, tokenStore: SecureTokenStore) =
        withContext(Dispatchers.IO) {
            if (tokenStore.getJwt().isNullOrBlank()) return@withContext
            val access = tokenStore.getAccessToken() ?: return@withContext
            if (access.isBlank()) return@withContext
            val refresh = tokenStore.getRefreshToken() ?: return@withContext
            val exp = tokenStore.getShopifyAccessExpiresAtEpochMs()
            val now = System.currentTimeMillis()
            val needRefresh =
                (exp <= 0L) ||
                    (exp > 0L && now >= exp - REFRESH_BUFFER_MS)
            if (!needRefresh) return@withContext

            val auth = ShopifyAuthService()
            try {
                val tr = auth.refreshAccessToken(refresh)
                val jwtResult = auth.exchangeShopifyTokenForJwt(
                    tr.accessToken,
                    tr.idToken.ifBlank { null }
                )
                val rt = tr.refreshToken?.takeIf { it.isNotBlank() } ?: refresh
                val newExp = System.currentTimeMillis() + tr.expiresInSeconds * 1000L
                tokenStore.saveTokens(
                    jwtResult.jwt,
                    jwtResult.ownerId,
                    tr.accessToken.ifBlank { null },
                    newExp,
                    refreshToken = rt,
                    clearRefreshTokenIfNull = false
                )
            } catch (_: IOException) {
                // Transient – nächster App-Start oder später erneut
            } catch (_: AuthException) {
                performFullLogout(context, tokenStore)
            }
        }

    /**
     * Nur noch nötig, wenn weder expires_at noch refresh_token (ältere Installs): Customer API ping.
     */
    suspend fun validateLegacyShopifySessionIfNeeded(context: Context, tokenStore: SecureTokenStore) =
        withContext(Dispatchers.IO) {
            if (tokenStore.getJwt().isNullOrBlank()) return@withContext
            val access = tokenStore.getAccessToken() ?: return@withContext
            if (access.isBlank()) return@withContext
            val exp = tokenStore.getShopifyAccessExpiresAtEpochMs()
            if (exp > 0L) return@withContext
            if (tokenStore.getRefreshToken() != null) return@withContext
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
                // Offline – nicht ausloggen
            }
        }
}
