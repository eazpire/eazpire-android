package com.eazpire.creator.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PkceUtils {
    private const val CODE_VERIFIER_LENGTH = 32

    /**
     * Generiert einen zufälligen code_verifier (RFC 7636).
     */
    fun generateCodeVerifier(): String {
        val buffer = ByteArray(CODE_VERIFIER_LENGTH)
        SecureRandom().nextBytes(buffer)
        return base64UrlEncode(buffer)
    }

    /**
     * Berechnet code_challenge = BASE64URL(SHA256(code_verifier)).
     */
    fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(codeVerifier.toByteArray(Charsets.UTF_8))
        return base64UrlEncode(hash)
    }

    fun generateState(): String {
        val buffer = ByteArray(16)
        SecureRandom().nextBytes(buffer)
        return base64UrlEncode(buffer)
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
