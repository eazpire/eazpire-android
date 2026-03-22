package com.eazpire.creator.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eazpire.creator.MainActivity
import com.eazpire.creator.R
import com.eazpire.creator.chat.EazySidebarTab

object EazNotificationDisplay {
    private const val REQ_PUSH = 1001
    private const val REQ_CART = 1002

    private fun smallIcon(): Int = R.drawable.ic_launcher_foreground

    /**
     * Maps FCM `data` (e.g. [open_target]) to MainActivity extras.
     * [open_target]: cart | eazy_jobs | eazy_notifications | eazy_chat
     */
    fun buildMainIntentFromPushExtras(context: Context, extras: Map<String, String?>): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val raw = extras["open_target"]?.lowercase() ?: extras["nav_target"]?.lowercase()
            when (raw) {
                "cart" -> putExtra(MainActivity.EXTRA_OPEN_CART, true)
                "eazy_jobs", "jobs" -> {
                    putExtra(MainActivity.EXTRA_OPEN_EAZY_CHAT, true)
                    putExtra(MainActivity.EXTRA_EAZY_TAB, EazySidebarTab.Jobs.name)
                }
                "eazy_chat", "chat" -> {
                    putExtra(MainActivity.EXTRA_OPEN_EAZY_CHAT, true)
                    putExtra(MainActivity.EXTRA_EAZY_TAB, EazySidebarTab.Chat.name)
                }
                "eazy_notifications", "notifications" -> {
                    putExtra(MainActivity.EXTRA_OPEN_EAZY_CHAT, true)
                    putExtra(MainActivity.EXTRA_EAZY_TAB, EazySidebarTab.Notifications.name)
                }
                null, "" -> {
                    putExtra(MainActivity.EXTRA_OPEN_EAZY_CHAT, true)
                    putExtra(MainActivity.EXTRA_EAZY_TAB, EazySidebarTab.Notifications.name)
                }
                else -> {
                    putExtra(MainActivity.EXTRA_OPEN_EAZY_CHAT, true)
                    putExtra(MainActivity.EXTRA_EAZY_TAB, EazySidebarTab.Notifications.name)
                }
            }
        }
    }

    fun showPush(
        context: Context,
        title: String,
        body: String,
        notificationId: Int,
        extras: Map<String, String?> = emptyMap()
    ) {
        EazNotificationChannels.ensure(context)
        val intent = buildMainIntentFromPushExtras(context, extras)
        val pending = PendingIntent.getActivity(
            context,
            REQ_PUSH + notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, EazNotificationChannels.PUSH_IN_APP)
            .setSmallIcon(smallIcon())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, n)
    }

    /**
     * Local test push (same channel and deep link as FCM) — for QA from notification settings.
     * [openTarget]: cart | eazy_jobs | eazy_notifications | eazy_chat
     */
    fun showTestPushForOpenTarget(context: Context, rowLabel: String, openTarget: String) {
        val title = context.getString(R.string.notif_test_push_title, rowLabel)
        val body = context.getString(R.string.notif_test_push_body)
        val extras = mapOf(
            "open_target" to openTarget,
            "category" to "test_local",
            "notification_id" to "test-${System.currentTimeMillis()}"
        )
        val nid = ((System.currentTimeMillis() % 100_000).toInt() + REQ_PUSH) and 0x7fff_ffff
        showPush(context, title, body, nid, extras)
    }

    fun showCartReminder(context: Context) {
        EazNotificationChannels.ensure(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_CART, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            REQ_CART,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = context.getString(R.string.notification_cart_title)
        val body = context.getString(R.string.notification_cart_body)
        val n = NotificationCompat.Builder(context, EazNotificationChannels.CART_REMINDER)
            .setSmallIcon(smallIcon())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(REQ_CART, n)
    }
}
