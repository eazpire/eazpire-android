package com.eazpire.creator.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
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

    /**
     * GET ?op=get-customer-account-profile&owner_id=xxx
     */
    suspend fun getCustomerProfile(ownerId: String): JSONObject = call(
        "get-customer-account-profile",
        mapOf("owner_id" to ownerId)
    )

    /**
     * POST ?op=save-customer-account-profile&owner_id=xxx
     */
    suspend fun saveCustomerProfile(ownerId: String, profile: Map<String, String>): JSONObject =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("$baseUrl/apps/creator-dispatch?op=save-customer-account-profile")
                append("&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
                append("&_t=${System.currentTimeMillis()}")
            }
            val body = org.json.JSONObject(profile).toString()
            val request = Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create("application/json".toMediaType(), body.toByteArray()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .apply {
                    jwt?.let { addHeader("Authorization", "Bearer $it") }
                }
                .build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: "{}"
            JSONObject(respBody)
        }
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
     * Returns standard (base languages), children (dialects/scripts per base), and all.
     */
    suspend fun getLanguages(): ApiLanguagesResponse = withContext(Dispatchers.IO) {
        parseLanguagesResponse(fetchLanguagesJson())
    }

    private fun fetchLanguagesJson(): String? {
        val url = "$baseUrl/api/languages"
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        return response.body?.string().takeIf { !it.isNullOrBlank() && response.isSuccessful }
    }

    private fun parseLanguagesResponse(body: String?): ApiLanguagesResponse {
        if (body.isNullOrBlank()) return ApiLanguagesResponse(emptyList(), emptyMap())
        return try {
            val json = JSONObject(body)
            val standard = parseLangArray(json.optJSONArray("standard"))
            val children = parseChildrenMap(json.optJSONObject("children"))
            ApiLanguagesResponse(standard, children)
        } catch (_: Exception) {
            ApiLanguagesResponse(emptyList(), emptyMap())
        }
    }

    private fun parseLangArray(arr: org.json.JSONArray?): List<ApiLanguageItem> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val code = obj.optString("code", "").ifBlank { return@mapNotNull null }
            ApiLanguageItem(
                code,
                obj.optString("native", obj.optString("name", code)),
                obj.optString("flag", "US").uppercase()
            )
        }
    }

    private fun parseChildrenMap(obj: JSONObject?): Map<String, ApiLanguageChildren> {
        if (obj == null) return emptyMap()
        val map = mutableMapOf<String, ApiLanguageChildren>()
        obj.keys().asSequence().forEach { baseLang ->
            val child = obj.optJSONObject(baseLang) ?: return@forEach
            val dialects = parseLangArray(child.optJSONArray("dialects"))
            val scripts = parseLangArray(child.optJSONArray("scripts"))
            if (dialects.isNotEmpty() || scripts.isNotEmpty()) {
                map[baseLang] = ApiLanguageChildren(dialects, scripts)
            }
        }
        return map
    }
}

data class ApiLanguageItem(val code: String, val label: String, val flagCode: String)
data class ApiLanguageChildren(val dialects: List<ApiLanguageItem>, val scripts: List<ApiLanguageItem>)
data class ApiLanguagesResponse(
    val standard: List<ApiLanguageItem>,
    val children: Map<String, ApiLanguageChildren>
)
