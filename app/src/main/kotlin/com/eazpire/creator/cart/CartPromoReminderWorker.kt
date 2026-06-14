package com.eazpire.creator.cart

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eazpire.creator.EazpireApplication
import com.eazpire.creator.notifications.EazNotificationDisplay
import com.eazpire.creator.notifications.NotificationPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local notification: 60 min and 10 min before promo slot ends (cart).
 * Respects shop_master + cart_reminder / promotions_ending_soon user prefs.
 */
private const val COLD_START_GRACE_MS = 12_000L

class CartPromoReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val kind = inputData.getString(KEY_KIND) ?: return@withContext Result.failure()
        val np = NotificationPreferencesRepository(applicationContext).readSnapshot()
        if (!np.shopMaster) return@withContext Result.success()
        val cartOk = np.shop["cart_reminder"] != false
        val promoEndingOk = np.shop["promotions_ending_soon"] != false
        if (!cartOk && !promoEndingOk) return@withContext Result.success()
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return@withContext Result.success()
        }
        if (System.currentTimeMillis() - EazpireApplication.processStartMs < COLD_START_GRACE_MS) {
            return@withContext Result.success()
        }
        EazNotificationDisplay.showCartPromoReminderInternal(applicationContext, kind)
        Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
    }
}
