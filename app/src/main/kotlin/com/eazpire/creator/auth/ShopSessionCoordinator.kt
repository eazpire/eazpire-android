package com.eazpire.creator.auth

import android.content.Context
import com.eazpire.creator.debug.AuthDebugLog
import com.eazpire.creator.perf.EazPerfTrace
import com.eazpire.creator.push.PushTokenRegistrar
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single entry for session refresh — avoids duplicate network work on cold start
 * (MainActivity + ShopScreen + onResume all used to call ShopSessionGuard separately).
 */
object ShopSessionCoordinator {

    private val mutex = Mutex()

    @Volatile
    private var lastRefreshAtMs: Long = 0L

    /** Minimum gap between automatic refreshes (resume / compose). */
    private const val DEBOUNCE_MS = 60_000L

    suspend fun refreshSession(
        context: Context,
        tokenStore: SecureTokenStore,
        reason: String,
        force: Boolean = false,
    ) {
        if (tokenStore.getJwt().isNullOrBlank()) return
        val now = System.currentTimeMillis()
        mutex.withLock {
            if (!force && now - lastRefreshAtMs < DEBOUNCE_MS) {
                AuthDebugLog.d("[TOKEN] session skip debounce reason=$reason")
                return
            }
            lastRefreshAtMs = now
            EazPerfTrace.measureSectionSuspend("ShopSessionCoordinator.refresh") {
                ShopSessionGuard.refreshAccessTokenIfNeeded(context, tokenStore)
                ShopSessionGuard.validateLegacyShopifySessionIfNeeded(context, tokenStore)
            }
            AuthDebugLog.d("[TOKEN] session refreshed reason=$reason ${tokenStore.sessionDebugSummary()}")
        }
    }

    fun syncPushIfLoggedIn(context: Context) {
        PushTokenRegistrar.syncIfLoggedIn(context)
    }
}
