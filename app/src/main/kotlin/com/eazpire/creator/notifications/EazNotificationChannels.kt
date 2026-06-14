package com.eazpire.creator.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.eazpire.creator.R

/**
 * One Android notification channel per push preference bucket so users can control
 * each type separately under system Settings → Apps → eazpire → Notifications.
 * Channel titles/descriptions come from string resources (device locale).
 */
object EazNotificationChannels {

    // Shop
    const val SHOP_CART = "eaz_shop_cart_reminder"
    const val SHOP_ORDERS = "eaz_shop_orders"
    const val SHOP_PROMOTIONS_NEW = "eaz_shop_promotions_new"
    const val SHOP_PROMOTIONS_ENDING = "eaz_shop_promotions_ending"
    const val SHOP_APP_PROMOTIONS = "eaz_shop_app_promotions"
    const val SHOP_DAILY_GAME = "eaz_shop_daily_game"

    // Creator
    const val CREATOR_GENERATIONS = "eaz_creator_generations"
    const val CREATOR_DESIGN_SAVED = "eaz_creator_design_saved"
    const val CREATOR_PRODUCT_PUBLISHED = "eaz_creator_product_published"
    const val CREATOR_COMMUNITY = "eaz_creator_community"
    const val CREATOR_OTHER = "eaz_creator_other"

    /** Replaced by granular channels — removed on [ensure] so system settings stay tidy. */
    private val LEGACY_CHANNEL_IDS = listOf(
        "eaz_push_in_app",
        "eaz_cart_reminder",
    )

    private data class ChannelSpec(
        val id: String,
        val titleRes: Int,
        val descRes: Int,
    )

    private val SHOP_CHANNELS = listOf(
        ChannelSpec(SHOP_CART, R.string.notif_shop_cart, R.string.notif_info_shop_cart),
        ChannelSpec(SHOP_ORDERS, R.string.notif_shop_orders, R.string.notif_info_shop_orders),
        ChannelSpec(
            SHOP_PROMOTIONS_NEW,
            R.string.notif_shop_promotions_new,
            R.string.notif_info_shop_promotions_new,
        ),
        ChannelSpec(
            SHOP_PROMOTIONS_ENDING,
            R.string.notif_shop_promotions_ending,
            R.string.notif_info_shop_promotions_ending,
        ),
        ChannelSpec(
            SHOP_APP_PROMOTIONS,
            R.string.notif_shop_app_promotions,
            R.string.notif_info_shop_app_promotions,
        ),
        ChannelSpec(SHOP_DAILY_GAME, R.string.notif_shop_daily_game, R.string.notif_info_shop_daily_game),
    )

    private val CREATOR_CHANNELS = listOf(
        ChannelSpec(
            CREATOR_GENERATIONS,
            R.string.notif_creator_generations,
            R.string.notif_info_creator_generations,
        ),
        ChannelSpec(
            CREATOR_DESIGN_SAVED,
            R.string.notif_creator_designs,
            R.string.notif_info_creator_designs,
        ),
        ChannelSpec(
            CREATOR_PRODUCT_PUBLISHED,
            R.string.notif_creator_publish,
            R.string.notif_info_creator_publish,
        ),
        ChannelSpec(
            CREATOR_COMMUNITY,
            R.string.notif_creator_community,
            R.string.notif_info_creator_community,
        ),
        ChannelSpec(
            CREATOR_OTHER,
            R.string.notif_creator_other,
            R.string.notif_info_creator_other,
        ),
    )

    enum class PrefScope { SHOP, CREATOR }

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        LEGACY_CHANNEL_IDS.forEach { nm.deleteNotificationChannel(it) }
        (SHOP_CHANNELS + CREATOR_CHANNELS).forEach { spec ->
            nm.createNotificationChannel(
                NotificationChannel(
                    spec.id,
                    context.getString(spec.titleRes),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(spec.descRes)
                },
            )
        }
    }

    /** Maps FCM `category` (+ optional `audience`) to a channel id. */
    fun channelIdForCategory(category: String?, audience: String? = null): String {
        val scope = NotificationCategoryMapping.inferScope(category, audience)
        val prefKey =
            if (scope == PrefScope.SHOP) {
                NotificationCategoryMapping.categoryToShopKey(category)
            } else {
                NotificationCategoryMapping.categoryToCreatorKey(category)
            }
        return channelIdForPrefKey(scope, prefKey)
    }

    /** Maps in-app preference bucket ids (same keys as worker notification prefs). */
    fun channelIdForPrefKey(scope: PrefScope, prefKey: String): String {
        return when (scope) {
            PrefScope.SHOP ->
                when (prefKey) {
                    "cart_reminder" -> SHOP_CART
                    "orders" -> SHOP_ORDERS
                    "promotions_new" -> SHOP_PROMOTIONS_NEW
                    "promotions_ending_soon" -> SHOP_PROMOTIONS_ENDING
                    "app_promotions" -> SHOP_APP_PROMOTIONS
                    "daily_game" -> SHOP_DAILY_GAME
                    else -> SHOP_PROMOTIONS_NEW
                }
            PrefScope.CREATOR ->
                when (prefKey) {
                    "generations" -> CREATOR_GENERATIONS
                    "design_saved" -> CREATOR_DESIGN_SAVED
                    "product_published" -> CREATOR_PRODUCT_PUBLISHED
                    "community" -> CREATOR_COMMUNITY
                    "other" -> CREATOR_OTHER
                    else -> CREATOR_OTHER
                }
        }
    }
}
