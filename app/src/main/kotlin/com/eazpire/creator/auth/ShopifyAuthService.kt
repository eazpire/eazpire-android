package com.eazpire.creator.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Shopify Customer Account API OAuth 2.0 mit PKCE.
 * 1. Discovery (OpenID config)
 * 2. Auth-URL bauen, in WebView öffnen
 * 3. Callback: code extrahieren
 * 4. Code gegen access_token tauschen (Shopify)
 * 5. access_token gegen JWT tauschen (creator-engine)
 */
class ShopifyAuthService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class AuthEndpoints(val authorizationEndpoint: String, val tokenEndpoint: String)

    suspend fun discoverEndpoints(): AuthEndpoints = withContext(Dispatchers.IO) {
        val url = "https://${AuthConfig.SHOP_DOMAIN}/.well-known/openid-configuration"
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw AuthException("Discovery failed: ${response.code}")
        }
        val body = response.body?.string() ?: throw AuthException("Empty discovery response")
        val json = JSONObject(body)
        val auth = json.optString("authorization_endpoint")
        val token = json.optString("token_endpoint")
        if (auth.isBlank() || token.isBlank()) {
            throw AuthException("Missing authorization_endpoint or token_endpoint")
        }
        AuthEndpoints(auth, token)
    }

    fun buildAuthorizationUrl(
        authorizationEndpoint: String,
        codeVerifier: String,
        state: String
    ): String {
        val codeChallenge = PkceUtils.generateCodeChallenge(codeVerifier)
        return buildString {
            append(authorizationEndpoint)
            append("?client_id=").append(java.net.URLEncoder.encode(AuthConfig.CLIENT_ID, "UTF-8"))
            append("&response_type=code")
            append("&redirect_uri=").append(java.net.URLEncoder.encode(AuthConfig.REDIRECT_URI, "UTF-8"))
            append("&scope=").append(java.net.URLEncoder.encode(AuthConfig.SCOPE, "UTF-8"))
            append("&state=").append(java.net.URLEncoder.encode(state, "UTF-8"))
            append("&code_challenge=").append(java.net.URLEncoder.encode(codeChallenge, "UTF-8"))
            append("&code_challenge_method=S256")
        }
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
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw AuthException("Empty token response")
            if (!response.isSuccessful) {
                throw AuthException("Token exchange failed: ${response.code} $body")
            }
            val json = JSONObject(body)
            val accessToken = json.optString("access_token")
            val idToken = json.optString("id_token")
            if (accessToken.isBlank() && idToken.isBlank()) {
                throw AuthException("No access_token or id_token in response")
            }
            TokenResponse(accessToken = accessToken, idToken = idToken)
        }

    data class TokenResponse(val accessToken: String, val idToken: String)

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
