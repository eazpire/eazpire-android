package com.eazpire.creator.cart

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.eazpire.creator.notifications.NotificationRemoteConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Schedules a local reminder when the cart has items (no in-app notification for this).
 * Uses [AppCartStore] counts; [CartReminderWorker] re-checks the Storefront cart before showing.
 * Delay from worker remote config (default 10 minutes).
 */
object CartReminderScheduler {
    private const val UNIQUE_NAME = "eaz_cart_abandonment"

    private lateinit var appCtx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appCtx = context.applicationContext
    }

    fun onCartCountChanged() {
        if (!::appCtx.isInitialized) return
        scope.launch {
            val config = NotificationRemoteConfigRepository.get(appCtx)
            if (!config.cartAbandonEnabled) {
                WorkManager.getInstance(appCtx).cancelUniqueWork(UNIQUE_NAME)
                return@launch
            }
            val wm = WorkManager.getInstance(appCtx)
            if (AppCartStore.itemCount <= 0) {
                wm.cancelUniqueWork(UNIQUE_NAME)
                return@launch
            }
            val delayMin = config.cartAbandonDelayMinutes.coerceAtLeast(1).toLong()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = OneTimeWorkRequestBuilder<CartReminderWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delayMin, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}
