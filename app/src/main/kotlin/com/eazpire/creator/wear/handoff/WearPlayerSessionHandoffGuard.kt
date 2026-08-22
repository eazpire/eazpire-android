package com.eazpire.creator.wear.handoff

/**
 * Only the official Wear Player phone app may receive a session result.
 * Signature-level manifest permissions are not used: Play App Signing issues
 * a different cert per applicationId, so they would break production Join Now.
 */
object WearPlayerSessionHandoffGuard {
    const val ALLOWED_PACKAGE = "com.eazpire.wear"

    fun isTrustedCaller(callingPackage: String?): Boolean =
        callingPackage == ALLOWED_PACKAGE
}
