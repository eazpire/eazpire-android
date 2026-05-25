package com.eazpire.creator

import android.app.Application
import com.eazpire.creator.cart.CartReminderScheduler
import com.eazpire.creator.notifications.EazNotificationChannels
import com.eazpire.creator.notifications.NotificationRemoteConfigSync

class EazpireApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EazNotificationChannels.ensure(this)
        CartReminderScheduler.init(this)
        NotificationRemoteConfigSync.syncOnAppStart(this)
    }
}
