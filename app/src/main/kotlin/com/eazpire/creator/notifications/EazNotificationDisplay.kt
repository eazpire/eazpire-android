package com.eazpire.creator.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.eazpire.creator.MainActivity
import com.eazpire.creator.R
import com.eazpire.creator.chat.EazySidebarTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.math.min

object EazNotificationDisplay {
    private const val REQ_PUSH = 1001
    private const val REQ_CART = 1002
    private const val REQ_DAILY_GAME = 1004

    /** Large-icon / hero bitmap size (px). */
    private const val HERO_ICON_DP = 256f

    /** Inset for category fallback vectors inside the hero circle. */
    private const val FALLBACK_ICON_INSET_SCALE = 0.62f

    private const val HERO_LOAD_TIMEOUT_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Status bar + compact notification app mark (white monochrome). */
    private fun smallIconRes(): Int = R.drawable.ic_notification

    /**
     * Maps FCM `data` (e.g. [open_target]) to MainActivity extras.
     * [open_target]: cart | eazy_jobs | eazy_notifications | eazy_chat | creator_designs_inactive
     */
    fun buildMainIntentFromPushExtras(context: Context, extras: Map<String, String?>): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            val raw = extras["open_target"]?.lowercase() ?: extras["nav_target"]?.lowercase()
            val gamesSection = (
                extras["games_section"]
                    ?: extras["pendingGamesSection"]
                    ?: extras["pending_games_section"]
                )?.trim()?.takeIf { it.isNotBlank() }
            val tradeOfferId = (
                extras["trade_offer_id"]
                    ?: extras["tradeOfferId"]
                )?.toIntOrNull()
            when (raw) {
                "cart" -> putExtra(MainActivity.EXTRA_OPEN_CART, true)
                "shop" -> putExtra(MainActivity.EXTRA_OPEN_SHOP, true)
                "creator_designs_inactive", "creator_inactive_designs", "designs_inactive" -> {
                    putExtra(MainActivity.EXTRA_OPEN_CREATOR_INACTIVE_DESIGNS, true)
                }
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
                "eazy_games", "games", "daily_game" -> {
                    putExtra(MainActivity.EXTRA_OPEN_EAZY_CHAT, true)
                    putExtra(MainActivity.EXTRA_EAZY_TAB, EazySidebarTab.Games.name)
                }
                "creator_settings_codes", "creator_codes", "creator-codes" -> {
                    putExtra(MainActivity.EXTRA_OPEN_CREATOR_CODES, true)
                    extras["code"]?.trim()?.takeIf { it.isNotBlank() }?.let {
                        putExtra(MainActivity.EXTRA_CREATOR_CODE_PREFILL, it)
                    }
                }
                "gift_cards_won", "voucher_gift_cards_won", "gift_cards_rewards" -> {
                    putExtra(MainActivity.EXTRA_OPEN_GIFT_CARDS_WON, true)
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
            if (!gamesSection.isNullOrBlank()) {
                putExtra(MainActivity.EXTRA_OPEN_EAZY_CHAT, true)
                putExtra(MainActivity.EXTRA_EAZY_TAB, EazySidebarTab.Games.name)
                putExtra(MainActivity.EXTRA_GAMES_SECTION, gamesSection)
            }
            if ((tradeOfferId ?: 0) > 0) {
                putExtra(MainActivity.EXTRA_OPEN_EAZY_CHAT, true)
                putExtra(MainActivity.EXTRA_EAZY_TAB, EazySidebarTab.Games.name)
                putExtra(MainActivity.EXTRA_GAMES_SECTION, gamesSection ?: "collection")
                putExtra(MainActivity.EXTRA_TRADE_OFFER_ID, tradeOfferId)
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
        scope.launch {
            showPushInternal(context, title, body, notificationId, extras)
        }
    }

    suspend fun showPushInternal(
        context: Context,
        title: String,
        body: String,
        notificationId: Int,
        extras: Map<String, String?> = emptyMap()
    ) {
        val app = context.applicationContext
        EazNotificationChannels.ensure(app)
        val category = extras["category"]
        val channelId = extras["android_channel_id"]?.takeIf { it.isNotBlank() }
            ?: EazNotificationChannels.channelIdForCategory(category, extras["audience"])
        val heroVisual = resolveHeroVisual(app, extras, category)
        postNotification(
            context = app,
            channelId = channelId,
            title = title,
            body = body,
            notificationId = notificationId,
            requestCode = REQ_PUSH + notificationId,
            extras = extras,
            heroVisual = heroVisual
        )
    }

    /**
     * Local test push (same channel and deep link as FCM) — for QA from notification settings.
     * [openTarget]: cart | eazy_jobs | eazy_notifications | eazy_chat
     */
    fun showTestPushForOpenTarget(
        context: Context,
        rowLabel: String,
        openTarget: String,
        prefScope: EazNotificationChannels.PrefScope,
        prefKey: String,
    ) {
        val title = context.getString(R.string.notif_test_push_title, rowLabel)
        val body = context.getString(R.string.notif_test_push_body)
        val category = testCategoryForPrefKey(prefScope, prefKey)
        val extras = mapOf(
            "open_target" to openTarget,
            "category" to category,
            "notification_id" to "test-${System.currentTimeMillis()}",
        )
        val nid = ((System.currentTimeMillis() % 100_000).toInt() + REQ_PUSH) and 0x7fff_ffff
        showPush(context, title, body, nid, extras)
    }

    fun showCartReminder(context: Context) {
        scope.launch {
            showCartReminderInternal(context)
        }
    }

    suspend fun showCartReminderInternal(context: Context) {
        val app = context.applicationContext
        EazNotificationChannels.ensure(app)
        val title = app.getString(R.string.notification_cart_title)
        val body = app.getString(R.string.notification_cart_body)
        val extras = mapOf(
            "open_target" to "cart",
            "category" to "android_cart_abandon"
        )
        val heroVisual = HeroVisual(appLauncherLargeIconBitmap(app), isRemoteImage = false)
        postNotification(
            context = app,
            channelId = EazNotificationChannels.channelIdForCategory("android_cart_abandon"),
            title = title,
            body = body,
            notificationId = REQ_CART,
            requestCode = REQ_CART,
            extras = extras,
            heroVisual = heroVisual
        )
    }

    fun showDailyGameAvailable(context: Context) {
        scope.launch {
            showDailyGameAvailableInternal(context)
        }
    }

    suspend fun showDailyGameAvailableInternal(context: Context) {
        val app = context.applicationContext
        EazNotificationChannels.ensure(app)
        val title = app.getString(R.string.notification_daily_game_title)
        val body = app.getString(R.string.notification_daily_game_body)
        val extras =
            mapOf(
                "open_target" to "eazy_games",
                "category" to "daily_game_reminder",
            )
        val heroVisual = HeroVisual(appLauncherLargeIconBitmap(app), isRemoteImage = false)
        postNotification(
            context = app,
            channelId = EazNotificationChannels.channelIdForCategory("daily_game_reminder"),
            title = title,
            body = body,
            notificationId = REQ_DAILY_GAME,
            requestCode = REQ_DAILY_GAME,
            extras = extras,
            heroVisual = heroVisual,
        )
    }

    fun showCartPromoReminder(context: Context, kind: String) {
        scope.launch {
            showCartPromoReminderInternal(context, kind)
        }
    }

    suspend fun showCartPromoReminderInternal(context: Context, kind: String) {
        val app = context.applicationContext
        EazNotificationChannels.ensure(app)
        val is60 = kind == "60"
        val title = app.getString(
            if (is60) R.string.notif_cart_promo_60_title else R.string.notif_cart_promo_10_title
        )
        val body = app.getString(
            if (is60) R.string.notif_cart_promo_60_body else R.string.notif_cart_promo_10_body
        )
        val category = if (is60) "android_cart_promo_60" else "android_cart_promo_10"
        val notificationId = if (is60) NOTIF_ID_CART_PROMO_60 else NOTIF_ID_CART_PROMO_10
        val extras = mapOf(
            "open_target" to "cart",
            "category" to category
        )
        val heroVisual = HeroVisual(appLauncherLargeIconBitmap(app), isRemoteImage = false)
        postNotification(
            context = app,
            channelId = EazNotificationChannels.channelIdForCategory(category),
            title = title,
            body = body,
            notificationId = notificationId,
            requestCode = notificationId,
            extras = extras,
            heroVisual = heroVisual
        )
    }

    /** Synthetic FCM-like categories so test pushes land on the matching system channel. */
    private fun testCategoryForPrefKey(
        prefScope: EazNotificationChannels.PrefScope,
        prefKey: String,
    ): String {
        if (prefScope == EazNotificationChannels.PrefScope.SHOP) {
            return when (prefKey) {
                "cart_reminder" -> "android_cart_abandon"
                "orders" -> "shop_order_update"
                "promotions_new" -> "shop_promotion_new"
                "promotions_ending_soon" -> "shop_promotion_ending_soon"
                "app_promotions" -> "app_install_bonus"
                "daily_game" -> "daily_game_reminder"
                "shop_master" -> "shop_promotion_new"
                else -> "shop_promotion_new"
            }
        }
        return when (prefKey) {
            "generations" -> "generated"
            "design_saved" -> "saved"
            "product_published" -> "published"
            "community" -> "community_referral"
            "creator_master", "other" -> "creator_system"
            else -> "creator_system"
        }
    }

    private data class HeroVisual(val bitmap: Bitmap?, val isRemoteImage: Boolean)

    private suspend fun postNotification(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        notificationId: Int,
        requestCode: Int,
        extras: Map<String, String?>,
        heroVisual: HeroVisual
    ) {
        val intent = buildMainIntentFromPushExtras(context, extras)
        val pending = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIconRes())
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)

        if (heroVisual.bitmap != null) {
            builder.setLargeIcon(heroVisual.bitmap)
        }

        if (heroVisual.isRemoteImage && heroVisual.bitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(heroVisual.bitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(body)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        withContext(Dispatchers.Main) {
            val nm = NotificationManagerCompat.from(context)
            if (!canPostNotifications(context, nm)) return@withContext
            try {
                nm.notify(notificationId, builder.build())
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS denied or revoked — never crash the app.
            }
        }
    }

    private fun canPostNotifications(context: Context, nm: NotificationManagerCompat): Boolean {
        if (!nm.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private suspend fun resolveHeroVisual(
        context: Context,
        extras: Map<String, String?>,
        category: String?
    ): HeroVisual {
        resolveHeroImageUrl(extras)?.let { url ->
            loadHeroBitmap(context, url)?.let { return HeroVisual(it, isRemoteImage = true) }
        }
        return HeroVisual(appLauncherLargeIconBitmap(context), isRemoteImage = false)
    }

    fun resolveHeroImageUrl(extras: Map<String, String?>): String? {
        val orderedKeys = listOf(
            "hero_image_url",
            "preview_url",
            "image_url",
            "thumbnail_url",
            "product_image_url"
        )
        for (key in orderedKeys) {
            extras[key]?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        }
        extras["result"]?.let { parseNestedImageUrl(it) }?.let { return it }
        return null
    }

    private fun parseNestedImageUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val json = JSONObject(trimmed)
            listOf("preview_url", "image_url", "thumbnail_url")
                .asSequence()
                .map { json.optString(it).trim() }
                .firstOrNull { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    @DrawableRes
    fun fallbackIconRes(category: String?): Int {
        val c = category?.lowercase()?.trim().orEmpty()
        if (c.isEmpty()) return R.drawable.ic_notification
        if (c.contains("cart") || c.contains("abandon") || c.startsWith("android_cart")) {
            return R.drawable.ic_notif_cart
        }
        if (c.contains("job_started") || c == "active_job") {
            return R.drawable.ic_notif_job
        }
        if (c.contains("shop")) {
            return R.drawable.ic_notif_shop
        }
        if (c.contains("video") || c.contains("hero_image") || c == "hero_image") {
            return R.drawable.ic_notif_media
        }
        if (c.startsWith("mentor_") &&
            !c.contains("design") &&
            !c.contains("hero") &&
            !c.contains("product")
        ) {
            return R.drawable.ic_notif_community
        }
        if (c.contains("gift") || c.contains("referral") || c.contains("community") || c.contains("creator_code")) {
            return R.drawable.ic_notif_community
        }
        return R.drawable.ic_notification
    }

    /** Orange eazpire app logo for notification large-icon slot. */
    private fun appLauncherLargeIconBitmap(context: Context): Bitmap? {
        return try {
            val d = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return null
            drawableToBitmap(d)?.let { fitCenterSquare(it, heroIconSizePx(context)) }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun loadHeroBitmap(context: Context, url: String): Bitmap? {
        return withTimeoutOrNull(HERO_LOAD_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .allowHardware(false)
                        .build()
                    when (val result = context.imageLoader.execute(request)) {
                        is SuccessResult -> drawableToBitmap(result.drawable)
                        else -> null
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }?.let { fitCenterSquare(it, heroIconSizePx(context)) }
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val w = drawable.intrinsicWidth.coerceAtLeast(1)
        val h = drawable.intrinsicHeight.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, w, h)
        drawable.draw(canvas)
        return bmp
    }

    private fun fallbackIconBitmap(context: Context, @DrawableRes resId: Int): Bitmap? {
        return try {
            val sizePx = heroIconSizePx(context)
            val d = ContextCompat.getDrawable(context, resId) ?: return null
            DrawableCompat.setTint(d, Color.WHITE)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.TRANSPARENT)
            val canvas = Canvas(bmp)
            val side = (sizePx * FALLBACK_ICON_INSET_SCALE).toInt()
            val left = (sizePx - side) / 2
            val top = (sizePx - side) / 2
            d.setBounds(left, top, left + side, top + side)
            d.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    /** Fit-center into a square without cropping or stretching. */
    fun fitCenterSquare(source: Bitmap, sizePx: Int): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(out)
        val scale = min(sizePx.toFloat() / source.width, sizePx.toFloat() / source.height)
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val left = (sizePx - w) / 2
        val top = (sizePx - h) / 2
        canvas.drawBitmap(source, null, Rect(left, top, left + w, top + h), null)
        return out
    }

    private fun heroIconSizePx(context: Context): Int {
        return (HERO_ICON_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(128)
    }

    private const val NOTIF_ID_CART_PROMO_60 = 91001
    private const val NOTIF_ID_CART_PROMO_10 = 91002
}
