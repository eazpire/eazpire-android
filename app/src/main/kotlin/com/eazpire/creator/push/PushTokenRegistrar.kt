package com.eazpire.creator.push

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.debug.AuthDebugLog
import com.eazpire.creator.notifications.NotificationRemoteConfigRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PushTokenRegistrar {

    private const val MAX_JWT_WAIT_ATTEMPTS = 8
    private const val JWT_WAIT_MS = 1_500L

    /**
     * Registers the device FCM token with the worker when the user is logged in.
     * Retries briefly when JWT is not ready yet (cold start before session refresh).
     */
    fun syncIfLoggedIn(context: Context, jwtWaitAttempt: Int = 0) {
        val app = context.applicationContext
        val jwt = SecureTokenStore.get(app).getJwt()
        if (jwt.isNullOrBlank()) {
            if (jwtWaitAttempt < MAX_JWT_WAIT_ATTEMPTS) {
                Handler(Looper.getMainLooper()).postDelayed({
                    syncIfLoggedIn(app, jwtWaitAttempt + 1)
                }, JWT_WAIT_MS)
            } else {
                AuthDebugLog.d("[PUSH] skip token sync — no JWT after waits")
            }
            return
        }
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    AuthDebugLog.d("[PUSH] FCM token fetch failed: ${task.exception?.message}")
                    return@addOnCompleteListener
                }
                val token = task.result ?: return@addOnCompleteListener
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val res = CreatorApi(jwt = jwt).registerFcmToken(token)
                        AuthDebugLog.d("[PUSH] register-fcm-token ok=${res.optBoolean("ok")}")
                        NotificationRemoteConfigRepository.syncFromServer(app, CreatorApi(jwt = jwt))
                    } catch (e: Exception) {
                        AuthDebugLog.d("[PUSH] register-fcm-token failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            AuthDebugLog.d("[PUSH] FCM unavailable: ${e.message}")
        }
    }

    fun unregisterBeforeLogout(context: Context, tokenStore: SecureTokenStore) {
        val jwt = tokenStore.getJwt() ?: return
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                val fcmToken = task.result ?: return@addOnCompleteListener
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        CreatorApi(jwt = jwt).unregisterFcmToken(fcmToken)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
