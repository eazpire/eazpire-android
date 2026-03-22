package com.eazpire.creator.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.notificationPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "eaz_notification_prefs"
)

private val BLOB_KEY = stringPreferencesKey("prefs_blob_v1")

class NotificationPreferencesRepository(private val context: Context) {

    private val prefs = context.applicationContext.notificationPrefsDataStore

    val prefsFlow: Flow<NotificationPrefs> = prefs.data.map { p ->
        val raw = p[BLOB_KEY]
        if (raw.isNullOrBlank()) NotificationPrefs()
        else {
            try {
                NotificationPrefs.fromJson(JSONObject(raw))
            } catch (_: Exception) {
                NotificationPrefs()
            }
        }
    }

    suspend fun readSnapshot(): NotificationPrefs = prefsFlow.first()

    suspend fun syncFromServer(api: CreatorApi) {
        val j = api.getNotificationPreferences()
        if (!j.optBoolean("ok")) return
        val merged = NotificationPrefs(
            shopMaster = j.optBoolean("shop_master", true),
            creatorMaster = j.optBoolean("creator_master", true),
            shop = mergeBoolMap(NotificationPrefs.defaultShop(), j.optJSONObject("shop")),
            creator = mergeBoolMap(NotificationPrefs.defaultCreator(), j.optJSONObject("creator"))
        )
        writeLocal(merged)
    }

    suspend fun saveShopMaster(api: CreatorApi, value: Boolean) {
        val cur = readSnapshot()
        val next = cur.copy(shopMaster = value)
        writeLocal(next)
        api.saveNotificationPreferences(shopMaster = value)
    }

    suspend fun saveCreatorMaster(api: CreatorApi, value: Boolean) {
        val cur = readSnapshot()
        val next = cur.copy(creatorMaster = value)
        writeLocal(next)
        api.saveNotificationPreferences(creatorMaster = value)
    }

    suspend fun saveShopKey(api: CreatorApi, key: String, value: Boolean) {
        val cur = readSnapshot()
        val nextShop = cur.shop.toMutableMap().apply { this[key] = value }
        val next = cur.copy(shop = nextShop)
        writeLocal(next)
        api.saveNotificationPreferences(shopPatch = mapOf(key to value))
    }

    suspend fun saveCreatorKey(api: CreatorApi, key: String, value: Boolean) {
        val cur = readSnapshot()
        val nextCreator = cur.creator.toMutableMap().apply { this[key] = value }
        val next = cur.copy(creator = nextCreator)
        writeLocal(next)
        api.saveNotificationPreferences(creatorPatch = mapOf(key to value))
    }

    private suspend fun writeLocal(p: NotificationPrefs) {
        prefs.edit { it[BLOB_KEY] = p.toJson().toString() }
    }

    private fun mergeBoolMap(defaults: Map<String, Boolean>, src: JSONObject?): Map<String, Boolean> {
        val out = defaults.toMutableMap()
        if (src == null) return out
        for (k in defaults.keys) {
            if (src.has(k)) out[k] = src.optBoolean(k, defaults[k] ?: true)
        }
        return out
    }
}
