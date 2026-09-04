package com.eazpire.shared.auth

import com.eazpire.shared.EazpireApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client for Worker ops `app-issue-exchange-token` / `app-complete-exchange` (IDEA-093).
 */
class AppExchangeClient(
    private val engineBaseUrl: String = DEFAULT_ENGINE_URL,
    private val http: OkHttpClient = defaultClient,
) {
    data class IssueResult(
        val exchangeToken: String,
        val ownerId: String,
    )

    data class CompleteResult(
        val jwt: String,
        val ownerId: String,
        val shopifyAccessToken: String? = null,
        val shopifyRefreshToken: String? = null,
        val shopifyExpiresAt: Long? = null,
    )

    suspend fun issueExchangeToken(
        bearerJwt: String,
        targetPackage: String,
        sourcePackage: String,
        shopifyAccessToken: String? = null,
        shopifyRefreshToken: String? = null,
        shopifyExpiresAt: Long? = null,
    ): IssueResult = withContext(Dispatchers.IO) {
        require(targetPackage == EazpireApps.SHOP || targetPackage == EazpireApps.CREATOR) {
            "invalid target_package"
        }
        val body = JSONObject()
            .put("target_package", targetPackage)
            .put("source_package", sourcePackage)
        if (!shopifyAccessToken.isNullOrBlank()) body.put("shopify_access_token", shopifyAccessToken)
        if (!shopifyRefreshToken.isNullOrBlank()) body.put("shopify_refresh_token", shopifyRefreshToken)
        if (shopifyExpiresAt != null && shopifyExpiresAt > 0L) body.put("shopify_expires_at", shopifyExpiresAt)

        val req = Request.Builder()
            .url("$engineBaseUrl/apps/creator-dispatch?op=app-issue-exchange-token")
            .header("Authorization", "Bearer ${bearerJwt.trim()}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(req).execute().use { res ->
            val json = JSONObject(res.body?.string().orEmpty().ifBlank { "{}" })
            if (!res.isSuccessful || !json.optBoolean("ok")) {
                throw AppExchangeException(json.optString("error", "issue_failed"), res.code)
            }
            IssueResult(
                exchangeToken = json.getString("exchange_token"),
                ownerId = json.getString("owner_id"),
            )
        }
    }

    suspend fun completeExchange(
        exchangeToken: String,
        targetPackage: String,
    ): CompleteResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("exchange_token", exchangeToken.trim())
            .put("target_package", targetPackage)
        val req = Request.Builder()
            .url("$engineBaseUrl/apps/creator-dispatch?op=app-complete-exchange")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(req).execute().use { res ->
            val json = JSONObject(res.body?.string().orEmpty().ifBlank { "{}" })
            if (!res.isSuccessful || !json.optBoolean("ok")) {
                throw AppExchangeException(json.optString("error", "complete_failed"), res.code)
            }
            CompleteResult(
                jwt = json.getString("jwt"),
                ownerId = json.getString("owner_id"),
                shopifyAccessToken = json.optString("shopify_access_token").takeIf { it.isNotBlank() },
                shopifyRefreshToken = json.optString("shopify_refresh_token").takeIf { it.isNotBlank() },
                shopifyExpiresAt = json.optLong("shopify_expires_at").takeIf { it > 0L },
            )
        }
    }

    companion object {
        const val DEFAULT_ENGINE_URL = "https://creator-engine.eazpire.workers.dev"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

class AppExchangeException(val error: String, val httpCode: Int) : Exception("app_exchange: $error ($httpCode)")
