package com.eazpire.shared

/**
 * Canonical package IDs for the dual-app split (IDEA-093).
 */
object EazpireApps {
    const val CREATOR = "com.eazpire.creator"
    const val SHOP = "com.eazpire.shop"
    const val WEAR_PLAYER = "com.eazpire.wear"

    const val SCHEME_CREATOR = "eazpire-creator"
    const val SCHEME_SHOP = "eazpire-shop"
    const val HOST_AUTH_HANDOFF = "auth"
    const val PATH_HANDOFF = "handoff"

    enum class Target {
        CREATOR,
        SHOP,
    }

    fun packageId(target: Target): String = when (target) {
        Target.CREATOR -> CREATOR
        Target.SHOP -> SHOP
    }

    fun scheme(target: Target): String = when (target) {
        Target.CREATOR -> SCHEME_CREATOR
        Target.SHOP -> SCHEME_SHOP
    }
}
