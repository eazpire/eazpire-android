package com.eazpire.creator.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.notificationRemoteConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "eaz_notification_remote_config"
)

data class NotificationRemoteConfig(
    val cartAbandonDelayMinutes: Int = 10,
    val cartPromo60Minutes: Int = 60,
    val cartPromo10Minutes: Int = 10,
    val cartAbandonEnabled: Boolean = true,
    val cartPromo60Enabled: Boolean = true,
    val cartPromo10Enabled: Boolean = true,
    val updatedAt: Long = 0L
)

object NotificationRemoteConfigRepository {

    private val KEY_CART_ABANDON_DELAY = intPreferencesKey("cart_abandon_delay")
    private val KEY_PROMO_60 = intPreferencesKey("cart_promo_60")
    private val KEY_PROMO_10 = intPreferencesKey("cart_promo_10")
    private val KEY_CART_ABANDON_ON = booleanPreferencesKey("cart_abandon_on")
    private val KEY_PROMO_60_ON = booleanPreferencesKey("cart_promo_60_on")
    private val KEY_PROMO_10_ON = booleanPreferencesKey("cart_promo_10_on")
    private val KEY_UPDATED_AT = longPreferencesKey("updated_at")

    suspend fun get(context: Context): NotificationRemoteConfig {
        val prefs = context.notificationRemoteConfigDataStore.data.first()
        return NotificationRemoteConfig(
            cartAbandonDelayMinutes = prefs[KEY_CART_ABANDON_DELAY] ?: 10,
            cartPromo60Minutes = prefs[KEY_PROMO_60] ?: 60,
            cartPromo10Minutes = prefs[KEY_PROMO_10] ?: 10,
            cartAbandonEnabled = prefs[KEY_CART_ABANDON_ON] ?: true,
            cartPromo60Enabled = prefs[KEY_PROMO_60_ON] ?: true,
            cartPromo10Enabled = prefs[KEY_PROMO_10_ON] ?: true,
            updatedAt = prefs[KEY_UPDATED_AT] ?: 0L
        )
    }

    suspend fun syncFromServer(context: Context, api: CreatorApi) {
        try {
            val o = api.getAndroidNotificationConfig()
            if (!o.optBoolean("ok", true) && o.has("error")) return
            val enabled = o.optJSONObject("enabled") ?: JSONObject()
            val promoArr = o.optJSONArray("cart_promo_reminder_minutes")
            val promo60 = if (promoArr != null && promoArr.length() > 0) promoArr.optInt(0, 60) else 60
            val promo10 = if (promoArr != null && promoArr.length() > 1) promoArr.optInt(1, 10) else 10
            context.notificationRemoteConfigDataStore.edit { prefs ->
                prefs[KEY_CART_ABANDON_DELAY] = o.optInt("cart_abandon_delay_minutes", 10)
                prefs[KEY_PROMO_60] = promo60
                prefs[KEY_PROMO_10] = promo10
                prefs[KEY_CART_ABANDON_ON] = enabled.optBoolean("cart_abandon", true)
                prefs[KEY_PROMO_60_ON] = enabled.optBoolean("cart_promo_60", true)
                prefs[KEY_PROMO_10_ON] = enabled.optBoolean("cart_promo_10", true)
                prefs[KEY_UPDATED_AT] = o.optLong("updated_at", System.currentTimeMillis())
            }
        } catch (_: Exception) {
        }
    }
}
