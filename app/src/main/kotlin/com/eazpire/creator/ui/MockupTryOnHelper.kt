package com.eazpire.creator.ui

import android.util.Log
import com.eazpire.creator.mockup.MockupPreviewPool
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Apparel product keys that support try-on (matches eaz-pdp-main.liquid). */
val TRY_ON_APPAREL_PRODUCT_KEYS = setOf(
    "unisex-softstyle-cotton-tee",
    "womens-favorite-tee",
    "unisex-jersey-tank",
    "backprint-unisex-hooded-sweatshirt",
    "unisex-crewneck-sweatshirt",
    "unisex-hooded-sweatshirt",
    "unisex-hoodie"
)

data class MockupTryOnInfo(
    val mockupId: Long,
    val designId: String?,
    val productKey: String,
    /** color hex (no #) → rendered image URL */
    val cachedByColor: Map<String, String>,
    val previewMockupIds: List<Long> = emptyList(),
    val shopPreviewEnabled: Boolean = true,
    private val cachedByMockupId: Map<Long, Map<String, String>> = emptyMap()
) {
    fun forColorIndex(handle: String, colorIndex: Int): MockupTryOnInfo {
        val picked = when {
            previewMockupIds.isNotEmpty() && shopPreviewEnabled -> {
                val seed = "$handle:$colorIndex"
                previewMockupIds[MockupPreviewPool.hashString(seed) % previewMockupIds.size]
            }
            else -> mockupId
        }
        val cache = cachedByMockupId[picked] ?: if (picked == mockupId) cachedByColor else emptyMap()
        return copy(mockupId = picked, cachedByColor = cache)
    }

    fun withPreviewPoolIndex(index: Int): MockupTryOnInfo {
        val ids = when {
            previewMockupIds.isNotEmpty() -> previewMockupIds
            mockupId > 0 -> listOf(mockupId)
            else -> emptyList()
        }
        if (ids.isEmpty()) return this
        val picked = ids[((index % ids.size) + ids.size) % ids.size]
        val cache = cachedByMockupId[picked] ?: if (picked == mockupId) cachedByColor else emptyMap()
        return copy(mockupId = picked, cachedByColor = cache)
    }
}

private val COLOR_NAME_TO_HEX = mapOf(
    "white" to "FFFFFF",
    "weiß" to "FFFFFF",
    "weiss" to "FFFFFF",
    "black" to "111111",
    "schwarz" to "111111",
    "navy" to "0B1F3A",
    "red" to "D11A2A",
    "rot" to "D11A2A",
    "purple" to "6B21A8",
    "lila" to "6B21A8",
    "sport grey" to "9CA3AF",
    "sport-grey" to "9CA3AF",
    "sport grau" to "9CA3AF",
    "dark heather" to "4B5563",
    "dark-heather" to "4B5563",
    "military green" to "4B5D3A",
    "natural" to "EFE7D6",
    "sand" to "D8C7A0",
    "daisy" to "F4D000",
    "light blue" to "7FB7FF",
    "hellblau" to "7FB7FF",
    "tropical blue" to "00A3D7",
    "tropisches blau" to "00A3D7",
    "dark chocolate" to "3A2618",
    "heather navy" to "2B3A55",
    "indigo" to "4F46E5",
    "coral" to "F87171",
    "teal" to "14B8A6",
    "burgundy" to "800020",
    "yellow" to "FACC15",
    "gelb" to "FACC15",
    "orange" to "EA580C",
    "pink" to "EC4899",
    "green" to "16A34A",
    "grün" to "16A34A",
    "gruen" to "16A34A",
    "blue" to "2563EB",
    "blau" to "2563EB",
    "royal" to "1F4FE0",
    "grey" to "9CA3AF",
    "gray" to "9CA3AF",
    "grau" to "9CA3AF",
    "brown" to "78350F",
    "braun" to "78350F"
)

/** German/English handle aliases → canonical English handle for catalog lookup. */
private val COLOR_HANDLE_ALIASES = mapOf(
    "tropisches-blau" to "tropical-blue",
    "hellblau" to "light-blue",
    "sport-grau" to "sport-grey",
    "dunkel-grau" to "dark-heather",
    "dunkelgrau" to "dark-heather",
    "militaergruen" to "military-green",
    "militärgrün" to "military-green",
    "dunkel-navy" to "heather-navy",
    "koenigsblau" to "royal",
    "königsblau" to "royal"
)

fun normalizeColorNameKey(value: String): String =
    value.lowercase().replace(Regex("[_-]+"), " ").replace(Regex("\\s+"), " ").trim()

fun colorHandle(value: String): String =
    normalizeColorNameKey(value).replace(" ", "-")

fun parseProductColorHexMap(json: JSONObject): Map<String, String> {
    val map = linkedMapOf<String, String>()
    val arr = json.optJSONArray("variants") ?: return map
    for (i in 0 until arr.length()) {
        val row = arr.optJSONObject(i) ?: continue
        val hex = row.optString("color_hex").replace("#", "").uppercase()
        val name = row.optString("color_name").trim()
        if (hex.length != 6 || name.isBlank()) continue
        map[normalizeColorNameKey(name)] = hex
        map[colorHandle(name)] = hex
    }
    return map
}

fun resolveProductColorHex(colorName: String, productColorMap: Map<String, String>): String {
    if (colorName.isBlank()) return "FFFFFF"
    val normalized = normalizeColorNameKey(colorName)
    productColorMap[normalized]?.let { return it }
    val handle = colorHandle(colorName)
    productColorMap[handle]?.let { return it }
    COLOR_HANDLE_ALIASES[handle]?.let { alias ->
        productColorMap[alias]?.let { return it }
        productColorMap[normalizeColorNameKey(alias.replace("-", " "))]?.let { return it }
    }
    for ((key, hex) in productColorMap) {
        if (colorHandle(key) == handle) return hex
    }
    return colorNameToHex(colorName)
}

fun colorNameToHex(colorName: String): String {
    val key = normalizeColorNameKey(colorName)
    COLOR_NAME_TO_HEX[key]?.let { return it }
    val handle = colorHandle(colorName)
    COLOR_HANDLE_ALIASES[handle]?.let { alias ->
        COLOR_NAME_TO_HEX[normalizeColorNameKey(alias.replace("-", " "))]?.let { return it }
    }
    return "FFFFFF"
}

fun isTryOnApparelProduct(productKey: String?): Boolean {
    val pk = productKey?.trim()?.lowercase().orEmpty()
    return pk.isNotBlank() && TRY_ON_APPAREL_PRODUCT_KEYS.contains(pk)
}

private const val MOCKUP_TRY_ON_TAG = "EazMockPreview"

private fun inferDesignIdFromCache(
    data: JSONObject,
    previewIds: List<Long>,
    primaryMockupId: Long
): String? {
    val cachedVariants = data.optJSONObject("cached_variants") ?: return null
    val designs = linkedSetOf<String>()
    val ids = previewIds.ifEmpty { listOf(primaryMockupId) }
    for (id in ids) {
        val rows = cachedVariants.optJSONArray(id.toString()) ?: continue
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            row.optString("design_id").takeIf { it.isNotBlank() }?.let { designs.add(it) }
        }
    }
    return if (designs.size == 1) designs.first() else null
}

/** Pick a specific preview mock from the wearing pool (multi-photo mocks). */
fun previewPoolSize(info: MockupTryOnInfo?): Int {
    if (info == null) return 0
    return when {
        info.previewMockupIds.isNotEmpty() -> info.previewMockupIds.size
        info.mockupId > 0 -> 1
        else -> 0
    }
}

fun parseMockupTryOnInfo(
    data: JSONObject,
    handle: String,
    productKeyMeta: String?,
    designIdMeta: String?
): MockupTryOnInfo? {
    fun gate(reason: String): MockupTryOnInfo? {
        Log.d(MOCKUP_TRY_ON_TAG, "handle=$handle metaPk=$productKeyMeta gate=$reason")
        return null
    }

    if (!data.optBoolean("ok", false)) return gate("not_ok")

    val mockupsObj = data.optJSONObject("mockups") ?: return gate("no_mockups")
    val handleToKey = data.optJSONObject("handle_to_key")
    var productKey = handleToKey?.optString(handle)?.takeIf { it.isNotBlank() }

    if (productKey.isNullOrBlank()) {
        val keys = mockupsObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (handle == k || handle.endsWith("-$k")) {
                productKey = k
                break
            }
        }
    }
    if (productKey.isNullOrBlank()) productKey = productKeyMeta?.takeIf { it.isNotBlank() }
    if (productKey.isNullOrBlank()) return gate("no_product_key")

    val info = mockupsObj.optJSONObject(productKey) ?: return gate("no_mockup_entry")
    if (!info.optBoolean("has_mask", false)) return gate("no_mask")
    if (!info.optBoolean("print_area_confirmed", false)) return gate("print_area_unconfirmed")

    val previewIds = MockupPreviewPool.getPreviewIds(info)
    val mockupId = MockupPreviewPool.pickMockupId(info, handle, 0)
        ?: info.optLong("mockup_id", -1L).takeIf { it > 0 }
        ?: info.optString("mockup_id").toLongOrNull()?.takeIf { it > 0 }
        ?: previewIds.firstOrNull()
        ?: return gate("no_mockup_id")

    val handleDesignMap = data.optJSONObject("handle_design_map")
    var designId = handleDesignMap?.optString(handle)?.takeIf { it.isNotBlank() }
    if (designId.isNullOrBlank()) designId = designIdMeta?.takeIf { it.isNotBlank() }

    val isTemplateProduct = handle == productKey
    if (designId.isNullOrBlank() && !isTemplateProduct) {
        designId = inferDesignIdFromCache(data, previewIds, mockupId)
    }
    if (designId.isNullOrBlank() && !isTemplateProduct) return gate("no_design_id")

    val wantDesign = designId ?: "NONE"
    val cachedVariants = data.optJSONObject("cached_variants")
    val idsForCache = if (previewIds.isNotEmpty()) previewIds else listOf(mockupId)
    val cachedByMockupId = mutableMapOf<Long, Map<String, String>>()

    fun parseGroup(id: Long): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val group = cachedVariants?.optJSONArray(id.toString()) ?: return out
        for (i in 0 until group.length()) {
            val row = group.optJSONObject(i) ?: continue
            val rowDesign = row.optString("design_id").takeIf { it.isNotBlank() } ?: "NONE"
            if (rowDesign != wantDesign && wantDesign != "NONE") continue
            val color = row.optString("color").uppercase().replace("#", "")
            val url = row.optString("url")
            if (color.length == 6 && url.isNotBlank()) {
                out[color] = url
            }
        }
        return out
    }

    for (id in idsForCache) {
        cachedByMockupId[id] = parseGroup(id)
    }

    val cachedByColor = cachedByMockupId[mockupId] ?: emptyMap()
    val shopEnabled = !info.has("shop_preview_enabled") || info.optBoolean("shop_preview_enabled", true)

    Log.d(
        MOCKUP_TRY_ON_TAG,
        "handle=$handle metaPk=$productKeyMeta resolvedPk=$productKey design=$designId gate=ok mockupId=$mockupId"
    )
    return MockupTryOnInfo(
        mockupId = mockupId,
        designId = designId,
        productKey = productKey,
        cachedByColor = cachedByColor,
        previewMockupIds = previewIds,
        shopPreviewEnabled = shopEnabled,
        cachedByMockupId = cachedByMockupId
    )
}

fun buildMockupRenderUrl(
    mockupId: Long,
    colorHex: String,
    ownerId: String,
    designId: String?,
    storeBase: String = "https://www.eazpire.com"
): String {
    val color = colorHex.replace("#", "").uppercase()
    val design = designId?.takeIf { it.isNotBlank() } ?: "NONE"
    return "$storeBase/apps/creator-dispatch?" +
        "op=render-mockup-variant" +
        "&mockup_id=$mockupId" +
        "&color=$color" +
        "&design_id=${java.net.URLEncoder.encode(design, "UTF-8")}" +
        "&owner_id=${java.net.URLEncoder.encode(ownerId, "UTF-8")}"
}

private val renderClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

private val RENDER_RETRY_DELAYS_MS = longArrayOf(0L, 8000L, 12000L, 18000L)

/**
 * Resolve a loadable mockup image URL (cached variant first, then on-demand render with retries).
 */
/** Up to [maxViews] mock preview URLs for PDP thumbs / gallery (parity with web preview pool). */
suspend fun buildMockGalleryUrlsForColor(
    info: MockupTryOnInfo,
    handle: String,
    colorName: String,
    ownerId: String,
    productColorMap: Map<String, String> = emptyMap(),
    maxViews: Int = 4
): List<String> {
    if (maxViews <= 0) return emptyList()
    val out = linkedSetOf<String>()
    val colorHex = resolveProductColorHex(colorName, productColorMap)
    for (i in 0 until maxViews) {
        val slice = info.forColorIndex(handle, i)
        // Only the selected color — never reuse another variant's cached mock on color change.
        val cached = slice.cachedByColor[colorHex]
        when {
            !cached.isNullOrBlank() -> out.add(cached)
            else -> resolveMockupImageUrl(slice, colorName, ownerId, productColorMap)?.let { out.add(it) }
        }
    }
    return out.toList()
}

suspend fun resolveMockupImageUrl(
    info: MockupTryOnInfo,
    colorName: String,
    ownerId: String,
    productColorMap: Map<String, String> = emptyMap()
): String? {
    val colorHex = resolveProductColorHex(colorName, productColorMap)
    info.cachedByColor[colorHex]?.let { return it }

    val renderUrl = buildMockupRenderUrl(info.mockupId, colorHex, ownerId, info.designId)
    for (attempt in RENDER_RETRY_DELAYS_MS.indices) {
        if (attempt > 0) delay(RENDER_RETRY_DELAYS_MS[attempt])
        val url = if (attempt == 0) renderUrl else "$renderUrl&_t=${System.currentTimeMillis()}"
        try {
            val request = Request.Builder().url(url).get().build()
            renderClient.newCall(request).execute().use { response ->
                val body = response.body
                if (response.isSuccessful && body != null) {
                    val ct = response.header("Content-Type").orEmpty()
                    if (ct.startsWith("image/") || response.code == 200) {
                        return response.request.url.toString()
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
    return null
}
