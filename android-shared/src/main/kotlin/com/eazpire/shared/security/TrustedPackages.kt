package com.eazpire.shared.security

import com.eazpire.shared.EazpireApps

/**
 * Per-package SHA-256 signing-cert digests (lowercase hex).
 *
 * Register Play App Signing + upload/debug digests after first store builds:
 * `TrustedPackages.register(EazpireApps.WEAR_PLAYER, "abc123…")`
 *
 * Empty digests → [PackageTrust] falls back to package-name + installed cert present
 * unless [com.eazpire.shared.BuildConfig.REQUIRE_CERT_DIGESTS] is true.
 */
object TrustedPackages {
    private val digestsByPackage = mutableMapOf<String, MutableSet<String>>()

    init {
        // Placeholders: packages known to the dual-app / Wear ecosystem.
        ensurePackage(EazpireApps.WEAR_PLAYER)
        ensurePackage(EazpireApps.CREATOR)
        ensurePackage(EazpireApps.SHOP)
    }

    fun ensurePackage(packageName: String) {
        digestsByPackage.getOrPut(packageName) { mutableSetOf() }
    }

    fun register(packageName: String, vararg sha256Hex: String) {
        val set = digestsByPackage.getOrPut(packageName) { mutableSetOf() }
        sha256Hex.forEach { hex ->
            val n = hex.trim().lowercase()
            if (n.isNotEmpty()) set.add(n)
        }
    }

    fun digestsFor(packageName: String): Set<String> =
        digestsByPackage[packageName]?.toSet() ?: emptySet()

    fun clearForTests() {
        digestsByPackage.clear()
        ensurePackage(EazpireApps.WEAR_PLAYER)
        ensurePackage(EazpireApps.CREATOR)
        ensurePackage(EazpireApps.SHOP)
    }
}
