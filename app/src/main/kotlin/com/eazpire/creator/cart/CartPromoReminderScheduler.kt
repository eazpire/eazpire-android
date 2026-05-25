package com.eazpire.creator.cart

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.eazpire.creator.notifications.NotificationRemoteConfigRepository
import kotlinx.coroutines.runBlocking

private const val UNIQUE_60 = "cart_promo_slot_60"
private const val UNIQUE_10 = "cart_promo_slot_10"

/**
 * Schedules two one-shot workers before [deadlineMs] using remote config delays/enabled flags.
 */
object CartPromoReminderScheduler {

    fun schedule(context: Context, deadlineMs: Long) {
        cancel(context)
        val config = runBlocking { NotificationRemoteConfigRepository.get(context) }
        val now = System.currentTimeMillis()
        if (deadlineMs <= now) return
        val wm = WorkManager.getInstance(context)
        if (config.cartPromo60Enabled) {
            val delay60 = deadlineMs - config.cartPromo60Minutes.coerceAtLeast(1) * 60 * 1000L - now
            if (delay60 > 3_000L) {
                val req = OneTimeWorkRequestBuilder<CartPromoReminderWorker>()
                    .setInitialDelay(delay60, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .setInputData(Data.Builder().putString(CartPromoReminderWorker.KEY_KIND, "60").build())
                    .build()
                wm.enqueueUniqueWork(UNIQUE_60, ExistingWorkPolicy.REPLACE, req)
            }
        }
        if (config.cartPromo10Enabled) {
            val delay10 = deadlineMs - config.cartPromo10Minutes.coerceAtLeast(1) * 60 * 1000L - now
            if (delay10 > 3_000L) {
                val req = OneTimeWorkRequestBuilder<CartPromoReminderWorker>()
                    .setInitialDelay(delay10, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .setInputData(Data.Builder().putString(CartPromoReminderWorker.KEY_KIND, "10").build())
                    .build()
                wm.enqueueUniqueWork(UNIQUE_10, ExistingWorkPolicy.REPLACE, req)
            }
        }
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(UNIQUE_60)
        wm.cancelUniqueWork(UNIQUE_10)
    }
}
