package com.eazpire.creator.mockup

import android.content.Context
import android.util.Log
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

    private const val TAG = "EazMockPreview"
    private const val PREFS = "eaz_mock_preview"
    private const val KEY_TRYON_HANDLES = "tryon_handles"
    private const val KEY_TRYON_OFF_HANDLES = "tryon_off_handles"
    private const val MAP_TTL_MS = 300_000L

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

    /** User explicitly turned try-on off — overrides auto-wearing preview. */
    fun isTryOnExplicitlyOff(context: Context, handle: String): Boolean {
        if (handle.isBlank()) return false
        val raw = prefs(context).getString(KEY_TRYON_OFF_HANDLES, null) ?: return false
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).any { arr.optString(it) == handle }
        }.getOrDefault(false)
    }

    fun setTryOnSessionActive(context: Context, handle: String, active: Boolean) {
        if (handle.isBlank()) return
        val onSet = linkedSetOf<String>()
        val offSet = linkedSetOf<String>()
        prefs(context).getString(KEY_TRYON_HANDLES, null)?.let { raw ->
            runCatching {
                val arr = org.json.JSONArray(raw)
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { onSet.add(it) }
                }
            }
        }
        prefs(context).getString(KEY_TRYON_OFF_HANDLES, null)?.let { raw ->
            runCatching {
                val arr = org.json.JSONArray(raw)
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { offSet.add(it) }
                }
            }
        }
        if (active) {
            onSet.add(handle)
            offSet.remove(handle)
        } else {
            onSet.remove(handle)
            offSet.add(handle)
        }
        prefs(context).edit()
            .putString(KEY_TRYON_HANDLES, org.json.JSONArray(onSet.toList()).toString())
            .putString(KEY_TRYON_OFF_HANDLES, org.json.JSONArray(offSet.toList()).toString())
            .apply()
    }

    /** Whether mock images should display on this card (respects explicit off). */
    fun isTryOnDisplayActive(
        context: Context,
        handle: String,
        autoWearing: Boolean
    ): Boolean {
        if (isTryOnExplicitlyOff(context, handle)) return false
        if (isTryOnSessionActive(context, handle)) return true
        return autoWearing
    }

    /** Cached map without network — use after [loadMap] on home. */
    fun peekMap(ownerId: String): JSONObject? {
        if (ownerId.isBlank() || mapOwnerId != ownerId) return null
        if (System.currentTimeMillis() - mapLoadedAt >= MAP_TTL_MS) return null
        return mapCache
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
        if (isTryOnExplicitlyOff(context, handle)) return false
        if (isTryOnSessionActive(context, handle)) return true
        return shouldAutoShowMockOnCard(map, handle, productKey, designId)
    }

    /** Wearing / shop-preview auto display (no manual session). */
    fun shouldAutoShowMockOnCard(
        map: JSONObject?,
        handle: String,
        productKey: String?,
        designId: String?
    ): Boolean {
        if (!MockupPreviewPool.SHOP_PREVIEW_AUTO_DISPLAY_ENABLED) return false
        if (tryOnInfo(map, handle, productKey, designId) == null) return false
        val pk = resolveProductKeyFromMap(map, handle, productKey) ?: return false
        val entry = map?.optJSONObject("mockups")?.optJSONObject(pk) ?: return false
        return MockupPreviewPool.isShopPreviewActive(entry)
    }

    /** Bottom-center Anziehen when preview mocks exist and auto shop preview is off. */
    fun shouldShowManualTryOnButton(
        map: JSONObject?,
        handle: String,
        productKey: String?,
        designId: String?
    ): Boolean {
        if (!isHandleEligibleForTryOn(map, handle, productKey)) return false
        if (tryOnInfo(map, handle, productKey, designId) == null) return false
        if (shouldAutoShowMockOnCard(map, handle, productKey, designId)) return false
        val pk = resolveProductKeyFromMap(map, handle, productKey) ?: return false
        val entry = map?.optJSONObject("mockups")?.optJSONObject(pk) ?: return false
        return MockupPreviewPool.hasPreviewPool(entry)
    }

    fun canUseTryOn(
        map: JSONObject?,
        handle: String,
        productKey: String?,
        designId: String?
    ): Boolean {
        if (!isHandleEligibleForTryOn(map, handle, productKey)) return false
        if (tryOnInfo(map, handle, productKey, designId) == null) return false
        val pk = resolveProductKeyFromMap(map, handle, productKey) ?: return false
        val entry = map?.optJSONObject("mockups")?.optJSONObject(pk) ?: return false
        return MockupPreviewPool.hasPreviewPool(entry)
    }

    /** PLP/PDP hanger icon when customer preview mocks exist for this handle. */
    fun shouldShowTryOnButton(
        map: JSONObject?,
        context: Context,
        handle: String,
        productKey: String?,
        designId: String?
    ): Boolean = canUseTryOn(map, handle, productKey, designId)

    fun resolveProductKeyFromMap(map: JSONObject?, handle: String, productKeyMeta: String?): String? {
        productKeyMeta?.takeIf { it.isNotBlank() }?.let { return it }
        if (map == null || handle.isBlank()) return null
        val htk = map.optJSONObject("handle_to_key")
        htk?.optString(handle)?.takeIf { it.isNotBlank() }?.let { return it }
        val mockups = map.optJSONObject("mockups") ?: return null
        if (mockups.has(handle)) return handle
        val hdm = map.optJSONObject("handle_design_map")
        if (hdm?.has(handle) == true) {
            val keys = mockups.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (handle == k || handle.endsWith("-$k")) return k
            }
        }
        return null
    }

    fun isHandleEligibleForTryOn(map: JSONObject?, handle: String, productKeyMeta: String?): Boolean {
        if (map == null || handle.isBlank()) return false
        if (map.optJSONObject("handle_to_key")?.optString(handle)?.isNotBlank() == true) return true
        if (map.optJSONObject("handle_design_map")?.has(handle) == true) return true
        if (map.optJSONObject("mockups")?.has(handle) == true) return true
        val pk = productKeyMeta?.takeIf { it.isNotBlank() }
        if (pk != null && handle == pk && map.optJSONObject("mockups")?.has(pk) == true) return true
        return false
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
    ): List<String> = withContext(Dispatchers.IO) {
        val base = product.variantImages.ifEmpty { product.images }
        val handle = product.handle
        val metaPk = product.metaProductKey

        if (ownerId.isBlank() || handle.isBlank()) {
            logCardSkip(handle, metaPk, null, product.designId, "no_owner_or_handle")
            return@withContext base
        }

        val data = map ?: loadMap(api, ownerId)
        if (data == null) {
            logCardSkip(handle, metaPk, null, product.designId, "no_map")
            return@withContext base
        }

        val info = tryOnInfo(data, handle, metaPk, product.designId)
        if (info == null) {
            logCardSkip(handle, metaPk, null, product.designId, "no_try_on_info")
            return@withContext base
        }

        if (!isTryOnApparelProduct(info.productKey)) {
            logCardSkip(handle, metaPk, info.productKey, product.designId, "not_apparel")
            return@withContext base
        }

        val show = shouldShowMockOnCard(data, context, handle, metaPk, product.designId)
        if (!show) {
            logCardSkip(handle, metaPk, info.productKey, product.designId, "should_not_show")
            return@withContext base
        }

        val colorMap = runCatching {
            val colorsResp = api.getColorVariants(info.productKey)
            if (colorsResp.optBoolean("ok", false)) parseProductColorHexMap(colorsResp) else emptyMap()
        }.getOrDefault(emptyMap())

        val colorNames = product.rotationColorNames
        val poolSize = kotlin.math.max(
            base.size,
            if (info.previewMockupIds.isNotEmpty()) info.previewMockupIds.size else 1
        ).coerceAtMost(24)
        val resolved = (0 until poolSize).mapNotNull { i ->
            val slice = info.forColorIndex(handle, i)
            val colorName = colorNames.getOrNull(i).orEmpty()
            val hex = resolveProductColorHex(colorName, colorMap)
            slice.cachedByColor[hex]
                ?: slice.cachedByColor.values.firstOrNull { it.isNotBlank() }
                ?: resolveMockupImageUrl(slice, colorName, ownerId, colorMap)
        }

        Log.d(
            TAG,
            "handle=$handle metaPk=$metaPk resolvedPk=${info.productKey} design=${product.designId} " +
                "show=$show cachedColors=${info.cachedByColor.size} resolvedUrls=${resolved.size}"
        )

        if (resolved.isEmpty()) return@withContext base
        return@withContext resolved.distinct()
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
    ): String? = withContext(Dispatchers.IO) {
        if (ownerId.isBlank() || handle.isBlank()) return@withContext fallbackUrl
        val map = loadMap(api, ownerId) ?: return@withContext fallbackUrl
        val info = tryOnInfo(map, handle, productKey, designId) ?: return@withContext fallbackUrl
        if (!isTryOnApparelProduct(info.productKey)) return@withContext fallbackUrl
        if (!shouldShowMockOnCard(map, context, handle, productKey, designId)) return@withContext fallbackUrl

        val cached = info.cachedByColor.values.firstOrNull { it.isNotBlank() }
        if (cached != null) return@withContext cached

        val colorMap = runCatching {
            val r = api.getColorVariants(info.productKey)
            if (r.optBoolean("ok", false)) parseProductColorHexMap(r) else emptyMap()
        }.getOrDefault(emptyMap())
        val color = colorName.ifBlank { "White" }
        resolveMockupImageUrl(info, color, ownerId, colorMap) ?: fallbackUrl
    }

    private fun logCardSkip(
        handle: String,
        metaPk: String?,
        resolvedPk: String?,
        designId: String?,
        reason: String
    ) {
        Log.d(
            TAG,
            "handle=$handle metaPk=$metaPk resolvedPk=$resolvedPk design=$designId skip=$reason"
        )
    }
}
