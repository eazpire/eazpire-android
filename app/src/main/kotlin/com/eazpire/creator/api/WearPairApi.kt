package com.eazpire.creator.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Claim Wear pairing session after scanning QR on the watch.
 */
class WearPairApi(
    private val baseUrl: String = "https://creator-engine.eazpire.workers.dev",
    private val jwt: String?,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json".toMediaType()

    suspend fun claim(token: String, phoneDeviceName: String?): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("token", token)
        if (!phoneDeviceName.isNullOrBlank()) body.put("phone_device_name", phoneDeviceName)
        postJson("$baseUrl/api/wear-pair/claim", body.toString())
    }

    companion object {
        /** Parses `eazpire://wear-pair?t=…` or HTTPS wear-pair URLs from QR text. */
        fun parseTokenFromQrPayload(raw: String): String? {
            val s = raw.trim()
            if (s.isBlank()) return null
            return try {
                when {
                    s.startsWith("eazpire://", ignoreCase = true) -> {
                        val u = java.net.URI(s.replace("eazpire://", "https://eazpire.local/"))
                        parseQueryToken(u.rawQuery)
                    }
                    s.contains("wear-pair") -> {
                        val u = java.net.URI(s)
                        parseQueryToken(u.rawQuery)
                    }
                    else -> {
                        Regex("[?&]t=([A-Za-z0-9_-]+)").find(s)?.groupValues?.getOrNull(1)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun parseQueryToken(query: String?): String? {
            if (query.isNullOrBlank()) return null
            for (part in query.split("&")) {
                if (part.startsWith("t=")) {
                    val v = part.removePrefix("t=").trim()
                    if (v.isNotBlank()) return v
                }
            }
            return null
        }
    }

    private fun postJson(url: String, jsonBody: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(jsonType))
            .apply {
                jwt?.let { addHeader("Authorization", "Bearer $it") }
            }
            .build()
        val response = client.newCall(request).execute()
        return JSONObject(response.body?.string() ?: "{}")
    }
}
