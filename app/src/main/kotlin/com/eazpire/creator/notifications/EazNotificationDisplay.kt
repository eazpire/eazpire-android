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

    /** Same visual weight as ~24dp slot; bitmap resolution scales with density. */
    private const val SMALL_ICON_DP = 24f

    /**
     * Slight inset so the circular large-icon mask matches how the launcher icon reads (no edge clip).
     * 1f = full bounds (identical raster to drawing the adaptive icon at that pixel size).
     */
    private const val LARGE_ICON_INSET_SCALE = 0.94f

    @Volatile
    private var cachedSmallIcon: Bitmap? = null

    @Volatile
    private var cachedLargeIcon: Bitmap? = null

    /**
     * Status bar / collapsed row: white silhouette of [R.drawable.eazpire_logo] (real mark).
     * Android does not allow full-color glyphs here; shape matches the brand PNG.
     */
    private fun smallIconCompat(context: Context): IconCompat {
        return try {
            val bmp = cachedSmallIcon
                ?: buildSmallIconBitmap(context.applicationContext).also { cachedSmallIcon = it }
            IconCompat.createWithBitmap(bmp)
        } catch (_: Exception) {
            IconCompat.createWithResource(context, R.drawable.ic_stat_eazpire)
        }
    }

    private fun buildSmallIconBitmap(context: Context): Bitmap {
        val d = ContextCompat.getDrawable(context, R.drawable.eazpire_logo)
            ?: return buildFallbackSmallIconBitmap(context)
        DrawableCompat.setTint(d, Color.WHITE)
        val sizePx = (SMALL_ICON_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bmp)
        val iw = d.intrinsicWidth.coerceAtLeast(1)
        val ih = d.intrinsicHeight.coerceAtLeast(1)
        val scale = min(sizePx.toFloat() / iw, sizePx.toFloat() / ih) * 1f
        val w = (iw * scale).toInt()
        val h = (ih * scale).toInt()
        val left = (sizePx - w) / 2
        val top = (sizePx - h) / 2
        d.setBounds(left, top, left + w, top + h)
        d.draw(canvas)
        return bmp
    }

    private fun buildFallbackSmallIconBitmap(context: Context): Bitmap {
        val d = ContextCompat.getDrawable(context, R.drawable.ic_stat_eazpire)
            ?: throw IllegalStateException("ic_stat_eazpire missing")
        val sizePx = (SMALL_ICON_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(48)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(canvas)
        return bmp
    }

    /**
     * Expanded notification: same [R.mipmap.ic_launcher] composition as the app icon, centered in a square,
     * scaled slightly so the system circular crop matches the launcher mask.
     */
    private fun largeIconBitmap(context: Context): Bitmap? {
        cachedLargeIcon?.let { return it }
        val app = context.applicationContext
        val d = ContextCompat.getDrawable(app, R.mipmap.ic_launcher) ?: return null
        val dm = app.resources.displayMetrics
        val sizePx = (64 * dm.density).toInt().coerceIn(160, 320)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bmp)
        canvas.save()
        canvas.scale(LARGE_ICON_INSET_SCALE, LARGE_ICON_INSET_SCALE, sizePx / 2f, sizePx / 2f)
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(canvas)
        canvas.restore()
        cachedLargeIcon = bmp
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
            .setLargeIcon(largeIconBitmap(context))
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
            .setLargeIcon(largeIconBitmap(context))
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
