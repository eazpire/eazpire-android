package com.eazpire.creator

import android.app.Application
import com.eazpire.creator.cart.CartReminderScheduler
import com.eazpire.creator.chat.DailyGameReminderScheduler
import com.eazpire.creator.config.AnimationFlagsSync
import com.eazpire.creator.notifications.EazNotificationChannels
import com.eazpire.creator.notifications.NotificationRemoteConfigSync
import com.eazpire.creator.perf.EazPerfTrace

class EazpireApplication : Application() {
    companion object {
        /** Process start — workers skip background notifications during cold-start window. */
        @Volatile
        var processStartMs: Long = 0L
            private set
    }

    override fun onCreate() {
        super.onCreate()
        processStartMs = System.currentTimeMillis()
        EazPerfTrace.init(this)
        EazPerfTrace.mark("application_onCreate_start")
        EazNotificationChannels.ensure(this)
        CartReminderScheduler.init(this)
        DailyGameReminderScheduler.init(this)
        // Defer non-critical network sync so cold start reaches home content sooner.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            NotificationRemoteConfigSync.syncOnAppStart(this)
            AnimationFlagsSync.syncOnAppStart(this)
        }, 3_000L)
        EazPerfTrace.mark("application_onCreate_end")
    }
}
