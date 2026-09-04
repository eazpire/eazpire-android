package com.eazpire.creator.wear.handoff

import android.content.Context
import com.eazpire.shared.EazpireApps
import com.eazpire.shared.BuildConfig as SharedBuildConfig
import com.eazpire.shared.security.PackageTrust
import com.eazpire.shared.security.TrustedPackages

/**
 * Only the official Wear Player phone app may receive a session result.
 * Checks package name + signing cert digests when registered
 * ([TrustedPackages.register]). Signature-level manifest permissions are not
 * used: Play App Signing issues a different cert per applicationId.
 */
object WearPlayerSessionHandoffGuard {
    const val ALLOWED_PACKAGE = EazpireApps.WEAR_PLAYER

    fun isTrustedCaller(context: Context, callingPackage: String?): Boolean =
        PackageTrust.isTrusted(
            context = context,
            callingPackage = callingPackage,
            allowedPackage = ALLOWED_PACKAGE,
            allowedDigests = TrustedPackages.digestsFor(ALLOWED_PACKAGE),
            requireDigests = SharedBuildConfig.REQUIRE_CERT_DIGESTS,
        )

    /** @deprecated Use [isTrustedCaller] with Context for cert checks. */
    @Deprecated("Pass Context for signing-cert verification", ReplaceWith("isTrustedCaller(context, callingPackage)"))
    fun isTrustedCaller(callingPackage: String?): Boolean =
        callingPackage == ALLOWED_PACKAGE
}
