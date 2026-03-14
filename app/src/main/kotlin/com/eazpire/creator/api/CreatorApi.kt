package com.eazpire.creator.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Creator API Client – analog zu creatorApiFetch im Web.
 * Basis-URL: https://creator-engine.eazpire.workers.dev
 */
class CreatorApi(
    private val baseUrl: String = "https://creator-engine.eazpire.workers.dev",
    private val jwt: String? = null
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Ruft eine Operation auf (GET oder POST).
     * @param op z.B. "get-balance", "list-designs"
     * @param params Query-Parameter
     * @param method GET oder POST
     */
    suspend fun call(
        op: String,
        params: Map<String, String> = emptyMap(),
        method: String = "GET"
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$baseUrl/apps/creator-dispatch?op=$op")
            if (method == "GET") append("&_t=${System.currentTimeMillis()}")
            params.forEach { (k, v) ->
                if (v.isNotBlank()) append("&${k}=${java.net.URLEncoder.encode(v, "UTF-8")}")
            }
        }
        val request = Request.Builder()
            .url(url)
            .apply {
                jwt?.let { addHeader("Authorization", "Bearer $it") }
            }
            .method(method, if (method == "POST") okhttp3.RequestBody.create(null, byteArrayOf()) else null)
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: "{}"
        JSONObject(body)
    }

    suspend fun getBalance(): JSONObject = call("get-balance")
    suspend fun getSettings(): JSONObject = call("get-settings")

    /** GET ?op=country-product-counts – Returns { ok, counts: { "DE": 1234, ... } } */
    suspend fun getCountryProductCounts(): JSONObject = call("country-product-counts")

    /** GET ?op=get-catalog-products&region=EU&design_type=classic */
    suspend fun getCatalogProducts(
        region: String,
        designType: String? = null
    ): JSONObject {
        val params = mutableMapOf("region" to region)
        designType?.let { params["design_type"] = it }
        return call("get-catalog-products", params)
    }

    /**
     * GET /api/languages – All developed languages, dialects, and scripts from our DB.
     * Returns standard + dialects (93+ languages).
     */
    suspend fun getLanguages(): List<ApiLanguageItem> = withContext(Dispatchers.IO) {
        parseLanguagesFromJson(fetchLanguagesJson())
    }

    private fun fetchLanguagesJson(): String? {
        val url = "$baseUrl/api/languages"
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        return response.body?.string().takeIf { !it.isNullOrBlank() && response.isSuccessful }
    }

    private fun parseLanguagesFromJson(body: String?): List<ApiLanguageItem> {
        if (body.isNullOrBlank()) return emptyList()
        return try {
            val json = JSONObject(body)
            val all = json.optJSONArray("all") ?: return emptyList()
            (0 until all.length()).mapNotNull { i ->
                val obj = all.optJSONObject(i) ?: return@mapNotNull null
                val code = obj.optString("code", "").ifBlank { return@mapNotNull null }
                ApiLanguageItem(
                    code,
                    obj.optString("native", obj.optString("name", code)),
                    obj.optString("flag", "US").uppercase()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

data class ApiLanguageItem(val code: String, val label: String, val flagCode: String)
