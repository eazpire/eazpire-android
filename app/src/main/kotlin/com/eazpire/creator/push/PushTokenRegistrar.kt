package com.eazpire.creator.push

import android.content.Context
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PushTokenRegistrar {

    fun syncIfLoggedIn(context: Context) {
        val jwt = SecureTokenStore(context).getJwt() ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val token = task.result ?: return@addOnCompleteListener
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    CreatorApi(jwt = jwt).registerFcmToken(token)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun unregisterBeforeLogout(context: Context, tokenStore: SecureTokenStore) {
        val jwt = tokenStore.getJwt() ?: return
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val fcmToken = task.result ?: return@addOnCompleteListener
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    CreatorApi(jwt = jwt).unregisterFcmToken(fcmToken)
                } catch (_: Exception) {
                }
            }
        }
    }
}
