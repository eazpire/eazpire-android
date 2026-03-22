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

    /** GET ?op=get-level&owner_id=xxx – Level/XP for Creator dashboard */
    suspend fun getLevel(ownerId: String): JSONObject = call(
        "get-level",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=get-design-source-counts&owner_id=xxx – generated/uploaded design counts */
    suspend fun getDesignSourceCounts(ownerId: String): JSONObject = call(
        "get-design-source-counts",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=get-publish-stats&owner_id=xxx – products online/offline */
    suspend fun getPublishStats(ownerId: String): JSONObject = call(
        "get-publish-stats",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=get-creator-sales&owner_id=xxx – sales/balance for Creator */
    suspend fun getCreatorSales(ownerId: String): JSONObject = call(
        "get-creator-sales",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=get-hero-analytics-summary&owner_id=xxx&days=90 */
    suspend fun getHeroAnalyticsSummary(ownerId: String, days: Int = 90): JSONObject = call(
        "get-hero-analytics-summary",
        mapOf("owner_id" to ownerId, "days" to days.toString())
    )

    /** GET ?op=get-onboarding-progress&owner_id=xxx – Creator Journey todos */
    suspend fun getOnboardingProgress(ownerId: String): JSONObject = call(
        "get-onboarding-progress",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=eazy-memory&user_id=xxx – user memory / preferences (EAZY_DB) */
    suspend fun getEazyMemory(userId: String): JSONObject = call(
        "eazy-memory",
        mapOf("user_id" to userId)
    )

    /** POST ?op=eazy-memory Body: { user_id, preferences } – merge preferences */
    suspend fun postEazyMemory(userId: String, preferences: org.json.JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/apps/creator-dispatch?op=eazy-memory&_t=${System.currentTimeMillis()}"
            val body = org.json.JSONObject()
                .put("user_id", userId)
                .put("preferences", preferences)
                .toString()
            val request = Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create("application/json".toMediaType(), body.toByteArray()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

    /**
     * GET ?op=get-customer-account-profile&owner_id=xxx
     */
    suspend fun getCustomerProfile(ownerId: String): JSONObject = call(
        "get-customer-account-profile",
        mapOf("owner_id" to ownerId)
    )

    /**
     * GET ?op=get-customer-gift-cards&customer_id=xxx&shop=xxx
     * Returns { ok: true, gift_cards: [...] }
     */
    suspend fun getCustomerGiftCards(customerId: String, shop: String): JSONObject = call(
        "get-customer-gift-cards",
        mapOf("customer_id" to customerId, "shop" to shop)
    )

    /**
     * GET ?op=get-customer-wallet-total&owner_id=xxx&currency=EUR
     */
    suspend fun getCustomerWalletTotal(ownerId: String, currency: String): JSONObject = call(
        "get-customer-wallet-total",
        mapOf("owner_id" to ownerId, "currency" to currency)
    )

    /**
     * GET ?op=get-promo-slots&customer_id=xxx
     * Returns { ok: true, slots: [...] } – creator promo codes
     */
    suspend fun getPromoSlots(customerId: String): JSONObject = call(
        "get-promo-slots",
        mapOf("customer_id" to customerId)
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
    /** GET ?op=get-settings&owner_id=xxx → { ok, settings: { creator_names, ... } } */
    suspend fun getSettings(ownerId: String? = null): JSONObject {
        val params = if (!ownerId.isNullOrBlank()) mapOf("owner_id" to ownerId) else emptyMap()
        return call("get-settings", params)
    }

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

    private suspend fun postDispatchJson(
        op: String,
        queryParams: Map<String, String> = emptyMap(),
        body: JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$baseUrl/apps/creator-dispatch?op=$op")
            append("&_t=${System.currentTimeMillis()}")
            queryParams.forEach { (k, v) ->
                if (v.isNotBlank()) {
                    append("&${k}=${java.net.URLEncoder.encode(v, "UTF-8")}")
                }
            }
        }
        val request = Request.Builder()
            .url(url)
            .post(okhttp3.RequestBody.create("application/json".toMediaType(), body.toString().toByteArray()))
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .apply {
                jwt?.let { addHeader("Authorization", "Bearer $it") }
            }
            .build()
        val response = client.newCall(request).execute()
        JSONObject(response.body?.string() ?: "{}")
    }

    /** GET ?op=get-size-recommendation&owner_id=xxx – all groups or single type with product_type */
    suspend fun getSizeRecommendations(ownerId: String, productTypeKey: String? = null): JSONObject {
        val params = mutableMapOf("owner_id" to ownerId)
        if (!productTypeKey.isNullOrBlank()) params["product_type"] = productTypeKey
        return call("get-size-recommendation", params)
    }

    /** GET ?op=list-reference-fits&owner_id=xxx */
    suspend fun listReferenceFits(ownerId: String): JSONObject =
        call("list-reference-fits", mapOf("owner_id" to ownerId))

    /** POST ?op=save-reference-fit&owner_id=xxx */
    suspend fun saveReferenceFit(
        ownerId: String,
        brandId: Long,
        productTypeId: Long,
        size: String,
        fitRating: String,
        notes: String?
    ): JSONObject = postDispatchJson(
        op = "save-reference-fit",
        queryParams = mapOf("owner_id" to ownerId),
        body = JSONObject().apply {
            put("brand_id", brandId)
            put("product_type_id", productTypeId)
            put("size", size)
            put("fit_rating", fitRating)
            if (!notes.isNullOrBlank()) put("notes", notes)
        }
    )

    /** POST ?op=delete-reference-fit&owner_id=xxx */
    suspend fun deleteReferenceFit(ownerId: String, referenceFitId: Long): JSONObject =
        postDispatchJson(
            op = "delete-reference-fit",
            queryParams = mapOf("owner_id" to ownerId),
            body = JSONObject().put("reference_fit_id", referenceFitId)
        )

    /** GET ?op=list-product-types – optional category key (tops, bottoms, footwear) */
    suspend fun listProductTypes(categoryKey: String? = null): JSONObject {
        val params = mutableMapOf<String, String>()
        if (!categoryKey.isNullOrBlank()) params["category"] = categoryKey
        return call("list-product-types", params)
    }

    /** GET ?op=list-brands – optional search */
    suspend fun listBrands(search: String? = null): JSONObject {
        val params = mutableMapOf<String, String>()
        if (!search.isNullOrBlank()) params["search"] = search
        return call("list-brands", params)
    }

    /** POST ?op=add-brand – body { name } */
    suspend fun addBrand(name: String): JSONObject =
        postDispatchJson(op = "add-brand", body = JSONObject().put("name", name.trim()))

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

    /** GET ?op=get-referral-code&owner_id=xxx → { ok, code, url, short_url } */
    suspend fun getReferralCode(ownerId: String): JSONObject = call(
        "get-referral-code",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=get-creator-code&owner_id=xxx → { is_creator, can_generate, active_code?, ref_url? } */
    suspend fun getCreatorCode(ownerId: String): JSONObject = call(
        "get-creator-code",
        mapOf("owner_id" to ownerId)
    )

    /** POST ?op=generate-creator-code&owner_id=xxx → { ok, code?, ref_url? } */
    suspend fun generateCreatorCode(ownerId: String): JSONObject =
        postJson("generate-creator-code", emptyMap(), mapOf("owner_id" to ownerId))

    /** POST ?op=redeem-creator-code&owner_id=xxx Body: { code } */
    suspend fun redeemCreatorCode(ownerId: String, code: String): JSONObject =
        postJson("redeem-creator-code", mapOf("code" to code), mapOf("owner_id" to ownerId))

    /** GET ?op=get-creator-code-stats&owner_id=xxx → { ok, stats: { total_generated, total_redeemed, community_size } } */
    suspend fun getCreatorCodeStats(ownerId: String): JSONObject = call(
        "get-creator-code-stats",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=list-interests → { ok, categories: [{ key, interests: [{ id, name }] }] } */
    suspend fun listInterests(): JSONObject = call("list-interests")

    /** GET ?op=get-user-interests&owner_id=xxx → { ok, interests: [{ id, name }] } */
    suspend fun getUserInterests(ownerId: String): JSONObject = call(
        "get-user-interests",
        mapOf("owner_id" to ownerId)
    )

    /** POST ?op=set-user-interests&owner_id=xxx Body: { interest_ids: [1,2,3] } */
    suspend fun setUserInterests(ownerId: String, interestIds: List<Long>): JSONObject =
        postJson("set-user-interests", mapOf("interest_ids" to org.json.JSONArray(interestIds)), mapOf("owner_id" to ownerId))

    /** POST ?op=add-creator-name&owner_id=xxx Body: { name } */
    suspend fun addCreatorName(ownerId: String, name: String): JSONObject =
        postJson("add-creator-name", mapOf("name" to name), mapOf("owner_id" to ownerId))

    /** GET ?op=get-customer-setting&owner_id=xxx&key=xxx → { ok, key, value } */
    suspend fun getCustomerSetting(ownerId: String, key: String): JSONObject = call(
        "get-customer-setting",
        mapOf("owner_id" to ownerId, "key" to key)
    )

    /** POST ?op=set-customer-setting&owner_id=xxx Body: { key, value } */
    suspend fun setCustomerSetting(ownerId: String, key: String, value: String): JSONObject =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("$baseUrl/apps/creator-dispatch?op=set-customer-setting")
                append("&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
                append("&_t=${System.currentTimeMillis()}")
            }
            val body = org.json.JSONObject(mapOf("key" to key, "value" to value)).toString()
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
            JSONObject(response.body?.string() ?: "{}")
        }

    /** POST ?op=sync-ref-link-slugs&owner_id=xxx Body: { links: [{ slug, name }] } */
    suspend fun syncRefLinkSlugs(ownerId: String, links: List<Map<String, String>>): JSONObject =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("$baseUrl/apps/creator-dispatch?op=sync-ref-link-slugs")
                append("&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
                append("&_t=${System.currentTimeMillis()}")
            }
            val body = org.json.JSONObject(mapOf("links" to org.json.JSONArray(links.map { org.json.JSONObject(it) }))).toString()
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
            JSONObject(response.body?.string() ?: "{}")
        }

    /** GET ?op=get-community-analytics-overview&owner_id=xxx&days=30&compare=0&link_id=&source= */
    suspend fun getCommunityAnalyticsOverview(
        ownerId: String,
        days: Int = 30,
        compare: Boolean = false,
        linkId: String? = null,
        source: String? = null
    ): JSONObject {
        val params = mutableMapOf("owner_id" to ownerId, "days" to days.toString(), "compare" to if (compare) "1" else "0")
        linkId?.takeIf { it.isNotBlank() }?.let { params["link_id"] = it }
        source?.takeIf { it.isNotBlank() }?.let { params["source"] = it }
        return call("get-community-analytics-overview", params)
    }

    /** GET ?op=get-community-analytics-links&owner_id=xxx&days=30&... */
    suspend fun getCommunityAnalyticsLinks(
        ownerId: String,
        days: Int = 30,
        linkId: String? = null,
        source: String? = null
    ): JSONObject {
        val params = mutableMapOf("owner_id" to ownerId, "days" to days.toString())
        linkId?.takeIf { it.isNotBlank() }?.let { params["link_id"] = it }
        source?.takeIf { it.isNotBlank() }?.let { params["source"] = it }
        return call("get-community-analytics-links", params)
    }

    /** GET ?op=get-community-analytics-sources&owner_id=xxx&days=30&... */
    suspend fun getCommunityAnalyticsSources(
        ownerId: String,
        days: Int = 30,
        linkId: String? = null,
        source: String? = null
    ): JSONObject {
        val params = mutableMapOf("owner_id" to ownerId, "days" to days.toString())
        linkId?.takeIf { it.isNotBlank() }?.let { params["link_id"] = it }
        source?.takeIf { it.isNotBlank() }?.let { params["source"] = it }
        return call("get-community-analytics-sources", params)
    }

    /** GET ?op=get-community-analytics-events&owner_id=xxx&days=30&limit=20&cursor= */
    suspend fun getCommunityAnalyticsEvents(
        ownerId: String,
        days: Int = 30,
        linkId: String? = null,
        source: String? = null,
        limit: Int = 20,
        cursor: String? = null
    ): JSONObject {
        val params = mutableMapOf("owner_id" to ownerId, "days" to days.toString(), "limit" to limit.toString())
        linkId?.takeIf { it.isNotBlank() }?.let { params["link_id"] = it }
        source?.takeIf { it.isNotBlank() }?.let { params["source"] = it }
        cursor?.takeIf { it.isNotBlank() }?.let { params["cursor"] = it }
        return call("get-community-analytics-events", params)
    }

    /** GET ?op=get-creator-payout-overview&owner_id=xxx&days=90 – fiat balance (availableAmount, currency) */
    suspend fun getCreatorPayoutOverview(
        ownerId: String,
        days: Int = 90,
        scope: String? = null
    ): JSONObject {
        val params = mutableMapOf("owner_id" to ownerId, "days" to days.toString())
        scope?.takeIf { it.isNotBlank() }?.let { params["scope"] = it }
        return call("get-creator-payout-overview", params)
    }

    /** GET ?op=get-shop-credits-summary&owner_id=xxx */
    suspend fun getShopCreditsSummary(ownerId: String): JSONObject =
        call("get-shop-credits-summary", mapOf("owner_id" to ownerId))

    /** GET ?op=get-creator-payout-details&owner_id=xxx */
    suspend fun getCreatorPayoutDetails(ownerId: String): JSONObject =
        call("get-creator-payout-details", mapOf("owner_id" to ownerId))

    /** POST ?op=save-creator-payout-details – add/remove payout method */
    suspend fun saveCreatorPayoutDetails(body: Map<String, Any?>): JSONObject =
        postJson("save-creator-payout-details", body)

    /** POST ?op=save-creator-payout-settings – auto-payout settings */
    suspend fun saveCreatorPayoutSettings(body: Map<String, Any?>): JSONObject =
        postJson("save-creator-payout-settings", body)

    /** POST ?op=convert-to-shop-credit – request payout as shop credit */
    suspend fun convertToShopCredit(body: Map<String, Any?>): JSONObject =
        postJson("convert-to-shop-credit", body)

    /** POST ?op=request-wise-payout */
    suspend fun requestWisePayout(body: Map<String, Any?>): JSONObject =
        postJson("request-wise-payout", body)

    /** POST ?op=request-paypal-payout */
    suspend fun requestPayPalPayout(body: Map<String, Any?>): JSONObject =
        postJson("request-paypal-payout", body)

    /** GET ?op=suggest-prompt → { ok, suggestedPrompt } – AI prompt suggestion */
    suspend fun suggestPrompt(): JSONObject = call("suggest-prompt")

    /**
     * POST ?op=accept – Submit design generation job.
     * Payload: prompt, image_url?, design_type, target_product, ratio, content_type, styles, design_colors,
     * background_colors, background, language, reference_images?, owner_id
     * Returns: { jobId } or { error, message }
     */
    suspend fun submitGenerateJob(
        ownerId: String,
        payload: org.json.JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$baseUrl/apps/creator-dispatch?op=accept")
            append("&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
            append("&_t=${System.currentTimeMillis()}")
        }
        val body = payload.toString()
        val request = Request.Builder()
            .url(url)
            .post(okhttp3.RequestBody.create("application/json".toMediaType(), body.toByteArray()))
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        val response = client.newCall(request).execute()
        JSONObject(response.body?.string() ?: "{}")
    }

    /** GET ?op=list-jobs&owner_id=xxx&limit=20 → { ok, items: [...] } */
    suspend fun listJobs(ownerId: String, limit: Int = 20): JSONObject = call(
        "list-jobs",
        mapOf("owner_id" to ownerId, "limit" to limit.toString())
    )

    /** GET ?op=list-generated&owner_id=xxx&path_prefix=/apps/creator-dispatch → { ok, items: [...] } */
    suspend fun listGenerated(ownerId: String, limit: Int = 200): JSONObject = call(
        "list-generated",
        mapOf(
            "owner_id" to ownerId,
            "limit" to limit.toString(),
            "path_prefix" to "/apps/creator-dispatch"
        )
    )

    /** GET ?op=list-public&limit=100&search=... → { ok, items: [{ preview_url, original_url }] } Public designs */
    suspend fun listPublic(limit: Int = 100, search: String? = null): JSONObject {
        val params = mutableMapOf("limit" to limit.toString())
        search?.takeIf { it.isNotBlank() }?.let { params["search"] = it }
        return call("list-public", params)
    }

    /** GET ?op=list&owner_id=xxx&limit=100 → { ok, items: [...] } Creator designs */
    suspend fun listDesigns(ownerId: String, limit: Int = 100): JSONObject = call(
        "list",
        mapOf("owner_id" to ownerId, "limit" to limit.toString())
    )

    /** GET ?op=get-published-summary&owner_id=xxx&shop=xxx → { ok, designs: [{ design_id, products_count }] } */
    suspend fun getPublishedSummary(ownerId: String, shop: String? = null): JSONObject {
        val params = mutableMapOf("owner_id" to ownerId)
        shop?.takeIf { it.isNotBlank() }?.let { params["shop"] = it }
        return call("get-published-summary", params)
    }

    /** GET ?op=get-published-products&owner_id=xxx&shop=xxx → { ok, products: [...] } */
    suspend fun getPublishedProducts(ownerId: String, shop: String? = null): JSONObject {
        val params = mutableMapOf("owner_id" to ownerId)
        shop?.takeIf { it.isNotBlank() }?.let { params["shop"] = it }
        return call("get-published-products", params)
    }

    /** GET ?op=get-product-image&shop=xxx&handle=xxx → { ok, image_url } Fallback für Shop-Bilder */
    suspend fun getProductImage(shop: String, handle: String): JSONObject = call(
        "get-product-image",
        mapOf("shop" to shop, "handle" to handle)
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

    /** GET ?op=get-shopify-products&shop=xxx&owner_id=xxx → { ok, products: [...] } */
    suspend fun getShopifyProducts(shop: String? = null, ownerId: String? = null, region: String? = null): JSONObject {
        val params = mutableMapOf<String, String>()
        shop?.let { params["shop"] = it }
        ownerId?.takeIf { it.isNotBlank() }?.let { params["owner_id"] = it }
        region?.takeIf { it.isNotBlank() }?.let { params["region"] = it }
        return call("get-shopify-products", params)
    }

    /** GET ?op=hero-used-products&owner_id=xxx → { ok, used_product_ids: [...] } */
    suspend fun getHeroUsedProducts(ownerId: String): JSONObject = call(
        "hero-used-products",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=video-used-products&owner_id=xxx → { ok, used_product_ids: [...] } */
    suspend fun getVideoUsedProducts(ownerId: String): JSONObject = call(
        "video-used-products",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=hero-list&owner_id=xxx&limit=100&status=active → { ok, items: [...] } */
    suspend fun heroList(ownerId: String, limit: Int = 100, status: String? = "active"): JSONObject = call(
        "hero-list",
        mutableMapOf<String, String>().apply {
            put("owner_id", ownerId)
            put("limit", limit.toString())
            status?.takeIf { it.isNotBlank() }?.let { put("status", it) }
        }
    )

    /** GET ?op=creator-videos-list&owner_id=xxx&limit=100 → { ok, items: [...] } */
    suspend fun creatorVideosList(ownerId: String, limit: Int = 100): JSONObject = call(
        "creator-videos-list",
        mapOf("owner_id" to ownerId, "limit" to limit.toString())
    )

    /** GET ?op=hero-published-random&limit=4 → { ok, images: [{ id, image_url, thumbnail_url, title }] } */
    suspend fun getHeroPublishedRandom(limit: Int = 4, region: String? = null): JSONObject = call(
        "hero-published-random",
        mutableMapOf<String, String>().apply {
            put("limit", limit.toString())
            region?.takeIf { it.isNotBlank() }?.let { put("region", it) }
        }
    )

    /** GET ?op=hero-get&hero_id=xxx&owner_id=xxx → { ok, hero_image: {...} } */
    suspend fun heroGet(ownerId: String, heroId: String): JSONObject = call(
        "hero-get",
        mapOf("owner_id" to ownerId, "hero_id" to heroId)
    )

    /** POST ?op=hero-update-hotspots – Body: { owner_id, hero_id, hotspots_json } */
    suspend fun heroUpdateHotspots(ownerId: String, heroId: String, hotspotsJson: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/apps/creator-dispatch?op=hero-update-hotspots&_t=${System.currentTimeMillis()}"
            val body = JSONObject()
                .put("owner_id", ownerId)
                .put("hero_id", heroId)
                .put("hotspots_json", hotspotsJson)
            val request = Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create("application/json".toMediaType(), body.toString().toByteArray()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

    /** POST ?op=hero-publish – Body: { owner_id, hero_id } */
    suspend fun heroPublish(ownerId: String, heroId: String): JSONObject =
        postJsonBodyOp("hero-publish", JSONObject().put("owner_id", ownerId).put("hero_id", heroId))

    /** POST ?op=hero-unpublish – Body: { owner_id, hero_id } */
    suspend fun heroUnpublish(ownerId: String, heroId: String): JSONObject =
        postJsonBodyOp("hero-unpublish", JSONObject().put("owner_id", ownerId).put("hero_id", heroId))

    /** GET ?op=get-products-by-shopify-ids&shopify_ids=...&owner_id=xxx */
    suspend fun getProductsByShopifyIds(ownerId: String, shopifyIds: String): JSONObject = call(
        "get-products-by-shopify-ids",
        mapOf("owner_id" to ownerId, "shopify_ids" to shopifyIds)
    )

    /** GET ?op=get-products-by-keys&product_keys=...&owner_id=xxx */
    suspend fun getProductsByKeys(ownerId: String, productKeys: String): JSONObject = call(
        "get-products-by-keys",
        mapOf("owner_id" to ownerId, "product_keys" to productKeys)
    )

    private suspend fun postJsonBodyOp(op: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/apps/creator-dispatch?op=$op&_t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create("application/json".toMediaType(), body.toString().toByteArray()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

    /** POST ?path_prefix=/tools/1.0/crop-image&owner_id=xxx – multipart: image
     *  Returns cropped PNG bytes (auto-crop to visible content). */
    suspend fun cropImage(ownerId: String, imageBytes: ByteArray, fileName: String = "upload.png"): ByteArray =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl?path_prefix=%2Ftools%2F1.0%2Fcrop-image&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}"
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", fileName, okhttp3.RequestBody.create("image/png".toMediaType(), imageBytes))
                .addFormDataPart("owner_id", ownerId)
                .build()
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept", "image/png, application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                val errMsg = try {
                    org.json.JSONObject(errBody).optString("error", errBody.take(200))
                } catch (_: Exception) { errBody.take(200) }
                throw RuntimeException(errMsg.ifBlank { "Crop failed (${response.code})" })
            }
            response.body?.bytes() ?: throw RuntimeException("Empty crop response")
        }

    /** POST ?path_prefix=/tools/1.0/remove-background&owner_id=xxx – multipart: image, format=PNG
     *  Returns PNG bytes (background removed via Picsart). Consumes EAZ. */
    suspend fun removeBackground(ownerId: String, imageBytes: ByteArray, fileName: String = "upload.png"): ByteArray =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl?path_prefix=%2Ftools%2F1.0%2Fremove-background&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}"
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", fileName, okhttp3.RequestBody.create("image/png".toMediaType(), imageBytes))
                .addFormDataPart("format", "PNG")
                .addFormDataPart("owner_id", ownerId)
                .build()
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept", "image/png, application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                val errMsg = try {
                    val jo = org.json.JSONObject(errBody)
                    if (jo.optString("code") == "INSUFFICIENT_EAZ") {
                        "Insufficient EAZ balance. Required: ${jo.opt("required")}, Available: ${jo.opt("balance_eaz")}"
                    } else jo.optString("error", errBody.take(200))
                } catch (_: Exception) { errBody.take(200) }
                throw RuntimeException(errMsg.ifBlank { "Remove background failed (${response.code})" })
            }
            response.body?.bytes() ?: throw RuntimeException("Empty remove-background response")
        }

    /** POST ?op=upload-design&owner_id=xxx – multipart: image, creator_name?, visibility? (My Creations)
     *  → R2 upload, Job in KV, Queue creator-jobs-upload-design (Metadata, Upscale, DB-Save) */
    suspend fun uploadDesign(
        ownerId: String,
        imageBytes: ByteArray,
        contentType: String,
        fileName: String? = null,
        creatorName: String? = null,
        visibility: String = "public"
    ): JSONObject = withContext(Dispatchers.IO) {
            val ext = when {
                contentType.contains("png") -> "png"
                contentType.contains("jpeg") || contentType.contains("jpg") -> "jpg"
                contentType.contains("svg") -> "svg"
                else -> "png"
            }
            val mediaType = contentType.toMediaType()
            val name = fileName?.takeIf { it.isNotBlank() } ?: "upload.$ext"
            val effectiveVisibility = if (visibility == "private") "private" else "public"
            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", name, okhttp3.RequestBody.create(mediaType, imageBytes))
                .addFormDataPart("visibility", effectiveVisibility)
            creatorName?.takeIf { it.isNotBlank() }?.let { bodyBuilder.addFormDataPart("creator_name", it) }
            val body = bodyBuilder.build()
            val url = "$baseUrl/apps/creator-dispatch?op=upload-design&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}&_t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

    /** POST ?op=upload-hero-image&owner_id=xxx – multipart: image, slot */
    suspend fun uploadHeroImage(ownerId: String, slot: String, imageBytes: ByteArray, contentType: String): JSONObject =
        withContext(Dispatchers.IO) {
            val ext = when {
                contentType.contains("png") -> "png"
                contentType.contains("webp") -> "webp"
                contentType.contains("gif") -> "gif"
                else -> "jpg"
            }
            val mediaType = contentType.toMediaType()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "$slot.$ext", okhttp3.RequestBody.create(mediaType, imageBytes))
                .addFormDataPart("slot", slot)
                .build()
            val url = "$baseUrl/apps/creator-dispatch?op=upload-hero-image&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}&_t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

    // ── Per-creator profile images (Creator Detail modal, same ops as web) ──

    private suspend fun postDispatchJson(
        op: String,
        jsonBody: JSONObject,
        queryParams: Map<String, String> = emptyMap()
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("$baseUrl/apps/creator-dispatch?op=$op&_t=${System.currentTimeMillis()}")
                queryParams.forEach { (k, v) ->
                    if (v.isNotBlank()) append("&${k}=${java.net.URLEncoder.encode(v, "UTF-8")}")
                }
            }
            val request = Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create("application/json".toMediaType(), jsonBody.toString().toByteArray()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

    /** GET ?op=get-creator-image */
    suspend fun getCreatorImage(ownerId: String, creatorName: String, imageCategory: String): JSONObject =
        call(
            "get-creator-image",
            mapOf(
                "owner_id" to ownerId,
                "creator_name" to creatorName,
                "image_category" to imageCategory
            )
        )

    /** POST multipart ?op=upload-creator-image */
    suspend fun uploadCreatorImage(
        ownerId: String,
        creatorName: String,
        imageCategory: String,
        imageBytes: ByteArray,
        contentType: String,
        fileName: String = "upload.jpg"
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val ext = when {
                contentType.contains("png") -> "png"
                contentType.contains("webp") -> "webp"
                else -> "jpg"
            }
            val mediaType = contentType.toMediaType()
            val name = fileName.ifBlank { "upload.$ext" }
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", name, okhttp3.RequestBody.create(mediaType, imageBytes))
                .build()
            val url = "$baseUrl/apps/creator-dispatch?op=upload-creator-image&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}&creator_name=${java.net.URLEncoder.encode(creatorName, "UTF-8")}&image_category=${java.net.URLEncoder.encode(imageCategory, "UTF-8")}&_t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

    /** POST ?op=save-creator-image — body matches web (upload / generated / delete pending payloads). */
    suspend fun saveCreatorImage(
        ownerId: String,
        creatorName: String,
        imageCategory: String,
        body: JSONObject
    ): JSONObject = postDispatchJson(
        "save-creator-image",
        body,
        mapOf("owner_id" to ownerId, "creator_name" to creatorName, "image_category" to imageCategory)
    )

    /** POST ?op=generate-creator-image */
    suspend fun generateCreatorImage(
        ownerId: String,
        creatorName: String,
        imageCategory: String,
        prompt: String,
        referenceImageUrl: String? = null
    ): JSONObject {
        val body = JSONObject().put("prompt", prompt)
        if (!referenceImageUrl.isNullOrBlank()) body.put("reference_image", referenceImageUrl)
        return postDispatchJson(
            "generate-creator-image",
            body,
            mapOf("owner_id" to ownerId, "creator_name" to creatorName, "image_category" to imageCategory)
        )
    }

    /** Same as [generateCreatorImage] but exposes HTTP status (e.g. 402 insufficient EAZ). */
    suspend fun generateCreatorImageWithHttpCode(
        ownerId: String,
        creatorName: String,
        imageCategory: String,
        prompt: String,
        referenceImageUrl: String? = null
    ): Pair<Int, JSONObject> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("prompt", prompt)
            if (!referenceImageUrl.isNullOrBlank()) body.put("reference_image", referenceImageUrl)
            val url = buildString {
                append("$baseUrl/apps/creator-dispatch?op=generate-creator-image&_t=${System.currentTimeMillis()}")
                append("&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
                append("&creator_name=${java.net.URLEncoder.encode(creatorName, "UTF-8")}")
                append("&image_category=${java.net.URLEncoder.encode(imageCategory, "UTF-8")}")
            }
            val request = Request.Builder()
                .url(url)
                .post(okhttp3.RequestBody.create("application/json".toMediaType(), body.toString().toByteArray()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            Pair(response.code, JSONObject(response.body?.string() ?: "{}"))
        }

    /** GET ?op=creator-image-status */
    suspend fun getCreatorImageStatus(predictionId: String): JSONObject =
        call("creator-image-status", mapOf("prediction_id" to predictionId))

    /** DELETE ?op=delete-creator-image */
    suspend fun deleteCreatorImage(ownerId: String, creatorName: String, imageCategory: String): JSONObject =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("$baseUrl/apps/creator-dispatch?op=delete-creator-image&_t=${System.currentTimeMillis()}")
                append("&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
                append("&creator_name=${java.net.URLEncoder.encode(creatorName, "UTF-8")}")
                append("&image_category=${java.net.URLEncoder.encode(imageCategory, "UTF-8")}")
            }
            val request = Request.Builder()
                .url(url)
                .delete()
                .addHeader("Accept", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

    /** POST ?op=save-cover-display-mode */
    suspend fun saveCoverDisplayMode(ownerId: String, creatorName: String, displayMode: String): JSONObject =
        postDispatchJson(
            "save-cover-display-mode",
            JSONObject().put("display_mode", displayMode),
            mapOf("owner_id" to ownerId, "creator_name" to creatorName)
        )

    /** POST ?op=toggle-hero-creator-page */
    suspend fun toggleHeroCreatorPage(ownerId: String, heroId: String, enabled: Boolean): JSONObject =
        postDispatchJson(
            "toggle-hero-creator-page",
            JSONObject().put("hero_id", heroId).put("enabled", enabled),
            mapOf("owner_id" to ownerId)
        )

    /** POST ?op=video-generate – Body: owner_id, product_ids, prompt, source_image_url, product_image_urls?, region? */
    suspend fun videoGenerate(
        ownerId: String,
        productIds: List<String>,
        prompt: String,
        sourceImageUrl: String,
        productImageUrls: List<String>? = null,
        region: String? = null
    ): JSONObject {
        val body = org.json.JSONObject()
            .put("owner_id", ownerId)
            .put("product_ids", org.json.JSONArray(productIds))
            .put("prompt", prompt)
            .put("source_image_url", sourceImageUrl)
        if (productImageUrls != null) body.put("product_image_urls", org.json.JSONArray(productImageUrls))
        if (region != null) body.put("region", region)
        return postDispatchJson("video-generate", body)
    }

    /** POST ?op=hero-generate – Body: owner_id, product_ids, prompt, product_image_urls?, model_image_url?, background_image_url?, api_version */
    suspend fun heroGenerate(
        ownerId: String,
        productIds: List<String>,
        prompt: String,
        productImageUrls: List<String>? = null,
        modelImageUrl: String? = null,
        backgroundImageUrl: String? = null,
        region: String? = null,
        apiVersion: String = "gpt-image-1.5"
    ): JSONObject = postJson(
        "hero-generate",
        mapOf(
            "owner_id" to ownerId,
            "product_ids" to org.json.JSONArray(productIds),
            "prompt" to prompt,
            "product_image_urls" to (productImageUrls?.let { org.json.JSONArray(it) } ?: org.json.JSONArray()),
            "model_image_url" to modelImageUrl,
            "background_image_url" to backgroundImageUrl,
            "region" to region,
            "api_version" to apiVersion
        )
    )

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

    /** GET ?op=get-favorites&customer_id=xxx → { ok, items: [...], count } */
    suspend fun getFavorites(customerId: String): JSONObject = call(
        "get-favorites",
        mapOf("customer_id" to customerId)
    )

    /** POST ?op=add-favorite Body: { customer_id, product_id, variant_id?, product_title?, product_image? } → { ok, added, count } */
    suspend fun addFavorite(
        customerId: String,
        productId: String,
        variantId: String? = null,
        productTitle: String? = null,
        productImage: String? = null
    ): JSONObject = postJson(
        "add-favorite",
        mapOf(
            "customer_id" to customerId,
            "product_id" to productId,
            "variant_id" to variantId,
            "product_title" to productTitle,
            "product_image" to productImage
        )
    )

    /** POST ?op=remove-favorite Body: { customer_id, product_id, variant_id? } */
    suspend fun removeFavorite(customerId: String, productId: String, variantId: String? = null): JSONObject =
        postJson(
            "remove-favorite",
            mapOf(
                "customer_id" to customerId,
                "product_id" to productId,
                "variant_id" to variantId
            )
        )

    /** GET ?op=get-favorite-lists&customer_id=xxx → { ok, lists: [{ id, name, items_count }] } */
    suspend fun getFavoriteLists(customerId: String): JSONObject = call(
        "get-favorite-lists",
        mapOf("customer_id" to customerId)
    )

    /** GET ?op=get-favorite-list-items&customer_id=xxx&list_id=123 → { ok, list, items } */
    suspend fun getFavoriteListItems(customerId: String, listId: Long): JSONObject = call(
        "get-favorite-list-items",
        mapOf("customer_id" to customerId, "list_id" to listId.toString())
    )

    /** POST create-favorite-list Body: { customer_id, name, description? } */
    suspend fun createFavoriteList(customerId: String, name: String, description: String? = null): JSONObject =
        postJson("create-favorite-list", mapOf("customer_id" to customerId, "name" to name, "description" to description))

    /** POST delete-favorite-list Body: { customer_id, list_id } */
    suspend fun deleteFavoriteList(customerId: String, listId: Long): JSONObject =
        postJson("delete-favorite-list", mapOf("customer_id" to customerId, "list_id" to listId))

    /** POST add-to-favorite-list Body: { customer_id, list_id, product_id, variant_id?, product_title?, product_image? } */
    suspend fun addToFavoriteList(
        customerId: String,
        listId: Long,
        productId: String,
        variantId: String? = null,
        productTitle: String? = null,
        productImage: String? = null
    ): JSONObject = postJson(
        "add-to-favorite-list",
        mapOf(
            "customer_id" to customerId,
            "list_id" to listId,
            "product_id" to productId,
            "variant_id" to variantId,
            "product_title" to productTitle,
            "product_image" to productImage
        )
    )

    /** POST remove-from-favorite-list Body: { customer_id, list_id, item_id } */
    suspend fun removeFromFavoriteList(customerId: String, listId: Long, itemId: Long): JSONObject =
        postJson("remove-from-favorite-list", mapOf("customer_id" to customerId, "list_id" to listId, "item_id" to itemId))

    /** POST save-favorites-as-list Body: { customer_id, name, description? } – moves pool to new list */
    suspend fun saveFavoritesAsList(customerId: String, name: String, description: String? = null): JSONObject =
        postJson("save-favorites-as-list", mapOf("customer_id" to customerId, "name" to name, "description" to description))

    /** POST clear-favorites Body: { customer_id } – removes all from pool */
    suspend fun clearFavorites(customerId: String): JSONObject =
        postJson("clear-favorites", mapOf("customer_id" to customerId))

    /** POST update-favorite-list Body: { customer_id, list_id, name?, description? } */
    suspend fun updateFavoriteList(customerId: String, listId: Long, name: String? = null, description: String? = null): JSONObject =
        postJson("update-favorite-list", mapOf("customer_id" to customerId, "list_id" to listId, "name" to name, "description" to description))

    /** POST duplicate-favorite-list Body: { customer_id, list_id } */
    suspend fun duplicateFavoriteList(customerId: String, listId: Long): JSONObject =
        postJson("duplicate-favorite-list", mapOf("customer_id" to customerId, "list_id" to listId))

    /** POST ensure-favorite-list-share-token Body: { customer_id, list_id } → { ok, share_token } */
    suspend fun ensureFavoriteListShareToken(customerId: String, listId: Long): JSONObject =
        postJson("ensure-favorite-list-share-token", mapOf("customer_id" to customerId, "list_id" to listId))

    // ── Eazy Chat ─────────────────────────────────────────
    /**
     * GET ?op=eazy-conv – [user_id] required; optional: page, auto_create, conv_id, list, status (active|closed).
     */
    suspend fun getEazyConversation(
        userId: String,
        extraParams: Map<String, String> = emptyMap()
    ): JSONObject {
        val params = mutableMapOf("user_id" to userId)
        params.putAll(extraParams)
        return call("eazy-conv", params)
    }

    /** GET ?op=get-notifications&owner_id=xxx */
    suspend fun getNotifications(ownerId: String): JSONObject = call(
        "get-notifications",
        mapOf("owner_id" to ownerId)
    )

    /** POST ?op=mark-notification-read – body owner_id, user_id, notification_id */
    suspend fun markNotificationRead(ownerId: String, notificationId: String): JSONObject =
        postJson(
            "mark-notification-read",
            mapOf(
                "owner_id" to ownerId,
                "user_id" to ownerId,
                "notification_id" to notificationId
            )
        )

    /** POST ?op=eazy-conv&new=1 */
    suspend fun eazyConvNew(userId: String): JSONObject =
        postJson("eazy-conv", mapOf("user_id" to userId), mapOf("new" to "1"))

    /** POST ?op=eazy-conv&close=1 */
    suspend fun eazyConvClose(userId: String, conversationId: String): JSONObject =
        postJson(
            "eazy-conv",
            mapOf("user_id" to userId, "conversation_id" to conversationId),
            mapOf("close" to "1")
        )

    /** POST ?op=eazy-conv&delete=1 */
    suspend fun eazyConvDelete(userId: String, conversationId: String): JSONObject =
        postJson(
            "eazy-conv",
            mapOf("user_id" to userId, "conversation_id" to conversationId),
            mapOf("delete" to "1")
        )

    /** POST ?op=eazy-conv&delete_history=1 */
    suspend fun eazyConvDeleteHistory(userId: String): JSONObject =
        postJson("eazy-conv", mapOf("user_id" to userId), mapOf("delete_history" to "1"))

    /** POST ?op=eazy-conv&reopen=1 */
    suspend fun eazyConvReopen(userId: String, conversationId: String): JSONObject =
        postJson(
            "eazy-conv",
            mapOf("user_id" to userId, "conversation_id" to conversationId),
            mapOf("reopen" to "1")
        )

    /** POST ?op=chat-completion – optional function_trigger (Eazy carousel / web startChatFunction) */
    suspend fun chatCompletion(
        userId: String,
        messages: List<Pair<String, String>>,
        conversationId: String?,
        context: Map<String, Any?> = emptyMap(),
        functionTrigger: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl/apps/creator-dispatch?op=chat-completion&_t=${System.currentTimeMillis()}"
        val msgArray = org.json.JSONArray()
        messages.forEach { (role, content) ->
            msgArray.put(org.json.JSONObject().put("role", role).put("content", content))
        }
        val body = org.json.JSONObject()
            .put("user_id", userId)
            .put("messages", msgArray)
            .put("conversation_id", conversationId ?: org.json.JSONObject.NULL)
            .put("context", org.json.JSONObject(context))
        if (!functionTrigger.isNullOrBlank()) {
            body.put("function_trigger", functionTrigger)
        }
        val request = Request.Builder()
            .url(url)
            .post(okhttp3.RequestBody.create("application/json".toMediaType(), body.toString().toByteArray()))
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        val response = client.newCall(request).execute()
        JSONObject(response.body?.string() ?: "{}")
    }

    // ── Mascot ─────────────────────────────────────────
    /** GET ?op=mascot-inventory&owner_id=xxx → { ok, mascots, mood, next_levels, locked_mascots } */
    suspend fun mascotInventory(ownerId: String?): JSONObject {
        val params = if (!ownerId.isNullOrBlank()) mapOf("owner_id" to ownerId) else emptyMap()
        return call("mascot-inventory", params)
    }

    /** POST ?op=mascot-init&owner_id=xxx – Initialize mascot for new user */
    suspend fun mascotInit(ownerId: String): JSONObject =
        call("mascot-init", mapOf("owner_id" to ownerId), "POST")

    /** GET ?op=mascot-quests&owner_id=xxx → { ok, quests } */
    suspend fun mascotQuests(ownerId: String): JSONObject =
        call("mascot-quests", mapOf("owner_id" to ownerId))

    /** POST ?op=mascot-select – Body: { mascot_id } */
    suspend fun mascotSelect(ownerId: String, mascotId: Int): JSONObject =
        postJson("mascot-select", mapOf("mascot_id" to mascotId), mapOf("owner_id" to ownerId))

    /** POST ?op=mascot-interact – Body: { action } (pet, feed, play) */
    suspend fun mascotInteract(ownerId: String, action: String): JSONObject =
        postJson("mascot-interact", mapOf("action" to action), mapOf("owner_id" to ownerId))

    /** GET ?op=mascot-config → { ok, abilities_by_type } */
    suspend fun mascotConfig(): JSONObject = call("mascot-config")

    /** POST ?op=mascot-complete-quest – Body: { quest_id } */
    suspend fun mascotCompleteQuest(ownerId: String, questId: String): JSONObject =
        postJson("mascot-complete-quest", mapOf("quest_id" to questId), mapOf("owner_id" to ownerId))

    /** GET ?op=list-audio-files → { ok, files: [{ id, title, url, duration_sec, owner_id, cover_url? }] } */
    suspend fun listAudioFiles(): JSONObject = call("list-audio-files")

    /** GET ?op=get-creator-audio&owner_id=xxx → { ok, url?, audio_id? } Creator's active audio for auto-play */
    suspend fun getCreatorAudio(ownerId: String): JSONObject =
        call("get-creator-audio", mapOf("owner_id" to ownerId))

    /** POST ?op=upload-audio-file&owner_id=xxx – multipart: audio, duration_sec? */
    suspend fun uploadAudioFile(ownerId: String, audioBytes: ByteArray, contentType: String, durationSec: Int? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val ext = when {
                contentType.contains("mp3") || contentType.contains("mpeg") -> "mp3"
                contentType.contains("wav") -> "wav"
                contentType.contains("ogg") -> "ogg"
                contentType.contains("webm") -> "webm"
                else -> "mp3"
            }
            val mediaType = contentType.toMediaType()
            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", "audio.$ext", okhttp3.RequestBody.create(mediaType, audioBytes))
            durationSec?.let { bodyBuilder.addFormDataPart("duration_sec", it.toString()) }
            val body = bodyBuilder.build()
            val url = "$baseUrl/apps/creator-dispatch?op=upload-audio-file&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}&_t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

    /** POST ?op=set-creator-audio&owner_id=xxx – Body: { audio_id } */
    suspend fun setCreatorAudio(ownerId: String, audioId: String): JSONObject =
        postJson("set-creator-audio", mapOf("audio_id" to audioId), mapOf("owner_id" to ownerId))

    /** POST ?op=delete-audio-file&owner_id=xxx – Body: { audio_id } */
    suspend fun deleteAudioFile(ownerId: String, audioId: String): JSONObject =
        postJson("delete-audio-file", mapOf("audio_id" to audioId), mapOf("owner_id" to ownerId))

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
     * GET /translations?lang=de&type=ui – Pre-computed translations from DB.
     * Fallback chain (server-side): lang → base → en (dialect/script → main → English).
     */
    suspend fun getTranslations(lang: String, type: String = "ui"): Map<String, String> = withContext(Dispatchers.IO) {
        val encodedLang = java.net.URLEncoder.encode(lang, "UTF-8")
        val url = "$baseUrl/translations?lang=$encodedLang&type=$type"
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        if (body.isNullOrBlank() || !response.isSuccessful) return@withContext emptyMap()
        try {
            val json = JSONObject(body)
            val trans = json.optJSONObject("translations") ?: return@withContext emptyMap()
            val map = mutableMapOf<String, String>()
            trans.keys().asSequence().forEach { key ->
                trans.optString(key, "").takeIf { it.isNotBlank() }?.let { map[key] = it }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
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
