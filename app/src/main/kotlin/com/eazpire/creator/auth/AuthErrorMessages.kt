package com.eazpire.creator.auth

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps technical auth errors to user-facing copy (VPN / Cloudflare / network friendly).
 */
object AuthErrorMessages {
    fun fromThrowable(t: Throwable?): String {
        if (t == null) return connectionHint()
        val msg = t.message?.trim().orEmpty()
        val lower = msg.lowercase()
        return when {
            t is UnknownHostException || t is SocketTimeoutException || t is IOException ->
                connectionHint()
            lower.contains("discovery failed") -> connectionHint()
            lower.contains("invalid state") ->
                "Anmeldesitzung abgelaufen. Bitte erneut versuchen."
            lower.contains("token exchange failed") || lower.contains("token refresh failed") ->
                "Anmeldung fehlgeschlagen. Bitte erneut versuchen."
            lower.contains("jwt exchange failed") ->
                "Anmeldung fehlgeschlagen. Bitte erneut versuchen oder Support kontaktieren."
            lower.contains("http 406") || lower.contains("406") ->
                "Login-Seite blockiert. App aktualisieren oder ohne VPN erneut versuchen."
            msg.isNotBlank() -> msg
            else -> connectionHint()
        }
    }

    fun connectionHint(): String =
        "Anmeldung fehlgeschlagen. Verbindung prüfen oder VPN kurz deaktivieren und erneut versuchen."
}
