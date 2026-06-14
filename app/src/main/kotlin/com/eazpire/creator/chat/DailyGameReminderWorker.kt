package com.eazpire.creator.chat

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

/** Skip background notify briefly after process start (WorkManager can fire before permission UI). */
private const val COLD_START_GRACE_MS = 12_000L

class DailyGameReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val pushFlag = inputData.getBoolean("push", true)
            if (!pushFlag) return@withContext Result.success()

            val np = NotificationPreferencesRepository(applicationContext).readSnapshot()
            if (!np.shopMaster) return@withContext Result.success()
            if (np.shop["daily_game"] == false) return@withContext Result.success()

            if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                return@withContext Result.success()
            }
            if (System.currentTimeMillis() - EazpireApplication.processStartMs < COLD_START_GRACE_MS) {
                return@withContext Result.success()
            }

            EazNotificationDisplay.showDailyGameAvailableInternal(applicationContext)
            Result.success()
        }
}
