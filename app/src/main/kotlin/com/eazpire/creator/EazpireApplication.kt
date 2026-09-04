package com.eazpire.creator

import android.app.Application
import com.eazpire.creator.cart.CartReminderScheduler
import com.eazpire.creator.chat.DailyGameReminderScheduler
import com.eazpire.creator.config.AnimationFlagsSync
import com.eazpire.creator.notifications.EazNotificationChannels
import com.eazpire.creator.notifications.NotificationRemoteConfigSync
import com.eazpire.creator.perf.EazPerfTrace
import com.eazpire.shared.EazpireApps
import com.eazpire.shared.security.TrustedPackages

class EazpireApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EazpireAppTiming.markProcessStart()
        EazPerfTrace.init(this)
        EazPerfTrace.mark("application_onCreate_start")
        // IDEA-093: register Play/upload cert digests here when available, e.g.:
        // TrustedPackages.register(EazpireApps.WEAR_PLAYER, "<sha256-hex>")
        TrustedPackages.ensurePackage(EazpireApps.WEAR_PLAYER)
        TrustedPackages.ensurePackage(EazpireApps.SHOP)
        TrustedPackages.ensurePackage(EazpireApps.CREATOR)
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
