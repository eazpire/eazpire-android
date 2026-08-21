package com.eazpire.creator.ui.creator

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Resolve + download a YouTube progressive MP4 from the phone.
 * The Worker often hits YouTube's datacenter bot check; the device IP does not.
 */
internal object YouTubeOnDeviceResolver {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    private const val VR_UA =
        "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12; eureka-user Build/SQ3A.220605.009.A1) gzip"

    fun parseVideoId(raw: String): String? {
        val trimmed = raw.trim()
        val url = runCatching { java.net.URI(trimmed).toURL() }.getOrNull() ?: return null
        val host = url.host?.replace(Regex("^www\\."), "")?.lowercase() ?: return null
        val path = url.path ?: "/"
        if (host == "youtu.be") {
            val id = path.trim('/').split('/').firstOrNull().orEmpty()
            return id.takeIf { it.length == 11 }
        }
        if (host == "youtube.com" || host == "m.youtube.com" || host == "music.youtube.com") {
            val query = url.query.orEmpty()
            val v = query.split('&').firstOrNull { it.startsWith("v=") }?.substringAfter("v=")
            if (v != null && v.length == 11) return v
            val matcher = Pattern.compile("/(?:embed|shorts|live|v)/([A-Za-z0-9_-]{11})").matcher(path)
            if (matcher.find()) return matcher.group(1)
        }
        return null
    }

    fun downloadProgressiveMp4(sourceUrl: String, dest: File): Boolean {
        val videoId = parseVideoId(sourceUrl) ?: return false
        val mediaUrl = resolveProgressiveUrl(videoId) ?: return false
        val request = Request.Builder()
            .url(mediaUrl)
            .header("User-Agent", VR_UA)
            .header("Accept", "*/*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            dest.outputStream().use { out -> body.byteStream().copyTo(out) }
            return dest.exists() && dest.length() > 0L
        }
    }

    private fun resolveProgressiveUrl(videoId: String): String? {
        val body = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", "ANDROID_VR")
                        .put("clientVersion", "1.60.19")
                        .put("androidSdkVersion", 32)
                        .put("deviceMake", "Oculus")
                        .put("deviceModel", "Quest 3")
                        .put("osName", "Android")
                        .put("osVersion", "12")
                        .put("hl", "en")
                        .put("gl", "US"),
                ),
            )
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .toString()
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header("Content-Type", "application/json")
            .header("User-Agent", VR_UA)
            .header("X-YouTube-Client-Name", "28")
            .header("X-YouTube-Client-Version", "1.60.19")
            .post(body.toRequestBody(jsonType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string().orEmpty())
            val play = json.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
            if (play.isNotBlank() && play != "OK") return null
            return pickProgressiveUrl(json.optJSONObject("streamingData")?.optJSONArray("formats"))
        }
    }

    private fun pickProgressiveUrl(formats: JSONArray?): String? {
        if (formats == null) return null
        var bestUrl: String? = null
        var bestHeight = -1
        for (i in 0 until formats.length()) {
            val item = formats.optJSONObject(i) ?: continue
            val url = item.optString("url")
            val mime = item.optString("mimeType")
            if (url.isBlank() || !mime.contains("video")) continue
            val muxed = mime.contains("mp4a") || mime.contains("audio") ||
                item.has("audioQuality") || item.has("audioSampleRate")
            if (!muxed && !mime.startsWith("video/")) continue
            val height = item.optInt("height")
            if (height >= bestHeight) {
                bestHeight = height
                bestUrl = url
            }
        }
        return bestUrl
    }
}
