package com.eazpire.creator.wear

import android.content.Context

/** Last successful QR pairing to a Wear device (shown in Creator Settings → Creator Wear). */
object WearPairPrefs {
    private const val PREFS = "eaz_wear_pair_prefs"
    private const val KEY_DEVICE_ID = "wear_device_id"
    private const val KEY_DEVICE_NAME = "wear_device_name"
    private const val KEY_PAIRED_AT = "paired_at"

    fun save(context: Context, deviceId: String, deviceName: String?) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEVICE_ID, deviceId.trim())
            .putString(KEY_DEVICE_NAME, deviceName?.trim().orEmpty())
            .putLong(KEY_PAIRED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getDeviceId(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_ID, null)?.trim()?.takeIf { it.isNotBlank() }

    fun getDeviceName(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DEVICE_NAME, null)?.trim()?.takeIf { it.isNotBlank() }

    fun getPairedAt(context: Context): Long =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_PAIRED_AT, 0L)

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
