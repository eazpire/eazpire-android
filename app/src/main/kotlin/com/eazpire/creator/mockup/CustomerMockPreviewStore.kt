package com.eazpire.creator.mockup

import android.content.Context
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.ui.MockupTryOnInfo
import com.eazpire.creator.ui.isTryOnApparelProduct
import com.eazpire.creator.ui.parseMockupTryOnInfo
import com.eazpire.creator.ui.parseProductColorHexMap
import com.eazpire.creator.ui.resolveMockupImageUrl
import com.eazpire.creator.ui.resolveProductColorHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Shared customer mock map + try-on session (parity with web collection-try-on / personalized-product).
 */
object CustomerMockPreviewStore {

    private const val PREFS = "eaz_mock_preview"
    private const val KEY_TRYON_HANDLES = "tryon_handles"
    private const val MAP_TTL_MS = 60_000L

    @Volatile
    private var mapCache: JSONObject? = null

    @Volatile
    private var mapOwnerId: String? = null

    @Volatile
    private var mapLoadedAt: Long = 0L

    @Volatile
    var revision: Int = 0
        private set

    fun invalidate() {
        mapCache = null
        mapOwnerId = null
        mapLoadedAt = 0L
        revision++
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isTryOnSessionActive(context: Context, handle: String): Boolean {
        if (handle.isBlank()) return false
        val raw = prefs(context).getString(KEY_TRYON_HANDLES, null) ?: return false
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).any { arr.optString(it) == handle }
        }.getOrDefault(false)
    }

    fun setTryOnSessionActive(context: Context, handle: String, active: Boolean) {
        if (handle.isBlank()) return
        val set = linkedSetOf<String>()
        val raw = prefs(context).getString(KEY_TRYON_HANDLES, null)
        if (raw != null) {
            runCatching {
                val arr = org.json.JSONArray(raw)
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { set.add(it) }
                }
            }
        }
        if (active) set.add(handle) else set.remove(handle)
        prefs(context).edit()
            .putString(KEY_TRYON_HANDLES, org.json.JSONArray(set.toList()).toString())
            .apply()
    }

    suspend fun loadMap(api: CreatorApi, ownerId: String, force: Boolean = false): JSONObject? =
        withContext(Dispatchers.IO) {
            if (ownerId.isBlank()) return@withContext null
            val now = System.currentTimeMillis()
            if (
                !force &&
                mapCache != null &&
                mapOwnerId == ownerId &&
                now - mapLoadedAt < MAP_TTL_MS
            ) {
                return@withContext mapCache
            }
            val resp = api.getCustomerMockupMap(ownerId, handle = null)
            if (!resp.optBoolean("ok", false)) return@withContext null
            mapCache = resp
            mapOwnerId = ownerId
            mapLoadedAt = now
            resp
        }

    fun tryOnInfo(map: JSONObject?, handle: String, productKey: String?, designId: String?): MockupTryOnInfo? {
        if (map == null || handle.isBlank()) return null
        return parseMockupTryOnInfo(map, handle, productKey, designId)
    }

    fun shouldShowMockOnCard(
        map: JSONObject?,
        context: Context,
        handle: String,
        productKey: String?,
        designId: String?
    ): Boolean {
        if (tryOnInfo(map, handle, productKey, designId) == null) return false
        if (isTryOnSessionActive(context, handle)) return true
        val pk = resolveProductKeyFromMap(map, handle, productKey) ?: return false
        val entry = map?.optJSONObject("mockups")?.optJSONObject(pk) ?: return false
        return entry.optBoolean("use_as_preview", false) || entry.optInt("use_as_preview", 0) == 1
    }

    private fun resolveProductKeyFromMap(map: JSONObject?, handle: String, productKeyMeta: String?): String? {
        productKeyMeta?.takeIf { it.isNotBlank() }?.let { return it }
        if (map == null) return null
        val htk = map.optJSONObject("handle_to_key")
        htk?.optString(handle)?.takeIf { it.isNotBlank() }?.let { return it }
        val mockups = map.optJSONObject("mockups") ?: return null
        val keys = mockups.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (handle == k || handle.endsWith("-$k")) return k
        }
        return null
    }

    /**
     * Replace PLP/carousel images with wearing mock URLs when applicable.
     */
    suspend fun resolveCardImages(
        context: Context,
        api: CreatorApi,
        ownerId: String,
        product: ShopifyProductsApi.ProductItem,
        map: JSONObject?
    ): List<String> {
        val base = product.variantImages.ifEmpty { product.images }
        val handle = product.handle
        val pk = product.metaProductKey
        if (ownerId.isBlank() || handle.isBlank() || !isTryOnApparelProduct(pk)) return base
        val data = map ?: loadMap(api, ownerId) ?: return base
        if (!shouldShowMockOnCard(data, context, handle, pk, product.designId)) return base

        val info = tryOnInfo(data, handle, pk, product.designId) ?: return base
        val cached = info.cachedByColor.values.distinct().filter { it.isNotBlank() }
        if (cached.isNotEmpty()) {
            return if (cached.size >= base.size.coerceAtLeast(1)) {
                cached.take(base.size.coerceAtLeast(1))
            } else {
                List(base.size.coerceAtLeast(1)) { i -> cached[i % cached.size] }
            }
        }

        val colorMap = if (!pk.isNullOrBlank()) {
            runCatching {
                val colorsResp = api.getColorVariants(pk)
                if (colorsResp.optBoolean("ok", false)) parseProductColorHexMap(colorsResp) else emptyMap()
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        val firstColor = ""
        val url = resolveMockupImageUrl(info, firstColor, ownerId, colorMap)
        return if (!url.isNullOrBlank()) listOf(url) else base
    }

    suspend fun resolveSingleImageUrl(
        context: Context,
        api: CreatorApi,
        ownerId: String,
        handle: String,
        productKey: String?,
        designId: String?,
        fallbackUrl: String?,
        colorName: String = ""
    ): String? {
        if (ownerId.isBlank() || handle.isBlank()) return fallbackUrl
        val map = loadMap(api, ownerId) ?: return fallbackUrl
        if (!shouldShowMockOnCard(map, context, handle, productKey, designId)) return fallbackUrl
        val info = tryOnInfo(map, handle, productKey, designId) ?: return fallbackUrl
        val cached = info.cachedByColor.values.firstOrNull { it.isNotBlank() }
        if (cached != null) return cached
        val colorMap = if (!productKey.isNullOrBlank()) {
            runCatching {
                val r = api.getColorVariants(productKey)
                if (r.optBoolean("ok", false)) parseProductColorHexMap(r) else emptyMap()
            }.getOrDefault(emptyMap())
        } else emptyMap()
        val color = colorName.ifBlank { "White" }
        return resolveMockupImageUrl(info, color, ownerId, colorMap) ?: fallbackUrl
    }
}
