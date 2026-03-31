package com.eazpire.creator.cart

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules a local reminder when the cart has items (no in-app notification for this).
 * Uses [AppCartStore] counts; [CartReminderWorker] re-checks the Storefront cart before showing.
 * Delay: 10 minutes after the last cart change (unique work REPLACE).
 */
object CartReminderScheduler {
    private const val UNIQUE_NAME = "eaz_cart_abandonment"
    private const val DELAY_MINUTES = 10L

    private lateinit var appCtx: Context

    fun init(context: Context) {
        appCtx = context.applicationContext
    }

    fun onCartCountChanged() {
        if (!::appCtx.isInitialized) return
        val wm = WorkManager.getInstance(appCtx)
        if (AppCartStore.itemCount <= 0) {
            wm.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = OneTimeWorkRequestBuilder<CartReminderWorker>()
            .setConstraints(constraints)
            .setInitialDelay(DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, req)
    }
}
