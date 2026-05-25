package com.eazpire.creator.notifications

import android.content.Context
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fetches worker notification remote config when user is logged in.
 */
object NotificationRemoteConfigSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncOnAppStart(context: Context) {
        scope.launch {
            val jwt = SecureTokenStore(context).getJwt() ?: return@launch
            try {
                NotificationRemoteConfigRepository.syncFromServer(context, CreatorApi(jwt = jwt))
            } catch (_: Exception) {
            }
        }
    }
}
