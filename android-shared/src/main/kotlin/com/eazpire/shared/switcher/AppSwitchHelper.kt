package com.eazpire.shared.switcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.eazpire.shared.EazpireApps
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Opens the sibling Shop/Creator app with an optional exchange token,
 * or the Play Store listing when the sibling is not installed.
 *
 * Soft-launch: keep in-process mode until [com.eazpire.creator.BuildConfig.USE_EXTERNAL_APP_SWITCH]
 * is enabled in the Creator app.
 */
object AppSwitchHelper {

    sealed class Result {
        data object OpenedApp : Result()
        data object OpenedPlayStore : Result()
        data object Failed : Result()
    }

    fun isInstalled(context: Context, target: EazpireApps.Target): Boolean {
        val pkg = EazpireApps.packageId(target)
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkg, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** Pure string builder — safe for JVM unit tests. */
    fun handoffUriString(target: EazpireApps.Target, exchangeToken: String? = null): String {
        val base = "${EazpireApps.scheme(target)}://${EazpireApps.HOST_AUTH_HANDOFF}/${EazpireApps.PATH_HANDOFF}"
        val token = exchangeToken?.trim().orEmpty()
        if (token.isEmpty()) return base
        val enc = URLEncoder.encode(token, StandardCharsets.UTF_8.name())
        return "$base?exchange_token=$enc"
    }

    fun playStoreUriString(target: EazpireApps.Target): String =
        "market://details?id=${EazpireApps.packageId(target)}"

    fun playStoreHttpsUriString(target: EazpireApps.Target): String =
        "https://play.google.com/store/apps/details?id=${EazpireApps.packageId(target)}"

    fun handoffUri(target: EazpireApps.Target, exchangeToken: String? = null): Uri =
        Uri.parse(handoffUriString(target, exchangeToken))

    fun playStoreUri(target: EazpireApps.Target): Uri =
        Uri.parse(playStoreUriString(target))

    fun playStoreHttpsUri(target: EazpireApps.Target): Uri =
        Uri.parse(playStoreHttpsUriString(target))

    fun openSiblingOrStore(
        context: Context,
        target: EazpireApps.Target,
        exchangeToken: String? = null,
    ): Result {
        val pkg = EazpireApps.packageId(target)
        if (isInstalled(context, target)) {
            val intent = Intent(Intent.ACTION_VIEW, handoffUri(target, exchangeToken)).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                Result.OpenedApp
            } catch (_: ActivityNotFoundException) {
                openPlayStore(context, target)
            }
        }
        return openPlayStore(context, target)
    }

    private fun openPlayStore(context: Context, target: EazpireApps.Target): Result {
        val market = Intent(Intent.ACTION_VIEW, playStoreUri(target)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(market)
            Result.OpenedPlayStore
        } catch (_: ActivityNotFoundException) {
            return try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, playStoreHttpsUri(target)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
                Result.OpenedPlayStore
            } catch (_: ActivityNotFoundException) {
                Result.Failed
            }
        }
    }
}
