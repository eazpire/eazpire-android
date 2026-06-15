package com.eazpire.creator.mockup

import android.util.LruCache
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.ui.parseProductColorHexMap

/** Avoid N+1 `getColorVariants` calls when resolving mock images on home carousel cards. */
object ColorVariantsCache {
    private val cache = LruCache<String, Map<String, String>>(48)

    suspend fun getOrLoad(api: CreatorApi, productKey: String): Map<String, String> {
        val key = productKey.trim()
        if (key.isBlank()) return emptyMap()
        cache.get(key)?.let { return it }
        val map = runCatching {
            val colorsResp = api.getColorVariants(key)
            if (colorsResp.optBoolean("ok", false)) parseProductColorHexMap(colorsResp) else emptyMap()
        }.getOrDefault(emptyMap())
        if (map.isNotEmpty()) cache.put(key, map)
        return map
    }

    fun clear() {
        cache.evictAll()
    }
}
