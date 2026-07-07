package com.eazpire.creator.wear.sync

import android.content.Context
import android.net.Uri
import com.eazpire.creator.auth.SecureTokenStore
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

/**
 * Pushes session to Wear app via Data Layer (path must match wear [WearAuthPaths.DATA_PATH]).
 */
object WearAuthSync {

    const val DATA_PATH = "/eaz/auth"

    fun push(context: Context, tokenStore: SecureTokenStore) {
        val appContext = context.applicationContext
        val jwt = tokenStore.getJwt()?.trim().orEmpty()
        val ownerId = tokenStore.getOwnerId()?.trim().orEmpty()
        if (jwt.isBlank() || ownerId.isBlank()) {
            clear(appContext)
            return
        }
        val payload = JSONObject()
            .put("jwt", jwt)
            .put("owner_id", ownerId)
            .put("updated_at", System.currentTimeMillis())
            .toString()
        val request = PutDataMapRequest.create(DATA_PATH).apply {
            dataMap.putString("payload", payload)
            dataMap.putLong("updated_at", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        try {
            Wearable.getDataClient(appContext).putDataItem(request)
        } catch (_: Exception) {
            // Play Services / Wear API unavailable — must not crash cold start.
        }
    }

    fun clear(context: Context) {
        val appContext = context.applicationContext
        val uri = Uri.Builder().scheme("wear").path(DATA_PATH).build()
        try {
            Wearable.getDataClient(appContext).deleteDataItems(uri)
        } catch (_: Exception) {
        }
    }
}
