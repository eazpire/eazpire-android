package com.eazpire.creator.auth

import com.eazpire.creator.debug.AuthDebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Shopify Customer Account API OAuth 2.0 mit PKCE.
 * Discovery via myshopify → account.eazpire.com (stable flow; avoid shopify.com 406/Cloudflare).
 */
class ShopifyAuthService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class AuthEndpoints(val authorizationEndpoint: String, val tokenEndpoint: String)

    suspend fun discoverEndpoints(): AuthEndpoints = withContext(Dispatchers.IO) {
        val url = "https://${AuthConfig.SHOP_DOMAIN}/.well-known/openid-configuration"
        AuthDebugLog.d("[DISCOVERY] Requesting $url")
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        AuthDebugLog.d("[DISCOVERY] Response code=${response.code} successful=${response.isSuccessful}")
        if (!response.isSuccessful) {
            throw AuthException("Discovery failed: ${response.code}")
        }
        val body = response.body?.string() ?: throw AuthException("Empty discovery response")
        AuthDebugLog.d("[DISCOVERY] Body preview=${body.take(500)}")
        val json = JSONObject(body)
        val auth = json.optString("authorization_endpoint")
        val token = json.optString("token_endpoint")
        if (auth.isBlank() || token.isBlank()) {
            throw AuthException("Missing authorization_endpoint or token_endpoint")
        }
        AuthDebugLog.d("[DISCOVERY] Parsed authorization_endpoint=$auth token_endpoint=$token")
        AuthEndpoints(auth, token)
    }

    fun buildAuthorizationUrl(
        authorizationEndpoint: String,
        codeVerifier: String,
        state: String,
    ): String {
        val codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier)
        val built = buildString {
            append(authorizationEndpoint)
            append("?client_id=").append(java.net.URLEncoder.encode(AuthConfig.CLIENT_ID, "UTF-8"))
            append("&response_type=code")
            append("&redirect_uri=").append(java.net.URLEncoder.encode(AuthConfig.REDIRECT_URI, "UTF-8"))
            append("&scope=").append(java.net.URLEncoder.encode(AuthConfig.SCOPE, "UTF-8"))
            append("&state=").append(java.net.URLEncoder.encode(state, "UTF-8"))
            append("&code_challenge=").append(java.net.URLEncoder.encode(codeChallenge, "UTF-8"))
            append("&code_challenge_method=S256")
        }
        AuthDebugLog.d("[AUTH_URL_BUILD] $built")
        return built
    }

    suspend fun exchangeCodeForTokens(code: String, codeVerifier: String): TokenResponse =
        withContext(Dispatchers.IO) {
            val endpoints = discoverEndpoints()
            val form = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", AuthConfig.CLIENT_ID)
                .add("redirect_uri", AuthConfig.REDIRECT_URI)
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .build()
            val request = Request.Builder()
                .url(endpoints.tokenEndpoint)
                .post(form)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build()
            AuthDebugLog.d("[TOKEN EXCHANGE] endpoint=${endpoints.tokenEndpoint} codeLength=${code.length} verifierLength=${codeVerifier.length}")
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw AuthException("Empty token response")
            val safeBodyPreview = body
                .replace(Regex("\"access_token\"\\s*:\\s*\"[^\"]+\""), "\"access_token\":\"***\"")
                .replace(Regex("\"id_token\"\\s*:\\s*\"[^\"]+\""), "\"id_token\":\"***\"")
                .replace(Regex("\"refresh_token\"\\s*:\\s*\"[^\"]+\""), "\"refresh_token\":\"***\"")
                .take(500)
            AuthDebugLog.d("[TOKEN EXCHANGE] responseCode=${response.code} successful=${response.isSuccessful} bodyPreview=$safeBodyPreview")
            if (!response.isSuccessful) {
                throw AuthException("Token exchange failed: ${response.code} $body")
            }
            val json = JSONObject(body)
            val accessToken = json.optString("access_token")
            val idToken = json.optString("id_token")
            if (accessToken.isBlank() && idToken.isBlank()) {
                throw AuthException("No access_token or id_token in response")
            }
            val expiresIn = json.optLong("expires_in", 3600L).coerceIn(60L, 365L * 24 * 3600L)
            val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
            TokenResponse(
                accessToken = accessToken,
                idToken = idToken,
                expiresInSeconds = expiresIn,
                refreshToken = refreshToken
            )
        }

    suspend fun refreshAccessToken(refreshToken: String): TokenResponse = withContext(Dispatchers.IO) {
        if (refreshToken.isBlank()) throw AuthException("Missing refresh_token")
        val endpoints = discoverEndpoints()
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", AuthConfig.CLIENT_ID)
            .add("redirect_uri", AuthConfig.REDIRECT_URI)
            .build()
        val request = Request.Builder()
            .url(endpoints.tokenEndpoint)
            .post(form)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw AuthException("Empty refresh response")
        if (!response.isSuccessful) {
            if (response.code >= 500) {
                throw IOException("Token refresh HTTP ${response.code}")
            }
            throw AuthException("Token refresh failed: ${response.code} $body")
        }
        val json = JSONObject(body)
        val accessToken = json.optString("access_token")
        if (accessToken.isBlank()) {
            throw AuthException("No access_token in refresh response")
        }
        val idToken = json.optString("id_token")
        val expiresIn = json.optLong("expires_in", 3600L).coerceIn(60L, 365L * 24 * 3600L)
        val newRefresh = json.optString("refresh_token").takeIf { it.isNotBlank() }
        TokenResponse(
            accessToken = accessToken,
            idToken = idToken,
            expiresInSeconds = expiresIn,
            refreshToken = newRefresh
        )
    }

    data class TokenResponse(
        val accessToken: String,
        val idToken: String,
        val expiresInSeconds: Long,
        val refreshToken: String?
    )

    suspend fun exchangeShopifyTokenForJwt(accessToken: String, idToken: String? = null): JwtResult = withContext(Dispatchers.IO) {
        val url = "${AuthConfig.CREATOR_ENGINE_URL}/apps/creator-dispatch?op=exchange-shopify-token"
        val escaped = (idToken ?: accessToken).replace("\\", "\\\\").replace("\"", "\\\"")
        val key = if (idToken != null) "id_token" else "access_token"
        val body = """{"$key":"$escaped"}"""
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()
        val response = client.newCall(request).execute()
        val respBody = response.body?.string() ?: "{}"
        val json = JSONObject(respBody)
        if (!response.isSuccessful || !json.optBoolean("ok", false)) {
            val err = json.optString("error", "unknown")
            val detail = json.optString("detail", "")
            throw AuthException(if (detail.isNotEmpty()) "JWT exchange failed: $err ($detail)" else "JWT exchange failed: $err")
        }
        val jwt = json.optString("jwt")
        val ownerId = json.optString("owner_id")
        if (jwt.isBlank()) throw AuthException("No jwt in response")
        JwtResult(jwt = jwt, ownerId = ownerId)
    }

    data class JwtResult(val jwt: String, val ownerId: String)
}

class AuthException(message: String) : Exception(message)
