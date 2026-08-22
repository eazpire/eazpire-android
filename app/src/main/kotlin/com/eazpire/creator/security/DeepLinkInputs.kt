package com.eazpire.creator.security

/**
 * Client-side shape checks for deep-link tokens.
 * Not a security control — the Worker must still validate and bind tokens.
 */
object DeepLinkInputs {
    const val MAX_PAIR_TOKEN_LEN = 64
    const val MAX_SESSION_ID_LEN = 64
    const val MAX_ARTIFACT_TOKEN_LEN = 128

    private val TOKEN_CHARS = Regex("^[A-Za-z0-9_-]+$")

    fun opaqueToken(raw: String?, maxLen: Int): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty() || s.length > maxLen) return null
        if (!TOKEN_CHARS.matches(s)) return null
        return s
    }

    fun pairToken(raw: String?): String? = opaqueToken(raw, MAX_PAIR_TOKEN_LEN)

    fun sessionId(raw: String?): String? = opaqueToken(raw, MAX_SESSION_ID_LEN)

    fun artifactToken(raw: String?): String? = opaqueToken(raw, MAX_ARTIFACT_TOKEN_LEN)

    /** Strip secret-bearing query values before logcat. */
    fun redactUriForLog(url: String): String =
        url.replace(Regex("([?&](?:code|state|id_token|access_token|token|t|s|artifact_token)=)[^&]+"), "$1REDACTED")
}
