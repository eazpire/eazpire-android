package com.eazpire.creator.notifications

import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EazFirebaseMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Do not filter by local DataStore here: the worker already applies
        // get-notification-preferences before sendFcmPush. A second client-side
        // filter caused "test works, real FCM silent" when local prefs were stale
        // or never synced after login.
        val title = message.data["title"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.title
            ?: return
        val body = message.data["body"]?.takeIf { it.isNotBlank() }
            ?: message.notification?.body
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
