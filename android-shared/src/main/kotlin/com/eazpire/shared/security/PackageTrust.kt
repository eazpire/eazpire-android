package com.eazpire.shared.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Verifies that a calling package is both allowlisted by name and
 * (when digests are registered) signed with an expected cert SHA-256.
 *
 * Play App Signing uses a different cert per applicationId, so we cannot use
 * a shared `signature` permission across apps — digests must be registered
 * per package (see [TrustedPackages]).
 */
object PackageTrust {

    fun sha256DigestsHex(context: Context, packageName: String): Set<String> {
        if (packageName.isBlank()) return emptySet()
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            val signerCerts = info.signingInfo?.apkContentsSigners ?: return emptySet()
            signerCerts.mapNotNull { sig ->
                runCatching {
                    MessageDigest.getInstance("SHA-256")
                        .digest(sig.toByteArray())
                        .joinToString("") { b -> "%02x".format(b) }
                }.getOrNull()
            }.toSet()
        } catch (_: PackageManager.NameNotFoundException) {
            emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    /**
     * @param allowedDigests empty = package-name check only (plus installed signing info must exist)
     * @param requireDigests when true, empty allowlist fails closed
     */
    fun isTrusted(
        context: Context,
        callingPackage: String?,
        allowedPackage: String,
        allowedDigests: Set<String>,
        requireDigests: Boolean = false,
    ): Boolean {
        if (callingPackage.isNullOrBlank() || callingPackage != allowedPackage) return false
        val actual = sha256DigestsHex(context, callingPackage)
        if (actual.isEmpty()) return false
        if (allowedDigests.isEmpty()) {
            return !requireDigests
        }
        return actual.any { it.lowercase() in allowedDigests }
    }
}
