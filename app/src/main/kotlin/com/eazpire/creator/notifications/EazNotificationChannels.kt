package com.eazpire.creator.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.eazpire.creator.R

object EazNotificationChannels {
    const val PUSH_IN_APP = "eaz_push_in_app"
    const val CART_REMINDER = "eaz_cart_reminder"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                PUSH_IN_APP,
                context.getString(R.string.notification_channel_push_title),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_push_desc)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CART_REMINDER,
                context.getString(R.string.notification_channel_cart_title),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_cart_desc)
            }
        )
    }
}
