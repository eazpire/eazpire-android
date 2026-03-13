package com.eazpire.creator.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Prüft GitHub Releases auf neuere Version.
 * Vergleicht versionCode mit release tag (build-X).
 */
object UpdateChecker {
    private const val RELEASES_URL = "https://api.github.com/repos/eazpire/eazpire-android/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(val downloadUrl: String, val releaseName: String)

    suspend fun checkForUpdate(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val tagName = json.optString("tag_name", "")
            val releaseVersion = tagName.removePrefix("build-").toIntOrNull() ?: return@withContext null
            if (releaseVersion <= currentVersionCode) return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var downloadUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    downloadUrl = asset.optString("browser_download_url", "")
                    break
                }
            }
            if (downloadUrl.isBlank()) return@withContext null
            val releaseName = json.optString("name", "Neue Version")
            UpdateInfo(downloadUrl, releaseName)
        } catch (_: Exception) {
            null
        }
    }
}
