package com.eazpire.creator.ui.home

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Disk cache for stale-while-revalidate home paint (last successful bootstrap). */
class HomeSnapshotStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class DiskSnapshot(
        val localeKey: String,
        val savedAtMs: Long,
        val bootstrapJson: String,
    )

    suspend fun load(): DiskSnapshot? = withContext(Dispatchers.IO) {
        val json = prefs.getString(KEY_SNAPSHOT, null) ?: return@withContext null
        runCatching {
            val o = JSONObject(json)
            DiskSnapshot(
                localeKey = o.optString("locale_key", ""),
                savedAtMs = o.optLong("saved_at_ms", 0L),
                bootstrapJson = o.optString("bootstrap_json", ""),
            )
        }.getOrNull()
    }

    suspend fun save(localeKey: String, bootstrapJson: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("locale_key", localeKey)
            .put("saved_at_ms", System.currentTimeMillis())
            .put("bootstrap_json", bootstrapJson)
        prefs.edit().putString(KEY_SNAPSHOT, payload.toString()).apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_SNAPSHOT).apply()
    }

    companion object {
        private const val PREFS = "eaz_home_snapshot_v1"
        private const val KEY_SNAPSHOT = "snapshot"
        /** Show cached home up to 15 minutes while refreshing. */
        const val MAX_AGE_MS = 15 * 60 * 1000L
    }
}
