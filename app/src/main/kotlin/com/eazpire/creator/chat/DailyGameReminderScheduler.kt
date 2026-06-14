package com.eazpire.creator.chat

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Schedules a local reminder when the daily game cooldown ends (matches server timer). */
object DailyGameReminderScheduler {
    private const val UNIQUE_NAME = "eaz_daily_game_available"

    private lateinit var appCtx: Context

    fun init(context: Context) {
        appCtx = context.applicationContext
    }

    fun sync(notifyAtMs: Long?, pushEnabled: Boolean, emailEnabled: Boolean) {
        if (!::appCtx.isInitialized) return
        val wm = WorkManager.getInstance(appCtx)
        if (notifyAtMs == null || (!pushEnabled && !emailEnabled)) {
            wm.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val delayMs = notifyAtMs - System.currentTimeMillis()
        if (delayMs <= 15_000L) {
            wm.cancelUniqueWork(UNIQUE_NAME)
            return
        }
        val req =
            OneTimeWorkRequestBuilder<DailyGameReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        "push" to pushEnabled,
                        "email" to emailEnabled,
                    ),
                )
                .build()
        wm.enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, req)
    }
}
