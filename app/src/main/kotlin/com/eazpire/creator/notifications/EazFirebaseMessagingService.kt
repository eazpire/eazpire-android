package com.eazpire.creator.notifications

import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class EazFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val category = message.data["category"]
        if (!shouldShowPush(category)) return
        val title = message.notification?.title
            ?: message.data["title"]
            ?: return
        val body = message.notification?.body
            ?: message.data["body"]
            ?: ""
        val nid = (message.data["notification_id"] ?: message.messageId ?: System.currentTimeMillis().toString())
            .hashCode()
        val extras = message.data.mapValues { it.value }
        EazNotificationDisplay.showPush(
            this,
            title,
            body,
            nid and 0x7fff_ffff,
            extras
        )
    }

    private fun shouldShowPush(category: String?): Boolean {
        return runBlocking {
            val prefs = NotificationPreferencesRepository(applicationContext).readSnapshot()
            val c = category?.lowercase() ?: ""
            val isShop = c.startsWith("shop_") || c.contains("shop_promotion")
            if (isShop) {
                if (!prefs.shopMaster) return@runBlocking false
                val key = NotificationCategoryMapping.categoryToShopKey(category)
                return@runBlocking prefs.shop[key] != false
            }
            if (!prefs.creatorMaster) return@runBlocking false
            val key = NotificationCategoryMapping.categoryToCreatorKey(category)
            prefs.creator[key] != false
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            val jwt = SecureTokenStore(this@EazFirebaseMessagingService).getJwt() ?: return@launch
            try {
                CreatorApi(jwt = jwt).registerFcmToken(token)
            } catch (_: Exception) {
            }
        }
    }
}
