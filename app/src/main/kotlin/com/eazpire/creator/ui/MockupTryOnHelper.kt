package com.eazpire.creator.ui

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
    val cachedByColor: Map<String, String>
)

private val COLOR_NAME_TO_HEX = mapOf(
    "white" to "FFFFFF",
    "black" to "111111",
    "navy" to "0B1F3A",
    "red" to "D11A2A",
    "purple" to "6B21A8",
    "sport grey" to "9CA3AF",
    "sport-grey" to "9CA3AF",
    "dark heather" to "4B5563",
    "dark-heather" to "4B5563",
    "military green" to "4B5D3A",
    "natural" to "EFE7D6",
    "sand" to "D8C7A0",
    "daisy" to "F4D000",
    "light blue" to "7FB7FF",
    "tropical blue" to "00A3D7",
    "dark chocolate" to "3A2618",
    "heather navy" to "2B3A55",
    "indigo" to "4F46E5",
    "coral" to "F87171",
    "teal" to "14B8A6",
    "burgundy" to "800020",
    "yellow" to "FACC15",
    "orange" to "EA580C",
    "pink" to "EC4899",
    "green" to "16A34A",
    "blue" to "2563EB",
    "grey" to "9CA3AF",
    "gray" to "9CA3AF",
    "brown" to "78350F"
)

fun normalizeColorNameKey(value: String): String =
    value.lowercase().replace(Regex("[_-]+"), " ").replace(Regex("\\s+"), " ").trim()

fun colorNameToHex(colorName: String): String {
    val key = normalizeColorNameKey(colorName)
    return COLOR_NAME_TO_HEX[key] ?: "FFFFFF"
}

fun isTryOnApparelProduct(productKey: String?): Boolean {
    val pk = productKey?.trim()?.lowercase().orEmpty()
    return pk.isNotBlank() && TRY_ON_APPAREL_PRODUCT_KEYS.contains(pk)
}

fun parseMockupTryOnInfo(
    data: JSONObject,
    handle: String,
    productKeyMeta: String?,
    designIdMeta: String?
): MockupTryOnInfo? {
    if (!data.optBoolean("ok", false)) return null

    val mockupsObj = data.optJSONObject("mockups") ?: return null
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
    if (productKey.isNullOrBlank()) return null

    val info = mockupsObj.optJSONObject(productKey) ?: return null
    if (!info.optBoolean("has_mask", false)) return null
    if (!info.optBoolean("print_area_confirmed", false)) return null

    val mockupId = info.optLong("mockup_id", -1L)
    if (mockupId < 0L) return null

    val handleDesignMap = data.optJSONObject("handle_design_map")
    var designId = handleDesignMap?.optString(handle)?.takeIf { it.isNotBlank() }
    if (designId.isNullOrBlank()) designId = designIdMeta?.takeIf { it.isNotBlank() }

    val isTemplateProduct = handle == productKey
    if (designId.isNullOrBlank() && !isTemplateProduct) return null

    val cachedByColor = mutableMapOf<String, String>()
    val cachedVariants = data.optJSONObject("cached_variants")
    val group = cachedVariants?.optJSONArray(mockupId.toString())
    if (group != null) {
        val wantDesign = designId ?: "NONE"
        for (i in 0 until group.length()) {
            val row = group.optJSONObject(i) ?: continue
            val rowDesign = row.optString("design_id").takeIf { it.isNotBlank() } ?: "NONE"
            if (rowDesign != wantDesign && wantDesign != "NONE") continue
            val color = row.optString("color").uppercase().replace("#", "")
            val url = row.optString("url")
            if (color.length == 6 && url.isNotBlank()) {
                cachedByColor[color] = url
            }
        }
    }

    return MockupTryOnInfo(
        mockupId = mockupId,
        designId = designId,
        productKey = productKey,
        cachedByColor = cachedByColor
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
suspend fun resolveMockupImageUrl(
    info: MockupTryOnInfo,
    colorName: String,
    ownerId: String
): String? {
    val colorHex = colorNameToHex(colorName)
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
