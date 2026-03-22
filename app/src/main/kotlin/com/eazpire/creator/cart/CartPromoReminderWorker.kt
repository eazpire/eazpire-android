package com.eazpire.creator.cart

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eazpire.creator.R
import com.eazpire.creator.notifications.EazNotificationChannels

/**
 * Local notification: 60 min and 10 min before promo slot ends (cart).
 */
class CartPromoReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: return Result.failure()
        EazNotificationChannels.ensure(applicationContext)
        val title = applicationContext.getString(
            if (kind == "60") R.string.notif_cart_promo_60_title else R.string.notif_cart_promo_10_title
        )
        val body = applicationContext.getString(
            if (kind == "60") R.string.notif_cart_promo_60_body else R.string.notif_cart_promo_10_body
        )
        val n = NotificationCompat.Builder(applicationContext, EazNotificationChannels.CART_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_eazpire)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(
            if (kind == "60") NOTIF_ID_60 else NOTIF_ID_10,
            n
        )
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
        private const val NOTIF_ID_60 = 91001
        private const val NOTIF_ID_10 = 91002
    }
}
