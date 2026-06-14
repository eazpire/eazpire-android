package com.eazpire.creator.config

import com.eazpire.creator.api.CreatorApi
import org.json.JSONObject

/**
 * Active Creator shell background from worker `get-creator-area-backgrounds`.
 * Android uses the resolved mobile entry (respects Admin → Themes “use desktop on mobile”).
 */
data class CreatorAreaBackground(
    val mediaType: String,
    val url: String?,
    val posterUrl: String?,
    val source: String?,
    val shopifyAsset: String?,
) {
    val isVideo: Boolean get() = mediaType == "video" && !url.isNullOrBlank()

    /** Image URL for display (poster when video is disabled). */
    val imageUrl: String?
        get() = when {
            isVideo -> posterUrl ?: url
            !url.isNullOrBlank() -> url
            else -> null
        }
}

object CreatorThemeBackgroundRepository {

    @Volatile
    private var memoryCache: CreatorAreaBackground? = null

    fun getCached(): CreatorAreaBackground? = memoryCache

    suspend fun syncFromServer(api: CreatorApi = CreatorApi()) {
        try {
            memoryCache = parseMobile(api.getCreatorAreaBackgrounds())
        } catch (_: Exception) {
        }
    }

    suspend fun loadMobile(api: CreatorApi = CreatorApi()): CreatorAreaBackground {
        return try {
            parseMobile(api.getCreatorAreaBackgrounds()).also { memoryCache = it }
        } catch (_: Exception) {
            memoryCache ?: defaultGalaxy()
        }
    }

    private fun parseMobile(root: JSONObject): CreatorAreaBackground {
        if (!root.optBoolean("ok", false)) return defaultGalaxy()
        val mobile = root.optJSONObject("backgrounds")?.optJSONObject("mobile")
            ?: return defaultGalaxy()
        return mobile.toCreatorAreaBackground()
    }

    private fun JSONObject.toCreatorAreaBackground(): CreatorAreaBackground {
        return CreatorAreaBackground(
            mediaType = optString("media_type", "image").ifBlank { "image" },
            url = optString("url", "").takeIf { it.isNotBlank() },
            posterUrl = optString("poster_url", "").takeIf { it.isNotBlank() },
            source = optString("source", "").takeIf { it.isNotBlank() },
            shopifyAsset = optString("shopify_asset", "").takeIf { it.isNotBlank() },
        )
    }

    fun defaultGalaxy(): CreatorAreaBackground = CreatorAreaBackground(
        mediaType = "image",
        url = null,
        posterUrl = null,
        source = "shopify_asset",
        shopifyAsset = "galaxy-nebula-bg.png",
    )
}
