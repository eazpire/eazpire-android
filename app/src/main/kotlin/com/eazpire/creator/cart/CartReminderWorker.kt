package com.eazpire.creator.cart

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.notifications.EazNotificationDisplay
import com.eazpire.creator.notifications.NotificationPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CartReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val np = NotificationPreferencesRepository(applicationContext).readSnapshot()
        if (!np.shopMaster) return@withContext Result.success()
        if (np.shop["cart_reminder"] == false) return@withContext Result.success()
        if (AppCartStore.itemCount <= 0) return@withContext Result.success()
        val cartStore = StorefrontCartStore(applicationContext)
        val cartId = cartStore.cartId ?: return@withContext Result.success()
        val api = ShopifyStorefrontCartApi()
        val cart = api.getCart(cartId) ?: run {
            cartStore.clear()
            AppCartStore.clear()
            return@withContext Result.success()
        }
        if (cart.lines.isEmpty()) {
            AppCartStore.setCount(0)
            return@withContext Result.success()
        }
        EazNotificationDisplay.showCartReminder(applicationContext)
        Result.success()
    }
}
