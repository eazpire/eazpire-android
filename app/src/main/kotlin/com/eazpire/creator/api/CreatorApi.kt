package com.eazpire.creator.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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

    suspend fun getBalance(ownerId: String? = null): JSONObject {
        val params = if (!ownerId.isNullOrBlank()) mapOf("owner_id" to ownerId) else emptyMap()
        return call("get-balance", params)
    }

    /**
     * GET ?op=get-customer-account-profile&owner_id=xxx
     */
    suspend fun getCustomerProfile(ownerId: String): JSONObject = call(
        "get-customer-account-profile",
        mapOf("owner_id" to ownerId)
    )

    /**
     * GET ?op=get-customer-email&customer_id=xxx&shop=xxx
     * Returns { ok: true, email: "user@example.com" } – email from Shopify account.
     * Shop must match the shop the user logged in with (e.g. AuthConfig.SHOP_DOMAIN).
     */
    suspend fun getCustomerEmail(
        customerId: String,
        shop: String
    ): JSONObject = call(
        "get-customer-email",
        mapOf(
            "customer_id" to customerId,
            "shop" to shop
        )
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

    /**
     * GET ?op=get-customer-profile&owner_id=xxx
     * Body measurements for Size AI (gender, height, weight, chest, waist, etc.)
     */
    suspend fun getSizeProfile(ownerId: String): JSONObject = call(
        "get-customer-profile",
        mapOf("owner_id" to ownerId)
    )

    /**
     * POST save-customer-profile – body measurements for Size AI
     */
    suspend fun saveSizeProfile(ownerId: String, profile: Map<String, Any?>): JSONObject =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("$baseUrl/apps/creator-dispatch?op=save-customer-profile")
                append("&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
                append("&_t=${System.currentTimeMillis()}")
            }
            val body = org.json.JSONObject(profile.filterValues { it != null }.mapValues { it.value!! }).toString()
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

    /** GET ?op=country-product-counts – Returns { ok, counts: { "DE": 1234, ... } } */
    suspend fun getCountryProductCounts(): JSONObject = call("country-product-counts")

    /** GET ?op=list-customer-mockups&owner_id=xxx → { ok, mockups: [...] } */
    suspend fun listCustomerMockups(ownerId: String): JSONObject = call(
        "list-customer-mockups",
        mapOf("owner_id" to ownerId)
    )

    /** POST ?op=generate-customer-mockups – multipart: photo + person_type */
    suspend fun generateCustomerMockups(
        ownerId: String,
        photoBytes: ByteArray,
        contentType: String,
        personType: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val ext = when {
            contentType.contains("png") -> "png"
            contentType.contains("webp") -> "webp"
            else -> "jpg"
        }
        val mediaType = contentType.toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "photo",
                "photo.$ext",
                okhttp3.RequestBody.create(mediaType, photoBytes)
            )
            .addFormDataPart("person_type", personType)
            .build()
        val url = "$baseUrl/apps/creator-dispatch?op=generate-customer-mockups&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}&_t=${System.currentTimeMillis()}"
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json")
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        val response = client.newCall(request).execute()
        JSONObject(response.body?.string() ?: "{}")
    }

    /** GET ?op=poll-job&job_id=xxx */
    suspend fun pollJob(jobId: String): JSONObject = call(
        "poll-job",
        mapOf("job_id" to jobId)
    )

    /** POST ?op=toggle-mockup-preview&owner_id=xxx – Body: { mockup_id, enabled } */
    suspend fun toggleMockupPreview(ownerId: String, mockupId: Long, enabled: Boolean): JSONObject =
        postJson("toggle-mockup-preview", mapOf("mockup_id" to mockupId, "enabled" to enabled), mapOf("owner_id" to ownerId))

    /** POST ?op=delete-customer-mockup&owner_id=xxx – Body: { mockup_id } */
    suspend fun deleteCustomerMockup(ownerId: String, mockupId: Long): JSONObject =
        postJson("delete-customer-mockup", mapOf("mockup_id" to mockupId), mapOf("owner_id" to ownerId))

    /** GET ?op=list-community-network&owner_id=xxx → { ok, network: {...} } */
    suspend fun listCommunityNetwork(ownerId: String): JSONObject = call(
        "list-community-network",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=list-jobs&owner_id=xxx&limit=20 → { ok, items: [...] } */
    suspend fun listJobs(ownerId: String, limit: Int = 20): JSONObject = call(
        "list-jobs",
        mapOf("owner_id" to ownerId, "limit" to limit.toString())
    )

    /** GET ?op=list-generated&owner_id=xxx → { ok, items: [...] } */
    suspend fun listGenerated(ownerId: String, limit: Int = 50): JSONObject = call(
        "list-generated",
        mapOf("owner_id" to ownerId, "limit" to limit.toString())
    )

    /** GET ?op=list&owner_id=xxx&limit=100 → { ok, items: [...] } Creator designs */
    suspend fun listDesigns(ownerId: String, limit: Int = 100): JSONObject = call(
        "list",
        mapOf("owner_id" to ownerId, "limit" to limit.toString())
    )

    /** GET ?op=get-customer-designs&owner_id=xxx → { ok, designs: [...] } Customer designs */
    suspend fun getCustomerDesigns(ownerId: String): JSONObject = call(
        "get-customer-designs",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=get-customer-products&owner_id=xxx → { ok, products: [...] } */
    suspend fun getCustomerProducts(ownerId: String): JSONObject = call(
        "get-customer-products",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=get-shopify-products&shop=xxx → { ok, products: [...] } */
    suspend fun getShopifyProducts(shop: String? = null): JSONObject {
        val params = shop?.let { mapOf("shop" to it) } ?: emptyMap()
        return call("get-shopify-products", params)
    }

    // ── Wardrobe ─────────────────────────────────────────
    /** GET ?op=wardrobe-list&customer_id=xxx → { ok, outfits: [...] } */
    suspend fun wardrobeList(customerId: String): JSONObject = call(
        "wardrobe-list",
        mapOf("customer_id" to customerId)
    )

    /** GET ?op=wardrobe-get&customer_id=xxx&outfit_id=xxx → { ok, outfit: {...} } */
    suspend fun wardrobeGet(customerId: String, outfitId: String): JSONObject = call(
        "wardrobe-get",
        mapOf("customer_id" to customerId, "outfit_id" to outfitId)
    )

    /** POST ?op=wardrobe-save – Body: customer_id, outfit_id?, name, gender, age_group, slots */
    suspend fun wardrobeSave(customerId: String, body: Map<String, Any?>): JSONObject =
        postJson("wardrobe-save", body + ("customer_id" to customerId))

    /** POST ?op=wardrobe-delete – Body: customer_id, outfit_id */
    suspend fun wardrobeDelete(customerId: String, outfitId: String): JSONObject =
        postJson("wardrobe-delete", mapOf("customer_id" to customerId, "outfit_id" to outfitId))

    /** POST ?op=wardrobe-generate – Body: customer_id, outfit_id, slots, gender, age_group, name, ... */
    suspend fun wardrobeGenerate(customerId: String, body: Map<String, Any?>): JSONObject =
        postJson("wardrobe-generate", body + ("customer_id" to customerId))

    private suspend fun postJson(op: String, body: Map<String, Any?>, queryParams: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("$baseUrl/apps/creator-dispatch?op=$op&_t=${System.currentTimeMillis()}")
                queryParams.forEach { (k, v) ->
                    if (v.isNotBlank()) append("&${k}=${java.net.URLEncoder.encode(v, "UTF-8")}")
                }
            }
            val jsonBody = org.json.JSONObject(body.filterValues { it != null }.mapValues { it.value!! }).toString()
            val request = Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create("application/json".toMediaType(), jsonBody.toByteArray()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

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
