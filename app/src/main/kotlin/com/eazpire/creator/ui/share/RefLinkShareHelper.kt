package com.eazpire.creator.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.CreatorHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

private const val REF_LINKS_SETTING_KEY = "community_ref_links_v1"
private const val WEB_BASE = "https://www.eazpire.com"
private const val JOIN_BASE = "https://join.eazpire.com"
private const val PLAY_STORE_APP = "https://play.google.com/store/apps/details?id=com.eazpire.creator"
private const val PREFS_NAME = "eaz_short_ref_v1"
private const val CACHE_TTL_MS = 30L * 60L * 1000L

enum class ReferralShareTarget {
    /** Web: eazpire.com homepage with ref tracking */
    Homepage,
    /** App share: ref link targeting Play Store install */
    AndroidApp,
}

private data class ActiveRefMeta(val joinUrl: String, val refName: String, val code: String)

data class JoinDeepLinkResult(
    val path: String,
    val ref: String? = null,
    val refName: String? = null,
)

private data class CachedMeta(val meta: ActiveRefMeta, val ts: Long)
private data class CachedShort(val url: String, val ts: Long)

/** In-memory short-link cache (process lifetime). */
private object ShortRefMemoryCache {
    val metaByOwner = ConcurrentHashMap<String, CachedMeta>()
    val shortByKey = ConcurrentHashMap<String, CachedShort>()
    val metaMutexByOwner = ConcurrentHashMap<String, Mutex>()
    val shortMutexByKey = ConcurrentHashMap<String, Mutex>()

    fun metaMutex(ownerId: String): Mutex =
        metaMutexByOwner.getOrPut(ownerId) { Mutex() }

    fun shortMutex(key: String): Mutex =
        shortMutexByKey.getOrPut(key) { Mutex() }
}

private fun shortCacheKey(ownerId: String, landingUrl: String, refName: String): String =
    "$ownerId|$landingUrl|$refName"

private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

private fun readPrefsShort(context: Context?, key: String): String? {
    if (context == null) return null
    return try {
        val raw = prefs(context).getString(key, null) ?: return null
        val obj = JSONObject(raw)
        val url = obj.optString("url", "").takeIf { it.isNotBlank() } ?: return null
        val ts = obj.optLong("ts", 0L)
        if (ts <= 0L || System.currentTimeMillis() - ts > CACHE_TTL_MS) {
            prefs(context).edit().remove(key).apply()
            null
        } else {
            url
        }
    } catch (_: Exception) {
        null
    }
}

private fun writePrefsShort(context: Context?, key: String, url: String) {
    if (context == null) return
    try {
        prefs(context).edit()
            .putString(key, JSONObject().put("url", url).put("ts", System.currentTimeMillis()).toString())
            .apply()
    } catch (_: Exception) {
    }
}

/**
 * Baut die aktive Ref-Link-Basis aus API-Daten.
 * Format: join.eazpire.com/{slug} oder join.eazpire.com/{code}
 */
suspend fun getActiveRefUrl(api: CreatorApi, ownerId: String): String? =
    getActiveRefMeta(api, ownerId)?.joinUrl

private suspend fun getActiveRefMeta(api: CreatorApi, ownerId: String): ActiveRefMeta? =
    withContext(Dispatchers.IO) {
        if (ownerId.isBlank()) return@withContext null
        val cached = ShortRefMemoryCache.metaByOwner[ownerId]
        if (cached != null && System.currentTimeMillis() - cached.ts < CACHE_TTL_MS) {
            return@withContext cached.meta
        }
        ShortRefMemoryCache.metaMutex(ownerId).withLock {
            val again = ShortRefMemoryCache.metaByOwner[ownerId]
            if (again != null && System.currentTimeMillis() - again.ts < CACHE_TTL_MS) {
                return@withLock again.meta
            }
            try {
                val ref = api.getReferralCode(ownerId)
                if (!ref.optBoolean("ok", false)) return@withLock null
                val code = ref.optString("code", "").takeIf { it.isNotBlank() } ?: return@withLock null
                val settingRes = api.getCustomerSetting(ownerId, REF_LINKS_SETTING_KEY)
                val raw =
                    if (settingRes.optBoolean("ok", false)) {
                        settingRes.optString("value", "").takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                val (links, activeId) = parseRefLinks(raw)
                val activeLink = links.find { it.id == activeId } ?: links.firstOrNull()
                val slug = activeLink?.slug?.takeIf { it.isNotBlank() }
                val pathPart = (slug ?: code).lowercase()
                val meta = ActiveRefMeta(
                    joinUrl = "$JOIN_BASE/$pathPart",
                    refName = slug?.lowercase() ?: "main",
                    code = code,
                )
                ShortRefMemoryCache.metaByOwner[ownerId] =
                    CachedMeta(meta, System.currentTimeMillis())
                meta
            } catch (_: Exception) {
                null
            }
        }
    }

private data class RefLink(val id: String, val name: String, val slug: String)

private fun parseRefLinks(raw: String?): Pair<List<RefLink>, String> {
    val defaultName = "Main link"
    val parsed = runCatching { raw?.let { JSONObject(it) } }.getOrNull()
    val linksArr = parsed?.optJSONArray("links") ?: JSONArray()
    val activeIdRaw = parsed?.optString("activeId", "") ?: ""
    val defaultNameLc = defaultName.lowercase()
    val normalized = (0 until linksArr.length()).mapNotNull { i ->
        val item = linksArr.optJSONObject(i) ?: return@mapNotNull null
        val label = item.optString("name", "").trim()
        if (label.isBlank()) return@mapNotNull null
        val id = item.optString("id", "id-${System.currentTimeMillis()}")
        val isDefault = id == "default" || label.lowercase() == defaultNameLc
        val rawSlug = item.optString("slug", "").trim()
        val slug = when {
            isDefault && (rawSlug == "main-link" || rawSlug == slugifyLabel(defaultName)) -> ""
            isDefault -> ""
            rawSlug.isNotBlank() -> slugifyLabel(rawSlug)
            else -> slugifyLabel(label)
        }
        RefLink(id = id, name = label, slug = slug)
    }.filter { it.name.isNotBlank() }.take(5)
    val links = if (normalized.isEmpty()) listOf(RefLink("default", defaultName, "")) else normalized
    val activeId = if (links.any { it.id == activeIdRaw }) activeIdRaw else links.first().id
    return Pair(links, activeId)
}

private fun slugifyLabel(name: String): String = name
    .lowercase()
    .replace(Regex("[^a-z0-9\\s-]"), "")
    .trim()
    .replace(Regex("\\s+"), "-")
    .replace(Regex("-+"), "-")
    .take(40)

private fun sanitizeShareTargetUrl(targetUrl: String): String {
    return try {
        val u = java.net.URI(targetUrl)
        val query = u.query
            ?.split("&")
            ?.mapNotNull { part ->
                val key = part.substringBefore("=").lowercase()
                if (key in setOf("eaz_pdp_modal", "ref", "ref_name", "logged_in_customer_id", "path_prefix")) {
                    null
                } else {
                    part
                }
            }
            ?.joinToString("&")
            ?.takeIf { it.isNotBlank() }
        buildString {
            append(u.scheme).append("://").append(u.host)
            if (u.port > 0) append(":").append(u.port)
            append(u.path ?: "/")
            if (!query.isNullOrBlank()) append("?").append(query)
        }
    } catch (_: Exception) {
        targetUrl
    }
}

private fun normalizeTargetUrl(pagePath: String): String {
    val targetUrl = when {
        pagePath.isBlank() || pagePath == "/" -> WEB_BASE
        pagePath.startsWith("http://") || pagePath.startsWith("https://") -> pagePath
        else -> WEB_BASE + (if (pagePath.startsWith("/")) pagePath else "/$pagePath")
    }
    return sanitizeShareTargetUrl(targetUrl)
}

/** Sync peek from memory/prefs — no network. */
fun peekShareUrl(ownerId: String, pagePath: String, context: Context? = null): String? {
    if (ownerId.isBlank()) return null
    val landing = normalizeTargetUrl(pagePath)
    val meta = ShortRefMemoryCache.metaByOwner[ownerId]?.meta
    val refName = meta?.refName ?: "main"
    val key = shortCacheKey(ownerId, landing, refName)
    val mem = ShortRefMemoryCache.shortByKey[key]
    if (mem != null && System.currentTimeMillis() - mem.ts < CACHE_TTL_MS) return mem.url
    val altKey = shortCacheKey(ownerId, landing, "main")
    val alt = ShortRefMemoryCache.shortByKey[altKey]
    if (alt != null && System.currentTimeMillis() - alt.ts < CACHE_TTL_MS) return alt.url
    return readPrefsShort(context, key) ?: readPrefsShort(context, altKey)
}

private fun storeShortUrl(
    context: Context?,
    ownerId: String,
    landingUrl: String,
    refName: String,
    url: String,
) {
    val key = shortCacheKey(ownerId, landingUrl, refName)
    ShortRefMemoryCache.shortByKey[key] = CachedShort(url, System.currentTimeMillis())
    writePrefsShort(context, key, url)
}

/**
 * Warm short-link cache so Share opens instantly.
 */
suspend fun prefetchShareUrl(
    api: CreatorApi,
    ownerId: String,
    pagePath: String,
    context: Context? = null,
): String? {
    if (ownerId.isBlank()) return null
    peekShareUrl(ownerId, pagePath, context)?.let { return it }
    return try {
        resolveShareUrl(api, ownerId, pagePath, context)
    } catch (_: Exception) {
        null
    }
}

/**
 * Resolve a shareable URL: prefers opaque short link join.eazpire.com/s/{token}.
 * Uses memory/prefs cache; falls back to legacy join/{slug}?url=… when needed.
 */
suspend fun resolveShareUrl(
    api: CreatorApi,
    ownerId: String,
    pagePath: String,
    context: Context? = null,
): String = withContext(Dispatchers.IO) {
    val targetUrl = normalizeTargetUrl(pagePath)
    peekShareUrl(ownerId, pagePath, context)?.let { return@withContext it }

    val meta = getActiveRefMeta(api, ownerId)
    val refUrl = meta?.joinUrl
    val refName = meta?.refName ?: "main"
    val key = shortCacheKey(ownerId, targetUrl, refName)

    ShortRefMemoryCache.shortMutex(key).withLock {
        peekShareUrl(ownerId, pagePath, context)?.let { return@withLock it }

        try {
            val shortRes = api.createShortRefLink(ownerId, targetUrl, refName)
            if (shortRes.optBoolean("ok", false)) {
                val shortUrl = shortRes.optString("short_url", "").takeIf { it.isNotBlank() }
                if (!shortUrl.isNullOrBlank()) {
                    storeShortUrl(context, ownerId, targetUrl, refName, shortUrl)
                    return@withLock shortUrl
                }
                if (shortRes.optBoolean("home", false) && !refUrl.isNullOrBlank()) {
                    storeShortUrl(context, ownerId, targetUrl, refName, refUrl)
                    return@withLock refUrl
                }
            }
        } catch (_: Exception) {
            // fall through
        }

        val fallback = if (!refUrl.isNullOrBlank()) {
            encodeJoinUrlWithTarget(refUrl, targetUrl)
        } else {
            targetUrl
        }
        storeShortUrl(context, ownerId, targetUrl, refName, fallback)
        fallback
    }
}

/**
 * Opens the system share sheet ASAP (cached short link when available).
 */
suspend fun sharePageLink(
    context: Context,
    api: CreatorApi,
    ownerId: String,
    pagePath: String,
    chooserTitle: String? = null,
) {
    val cached = peekShareUrl(ownerId, pagePath, context)
    val url = if (!cached.isNullOrBlank()) {
        cached
    } else {
        try {
            resolveShareUrl(api, ownerId, pagePath, context)
        } catch (_: Exception) {
            normalizeTargetUrl(pagePath)
        }
    }
    withContext(Dispatchers.Main) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        val chooser = Intent.createChooser(sendIntent, chooserTitle)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

/**
 * Baut Share-URL im Web-Format: bevorzugt Short-Link, sonst join?url=
 * @deprecated Prefer [resolveShareUrl] which creates opaque short tokens.
 */
fun buildShareUrl(refUrl: String, pagePath: String): String {
    val targetUrl = normalizeTargetUrl(pagePath)
    return encodeJoinUrlWithTarget(refUrl, targetUrl)
}

/** Ref share URL for journey invite etc. — uses opaque shortener when possible. */
suspend fun buildReferralShareUrl(
    api: CreatorApi,
    ownerId: String,
    target: ReferralShareTarget,
    context: Context? = null,
): String = when (target) {
    ReferralShareTarget.Homepage -> resolveShareUrl(api, ownerId, "/", context)
    ReferralShareTarget.AndroidApp -> resolveShareUrl(api, ownerId, PLAY_STORE_APP, context)
}

/** Legacy overload kept for call sites that already have a join base URL. */
fun buildReferralShareUrl(refUrl: String, target: ReferralShareTarget): String = when (target) {
    ReferralShareTarget.Homepage -> buildShareUrl(refUrl, "/")
    ReferralShareTarget.AndroidApp -> buildShareUrl(refUrl, PLAY_STORE_APP)
}

private fun encodeJoinUrlWithTarget(refUrl: String, targetUrl: String): String {
    return try {
        val u = java.net.URI(refUrl)
        val base = "${u.scheme}://${u.host}${u.path}"
        "$base?url=${java.net.URLEncoder.encode(targetUrl, "UTF-8")}"
    } catch (_: Exception) {
        refUrl
    }
}

/**
 * Resolve join.eazpire.com deep links, including opaque short tokens `/s/{token}`.
 */
suspend fun resolveJoinDeepLink(uri: Uri): JoinDeepLinkResult = withContext(Dispatchers.IO) {
    if (!uri.host.equals("join.eazpire.com", ignoreCase = true)) {
        return@withContext JoinDeepLinkResult(path = uri.path ?: "/")
    }

    val segments = uri.pathSegments.orEmpty()
    if (segments.size >= 2 && segments[0].equals("s", ignoreCase = true)) {
        val token = segments[1].trim()
        if (token.isNotBlank()) {
            val location = fetchJoinRedirectLocation("$JOIN_BASE/s/$token")
            if (!location.isNullOrBlank()) {
                return@withContext parseLandingFromAbsoluteUrl(location)
            }
        }
        return@withContext JoinDeepLinkResult(path = "/")
    }

    // Legacy: join/{slug}?url=…
    val urlParam = uri.getQueryParameter("url")
    if (!urlParam.isNullOrBlank()) {
        val decoded = try {
            java.net.URLDecoder.decode(urlParam, "UTF-8")
        } catch (_: Exception) {
            urlParam
        }
        return@withContext parseLandingFromAbsoluteUrl(decoded)
    }

    JoinDeepLinkResult(path = "/")
}

private fun parseLandingFromAbsoluteUrl(absolute: String): JoinDeepLinkResult {
    return try {
        val u = Uri.parse(absolute)
        JoinDeepLinkResult(
            path = (u.path ?: "/").ifBlank { "/" },
            ref = u.getQueryParameter("ref"),
            refName = u.getQueryParameter("ref_name"),
        )
    } catch (_: Exception) {
        val path = absolute
            .substringAfter("www.eazpire.com", "")
            .substringAfter("eazpire.com", "")
            .ifBlank { "/" }
            .substringBefore("?")
        JoinDeepLinkResult(path = if (path.startsWith("/")) path else "/$path")
    }
}

private fun fetchJoinRedirectLocation(url: String): String? {
    return try {
        val client = CreatorHttpClient.instance.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html,*/*")
            .build()
        client.newCall(request).execute().use { resp ->
            resp.header("Location")?.takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        null
    }
}
