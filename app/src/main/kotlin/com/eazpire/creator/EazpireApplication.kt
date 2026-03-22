package com.eazpire.creator

import android.app.Application
import com.eazpire.creator.cart.CartReminderScheduler
import com.eazpire.creator.notifications.EazNotificationChannels

class EazpireApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        EazNotificationChannels.ensure(this)
        CartReminderScheduler.init(this)
    }
}
