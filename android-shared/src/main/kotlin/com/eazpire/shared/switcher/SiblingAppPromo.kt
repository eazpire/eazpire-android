package com.eazpire.shared.switcher

import com.eazpire.shared.EazpireApps

/**
 * Copy helpers for the soft-launch “get the other app” banner (IDEA-093 Phase 4).
 */
object SiblingAppPromo {

    fun title(target: EazpireApps.Target): String = when (target) {
        EazpireApps.Target.SHOP -> "Get the Shop app"
        EazpireApps.Target.CREATOR -> "Get the Creator app"
    }

    fun body(target: EazpireApps.Target): String = when (target) {
        EazpireApps.Target.SHOP ->
            "Browse and checkout in eazpire. Install the Shop app for the full shopping experience."
        EazpireApps.Target.CREATOR ->
            "Design and publish in eazpire Creator. Install the Creator app for creator tools."
    }

    fun cta(target: EazpireApps.Target): String = when (target) {
        EazpireApps.Target.SHOP -> "Open Play Store"
        EazpireApps.Target.CREATOR -> "Open Play Store"
    }
}
