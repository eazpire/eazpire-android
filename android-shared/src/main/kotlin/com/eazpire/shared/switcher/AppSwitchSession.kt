package com.eazpire.shared.switcher

import android.content.Context
import com.eazpire.shared.EazpireApps
import com.eazpire.shared.auth.AppExchangeClient

/**
 * Issues a short-lived exchange token (when logged in) then opens the sibling app
 * or Play Store (IDEA-093 Phase 4).
 */
object AppSwitchSession {

    data class SessionSnapshot(
        val jwt: String?,
        val sourcePackage: String,
        val shopifyAccessToken: String? = null,
        val shopifyRefreshToken: String? = null,
        val shopifyExpiresAt: Long? = null,
    )

    /**
     * Opens sibling with exchange token when [SessionSnapshot.jwt] is present.
     * On issue failure, still opens the sibling without a token (target can fall back to OAuth).
     */
    suspend fun openSiblingWithOptionalExchange(
        context: Context,
        target: EazpireApps.Target,
        session: SessionSnapshot,
        client: AppExchangeClient = AppExchangeClient(),
    ): AppSwitchHelper.Result {
        val jwt = session.jwt?.trim().orEmpty()
        val exchangeToken = if (jwt.isNotEmpty()) {
            runCatching {
                client.issueExchangeToken(
                    bearerJwt = jwt,
                    targetPackage = EazpireApps.packageId(target),
                    sourcePackage = session.sourcePackage,
                    shopifyAccessToken = session.shopifyAccessToken,
                    shopifyRefreshToken = session.shopifyRefreshToken,
                    shopifyExpiresAt = session.shopifyExpiresAt,
                ).exchangeToken
            }.getOrNull()
        } else {
            null
        }
        return AppSwitchHelper.openSiblingOrStore(context, target, exchangeToken)
    }
}
