package com.eazpire.creator.config

import android.content.Context
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AnimationFlagsSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncOnAppStart(context: Context) {
        scope.launch {
            val jwt = SecureTokenStore(context).getJwt()
            if (jwt != null) {
                try {
                    AnimationFlagsRepository.syncFromServer(context, CreatorApi(jwt = jwt))
                } catch (_: Exception) {
                }
            } else {
                AnimationFlagsRepository.syncFromServerPublic(context)
            }
        }
    }
}
