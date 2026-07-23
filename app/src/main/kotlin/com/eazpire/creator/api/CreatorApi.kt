package com.eazpire.creator.api

import com.eazpire.creator.auth.AuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Creator API Client – analog zu creatorApiFetch im Web.
 * Basis-URL: https://creator-engine.eazpire.workers.dev
 */
class CreatorApi(
    private val baseUrl: String = "https://creator-engine.eazpire.workers.dev",
    private val jwt: String? = null
) {
    private val client: OkHttpClient = CreatorHttpClient.instance
    private val dailyGameClient: OkHttpClient = CreatorHttpClient.dailyGameInstance

    private fun parseJsonResponse(response: Response): JSONObject {
        val raw = response.body?.string().orEmpty()
        if (raw.isBlank()) {
            return JSONObject()
                .put("ok", false)
                .put("error", "empty_response")
                .put("message", "Empty server response.")
        }
        return try {
            JSONObject(raw)
        } catch (_: JSONException) {
            JSONObject()
                .put("ok", false)
                .put("error", "invalid_json")
                .put("message", "Invalid server response.")
        }
    }

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
            if (method == "GET" && CreatorHttpClient.shouldCacheBust(op)) {
                append("&_t=${System.currentTimeMillis()}")
            }
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

    suspend fun convertEazToFiat(ownerId: String, amountEaz: Double): JSONObject =
        postJsonBodyOp(
            "convert-eaz-to-fiat",
            JSONObject().put("owner_id", ownerId).put("amount_eaz", amountEaz)
        )

    suspend fun convertEazToGiftCard(ownerId: String, amountEaz: Double): JSONObject =
        postJsonBodyOp(
            "convert-eaz-to-gift-card",
            JSONObject().put("owner_id", ownerId).put("amount_eaz", amountEaz)
        )

    suspend fun convertEazcToEazg(
        ownerId: String,
        amountEaz: Double,
        contextShortfall: Boolean = false,
    ): JSONObject = postJsonBodyOp(
        "convert-eazc-to-eazg",
        JSONObject()
            .put("owner_id", ownerId)
            .put("amount_eaz", amountEaz)
            .put("context_shortfall", contextShortfall)
    )

    suspend fun getEazEconomyTree(ownerId: String): JSONObject =
        call("get-eaz-economy-tree", mapOf("owner_id" to ownerId))

    suspend fun getEarnedBalance(ownerId: String): JSONObject =
        call("get-earned-balance", mapOf("owner_id" to ownerId))

    suspend fun getEarnedTransactions(
        ownerId: String,
        limit: Int = 100,
        filter: String = "all",
    ): JSONObject = call(
        "get-earned-transactions",
        mapOf(
            "owner_id" to ownerId,
            "limit" to limit.coerceIn(1, 500).toString(),
            "filter" to filter,
        )
    )

    suspend fun activateEazEconomySkill(ownerId: String, skillKey: String): JSONObject =
        postJsonBodyOp(
            "activate-eaz-economy-skill",
            JSONObject().put("owner_id", ownerId).put("skill_key", skillKey)
        )

    suspend fun redeemKickstarterEazBonus(ownerId: String, code: String): JSONObject =
        postJsonBodyOp(
            "redeem-kickstarter-eaz-bonus",
            JSONObject().put("owner_id", ownerId).put("code", code)
        )

    /** GET ?op=get-trial-starter-pack&owner_id= — Starter Pack quotas + previews */
    suspend fun getTrialStarterPack(ownerId: String): JSONObject = call(
        "get-trial-starter-pack",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=get-transactions&owner_id=&limit= */
    suspend fun getTransactions(ownerId: String, limit: Int = 200): JSONObject = call(
        "get-transactions",
        mapOf("owner_id" to ownerId, "limit" to limit.coerceIn(1, 500).toString())
    )

    /** POST ?op=eaz-stripe-checkout&owner_id= — body { eaz } */
    suspend fun eazStripeCheckout(ownerId: String, eaz: Int): JSONObject = postJsonBodyOp(
        "eaz-stripe-checkout",
        JSONObject().put("eaz", eaz),
        mapOf("owner_id" to ownerId)
    )

    /**
     * GET ?op=get-shop-create-product-catalog&region=EU — online catalog + mock_urls.
     * Pass [includeStudioCardPreview] + [ownerId] for My Creations / Designs product-picker
     * previews (Admin default view + placement; design composited client-side).
     */
    suspend fun getShopCreateProductCatalog(
        region: String,
        includeStudioCardPreview: Boolean = false,
        ownerId: String? = null,
        designId: String? = null,
    ): JSONObject = call(
        "get-shop-create-product-catalog",
        buildMap {
            put("region", region)
            if (includeStudioCardPreview) {
                put("include_studio_card_preview", "1")
                ownerId?.takeIf { it.isNotBlank() }?.let {
                    put("owner_id", it)
                    put("logged_in_customer_id", it)
                }
                designId?.takeIf { it.isNotBlank() }?.let { put("design_id", it) }
            }
        }
    )

    /** GET ?op=design-studio-config — mock URLs + print area for shop design studio */
    suspend fun getDesignStudioShopConfig(
        ownerId: String,
        productKey: String,
        colorKey: String? = null
    ): JSONObject = call(
        "design-studio-config",
        buildMap {
            put("owner_id", ownerId)
            put("product_key", productKey)
            if (!colorKey.isNullOrBlank()) put("color_key", colorKey)
        }
    )

    /** POST ?op=printify-studio-test-open — duplicate Printify template for editing session */
    suspend fun printifyStudioTestOpen(
        ownerId: String,
        productKey: String
    ): JSONObject = postDispatchJson(
        op = "printify-studio-test-open",
        queryParams = mapOf(
            "owner_id" to ownerId,
            "logged_in_customer_id" to ownerId
        ),
        body = JSONObject()
            .put("op", "printify-studio-test-open")
            .put("path_prefix", "/apps/creator-dispatch")
            .put("owner_id", ownerId)
            .put("product_key", productKey)
    )

    /** POST ?op=printify-studio-test-sync — placement + preview sync (creates product when printify_product_id omitted) */
    suspend fun printifyStudioTestSync(
        ownerId: String,
        productKey: String,
        printifyProductId: String? = null,
        placement: JSONObject,
        imageUrl: String? = null,
        designImageBase64: String? = null,
        designImageContentType: String? = null
    ): JSONObject = postDispatchJson(
        op = "printify-studio-test-sync",
        queryParams = mapOf(
            "owner_id" to ownerId,
            "logged_in_customer_id" to ownerId
        ),
        body = JSONObject()
            .put("op", "printify-studio-test-sync")
            .put("path_prefix", "/apps/creator-dispatch")
            .put("owner_id", ownerId)
            .put("product_key", productKey)
            .put("placement", placement)
            .apply {
                if (!printifyProductId.isNullOrBlank()) put("printify_product_id", printifyProductId)
                if (!imageUrl.isNullOrBlank()) put("image_url", imageUrl)
                if (!designImageBase64.isNullOrBlank()) put("design_image_base64", designImageBase64)
                if (!designImageContentType.isNullOrBlank()) {
                    put("design_image_content_type", designImageContentType)
                }
            }
    )

    /** GET ?op=printify-studio-test-product-meta — Printify options/variants for studio footer */
    suspend fun printifyStudioTestProductMeta(
        ownerId: String,
        printifyProductId: String
    ): JSONObject = call(
        "printify-studio-test-product-meta",
        mapOf(
            "owner_id" to ownerId,
            "logged_in_customer_id" to ownerId,
            "printify_product_id" to printifyProductId
        )
    )

    /** GET ?op=printify-studio-test-list-drafts — saved drafts for product_key */
    suspend fun printifyStudioTestListDrafts(
        ownerId: String,
        productKey: String
    ): JSONObject = call(
        "printify-studio-test-list-drafts",
        mapOf(
            "owner_id" to ownerId,
            "logged_in_customer_id" to ownerId,
            "product_key" to productKey
        )
    )

    /** POST ?op=printify-studio-test-delete-draft — remove saved studio draft */
    suspend fun printifyStudioTestDeleteDraft(
        ownerId: String,
        draftId: Long
    ): JSONObject = postDispatchJson(
        op = "printify-studio-test-delete-draft",
        queryParams = mapOf(
            "owner_id" to ownerId,
            "logged_in_customer_id" to ownerId
        ),
        body = JSONObject()
            .put("op", "printify-studio-test-delete-draft")
            .put("path_prefix", "/apps/creator-dispatch")
            .put("owner_id", ownerId)
            .put("draft_id", draftId)
    )

    /** GET ?op=printify-studio-existing-product — shop product for inspiration design + product type */
    suspend fun printifyStudioExistingProduct(
        designId: String,
        productKey: String
    ): JSONObject = call(
        "printify-studio-existing-product",
        mapOf(
            "design_id" to designId,
            "product_key" to productKey
        )
    )

    /** POST ?op=printify-studio-test-abandon — discard ephemeral Printify product */
    suspend fun printifyStudioTestAbandon(
        ownerId: String,
        printifyProductId: String
    ): JSONObject = postDispatchJson(
        op = "printify-studio-test-abandon",
        queryParams = mapOf("owner_id" to ownerId),
        body = JSONObject()
            .put("op", "printify-studio-test-abandon")
            .put("path_prefix", "/apps/creator-dispatch")
            .put("owner_id", ownerId)
            .put("printify_product_id", printifyProductId)
    )

    /** GET ?op=get-shop-navigation — Storefront menus for drawer (main + optional audience). */
    suspend fun getShopNavigation(
        mainMenu: String = "main-menu",
        audienceMenu: String = "audience",
    ): JSONObject = call(
        "get-shop-navigation",
        mapOf(
            "main_menu" to mainMenu,
            "audience_menu" to audienceMenu,
        ),
    )

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

    /** GET ?op=get-creator-journey&owner_id=xxx */
    suspend fun getCreatorJourney(ownerId: String): JSONObject = call(
        "get-creator-journey",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=get-journey-product-skill-info&product_key=xxx */
    suspend fun getJourneyProductSkillInfo(productKey: String): JSONObject = call(
        "get-journey-product-skill-info",
        mapOf("product_key" to productKey)
    )

    /** POST ?op=set-starter-selection */
    suspend fun setStarterSelection(
        ownerId: String,
        productKey: String,
        regionCode: String,
    ): JSONObject = postDispatchJson(
        op = "set-starter-selection",
        queryParams = mapOf("owner_id" to ownerId),
        body = JSONObject()
            .put("product_key", productKey)
            .put("region_code", regionCode)
    )

    /** POST ?op=commit-creator-unlock */
    suspend fun commitCreatorUnlock(
        ownerId: String,
        nodeKey: String,
        amount: Double,
    ): JSONObject = postDispatchJson(
        op = "commit-creator-unlock",
        queryParams = mapOf("owner_id" to ownerId),
        body = JSONObject()
            .put("node_key", nodeKey)
            .put("amount", amount)
    )

    /** POST ?op=unlock-creator-node */
    suspend fun unlockCreatorNode(ownerId: String, nodeKey: String): JSONObject = postDispatchJson(
        op = "unlock-creator-node",
        queryParams = mapOf("owner_id" to ownerId),
        body = JSONObject().put("node_key", nodeKey)
    )

    /** GET ?op=get-product-catalog-preferences&owner_id=xxx */
    suspend fun getProductCatalogPreferences(ownerId: String): JSONObject = call(
        "get-product-catalog-preferences",
        mapOf("owner_id" to ownerId),
    )

    /** POST ?op=save-product-catalog-preferences&owner_id=xxx */
    suspend fun saveProductCatalogPreferences(
        ownerId: String,
        preferences: JSONObject,
    ): JSONObject = postDispatchJson(
        op = "save-product-catalog-preferences",
        queryParams = mapOf("owner_id" to ownerId),
        body = JSONObject().put("preferences", preferences),
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

    suspend fun getAccountUsername(ownerId: String): JSONObject =
        call("get-account-username", mapOf("owner_id" to ownerId))

    suspend fun setAccountUsername(ownerId: String, username: String): JSONObject =
        postJson("set-account-username", mapOf("username" to username), mapOf("owner_id" to ownerId))

    suspend fun listCreatorCodeRecipients(ownerId: String, query: String? = null): JSONObject {
        val params = mutableMapOf("owner_id" to ownerId, "limit" to "30")
        query?.trim()?.takeIf { it.length >= 2 }?.let { params["q"] = it }
        return call("list-creator-code-recipients", params)
    }

    suspend fun uploadAccountProfilePicture(
        ownerId: String,
        imageBytes: ByteArray,
        mimeType: String,
        fileName: String,
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl/apps/creator-dispatch?op=upload-account-profile-picture&owner_id=$ownerId&_t=${System.currentTimeMillis()}"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "photo",
                fileName,
                okhttp3.RequestBody.create(mimeType.toMediaType(), imageBytes),
            )
            .build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json")
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        val response = client.newCall(request).execute()
        JSONObject(response.body?.string() ?: "{}")
    }

    /**
     * GET ?op=get-customer-gift-cards&customer_id=xxx&shop=xxx
     * Returns { ok: true, gift_cards: [...] }
     */
    suspend fun getCustomerGiftCards(customerId: String, shop: String): JSONObject = call(
        "get-customer-gift-cards",
        mapOf("customer_id" to customerId, "shop" to shop)
    )

    /** GET ?op=daily-game-state&shop=… — JWT + owner query params (wie Web App-Proxy). */
    suspend fun getDailyGameState(shop: String, ownerId: String? = null): JSONObject {
        val params = mutableMapOf("shop" to shop)
        ownerId?.trim()?.takeIf { it.isNotBlank() }?.let { id ->
            params["logged_in_customer_id"] = id
            params["owner_id"] = id
        }
        return call("daily-game-state", params)
    }

    suspend fun getCommunityGamesCatalog(shop: String, ownerId: String? = null): JSONObject {
        val params = mutableMapOf("shop" to shop)
        ownerId?.trim()?.takeIf { it.isNotBlank() }?.let { id ->
            params["logged_in_customer_id"] = id
        }
        return call("community-games-catalog", params)
    }

    suspend fun startCommunityGameSession(shop: String, ownerId: String, gameSlug: String): JSONObject =
        postJson(
            "community-game-session-start",
            mapOf(
                "game_slug" to gameSlug,
                "logged_in_customer_id" to ownerId,
                "owner_id" to ownerId,
            ),
            mapOf("shop" to shop),
        )

    suspend fun finishCommunityGameSession(
        shop: String,
        ownerId: String,
        sessionToken: String,
        outcome: String,
        score: Int?,
        durationMs: Long?,
    ): JSONObject =
        postJson(
            "community-game-session-finish",
            mapOf(
                "session_token" to sessionToken,
                "logged_in_customer_id" to ownerId,
                "outcome" to outcome,
                "score" to (score ?: 0),
                "duration_ms" to (durationMs ?: 0),
            ),
            mapOf("shop" to shop),
        )

    suspend fun listGamesInviteFriends(ownerId: String, shop: String): JSONObject = call(
        "list-games-invite-friends",
        mapOf("owner_id" to ownerId, "shop" to shop),
    )

    suspend fun listGamesInviteRequests(ownerId: String): JSONObject = call(
        "list-games-invite-requests",
        mapOf("owner_id" to ownerId, "status" to "pending"),
    )

    suspend fun createGamesPlayRequest(ownerId: String, targetId: String, shop: String): JSONObject =
        postJson(
            "create-games-play-request",
            mapOf("target_id" to targetId, "shop" to shop),
            mapOf("owner_id" to ownerId, "shop" to shop),
        )

    suspend fun respondGamesPlayRequest(ownerId: String, requestId: Int, action: String): JSONObject =
        postJson(
            "respond-games-play-request",
            mapOf("request_id" to requestId, "action" to action),
            mapOf("owner_id" to ownerId),
        )

    suspend fun listGamesLifeInvites(ownerId: String): JSONObject = call(
        "list-games-life-invites",
        mapOf("owner_id" to ownerId),
    )

    suspend fun sendGamesLife(
        ownerId: String,
        targetId: String,
        shop: String,
        gameSlug: String? = null,
    ): JSONObject = postJson(
        "send-games-life",
        buildMap {
            put("target_id", targetId)
            put("shop", shop)
            gameSlug?.takeIf { it.isNotBlank() }?.let { put("game_slug", it) }
        },
        mapOf("owner_id" to ownerId, "shop" to shop),
    )

    suspend fun acceptGamesLifeInvite(ownerId: String, inviteId: Int): JSONObject =
        postJson(
            "accept-games-life-invite",
            mapOf("invite_id" to inviteId),
            mapOf("owner_id" to ownerId),
        )

    /** POST ?op=daily-game-play&shop=… Body: owner_id */
    suspend fun postDailyGamePlay(shop: String, ownerId: String): JSONObject =
        postJson("daily-game-play", mapOf("owner_id" to ownerId), mapOf("shop" to shop))

    /** POST ?op=daily-game-play memory_action begin */
    suspend fun postDailyGameMemoryBegin(
        shop: String,
        ownerId: String,
        gameSlug: String = "memory_match",
        playKind: String = "standard",
    ): JSONObject =
        postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("game_slug", gameSlug)
                put("memory_action", "begin")
                if (playKind == "bonus") put("play_kind", "bonus")
            },
        )

    /** POST ?op=daily-game-play memory_action start_play — anchor server deadline when the round begins. */
    suspend fun postDailyGameMemoryStartPlay(shop: String, ownerId: String): JSONObject =
        postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("memory_action", "start_play")
            },
        )

    /** POST ?op=daily-game-play memory_action finish */
    suspend fun postDailyGameMemoryFinish(
        shop: String,
        ownerId: String,
        forfeit: Boolean,
        flipLog: List<Int>,
    ): JSONObject {
        val arr = org.json.JSONArray()
        flipLog.forEach { arr.put(it) }
        return postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("memory_action", "finish")
                put("memory_forfeit", forfeit)
                put("memory_flip_log", arr)
            },
        )
    }

    /** POST ?op=daily-game-play memory_action sync_flip — sync wrong-move count after a mismatch. */
    suspend fun postDailyGameMemorySyncFlip(
        shop: String,
        ownerId: String,
        flipLog: List<Int>,
    ): JSONObject {
        val arr = org.json.JSONArray()
        flipLog.forEach { arr.put(it) }
        return postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("memory_action", "sync_flip")
                put("memory_flip_log", arr)
            },
        )
    }

    suspend fun postDailyGameConnectBegin(
        shop: String,
        ownerId: String,
        gameSlug: String = "connect_four_5x5",
        playKind: String = "standard",
    ): JSONObject =
        postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("game_slug", gameSlug)
                put("connect_action", "begin")
                if (playKind == "bonus") put("play_kind", "bonus")
            },
        )

    suspend fun postDailyGameConnectMove(
        shop: String,
        ownerId: String,
        row: Int,
        col: Int,
    ): JSONObject =
        postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("connect_action", "move")
                put("row", row)
                put("col", col)
            },
        )

    suspend fun postDailyGameConnectFinish(
        shop: String,
        ownerId: String,
        forfeit: Boolean,
    ): JSONObject =
        postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("connect_action", "finish")
                put("connect_forfeit", forfeit)
            },
        )

    suspend fun postDailyGameConnectForfeit(shop: String, ownerId: String): JSONObject =
        postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("connect_action", "forfeit")
            },
        )

    suspend fun postDailyGameSimonBegin(shop: String, ownerId: String, playKind: String = "standard"): JSONObject =
        postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("game_slug", "simon_says")
                put("simon_action", "begin")
                if (playKind == "bonus") put("play_kind", "bonus")
            },
        )

    suspend fun postDailyGameSimonStartInput(shop: String, ownerId: String): JSONObject =
        postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("game_slug", "simon_says")
                put("simon_action", "start_input")
            },
        )

    suspend fun postDailyGameSimonTap(shop: String, ownerId: String, color: Int): JSONObject =
        postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("game_slug", "simon_says")
                put("simon_action", "tap")
                put("color", color)
            },
        )

    suspend fun postDailyGameSimonForfeit(shop: String, ownerId: String): JSONObject =
        postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("game_slug", "simon_says")
                put("simon_action", "forfeit")
            },
        )

    suspend fun postDailyGameSimonClaimWin(shop: String, ownerId: String): JSONObject =
        postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("game_slug", "simon_says")
                put("simon_action", "claim_win")
            },
        )

    suspend fun postDailyGameSimonContinueGamble(shop: String, ownerId: String): JSONObject =
        postDailyGamePlayJson(
            shop,
            JSONObject().apply {
                put("owner_id", ownerId)
                put("shop", shop)
                put("game_slug", "simon_says")
                put("simon_action", "continue_gamble")
            },
        )

    suspend fun getPrizesInventoryList(
        ownerId: String,
        shop: String,
        type: String = "card",
        category: String = "all",
        group: Boolean = false,
    ): JSONObject = postJsonWithShop(
        "prizes-inventory-list",
        shop,
        mapOf(
            "owner_id" to ownerId,
            "type" to type,
            "category" to category,
            "group" to if (group) 1 else 0,
        ),
    )

    suspend fun getArtifactsInventoryList(
        ownerId: String,
        shop: String,
        slotType: String? = null,
    ): JSONObject {
        val body = mutableMapOf<String, Any?>("owner_id" to ownerId)
        if (!slotType.isNullOrBlank() && slotType != "all") body["slot_type"] = slotType
        return postJsonWithShop("artifacts-inventory-list", shop, body)
    }

    suspend fun getArtifactsInventoryState(ownerId: String, shop: String): JSONObject =
        call("artifacts-inventory-state", mapOf("owner_id" to ownerId, "shop" to shop))

    suspend fun getArtifactsLoadout(ownerId: String, shop: String): JSONObject =
        call("artifacts-loadout-get", mapOf("owner_id" to ownerId, "shop" to shop))

    suspend fun getArtifactsSetStatus(ownerId: String, shop: String): JSONObject =
        call("artifacts-set-status", mapOf("owner_id" to ownerId, "shop" to shop))

    suspend fun postArtifactsLoadoutSet(ownerId: String, shop: String, slots: JSONObject): JSONObject {
        val body = JSONObject().apply {
            put("owner_id", ownerId)
            put("slots", slots)
        }
        return postJsonBodyOpWithShop("artifacts-loadout-set", shop, body)
    }

    suspend fun postArtifactsLoadoutVisibility(ownerId: String, shop: String, visibility: JSONObject): JSONObject {
        val body = JSONObject().apply {
            put("owner_id", ownerId)
            put("visibility", visibility)
        }
        return postJsonBodyOpWithShop("artifacts-loadout-visibility", shop, body)
    }

    suspend fun getArtifactsTradeListings(shop: String, limit: Int = 30, scope: String = "market"): JSONObject =
        call("artifacts-trade-listings", mapOf("shop" to shop, "limit" to limit.toString(), "scope" to scope))

    suspend fun postArtifactsTradeListing(ownerId: String, shop: String, instanceId: Int): JSONObject =
        postJsonWithShop(
            "artifacts-trade-listings",
            shop,
            mapOf("owner_id" to ownerId, "instance_id" to instanceId),
        )

    suspend fun deleteArtifactsTradeListing(ownerId: String, shop: String, listingId: Int): JSONObject {
        val body = JSONObject().apply {
            put("owner_id", ownerId)
            put("listing_id", listingId)
        }
        return deleteJsonBodyOpWithShop("artifacts-trade-listings", shop, body, mapOf("listing_id" to listingId.toString()))
    }

    suspend fun postArtifactsTradeOffer(
        ownerId: String,
        shop: String,
        listingId: Int,
        offeredInstanceId: Int,
    ): JSONObject = postJsonWithShop(
        "artifacts-trade-offer",
        shop,
        mapOf(
            "owner_id" to ownerId,
            "listing_id" to listingId,
            "offered_instance_id" to offeredInstanceId,
        ),
    )

    suspend fun postArtifactsMintPrepare(
        ownerId: String,
        shop: String,
        referenceImageUrl: String? = null,
    ): JSONObject {
        val body = mutableMapOf("owner_id" to ownerId)
        referenceImageUrl?.trim()?.takeIf { it.isNotBlank() }?.let { body["reference_image_url"] = it }
        return postJsonWithShop("artifacts-mint-prepare", shop, body)
    }

    suspend fun uploadArtifactsMintReference(ownerId: String, shop: String, imageBytes: ByteArray, contentType: String): JSONObject =
        withContext(Dispatchers.IO) {
            val ext = when {
                contentType.contains("png") -> "png"
                contentType.contains("webp") -> "webp"
                else -> "jpg"
            }
            val mediaType = contentType.toMediaType()
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "mint-reference.$ext", okhttp3.RequestBody.create(mediaType, imageBytes))
                .addFormDataPart("owner_id", ownerId)
                .build()
            val url =
                "$baseUrl/apps/creator-dispatch?op=artifacts-mint-reference-upload&shop=${java.net.URLEncoder.encode(normalizeShopDomain(shop), "UTF-8")}&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}&_t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

    suspend fun postArtifactsMintCharacter(
        ownerId: String,
        shop: String,
        mintIntentId: String,
        confirmPhrase: String,
    ): JSONObject = postJsonWithShop(
        "artifacts-mint-character",
        shop,
        mapOf(
            "owner_id" to ownerId,
            "mint_intent_id" to mintIntentId,
            "confirm_phrase" to confirmPhrase,
        ),
    )

    suspend fun getArtifactsMarketList(shop: String, limit: Int = 30, scope: String = "buy"): JSONObject =
        call("artifacts-market-list", mapOf("shop" to shop, "limit" to limit.toString(), "scope" to scope))

    suspend fun postAdminArtifactsGrantQr(ownerId: String, shop: String): JSONObject =
        postJson(
            "admin-artifacts-grant-qr",
            mapOf("random_product" to true, "note" to "android-admin-test"),
            mapOf(
                "shop" to shop,
                "owner_id" to ownerId,
                "logged_in_customer_id" to ownerId,
            ),
        )

    suspend fun getAdminArtifactsOverview(ownerId: String, shop: String): JSONObject =
        call(
            "admin-artifacts-overview",
            mapOf(
                "shop" to shop,
                "owner_id" to ownerId,
                "logged_in_customer_id" to ownerId,
            ),
        )

    suspend fun postArtifactsMarketBuy(ownerId: String, shop: String, listingId: Int): JSONObject =
        postJsonWithShop(
            "artifacts-market-buy",
            shop,
            mapOf("owner_id" to ownerId, "listing_id" to listingId),
        )

    suspend fun postArtifactsMarketListCharacter(
        ownerId: String,
        shop: String,
        characterId: Int,
        priceEaz: Double,
    ): JSONObject = postJsonWithShop(
        "artifacts-market-list-character",
        shop,
        mapOf(
            "owner_id" to ownerId,
            "character_id" to characterId,
            "price_eaz" to priceEaz,
        ),
    )

    suspend fun postArtifactsMarketCancel(ownerId: String, shop: String, listingId: Int): JSONObject =
        postJsonWithShop(
            "artifacts-market-cancel",
            shop,
            mapOf("owner_id" to ownerId, "listing_id" to listingId),
        )

    suspend fun postArtifactsSetActiveCharacter(
        ownerId: String,
        shop: String,
        characterId: Int?,
    ): JSONObject {
        val body = mutableMapOf<String, Any?>("owner_id" to ownerId)
        if (characterId != null) body["character_id"] = characterId
        return postJsonWithShop("artifacts-set-active-character", shop, body)
    }

    suspend fun postArtifactsClaimQr(ownerId: String, shop: String, token: String): JSONObject =
        postJson(
            "artifacts-claim-qr",
            mapOf("owner_id" to ownerId, "token" to token),
            mapOf(
                "shop" to shop,
                "owner_id" to ownerId,
                "logged_in_customer_id" to ownerId,
            ),
        )

    suspend fun getArtifactsShopDiscountState(ownerId: String, shop: String): JSONObject =
        call("artifacts-shop-discount-state", mapOf("owner_id" to ownerId, "shop" to shop))

    suspend fun postArtifactsShopDiscountApply(ownerId: String, shop: String): JSONObject =
        postJsonWithShop("artifacts-shop-discount-apply", shop, mapOf("owner_id" to ownerId))

    suspend fun postPrizesFuse(
        ownerId: String,
        cardDefinitionId: Int,
        instanceIds: List<Int>,
        shop: String,
    ): JSONObject {
        val body = JSONObject().apply {
            put("owner_id", ownerId)
            put("card_definition_id", cardDefinitionId)
            put("instance_ids", JSONArray(instanceIds))
        }
        return postJsonBodyOpWithShop("prizes-fuse", shop, body)
    }

    suspend fun getPrizesTradeListings(limit: Int = 30, sellerId: String? = null, shop: String = AuthConfig.SHOP_DOMAIN): JSONObject {
        val params = mutableMapOf("limit" to limit.toString())
        sellerId?.trim()?.takeIf { it.isNotBlank() }?.let { params["seller_id"] = it }
        return call("prizes-trade-listings", params + mapOf("shop" to shop))
    }

    suspend fun getPrizesTradeTokens(ownerId: String, shop: String = AuthConfig.SHOP_DOMAIN): JSONObject =
        call("prizes-trade-tokens", mapOf("owner_id" to ownerId, "shop" to shop))

    suspend fun getPrizesTradeMyOffers(ownerId: String, shop: String = AuthConfig.SHOP_DOMAIN): JSONObject =
        call("prizes-trade-my-offers", mapOf("owner_id" to ownerId, "shop" to shop))

    suspend fun getPrizesInventoryState(ownerId: String, shop: String = AuthConfig.SHOP_DOMAIN): JSONObject =
        call("prizes-inventory-state", mapOf("owner_id" to ownerId, "shop" to shop))

    suspend fun getPrizesTradeOfferDetail(
        ownerId: String,
        offerId: Int,
        shop: String = AuthConfig.SHOP_DOMAIN,
    ): JSONObject = call(
        "prizes-trade-offer-detail",
        mapOf(
            "owner_id" to ownerId,
            "offer_id" to offerId.toString(),
            "shop" to shop,
        ),
    )

    suspend fun postPrizesRedeem(ownerId: String, instanceId: Int, shop: String): JSONObject =
        postJsonWithShop("prizes-redeem", shop, mapOf("owner_id" to ownerId, "instance_id" to instanceId))

    suspend fun postPrizesRotate(ownerId: String, instanceId: Int, shop: String): JSONObject =
        postJsonWithShop(
            "prizes-rotate",
            shop,
            mapOf("owner_id" to ownerId, "instance_type" to "card", "instance_id" to instanceId),
        )

    suspend fun postPrizesTradeListing(ownerId: String, instanceType: String, instanceId: Int, shop: String): JSONObject {
        val body = JSONObject().apply {
            put("owner_id", ownerId)
            put("instance_type", instanceType)
            put("instance_id", instanceId)
            put("wishlist", JSONArray())
        }
        return postJsonBodyOpWithShop("prizes-trade-listings", shop, body)
    }

    suspend fun deletePrizesTradeListing(ownerId: String, listingId: Int, shop: String): JSONObject {
        val body = JSONObject().apply {
            put("owner_id", ownerId)
            put("listing_id", listingId)
        }
        return deleteJsonBodyOpWithShop("prizes-trade-listings", shop, body, mapOf("listing_id" to listingId.toString()))
    }

    suspend fun postPrizesTradeOffer(ownerId: String, action: String, offerId: Int, shop: String): JSONObject =
        postJsonWithShop(
            "prizes-trade-offer",
            shop,
            mapOf("owner_id" to ownerId, "action" to action, "offer_id" to offerId),
        )

    suspend fun postPrizesTradeOfferCreate(
        ownerId: String,
        listingId: Int,
        instanceType: String,
        instanceId: Int,
        shop: String = AuthConfig.SHOP_DOMAIN,
    ): JSONObject = postJsonWithShop(
        "prizes-trade-offer",
        shop,
        mapOf(
            "owner_id" to ownerId,
            "action" to "create",
            "listing_id" to listingId,
            "instance_type" to instanceType,
            "instance_id" to instanceId,
        ),
    )

    suspend fun postPrizesCardDiscard(
        ownerId: String,
        instanceId: Int,
        shop: String = AuthConfig.SHOP_DOMAIN,
    ): JSONObject = postJsonWithShop(
        "prizes-card-discard",
        shop,
        mapOf("owner_id" to ownerId, "instance_id" to instanceId),
    )

    suspend fun postPrizesCardGift(
        ownerId: String,
        instanceId: Int,
        targetOwnerId: String,
        shop: String = AuthConfig.SHOP_DOMAIN,
    ): JSONObject = postJsonWithShop(
        "prizes-card-gift",
        shop,
        mapOf(
            "owner_id" to ownerId,
            "instance_id" to instanceId,
            "target_owner_id" to targetOwnerId,
        ),
    )

    private suspend fun postJsonWithShop(op: String, shop: String, body: Map<String, Any?>): JSONObject =
        postJson(op, body, mapOf("shop" to shop))

    private suspend fun postJsonBodyOpWithShop(op: String, shop: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val url =
                "$baseUrl/apps/creator-dispatch?op=$op&shop=${
                    java.net.URLEncoder.encode(shop, "UTF-8")
                }&_t=${System.currentTimeMillis()}"
            val request =
                Request.Builder()
                    .url(url)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                    .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

    private suspend fun deleteJsonBodyOpWithShop(
        op: String,
        shop: String,
        body: JSONObject,
        extraQuery: Map<String, String> = emptyMap(),
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$baseUrl/apps/creator-dispatch?op=$op&shop=${java.net.URLEncoder.encode(shop, "UTF-8")}")
            extraQuery.forEach { (k, v) -> append("&${k}=${java.net.URLEncoder.encode(v, "UTF-8")}") }
            append("&_t=${System.currentTimeMillis()}")
        }
        val request =
            Request.Builder()
                .url(url)
                .delete(body.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
        val response = client.newCall(request).execute()
        JSONObject(response.body?.string() ?: "{}")
    }

    private suspend fun postDailyGamePlayJson(shop: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val url =
                "$baseUrl/apps/creator-dispatch?op=daily-game-play&shop=${
                    java.net.URLEncoder.encode(
                        shop,
                        "UTF-8",
                    )
                }&_t=${System.currentTimeMillis()}"
            val request =
                Request.Builder()
                    .url(url)
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                    .build()
            val response = dailyGameClient.newCall(request).execute()
            parseJsonResponse(response)
        }

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

    /** GET ?op=get-loyalty-status&customer_id=xxx — LoyaliTee stamp card status */
    suspend fun getLoyaltyStatus(customerId: String): JSONObject = call(
        "get-loyalty-status",
        mapOf("customer_id" to customerId)
    )

    /** GET ?op=list-loyalitee-products — eligible Softstyle tees for reward picker */
    suspend fun listLoyaliteeProducts(
        limit: Int = 48,
        offset: Int = 0,
        q: String? = null
    ): JSONObject = call(
        "list-loyalitee-products",
        buildMap {
            put("limit", limit.toString())
            put("offset", offset.toString())
            if (!q.isNullOrBlank()) put("q", q)
        }
    )

    /** POST ?op=redeem-loyalty-reward — reserve reward + discount code */
    suspend fun redeemLoyaltyReward(
        customerId: String,
        rewardId: String,
        productId: String,
        variantId: String
    ): JSONObject = postJsonBodyOp(
        "redeem-loyalty-reward",
        JSONObject()
            .put("customer_id", customerId)
            .put("reward_id", rewardId)
            .put("product_id", productId)
            .put("variant_id", variantId)
    )

    /** POST ?op=revoke-promo-code Body: { customer_id, promo_id } */
    suspend fun revokePromoCode(customerId: String, promoId: String): JSONObject =
        postJson(
            "revoke-promo-code",
            mapOf("customer_id" to customerId, "promo_id" to promoId)
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

    /** GET ?op=get-ui-animation-flags – public UI animation toggles (creator + shop scopes). */
    suspend fun getUiAnimationFlags(): JSONObject = call("get-ui-animation-flags")

    /** GET ?op=get-creator-area-backgrounds – active Creator shell backgrounds (mobile + desktop). */
    suspend fun getCreatorAreaBackgrounds(): JSONObject = call("get-creator-area-backgrounds")

    /** GET ?op=list-customer-mockups&owner_id=xxx → { ok, mockups: [...] } */
    suspend fun listCustomerMockups(ownerId: String, productKey: String? = null): JSONObject = call(
        "list-customer-mockups",
        buildMap {
            put("owner_id", ownerId)
            if (!productKey.isNullOrBlank()) put("product_key", productKey)
        }
    )

    /** GET ?op=get-customer-mockup-map&owner_id=xxx&handle=... — activated preview mockups only */
    suspend fun getCustomerMockupMap(ownerId: String, handle: String? = null): JSONObject = call(
        "get-customer-mockup-map",
        buildMap {
            put("owner_id", ownerId)
            if (!handle.isNullOrBlank()) put("handle", handle)
        }
    )

    /** GET ?op=get-color-variants&product_key=... — catalog color hex map for overlay */
    suspend fun getColorVariants(productKey: String, printAreaKey: String = "front"): JSONObject = call(
        "get-color-variants",
        mapOf("product_key" to productKey, "print_area_key" to printAreaKey)
    )

    /** POST ?op=upload-mockup-photo – multipart: photo + optional person_type */
    suspend fun uploadMockupPhoto(
        ownerId: String,
        photoBytes: ByteArray,
        contentType: String,
        personType: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val ext = when {
            contentType.contains("png") -> "png"
            contentType.contains("webp") -> "webp"
            else -> "jpg"
        }
        val mediaType = contentType.toMediaType()
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "photo",
                "photo.$ext",
                okhttp3.RequestBody.create(mediaType, photoBytes)
            )
        personType?.takeIf { it.isNotBlank() }?.let { builder.addFormDataPart("person_type", it) }
        val url =
            "$baseUrl/apps/creator-dispatch?op=upload-mockup-photo&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}&logged_in_customer_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}&_t=${System.currentTimeMillis()}"
        val request = Request.Builder()
            .url(url)
            .post(builder.build())
            .addHeader("Accept", "application/json")
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        val response = client.newCall(request).execute()
        JSONObject(response.body?.string() ?: "{}")
    }

    /** GET ?op=list-mockup-photos&owner_id=xxx */
    suspend fun listMockupPhotos(ownerId: String): JSONObject = call(
        "list-mockup-photos",
        mapOf("owner_id" to ownerId, "logged_in_customer_id" to ownerId)
    )

    /** GET ?op=list-mockup-products&owner_id=xxx */
    suspend fun listMockupProducts(ownerId: String): JSONObject = call(
        "list-mockup-products",
        mapOf("owner_id" to ownerId, "logged_in_customer_id" to ownerId)
    )

    /** POST ?op=generate-customer-mockups – JSON: product_key + optional photo_ids[] */
    suspend fun generateCustomerMockupsForProduct(
        ownerId: String,
        productKey: String,
        photoIds: List<Long> = emptyList()
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = JSONObject().put("product_key", productKey)
        if (photoIds.isNotEmpty()) {
            body.put("photo_ids", JSONArray().apply { photoIds.forEach { put(it) } })
        }
        val url = buildString {
            append("$baseUrl/apps/creator-dispatch?op=generate-customer-mockups")
            append("&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
            append("&logged_in_customer_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
            append("&_t=${System.currentTimeMillis()}")
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

    /** POST ?op=delete-mockup-photo – Body: { photo_id } */
    suspend fun deleteMockupPhoto(ownerId: String, photoId: Long): JSONObject =
        postJson("delete-mockup-photo", mapOf("photo_id" to photoId), mapOf("owner_id" to ownerId))

    /** POST ?op=regenerate-customer-mockup – Body: { mockup_id } */
    suspend fun regenerateCustomerMockup(ownerId: String, mockupId: Long): JSONObject =
        postJson("regenerate-customer-mockup", mapOf("mockup_id" to mockupId), mapOf("owner_id" to ownerId))

    /** POST ?op=apply-customer-mockup – Body: { mockup_id } (sets Wearing) */
    suspend fun applyCustomerMockup(ownerId: String, mockupId: Long): JSONObject =
        postJson("apply-customer-mockup", mapOf("mockup_id" to mockupId), mapOf("owner_id" to ownerId))

    /** GET ?op=poll-job&job_id=xxx */
    suspend fun pollJob(jobId: String): JSONObject = call(
        "poll-job",
        mapOf("job_id" to jobId)
    )

    /** Reference slot for shop design generate (matches web `reference_images`). */
    data class ShopReferenceImage(
        val url: String,
        val label: String,
        val strength: Int = 60,
        val inspirationMode: String? = null,
        val excludeElements: List<String> = emptyList()
    )

    /**
     * POST ?op=accept-customer-design — Shop "Create Product" AI generate (private shop_design job).
     * [referenceImages] optional A–E data URLs or https URLs (same as web).
     * Returns { ok: true, job_id, status } on 202 or { ok: false, error, message }.
     */
    suspend fun acceptShopCustomerDesignGenerate(
        ownerId: String,
        productKey: String,
        prompt: String,
        referenceImages: List<ShopReferenceImage> = emptyList(),
        designType: String = "classic",
        /** Comma-separated catalog keys, e.g. "tee-1,tee-2" — min one key; falls back to [productKey]. */
        targetProductCsv: String? = null,
        ratio: String = "portrait",
        contentType: String = "design-text",
        styles: List<String> = emptyList(),
        designColors: List<String> = emptyList(),
        backgroundColors: List<String> = emptyList(),
        backgroundMode: String = "transparent",
        languageMode: String = "as-design",
        languageCode: String = "en",
        generatorUiSnapshot: JSONObject? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$baseUrl/apps/creator-dispatch?op=accept-customer-design")
            append("&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
            append("&logged_in_customer_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
            append("&_t=${System.currentTimeMillis()}")
        }
        val targetCsv = targetProductCsv?.trim()?.takeIf { it.isNotEmpty() } ?: productKey
        val stylesJa = JSONArray().apply { styles.forEach { put(it) } }
        val designColorsJa = JSONArray().apply { designColors.forEach { put(it) } }
        val bgColorsJa = JSONArray().apply { backgroundColors.forEach { put(it) } }
        val langObj = JSONObject().put("mode", languageMode)
        if (languageMode == "manual" && languageCode.isNotBlank()) langObj.put("language", languageCode)
        val body = JSONObject().apply {
            put("type", "generate")
            put("product_key", productKey)
            put("shop_design", true)
            put("prompt", prompt.trim())
            put("design_type", designType.ifBlank { "classic" })
            put("target_product", targetCsv)
            put("ratio", ratio.ifBlank { "portrait" })
            put("content_type", contentType.ifBlank { "design-text" })
            put("styles", stylesJa)
            put("design_colors", designColorsJa)
            put("background_colors", bgColorsJa)
            put("background", JSONObject().put("mode", backgroundMode.ifBlank { "transparent" }))
            put("language", langObj)
            if (referenceImages.isNotEmpty()) {
                val arr = JSONArray()
                referenceImages.forEach { r ->
                    val o = JSONObject()
                        .put("url", r.url)
                        .put("label", r.label)
                        .put("strength", r.strength.coerceIn(0, 100))
                    r.inspirationMode?.takeIf { it.isNotBlank() }?.let { o.put("inspiration_mode", it) }
                    if (r.excludeElements.isNotEmpty()) {
                        o.put("exclude_elements", JSONArray().apply { r.excludeElements.forEach { put(it) } })
                        val els = JSONObject()
                        r.excludeElements.forEach { els.put(it, "exclude") }
                        o.put("elements", els)
                    }
                    arr.put(o)
                }
                put("reference_images", arr)
                put("image_url", referenceImages.first().url)
            }
            generatorUiSnapshot?.let { put("generator_ui_snapshot", it) }
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

    /**
     * POST ?op=accept-customer-design — multipart upload for shop_design (image + generator_json).
     */
    suspend fun acceptShopCustomerDesignUpload(
        ownerId: String,
        productKey: String,
        imageBytes: ByteArray,
        mimeType: String,
        fileName: String,
        visibilityPublic: Boolean = true,
        creatorName: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$baseUrl/apps/creator-dispatch?op=accept-customer-design")
            append("&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
            append("&logged_in_customer_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
            append("&_t=${System.currentTimeMillis()}")
        }
        val genJson = JSONObject()
            .put("shop_design", true)
            .put("visibility", if (visibilityPublic) "public" else "private")
            .apply {
                creatorName?.trim()?.takeIf { it.isNotEmpty() }?.let { put("creator_name", it) }
            }
            .toString()
        val media = (mimeType.ifBlank { "image/png" }).toMediaType()
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("type", "upload")
            .addFormDataPart("product_key", productKey)
            .addFormDataPart("generator_json", genJson)
            .addFormDataPart(
                "image",
                fileName.ifBlank { "upload.png" },
                imageBytes.toRequestBody(media)
            )
            .build()
        val request = Request.Builder()
            .url(url)
            .post(multipart)
            .addHeader("Accept", "application/json")
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        val response = client.newCall(request).execute()
        JSONObject(response.body?.string() ?: "{}")
    }

    /** POST ?op=toggle-mockup-preview&owner_id=xxx – Body: { mockup_id, enabled } */
    suspend fun toggleMockupPreview(ownerId: String, mockupId: Long, enabled: Boolean): JSONObject =
        postJson("toggle-mockup-preview", mapOf("mockup_id" to mockupId, "enabled" to enabled), mapOf("owner_id" to ownerId))

    /** POST ?op=toggle-product-shop-preview&owner_id=xxx – Body: { product_key, enabled } */
    suspend fun toggleProductShopPreview(ownerId: String, productKey: String, enabled: Boolean): JSONObject =
        postJson(
            "toggle-product-shop-preview",
            mapOf("product_key" to productKey, "enabled" to enabled),
            mapOf("owner_id" to ownerId)
        )

    /** POST ?op=toggle-auto-mock-display&owner_id=xxx – Body: { enabled } */
    suspend fun toggleAutoMockDisplay(ownerId: String, enabled: Boolean): JSONObject =
        postJson("toggle-auto-mock-display", mapOf("enabled" to enabled), mapOf("owner_id" to ownerId))

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

    /**
     * POST ?op=create-short-ref-link&owner_id=xxx
     * Body: { url, ref_name? } → { ok, token, short_url, landing_path, reused, home? }
     */
    suspend fun createShortRefLink(
        ownerId: String,
        url: String,
        refName: String? = null,
    ): JSONObject = withContext(Dispatchers.IO) {
        val endpoint = buildString {
            append("$baseUrl/apps/creator-dispatch?op=create-short-ref-link")
            append("&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
            append("&_t=${System.currentTimeMillis()}")
        }
        val payload = JSONObject().put("url", url)
        if (!refName.isNullOrBlank()) payload.put("ref_name", refName)
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .apply {
                jwt?.let { addHeader("Authorization", "Bearer $it") }
            }
            .build()
        val response = client.newCall(request).execute()
        parseJsonResponse(response)
    }

    /** GET ?op=video-generator-results&owner_id=xxx → { ok, items: [...] } */
    suspend fun videoGeneratorResults(ownerId: String): JSONObject = call(
        "video-generator-results",
        mapOf("owner_id" to ownerId)
    )

    /** POST multipart ?op=upload-video-motion-ref&owner_id=xxx → { ok, url } */
    suspend fun uploadVideoMotionRef(
        ownerId: String,
        videoBytes: ByteArray,
        filename: String,
        contentType: String?,
    ): JSONObject = withContext(Dispatchers.IO) {
        val mime = contentType?.takeIf { it.isNotBlank() } ?: "video/mp4"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "video",
                filename.ifBlank { "motion.mp4" },
                videoBytes.toRequestBody(mime.toMediaType())
            )
            .build()
        val url =
            "$baseUrl/apps/creator-dispatch?op=upload-video-motion-ref&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}&_t=${System.currentTimeMillis()}"
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json")
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        parseJsonResponse(client.newCall(request).execute())
    }

    /**
     * POST ?op=video-generate — motion-control path.
     * Body: owner_id, motion_video_url, source_image_url, prompt, character_orientation, keep_original_sound
     */
    suspend fun videoGenerateMotionControl(
        ownerId: String,
        motionVideoUrl: String,
        sourceImageUrl: String,
        prompt: String,
        characterOrientation: String,
        keepOriginalSound: Boolean,
    ): JSONObject = postJson(
        "video-generate",
        mapOf(
            "owner_id" to ownerId,
            "motion_video_url" to motionVideoUrl,
            "source_image_url" to sourceImageUrl,
            "prompt" to prompt,
            "character_orientation" to characterOrientation,
            "keep_original_sound" to keepOriginalSound,
            "content_type" to "motion_control",
        ),
        mapOf("owner_id" to ownerId),
    )

    /** POST ?op=video-save-to-library — Body: owner_id, result_id */
    suspend fun videoSaveToLibrary(ownerId: String, resultId: String): JSONObject =
        postJson(
            "video-save-to-library",
            mapOf("owner_id" to ownerId, "result_id" to resultId),
            mapOf("owner_id" to ownerId),
        )

    /** POST ?op=video-studio-link-ingest — Body: owner_id, url, kind → { ok, url } */
    suspend fun videoStudioLinkIngest(ownerId: String, url: String, kind: String): JSONObject =
        postJson(
            "video-studio-link-ingest",
            mapOf("owner_id" to ownerId, "url" to url, "kind" to kind),
            mapOf("owner_id" to ownerId),
        )

    // ── Social Media Manager (IDEA-040 / IDEA-043) ─────────────────────────

    /** GET ?op=creator-social-connections → normalized as { ok, channels:[{channel, connected, skill_unlocked}] } */
    suspend fun creatorSocialChannelsStatus(ownerId: String): JSONObject {
        val raw = call("creator-social-connections", mapOf("owner_id" to ownerId))
        if (!raw.optBoolean("ok", false) && raw.has("channels")) {
            // already shaped
        }
        val channels = raw.optJSONArray("channels") ?: raw.optJSONArray("items")
        if (channels != null) {
            val normalized = org.json.JSONArray()
            for (i in 0 until channels.length()) {
                val o = channels.optJSONObject(i) ?: continue
                val channel = o.optString("channel", o.optString("id", "")).lowercase()
                val connected = o.optBoolean(
                    "connected",
                    o.optBoolean("online", false) || o.optInt("account_count", 0) > 0,
                )
                normalized.put(
                    JSONObject()
                        .put("channel", channel)
                        .put("connected", connected)
                        .put("skill_unlocked", o.optBoolean("skill_unlocked", false))
                        .put("account_count", o.optInt("account_count", if (connected) 1 else 0)),
                )
            }
            return JSONObject().put("ok", true).put("channels", normalized)
        }
        // Map object-keyed response { facebook: {...}, tiktok: {...} }
        val out = org.json.JSONArray()
        val keys = raw.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "ok" || key == "error") continue
            val o = raw.optJSONObject(key) ?: continue
            val connected = o.optBoolean("online", false) ||
                o.optBoolean("connected", false) ||
                o.optInt("account_count", 0) > 0
            out.put(
                JSONObject()
                    .put("channel", key.lowercase())
                    .put("connected", connected)
                    .put("skill_unlocked", o.optBoolean("skill_unlocked", false))
                    .put("account_count", o.optInt("account_count", if (connected) 1 else 0)),
            )
        }
        return JSONObject().put("ok", raw.optBoolean("ok", true)).put("channels", out)
    }

    /** POST ?op=creator-social-oauth-start — platform=android stores return_to in OAuth state */
    suspend fun creatorSocialOAuthStart(
        ownerId: String,
        channel: String,
        platform: String = "android",
    ): JSONObject = postJson(
        "creator-social-oauth-start",
        mapOf(
            "owner_id" to ownerId,
            "channel" to channel,
            "platform" to platform,
            "return_to" to "eazpire://smm-oauth-callback",
        ),
        mapOf("owner_id" to ownerId),
    )

    /** POST ?op=creator-social-disconnect */
    suspend fun creatorSocialDisconnect(ownerId: String, channel: String): JSONObject =
        postJson(
            "creator-social-disconnect",
            mapOf("owner_id" to ownerId, "channel" to channel),
            mapOf("owner_id" to ownerId),
        )

    /** GET ?op=creator-social-compose-assets */
    suspend fun composerAssets(ownerId: String): JSONObject =
        call("creator-social-compose-assets", mapOf("owner_id" to ownerId))

    /** GET ?op=creator-social-post-targets */
    suspend fun creatorSocialPostTargets(ownerId: String, mediaKind: String = "image"): JSONObject =
        call(
            "creator-social-post-targets",
            mapOf("owner_id" to ownerId, "media_kind" to mediaKind),
        )

    /** GET ?op=creator-social-posts-list */
    suspend fun creatorSocialPostsList(ownerId: String, limit: Int = 50): JSONObject =
        call(
            "creator-social-posts-list",
            mapOf("owner_id" to ownerId, "limit" to limit.toString()),
        )

    /** POST ?op=creator-social-posts-create */
    suspend fun creatorSocialPostsCreate(ownerId: String, body: JSONObject): JSONObject {
        val payload = JSONObject(body.toString()).put("owner_id", ownerId)
        return postDispatchJson("creator-social-posts-create", payload, mapOf("owner_id" to ownerId))
    }

    // ── Video Studio (IDEA-028 / IDEA-043) ──────────────────────────────────

    suspend fun videoStudioProjectsList(ownerId: String): JSONObject =
        call("video-studio-project-list", mapOf("owner_id" to ownerId))

    suspend fun videoStudioProjectCreate(
        ownerId: String,
        name: String,
        aspectRatio: String,
    ): JSONObject = postJson(
        "video-studio-project-create",
        mapOf(
            "owner_id" to ownerId,
            "name" to name,
            "aspect_ratio" to aspectRatio,
        ),
        mapOf("owner_id" to ownerId),
    )

    suspend fun videoStudioProjectDelete(ownerId: String, projectId: String): JSONObject =
        postJson(
            "video-studio-project-delete",
            mapOf("owner_id" to ownerId, "project_id" to projectId),
            mapOf("owner_id" to ownerId),
        )

    suspend fun videoStudioProjectUpdate(
        ownerId: String,
        projectId: String,
        body: JSONObject,
    ): JSONObject {
        val payload = JSONObject(body.toString())
            .put("owner_id", ownerId)
            .put("project_id", projectId)
        return postDispatchJson("video-studio-project-save", payload, mapOf("owner_id" to ownerId))
    }

    suspend fun videoStudioAssetsList(ownerId: String, projectId: String): JSONObject =
        call(
            "video-studio-project-assets-list",
            mapOf("owner_id" to ownerId, "project_id" to projectId),
        )

    suspend fun videoStudioAssetUpload(
        ownerId: String,
        projectId: String,
        bytes: ByteArray,
        filename: String,
        mime: String,
    ): JSONObject = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                filename.ifBlank { "asset.bin" },
                bytes.toRequestBody(mime.toMediaType()),
            )
            .addFormDataPart("project_id", projectId)
            .build()
        val url =
            "$baseUrl/apps/creator-dispatch?op=video-studio-asset-upload-simple" +
                "&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}" +
                "&_t=${System.currentTimeMillis()}"
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json")
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        parseJsonResponse(client.newCall(request).execute())
    }

    suspend fun videoStudioAssetDelete(
        ownerId: String,
        projectId: String,
        assetId: String,
    ): JSONObject = postJson(
        "video-studio-asset-delete",
        mapOf("owner_id" to ownerId, "project_id" to projectId, "asset_id" to assetId),
        mapOf("owner_id" to ownerId),
    )

    suspend fun videoStudioExport(ownerId: String, projectId: String): JSONObject =
        postJson(
            "video-studio-export",
            mapOf("owner_id" to ownerId, "project_id" to projectId),
            mapOf("owner_id" to ownerId),
        )

    suspend fun videoStudioAssetCut(
        ownerId: String,
        projectId: String,
        assetId: String,
        atMs: Long,
    ): JSONObject = postJson(
        "video-studio-asset-overwrite",
        mapOf(
            "owner_id" to ownerId,
            "project_id" to projectId,
            "asset_id" to assetId,
            "action" to "cut",
            "at_ms" to atMs,
        ),
        mapOf("owner_id" to ownerId),
    )

    suspend fun videoStudioAssetRemoveAudio(
        ownerId: String,
        projectId: String,
        assetId: String,
    ): JSONObject = postJson(
        "video-studio-asset-overwrite",
        mapOf(
            "owner_id" to ownerId,
            "project_id" to projectId,
            "asset_id" to assetId,
            "action" to "remove_audio",
        ),
        mapOf("owner_id" to ownerId),
    )

    suspend fun videoStudioAssetDuplicate(
        ownerId: String,
        projectId: String,
        assetId: String,
    ): JSONObject = postJson(
        "video-studio-asset-duplicate",
        mapOf("owner_id" to ownerId, "project_id" to projectId, "asset_id" to assetId),
        mapOf("owner_id" to ownerId),
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

    /** POST ?op=reveal-creator-code&owner_id=xxx Body: { entitlement_id } */
    suspend fun revealCreatorCode(ownerId: String, entitlementId: Long): JSONObject =
        postJson("reveal-creator-code", mapOf("entitlement_id" to entitlementId), mapOf("owner_id" to ownerId))

    /** POST ?op=gift-creator-code&owner_id=xxx Body: { code_id, channel, target?, confirmed } */
    suspend fun giftCreatorCode(ownerId: String, codeId: Long, channel: String, target: String? = null): JSONObject {
        val body = mutableMapOf<String, Any>(
            "code_id" to codeId,
            "channel" to channel,
            "confirmed" to true,
        )
        target?.trim()?.takeIf { it.isNotBlank() }?.let { body["target"] = it }
        return postJson("gift-creator-code", body, mapOf("owner_id" to ownerId))
    }

    /** POST ?op=claim-purchase-via-qr&owner_id=xxx */
    suspend fun claimPurchaseViaQr(ownerId: String, qrToken: String): JSONObject =
        postJson("claim-purchase-via-qr", mapOf("qr_token" to qrToken), mapOf("owner_id" to ownerId))

    /** GET ?op=get-creator-code-stats&owner_id=xxx → { ok, stats: { total_generated, total_redeemed, community_size } } */
    suspend fun getCreatorCodeStats(ownerId: String): JSONObject = call(
        "get-creator-code-stats",
        mapOf("owner_id" to ownerId)
    )

    /** GET ?op=list-redeemed-codes&owner_id=xxx */
    suspend fun listRedeemedCreatorCodes(ownerId: String, limit: Int = 20): JSONObject = call(
        "list-redeemed-codes",
        mapOf("owner_id" to ownerId, "limit" to limit.toString()),
    )

    /** GET ?op=get-creator-community-settings&owner_id=xxx */
    suspend fun getCreatorCommunitySettings(ownerId: String): JSONObject = call(
        "get-creator-community-settings",
        mapOf("owner_id" to ownerId),
    )

    /** POST ?op=set-creator-community-opt-in&owner_id=xxx */
    suspend fun setCreatorCommunityOptIn(
        ownerId: String,
        role: String,
        enabled: Boolean,
        communityOwnerId: String? = null,
    ): JSONObject {
        val body = mutableMapOf<String, Any>("role" to role, "enabled" to enabled)
        communityOwnerId?.trim()?.takeIf { it.isNotBlank() }?.let { body["community_owner_id"] = it }
        return postJson("set-creator-community-opt-in", body, mapOf("owner_id" to ownerId))
    }

    /** GET ?op=list-creator-community-members&owner_id=xxx */
    suspend fun listCreatorCommunityMembers(ownerId: String): JSONObject = call(
        "list-creator-community-members",
        mapOf("owner_id" to ownerId),
    )

    /** GET ?op=get-community-designs&owner_id=xxx */
    suspend fun getCommunityDesigns(ownerId: String): JSONObject = call(
        "get-community-designs",
        mapOf("owner_id" to ownerId),
    )

    /** POST ?op=claim-community-design&owner_id=xxx */
    suspend fun claimCommunityDesign(ownerId: String, communityDesignId: Long): JSONObject =
        postJson(
            "claim-community-design",
            mapOf("community_design_id" to communityDesignId),
            mapOf("owner_id" to ownerId),
        )

    /** POST ?op=dismiss-community-design&owner_id=xxx */
    suspend fun dismissCommunityDesign(ownerId: String, communityDesignId: Long): JSONObject =
        postJson(
            "dismiss-community-design",
            mapOf("community_design_id" to communityDesignId),
            mapOf("owner_id" to ownerId),
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

    /** GET ?op=get-system-notifications&owner_id=&audience=creator|shop */
    suspend fun getSystemNotifications(ownerId: String, audience: String): JSONObject = call(
        "get-system-notifications",
        mapOf("owner_id" to ownerId, "audience" to audience.lowercase())
    )

    /** GET ?op=list-system-jobs&owner_id=&audience=creator|shop&limit=&active_only=1 */
    suspend fun listSystemJobs(
        ownerId: String,
        audience: String = "creator",
        limit: Int = 50,
        activeOnly: Boolean = true,
    ): JSONObject {
        val params = mutableMapOf(
            "owner_id" to ownerId,
            "audience" to audience.lowercase(),
            "limit" to limit.toString(),
        )
        if (activeOnly) params["active_only"] = "1"
        return call("list-system-jobs", params)
    }

    /** GET ?op=list-generated&owner_id=xxx&path_prefix=/apps/creator-dispatch → { ok, items: [...] } */
    suspend fun listGenerated(ownerId: String, limit: Int = 200): JSONObject = call(
        "list-generated",
        mapOf(
            "owner_id" to ownerId,
            "limit" to limit.toString(),
            "path_prefix" to "/apps/creator-dispatch"
        )
    )

    /** GET ?op=list-public&limit=200&search=...&cursor=...&filter_*=... */
    suspend fun listPublic(
        limit: Int = 200,
        search: String? = null,
        cursor: String? = null,
        filterParams: Map<String, String> = emptyMap(),
        activePublicOnly: Boolean = false,
        excludeOwnerId: String? = null
    ): JSONObject {
        val params = mutableMapOf("limit" to limit.coerceIn(1, 200).toString())
        search?.takeIf { it.isNotBlank() }?.let { params["search"] = it }
        cursor?.takeIf { it.isNotBlank() }?.let { params["cursor"] = it }
        if (activePublicOnly) params["active_public_only"] = "1"
        excludeOwnerId?.takeIf { it.isNotBlank() }?.let { params["exclude_owner_id"] = it }
        filterParams.forEach { (k, v) -> if (v.isNotBlank()) params[k] = v }
        return call("list-public", params)
    }

    /** Paginate list-public until no next_cursor (cap pages for safety). */
    suspend fun listPublicAll(
        search: String? = null,
        filterParams: Map<String, String> = emptyMap()
    ): Pair<List<JSONObject>, Int> {
        val all = mutableListOf<JSONObject>()
        var cursor: String? = null
        var total = 0
        var pages = 0
        do {
            val data = listPublic(limit = 200, search = search, cursor = cursor, filterParams = filterParams)
            if (!data.optBoolean("ok", false)) break
            if (data.has("total_count")) total = data.optInt("total_count", total)
            val items = data.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                items.optJSONObject(i)?.let { all.add(it) }
            }
            cursor = data.optString("next_cursor", "").trim().ifBlank { null }
            pages += 1
        } while (cursor != null && pages < 40)
        return all to (if (total > 0) total else all.size)
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

    /** GET ?op=get-creations-product-badges — eligible + published counts per design (same as web). */
    suspend fun getCreationsProductBadges(ownerId: String, region: String = "EU", shop: String? = null): JSONObject {
        val params = mutableMapOf("owner_id" to ownerId, "region" to region)
        shop?.takeIf { it.isNotBlank() }?.let { params["shop"] = it }
        return call("get-creations-product-badges", params)
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

    /** GET ?op=get-creator-profile */
    suspend fun getCreatorProfile(
        creatorName: String? = null,
        creatorSlug: String? = null,
        ownerId: String? = null,
        region: String? = null,
        customerId: String? = null,
    ): JSONObject {
        val params = mutableMapOf<String, String>()
        creatorName?.takeIf { it.isNotBlank() }?.let { params["creator_name"] = it }
        creatorSlug?.takeIf { it.isNotBlank() }?.let { params["creator_slug"] = it }
        ownerId?.takeIf { it.isNotBlank() }?.let { params["owner_id"] = it }
        region?.takeIf { it.isNotBlank() }?.let { params["region"] = it }
        customerId?.takeIf { it.isNotBlank() }?.let { params["customer_id"] = it }
        return call("get-creator-profile", params)
    }

    /** GET ?op=get-creator-reviews */
    suspend fun getCreatorReviews(
        creatorName: String? = null,
        creatorSlug: String? = null,
        ownerId: String? = null,
        limit: Int = 100,
        offset: Int = 0
    ): JSONObject {
        val params = mutableMapOf(
            "limit" to limit.coerceIn(1, 100).toString(),
            "offset" to offset.coerceAtLeast(0).toString()
        )
        creatorName?.takeIf { it.isNotBlank() }?.let { params["creator_name"] = it }
        creatorSlug?.takeIf { it.isNotBlank() }?.let { params["creator_slug"] = it }
        ownerId?.takeIf { it.isNotBlank() }?.let { params["owner_id"] = it }
        return call("get-creator-reviews", params)
    }

    /** GET ?op=get-shopify-products filtered by creator */
    suspend fun getCreatorShopProducts(
        creatorName: String? = null,
        creatorSlug: String? = null,
        ownerId: String? = null,
        country: String? = null,
        region: String? = null,
        limit: Int? = null,
        offset: Int? = null
    ): JSONObject {
        val params = mutableMapOf<String, String>()
        creatorName?.takeIf { it.isNotBlank() }?.let { params["creator_name"] = it }
        creatorSlug?.takeIf { it.isNotBlank() }?.let { params["creator_slug"] = it }
        ownerId?.takeIf { it.isNotBlank() }?.let { params["owner_id"] = it }
        country?.takeIf { it.isNotBlank() }?.let { params["country"] = it.uppercase().take(2) }
        region?.takeIf { it.isNotBlank() }?.let { params["region"] = it.uppercase() }
        limit?.takeIf { it > 0 }?.let { params["limit"] = it.coerceIn(1, 100).toString() }
        offset?.takeIf { it >= 0 }?.let { params["offset"] = it.toString() }
        return call("get-shopify-products", params)
    }

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

    /** GET ?op=list-promotions&owner_id=xxx */
    suspend fun listPromotions(ownerId: String): JSONObject =
        call("list-promotions", mapOf("owner_id" to ownerId))

    /** GET ?op=list-shop-creators&sort=recommend|new|subscribed&limit=24 */
    suspend fun listShopCreators(
        sort: String = "recommend",
        limit: Int = 20,
        includeProducts: Boolean = false,
        productsPerCreator: Int = 12,
        customerId: String? = null,
    ): JSONObject =
        call(
            "list-shop-creators",
            buildMap {
                put("sort", sort.lowercase())
                put("limit", limit.coerceIn(4, 50).toString())
                if (includeProducts) {
                    put("include_products", "1")
                    put("products_per_creator", productsPerCreator.coerceIn(1, 20).toString())
                }
                customerId?.takeIf { it.isNotBlank() }?.let { put("customer_id", it) }
            },
        )

    /** POST ?op=follow-creator */
    suspend fun followCreator(
        customerId: String,
        creatorName: String,
        creatorOwnerId: String? = null,
        focusProducts: Boolean = true,
        notifyEmail: Boolean = true,
        notifyEazy: Boolean = true,
        notifyPush: Boolean = true,
    ): JSONObject = postJsonBodyOp(
        "follow-creator",
        JSONObject().apply {
            put("customer_id", customerId)
            put("creator_name", creatorName)
            creatorOwnerId?.takeIf { it.isNotBlank() }?.let { put("creator_owner_id", it) }
            put("focus_products", focusProducts)
            put("notify_email", notifyEmail)
            put("notify_eazy", notifyEazy)
            put("notify_push", notifyPush)
        },
    )

    /** POST ?op=unfollow-creator */
    suspend fun unfollowCreator(customerId: String, creatorName: String): JSONObject =
        postJsonBodyOp(
            "unfollow-creator",
            JSONObject()
                .put("customer_id", customerId)
                .put("creator_name", creatorName),
        )

    /** POST ?op=update-creator-follow */
    suspend fun updateCreatorFollow(
        customerId: String,
        creatorName: String,
        focusProducts: Boolean,
        notifyEmail: Boolean,
        notifyEazy: Boolean,
        notifyPush: Boolean,
    ): JSONObject = postJsonBodyOp(
        "update-creator-follow",
        JSONObject()
            .put("customer_id", customerId)
            .put("creator_name", creatorName)
            .put("focus_products", focusProducts)
            .put("notify_email", notifyEmail)
            .put("notify_eazy", notifyEazy)
            .put("notify_push", notifyPush),
    )

    /** GET ?op=get-creator-follow */
    suspend fun getCreatorFollow(customerId: String?, creatorName: String): JSONObject =
        call(
            "get-creator-follow",
            buildMap {
                put("creator_name", creatorName)
                customerId?.takeIf { it.isNotBlank() }?.let { put("customer_id", it) }
            },
        )

    /** GET ?op=list-active-shop-promotion-products — storefront (no JWT); active creator bundle promos; optional country for 4h slot display */
    suspend fun listActiveShopPromotionProducts(countryCode: String? = null): JSONObject {
        val params = mutableMapOf<String, String>()
        countryCode?.takeIf { it.isNotBlank() }?.let { params["country"] = it }
        return call("list-active-shop-promotion-products", params)
    }

    /** GET ?op=list-home-carousel-products — home carousel pools (promotions, new-arrivals, bestseller, personalizable). */
    suspend fun listHomeCarouselProducts(
        slot: String,
        category: String = "all",
        limit: Int = 100,
        personalizableMode: String = "shoppable",
        countryCode: String? = null,
    ): JSONObject {
        val params = mutableMapOf(
            "slot" to slot,
            "category" to category,
            "limit" to limit.coerceIn(1, 100).toString(),
        )
        if (slot == "personalizable") params["personalizable_mode"] = personalizableMode
        countryCode?.takeIf { it.isNotBlank() }?.let { params["country"] = it }
        return call("list-home-carousel-products", params)
    }

    /** GET ?op=list-home-carousel-bootstrap — all home carousel pools in one round-trip. */
    suspend fun listHomeCarouselBootstrap(
        slots: List<String> = listOf("promotions", "new-arrivals", "bestseller", "personalizable"),
        category: String = "all",
        limit: Int = 24,
        personalizableMode: String = "shoppable",
        countryCode: String? = null,
    ): JSONObject {
        val params = mutableMapOf(
            "slots" to slots.joinToString(","),
            "category" to category,
            "limit" to limit.coerceIn(1, 100).toString(),
        )
        if (slots.any { it == "personalizable" }) params["personalizable_mode"] = personalizableMode
        countryCode?.takeIf { it.isNotBlank() }?.let { params["country"] = it }
        return call("list-home-carousel-bootstrap", params)
    }

    /** POST ?op=resolve-promo-cart — cart line promo prices for country + slot */
    suspend fun resolvePromoCart(countryCode: String, lines: JSONArray): JSONObject =
        postJsonBodyOp(
            "resolve-promo-cart",
            JSONObject().put("country", countryCode).put("lines", lines)
        )

    /** POST ?op=save-promotion – Body JSON (owner_id, name, discount_type, duration_days, product_ids, …) */
    suspend fun savePromotion(body: JSONObject): JSONObject =
        postJsonBodyOp("save-promotion", body)

    /** POST ?op=broadcast-shop-promotion-push – fan-out FCM for new / ending_soon bundle promos (JWT, owner_id match). */
    suspend fun broadcastShopPromotionPush(body: JSONObject): JSONObject =
        postJsonBodyOp("broadcast-shop-promotion-push", body)

    /** POST ?op=delete-promotion – Body { owner_id, promotion_id } */
    suspend fun deletePromotion(ownerId: String, promotionId: String): JSONObject =
        postJsonBodyOp(
            "delete-promotion",
            JSONObject().put("owner_id", ownerId).put("promotion_id", promotionId)
        )

    /** GET ?op=list-products-for-promotion */
    suspend fun listProductsForPromotion(
        ownerId: String,
        promotionId: String? = null,
        q: String? = null,
        collectionHandle: String? = null
    ): JSONObject {
        val params = mutableMapOf("owner_id" to ownerId)
        promotionId?.takeIf { it.isNotBlank() }?.let { params["promotion_id"] = it }
        q?.takeIf { it.isNotBlank() }?.let { params["q"] = it }
        collectionHandle?.takeIf { it.isNotBlank() }?.let { params["collection_handle"] = it }
        return call("list-products-for-promotion", params)
    }

    private suspend fun postJsonBodyOp(
        op: String,
        body: JSONObject,
        queryParams: Map<String, String> = emptyMap(),
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
                .post(okhttp3.RequestBody.create("application/json".toMediaType(), body.toString().toByteArray()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

    private suspend fun putJsonBodyOp(op: String, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/apps/creator-dispatch?op=$op&_t=${System.currentTimeMillis()}"
            val request = Request.Builder()
                .url(url)
                .put(okhttp3.RequestBody.create("application/json".toMediaType(), body.toString().toByteArray()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
                .build()
            val response = client.newCall(request).execute()
            JSONObject(response.body?.string() ?: "{}")
        }

    private suspend fun deleteWithQuery(op: String, params: Map<String, String>): JSONObject =
        withContext(Dispatchers.IO) {
            val url = buildString {
                append("$baseUrl/apps/creator-dispatch?op=$op&_t=${System.currentTimeMillis()}")
                params.forEach { (k, v) ->
                    if (v.isNotBlank()) append("&${k}=${java.net.URLEncoder.encode(v, "UTF-8")}")
                }
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

    /** GET ?op=get-design&design_id=&owner_id= */
    suspend fun getDesign(ownerId: String, designId: String): JSONObject = call(
        "get-design",
        mapOf("design_id" to designId, "owner_id" to ownerId)
    )

    /** PUT ?op=update-design — body must include design_id; optional metadata, prompt, visibility, title, description */
    suspend fun updateDesign(body: JSONObject): JSONObject = putJsonBodyOp("update-design", body)

    /** POST ?op=save-design — save generated job to library (bulk / inactive tab). */
    suspend fun saveDesign(body: JSONObject): JSONObject = postJsonBodyOp("save-design", body)

    /** GET ?op=get-design-published-rows&design_id=&owner_id= */
    suspend fun getDesignPublishedRows(ownerId: String, designId: String, shop: String? = null): JSONObject {
        val params = mutableMapOf("design_id" to designId, "owner_id" to ownerId)
        shop?.takeIf { it.isNotBlank() }?.let { params["shop"] = it }
        return call("get-design-published-rows", params)
    }

    /** POST ?op=batch-unpublish-published — body { published_design_ids: [numbers] } */
    suspend fun batchUnpublishPublished(
        ownerId: String,
        publishedDesignIds: List<Long>,
        shop: String? = null,
    ): JSONObject {
        val body = JSONObject().put(
            "published_design_ids",
            JSONArray().apply { publishedDesignIds.forEach { put(it) } }
        )
        val url = buildString {
            append("$baseUrl/apps/creator-dispatch?op=batch-unpublish-published")
            append("&logged_in_customer_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
            shop?.takeIf { it.isNotBlank() }?.let {
                append("&shop=${java.net.URLEncoder.encode(it, "UTF-8")}")
            }
            append("&_t=${System.currentTimeMillis()}")
        }
        return withContext(Dispatchers.IO) {
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
    }

    /** POST ?op=delete-job&job_id=&owner_id= */
    suspend fun deleteJob(ownerId: String, jobId: String): JSONObject = withContext(Dispatchers.IO) {
        val url = buildString {
            append("$baseUrl/apps/creator-dispatch?op=delete-job")
            append("&job_id=${java.net.URLEncoder.encode(jobId, "UTF-8")}")
            append("&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}")
            append("&_t=${System.currentTimeMillis()}")
        }
        val request = Request.Builder()
            .url(url)
            .post(okhttp3.RequestBody.create("application/json".toMediaType(), ByteArray(0)))
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        val response = client.newCall(request).execute()
        JSONObject(response.body?.string() ?: "{}")
    }

    /** DELETE ?op=delete-design&design_id=&owner_id= */
    suspend fun deleteDesign(ownerId: String, designId: String): JSONObject = deleteWithQuery(
        "delete-design",
        mapOf("design_id" to designId, "owner_id" to ownerId)
    )

    /** PUT ?op=transfer-design — JSON body */
    suspend fun transferDesign(ownerId: String, designId: String, newCreatorName: String): JSONObject =
        putJsonBodyOp(
            "transfer-design",
            JSONObject()
                .put("owner_id", ownerId)
                .put("design_id", designId)
                .put("new_creator_name", newCreatorName)
        )

    /** GET ?op=list-design-metadata-history */
    suspend fun listDesignMetadataHistory(ownerId: String, designId: String, limit: Int = 50): JSONObject = call(
        "list-design-metadata-history",
        mapOf("design_id" to designId, "owner_id" to ownerId, "limit" to limit.toString())
    )

    /** POST ?op=regenerate-design-metadata */
    suspend fun regenerateDesignMetadata(ownerId: String, designId: String): JSONObject =
        postJsonBodyOp(
            "regenerate-design-metadata",
            JSONObject().put("owner_id", ownerId).put("design_id", designId)
        )

    /** POST ?op=sync-design-products */
    suspend fun syncDesignProducts(ownerId: String, designId: String): JSONObject =
        postJsonBodyOp(
            "sync-design-products",
            JSONObject().put("owner_id", ownerId).put("design_id", designId)
        )

    /**
     * Multipart POST to creator-dispatch tool routes (same as web: `/apps/creator-dispatch?path_prefix=/tools/1.0/...`).
     */
    private fun postToolImageMultipart(
        url: String,
        ownerId: String,
        imageBytes: ByteArray,
        fileName: String,
        includeFormatPng: Boolean
    ): okhttp3.Response {
        val bodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", fileName, okhttp3.RequestBody.create("image/png".toMediaType(), imageBytes))
            .addFormDataPart("owner_id", ownerId)
        if (includeFormatPng) bodyBuilder.addFormDataPart("format", "PNG")
        val body = bodyBuilder.build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "image/png, application/json")
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        return client.newCall(request).execute()
    }

    private fun readToolImageBytesOrThrow(
        response: okhttp3.Response,
        emptyLabel: String,
        parseRemoveBgError: Boolean
    ): ByteArray {
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            val errMsg = if (parseRemoveBgError) {
                try {
                    val jo = org.json.JSONObject(errBody)
                    if (jo.optString("code") == "INSUFFICIENT_EAZ") {
                        "Insufficient EAZ balance. Required: ${jo.opt("required")}, Available: ${jo.opt("balance_eaz")}"
                    } else jo.optString("error", errBody.take(200))
                } catch (_: Exception) {
                    errBody.take(200)
                }
            } else {
                try {
                    org.json.JSONObject(errBody).optString("error", errBody.take(200))
                } catch (_: Exception) {
                    errBody.take(200)
                }
            }
            throw RuntimeException(errMsg.ifBlank { "$emptyLabel (${response.code})" })
        }
        return response.body?.bytes() ?: throw RuntimeException("Empty $emptyLabel response")
    }

    /** POST ?path_prefix=/tools/1.0/crop-image&owner_id=xxx – multipart: image
     *  Returns cropped PNG bytes (auto-crop to visible content). */
    suspend fun cropImage(ownerId: String, imageBytes: ByteArray, fileName: String = "upload.png"): ByteArray =
        withContext(Dispatchers.IO) {
            val encOwner = java.net.URLEncoder.encode(ownerId, "UTF-8")
            val dispatch = "$baseUrl/apps/creator-dispatch"
            val primary =
                "$dispatch?path_prefix=${java.net.URLEncoder.encode("/tools/1.0/crop-image", "UTF-8")}&owner_id=$encOwner"
            val fallback = "$dispatch?op=crop-image&owner_id=$encOwner"
            var firstErr: Exception? = null
            for (url in listOf(primary, fallback)) {
                try {
                    postToolImageMultipart(url, ownerId, imageBytes, fileName, includeFormatPng = false).use { resp ->
                        return@withContext readToolImageBytesOrThrow(resp, "Crop failed", parseRemoveBgError = false)
                    }
                } catch (e: Exception) {
                    if (firstErr == null) firstErr = e
                }
            }
            throw firstErr ?: RuntimeException("Crop failed")
        }

    /** POST ?path_prefix=/tools/1.0/remove-background&owner_id=xxx – multipart: image, format=PNG
     *  Returns PNG bytes (background removed via Picsart). Consumes EAZ. */
    suspend fun removeBackground(ownerId: String, imageBytes: ByteArray, fileName: String = "upload.png"): ByteArray =
        withContext(Dispatchers.IO) {
            val encOwner = java.net.URLEncoder.encode(ownerId, "UTF-8")
            val dispatch = "$baseUrl/apps/creator-dispatch"
            val url =
                "$dispatch?path_prefix=${java.net.URLEncoder.encode("/tools/1.0/remove-background", "UTF-8")}&owner_id=$encOwner"
            postToolImageMultipart(url, ownerId, imageBytes, fileName, includeFormatPng = true).use { resp ->
                readToolImageBytesOrThrow(resp, "Remove background failed", parseRemoveBgError = true)
            }
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

    /** GET ?op=list-creator-image-assets */
    suspend fun listCreatorImageAssets(
        ownerId: String,
        creatorName: String,
        imageCategory: String
    ): JSONObject = call(
        "list-creator-image-assets",
        mapOf(
            "owner_id" to ownerId,
            "creator_name" to creatorName,
            "image_category" to imageCategory
        )
    )

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
        variantTitle: String? = null,
        productTitle: String? = null,
        productImage: String? = null
    ): JSONObject = postJson(
        "add-favorite",
        mapOf(
            "customer_id" to customerId,
            "product_id" to productId,
            "variant_id" to variantId,
            "variant_title" to variantTitle,
            "product_title" to productTitle,
            "product_image" to productImage
        )
    )

    /** POST ?op=update-favorite-list-item */
    suspend fun updateFavoriteListItem(
        customerId: String,
        listId: Long,
        itemId: Long,
        variantId: String? = null,
        variantTitle: String? = null,
        productTitle: String? = null,
        productImage: String? = null
    ): JSONObject = postJson(
        "update-favorite-list-item",
        mapOf(
            "customer_id" to customerId,
            "list_id" to listId,
            "item_id" to itemId,
            "variant_id" to variantId,
            "variant_title" to variantTitle,
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

    /** POST ?op=mark-system-notification-read – body user_id, notification_id */
    suspend fun markSystemNotificationRead(userId: String, notificationId: String): JSONObject =
        postJson(
            "mark-system-notification-read",
            mapOf("user_id" to userId, "notification_id" to notificationId)
        )

    /** POST ?op=register-fcm-token – body token, platform (auth: JWT) */
    suspend fun registerFcmToken(token: String, platform: String = "android"): JSONObject =
        postJson(
            "register-fcm-token",
            mapOf("token" to token, "platform" to platform)
        )

    /** POST ?op=unregister-fcm-token – body token (auth: JWT) */
    suspend fun unregisterFcmToken(token: String): JSONObject =
        postJson("unregister-fcm-token", mapOf("token" to token))

    /** GET ?op=android-notification-config (auth: JWT) */
    suspend fun getAndroidNotificationConfig(): JSONObject =
        call("android-notification-config", emptyMap())

    /** GET ?op=get-notification-preferences (auth: JWT) */
    suspend fun getNotificationPreferences(): JSONObject =
        call("get-notification-preferences", emptyMap())

    /** POST ?op=save-notification-preferences – partial body (auth: JWT) */
    suspend fun saveNotificationPreferences(
        shopMaster: Boolean? = null,
        creatorMaster: Boolean? = null,
        shopPatch: Map<String, Boolean>? = null,
        creatorPatch: Map<String, Boolean>? = null
    ): JSONObject {
        val body = org.json.JSONObject()
        if (shopMaster != null) body.put("shop_master", shopMaster)
        if (creatorMaster != null) body.put("creator_master", creatorMaster)
        shopPatch?.let { m ->
            val o = org.json.JSONObject()
            // Nur Push-Kanal patchen — In-App-Einstellungen vom Web bleiben erhalten.
            m.forEach { (k, v) -> o.put(k, org.json.JSONObject().put("push", v)) }
            body.put("shop", o)
        }
        creatorPatch?.let { m ->
            val o = org.json.JSONObject()
            m.forEach { (k, v) -> o.put(k, org.json.JSONObject().put("push", v)) }
            body.put("creator", o)
        }
        return postJsonBodyOp("save-notification-preferences", body)
    }

    /** POST ?op=save-notification-preferences – raw shop/creator JSON patches (channel objects). */
    suspend fun saveNotificationPreferencesRaw(body: JSONObject): JSONObject =
        postJsonBodyOp("save-notification-preferences", body)

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

    /** POST ?op=eazy-conv&delete_all=1 – delete all conversations + messages for user (Eazy settings: clear chat history). */
    suspend fun eazyConvDeleteAllChats(userId: String): JSONObject =
        postJson("eazy-conv", mapOf("user_id" to userId), mapOf("delete_all" to "1"))

    /** POST ?op=eazy-conv&reopen=1 */
    suspend fun eazyConvReopen(userId: String, conversationId: String): JSONObject =
        postJson(
            "eazy-conv",
            mapOf("user_id" to userId, "conversation_id" to conversationId),
            mapOf("reopen" to "1")
        )

    /** POST ?op=eazy-conv – save message (support / ai) */
    suspend fun eazyConvPostMessage(
        userId: String,
        conversationId: String,
        role: String,
        content: String,
        messageType: String = "ai",
    ): JSONObject = postJson(
        "eazy-conv",
        mapOf(
            "user_id" to userId,
            "conversation_id" to conversationId,
            "role" to role,
            "content" to content,
            "message_type" to messageType,
        )
    )

    /** POST ?op=eazy-support-survey */
    suspend fun eazySupportSurvey(
        userId: String,
        conversationId: String,
        solved: Boolean,
        rating: Int?,
        feedback: String?,
    ): JSONObject = postJson(
        "eazy-support-survey",
        buildMap {
            put("user_id", userId)
            put("conversation_id", conversationId)
            put("solved", solved)
            rating?.let { put("rating", it) }
            feedback?.takeIf { it.isNotBlank() }?.let { put("feedback", it) }
        }
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

    /** POST ?op=guide-explain – Eazy Guide Mode hybrid explain */
    suspend fun guideExplain(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl/apps/creator-dispatch?op=guide-explain&_t=${System.currentTimeMillis()}"
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

    /** POST ?op=creator-music-reward&owner_id=xxx – Free EAZ while listening (10s windows) */
    suspend fun postCreatorMusicReward(ownerId: String): JSONObject =
        postJson("creator-music-reward", emptyMap(), mapOf("owner_id" to ownerId))

    /** POST ?op=delete-audio-file&owner_id=xxx – Body: { audio_id } */
    suspend fun deleteAudioFile(ownerId: String, audioId: String): JSONObject =
        postJson("delete-audio-file", mapOf("audio_id" to audioId), mapOf("owner_id" to ownerId))

    /** GET ?op=get-catalog-products&region=EU&design_id=…&design_type=classic */
    suspend fun getCatalogProducts(
        region: String,
        designType: String? = null,
        designId: String? = null,
    ): JSONObject {
        val params = mutableMapOf("region" to region)
        designType?.let { params["design_type"] = it }
        designId?.takeIf { it.isNotBlank() }?.let { params["design_id"] = it }
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

    // --- Gift card detail (same ops as theme/assets/gift-card-detail.js) ---

    private fun normalizeShopDomain(shop: String): String =
        if (shop.contains('.')) shop else "$shop.myshopify.com"

    /** POST with raw JSON body (nested objects). Same as private [postJsonBodyOp]. */
    suspend fun postDispatchJson(op: String, body: JSONObject): JSONObject = postJsonBodyOp(op, body)

    suspend fun getGiftCard(giftCardId: String, shop: String): JSONObject =
        call("get-gift-card", mapOf("gift_card_id" to giftCardId, "shop" to normalizeShopDomain(shop)))

    suspend fun checkGiftCardBuyer(giftCardId: String, customerId: String, shop: String): JSONObject =
        call(
            "check-gift-card-buyer",
            mapOf(
                "gift_card_id" to giftCardId,
                "customer_id" to customerId,
                "shop" to normalizeShopDomain(shop)
            )
        )

    suspend fun getGiftCardEmailTemplate(giftCardId: String): JSONObject =
        call("get-gift-card-email-template", mapOf("gift_card_id" to giftCardId))

    suspend fun getGiftCardSelection(giftCardId: String, customerId: String?): JSONObject {
        val params = mutableMapOf("gift_card_id" to giftCardId)
        if (!customerId.isNullOrBlank()) params["customer_id"] = customerId
        return call("get-gift-card-selection", params)
    }

    suspend fun getGiftCardGenerationCount(giftCardId: String, shop: String, type: String): JSONObject =
        call(
            "get-gift-card-generation-count",
            mapOf(
                "gift_card_id" to giftCardId,
                "shop" to normalizeShopDomain(shop),
                "type" to type
            )
        )

    suspend fun getShopifyProducts(shop: String): JSONObject =
        call("get-shopify-products", mapOf("shop" to normalizeShopDomain(shop)))

    suspend fun saveGiftCardSelection(giftCardId: String, productIds: List<String>): JSONObject {
        val arr = org.json.JSONArray()
        productIds.forEach { arr.put(it) }
        val body = JSONObject().put("gift_card_id", giftCardId).put("product_ids", arr)
        return postJsonBodyOp("save-gift-card-selection", body)
    }

    suspend fun saveGiftCardEmailTemplate(body: JSONObject): JSONObject =
        postJsonBodyOp("save-gift-card-email-template", body)

    suspend fun sendGiftCardEmail(giftCardId: String, shop: String): JSONObject {
        val body = JSONObject()
            .put("gift_card_id", giftCardId)
            .put("shop", normalizeShopDomain(shop))
        return postJsonBodyOp("send-gift-card-email", body)
    }

    suspend fun sendGiftCardPostcard(giftCardId: String, shop: String): JSONObject {
        val body = JSONObject()
            .put("gift_card_id", giftCardId)
            .put("shop", normalizeShopDomain(shop))
        return postJsonBodyOp("send-gift-card-postcard", body)
    }

    suspend fun generateGiftCardText(
        prompt: String,
        giftCardId: String,
        senderName: String,
        recipientName: String
    ): JSONObject {
        val body = JSONObject()
            .put("prompt", prompt)
            .put("gift_card_id", giftCardId)
            .put("sender_name", senderName)
            .put("recipient_name", recipientName)
        return postJsonBodyOp("generate-gift-card-text", body)
    }

    suspend fun generateGiftCardImageJson(
        prompt: String,
        giftCardId: String?,
        customerId: String?,
        imageUrl: String? = null
    ): JSONObject {
        val body = JSONObject().put("prompt", prompt)
        giftCardId?.let { body.put("gift_card_id", it) }
        customerId?.let { body.put("customer_id", it) }
        imageUrl?.let { body.put("image_url", it) }
        return postJsonBodyOp("generate-gift-card-image", body)
    }

    suspend fun generateGiftCardImageMultipart(
        prompt: String,
        giftCardId: String,
        customerId: String,
        imageBytes: ByteArray,
        fileName: String
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = "$baseUrl/apps/creator-dispatch?op=generate-gift-card-image&_t=${System.currentTimeMillis()}"
        val mediaType = "image/*".toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("gift_card_id", giftCardId)
            .addFormDataPart("customer_id", customerId)
            .addFormDataPart("image", fileName, okhttp3.RequestBody.create(mediaType, imageBytes))
            .build()
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Accept", "application/json")
            .apply { jwt?.let { addHeader("Authorization", "Bearer $it") } }
            .build()
        val response = client.newCall(request).execute()
        JSONObject(response.body?.string() ?: "{}")
    }

    /** GET ?op=list-design-automations&owner_id=&filter=active|scheduled|expired */
    suspend fun listDesignAutomations(ownerId: String, filter: String): JSONObject =
        call("list-design-automations", mapOf("owner_id" to ownerId, "filter" to filter))

    /** POST ?op=create-design-automation */
    suspend fun createDesignAutomation(body: JSONObject): JSONObject =
        postDispatchJson("create-design-automation", emptyMap(), body)

    /** POST ?op=end-design-automation body: { id } */
    suspend fun endDesignAutomation(automationId: Long): JSONObject =
        postDispatchJson("end-design-automation", emptyMap(), JSONObject().put("id", automationId))

    /** POST ?op=erase-optional-customer-data */
    suspend fun eraseOptionalCustomerData(
        ownerId: String,
        confirmEmail: String,
        locale: String,
        firstName: String,
        scopeConsent: Boolean,
        privacyConsent: Boolean,
    ): JSONObject = postJson(
        "erase-optional-customer-data",
        mapOf(
            "owner_id" to ownerId,
            "confirm_email" to confirmEmail,
            "locale" to locale,
            "first_name" to firstName,
            "consents" to mapOf(
                "optional_erase" to mapOf(
                    "scope" to scopeConsent,
                    "privacy" to privacyConsent,
                ),
            ),
        ),
    )

    /** GET ?op=verify-status */
    suspend fun verifyStatus(ownerId: String): JSONObject = call(
        "verify-status",
        mapOf("owner_id" to ownerId, "logged_in_customer_id" to ownerId),
    )

    /** POST ?op=verify-accept-terms */
    suspend fun verifyAcceptTerms(ownerId: String): JSONObject = postJson(
        "verify-accept-terms",
        mapOf("confirm_16_plus" to true),
        mapOf("owner_id" to ownerId, "logged_in_customer_id" to ownerId),
    )

    /** GET ?op=verify-next-item */
    suspend fun verifyNextItem(ownerId: String, entityType: String): JSONObject = call(
        "verify-next-item",
        mapOf(
            "owner_id" to ownerId,
            "logged_in_customer_id" to ownerId,
            "entity_type" to entityType,
        ),
    )

    /** GET ?op=verify-completed-list */
    suspend fun verifyCompletedList(ownerId: String, entityType: String, outcome: String = "all"): JSONObject = call(
        "verify-completed-list",
        mapOf(
            "owner_id" to ownerId,
            "logged_in_customer_id" to ownerId,
            "entity_type" to entityType,
            "outcome" to outcome,
        ),
    )

    /** POST ?op=verify-submit-vote */
    suspend fun verifySubmitVote(
        ownerId: String,
        itemId: Long,
        vote: String,
        rejectReasons: List<String>? = null,
        note: String? = null,
        entityType: String? = null,
    ): JSONObject = postJson(
        "verify-submit-vote",
        buildMap<String, Any?> {
            put("item_id", itemId)
            put("vote", vote)
            rejectReasons?.takeIf { it.isNotEmpty() }?.let { put("reject_reasons", it) }
            note?.takeIf { it.isNotBlank() }?.let { put("note", it) }
            entityType?.takeIf { it.isNotBlank() }?.let { put("entity_type", it) }
        },
        mapOf("owner_id" to ownerId, "logged_in_customer_id" to ownerId),
    )

    /** POST ?op=verify-admin-approve-all — admin only */
    suspend fun verifyAdminApproveAll(ownerId: String): JSONObject = postJson(
        "verify-admin-approve-all",
        emptyMap(),
        mapOf("owner_id" to ownerId, "logged_in_customer_id" to ownerId),
    )

    /** POST ?op=delete-shopify-customer */
    suspend fun deleteShopifyCustomer(
        ownerId: String,
        confirmEmail: String,
        locale: String,
        firstName: String,
        irreversibleConsent: Boolean,
        retentionConsent: Boolean,
        publicDesignsConsent: Boolean,
    ): JSONObject = postJson(
        "delete-shopify-customer",
        mapOf(
            "confirm" to true,
            "owner_id" to ownerId,
            "confirm_email" to confirmEmail,
            "locale" to locale,
            "first_name" to firstName,
            "consents" to mapOf(
                "schedule_account_deletion" to mapOf(
                    "irreversible" to irreversibleConsent,
                    "retention" to retentionConsent,
                    "public_designs_ack" to publicDesignsConsent,
                ),
            ),
        ),
    )
}

data class ApiLanguageItem(val code: String, val label: String, val flagCode: String)
data class ApiLanguageChildren(val dialects: List<ApiLanguageItem>, val scripts: List<ApiLanguageItem>)
data class ApiLanguagesResponse(
    val standard: List<ApiLanguageItem>,
    val children: Map<String, ApiLanguageChildren>
)
