package com.eazpire.creator.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.IconCompat
import com.eazpire.creator.MainActivity
import com.eazpire.creator.R
import com.eazpire.creator.chat.EazySidebarTab
import kotlin.math.min

object EazNotificationDisplay {
    private const val REQ_PUSH = 1001
    private const val REQ_CART = 1002

    /**
     * Brand mark is shown only via [setLargeIcon] (left circle). [setSmallIcon] is mandatory but on
     * Material/Pixel also fills the trailing slot — we use a 1×1 transparent bitmap so no second logo on the right.
     */
    private const val SHADE_LARGE_ICON_DP = 64f

    /** Inset for the shade left large circle. */
    private const val SHADE_LARGE_ICON_INSET_SCALE = 0.88f

    /** Bump when changing icon rendering so cached bitmaps are not reused across builds. */
    private const val ICON_RENDER_VERSION = 9

    @Volatile
    private var transparentSmallIconBitmap: Bitmap? = null

    @Volatile
    private var cachedShadeLargeIcon: Pair<Int, Bitmap>? = null

    /** Required platform small icon: invisible in the shade so only [shadeLargeIconBitmap] shows the mark. */
    private fun smallIconCompat(context: Context): IconCompat {
        return try {
            val bmp = transparentSmallIconBitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(Color.TRANSPARENT)
                transparentSmallIconBitmap = it
            }
            IconCompat.createWithBitmap(bmp)
        } catch (_: Exception) {
            IconCompat.createWithResource(context, R.drawable.ic_stat_eazpire)
        }
    }

    /** Same brand mark for the shade’s large-icon slot (left circle). */
    private fun shadeLargeIconBitmap(context: Context): Bitmap? {
        val app = context.applicationContext
        val cached = cachedShadeLargeIcon
        if (cached != null && cached.first == ICON_RENDER_VERSION) {
            return cached.second
        }
        return try {
            val bmp = buildBrandIconBitmap(app, SHADE_LARGE_ICON_DP, SHADE_LARGE_ICON_INSET_SCALE)
            cachedShadeLargeIcon = ICON_RENDER_VERSION to bmp
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun buildBrandIconBitmap(context: Context, canvasDp: Float, insetScale: Float): Bitmap {
        val d = ContextCompat.getDrawable(context, R.drawable.eazpire_logo)
            ?: return buildFallbackBrandIconBitmap(context, canvasDp, insetScale)
        DrawableCompat.setTint(d, Color.WHITE)
        val sizePx = (canvasDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bmp)
        val iw = d.intrinsicWidth.coerceAtLeast(1)
        val ih = d.intrinsicHeight.coerceAtLeast(1)
        val scale = min(sizePx.toFloat() / iw, sizePx.toFloat() / ih) * insetScale
        val w = (iw * scale).toInt()
        val h = (ih * scale).toInt()
        val left = (sizePx - w) / 2
        val top = (sizePx - h) / 2
        d.setBounds(left, top, left + w, top + h)
        d.draw(canvas)
        return bmp
    }

    private fun buildFallbackBrandIconBitmap(context: Context, canvasDp: Float, insetScale: Float): Bitmap {
        val d = ContextCompat.getDrawable(context, R.drawable.ic_stat_eazpire)
            ?: throw IllegalStateException("ic_stat_eazpire missing")
        val sizePx = (canvasDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bmp)
        val side = (sizePx * insetScale).toInt()
        val left = (sizePx - side) / 2
        val top = (sizePx - side) / 2
        d.setBounds(left, top, left + side, top + side)
        d.draw(canvas)
        return bmp
    }

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
                "shop" -> putExtra(MainActivity.EXTRA_OPEN_SHOP, true)
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
            .setSmallIcon(smallIconCompat(context))
            .setLargeIcon(shadeLargeIconBitmap(context))
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
            .setSmallIcon(smallIconCompat(context))
            .setLargeIcon(shadeLargeIconBitmap(context))
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
