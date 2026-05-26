package com.eazpire.creator.cart

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eazpire.creator.notifications.EazNotificationDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local notification: 60 min and 10 min before promo slot ends (cart).
 */
class CartPromoReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: return Result.failure()
        withContext(Dispatchers.IO) {
            EazNotificationDisplay.showCartPromoReminderInternal(applicationContext, kind)
        }
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
    }
}
