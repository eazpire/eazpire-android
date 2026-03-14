package com.eazpire.creator.util

import android.util.Log

/**
 * Debug-Logging für die App-Entwicklung.
 *
 * Logs in Android Studio anzeigen:
 * 1. View → Tool Windows → Logcat
 * 2. Filter: Tag "Eazpire" oder Package "com.eazpire.creator"
 * 3. Bei Klick: Log.d wird ausgeführt → erscheint in Logcat
 *
 * Oder per Terminal:
 *   adb logcat -s Eazpire:D
 */
object DebugLog {
    private const val TAG = "Eazpire"

    fun d(message: String) = Log.d(TAG, message)
    fun i(message: String) = Log.i(TAG, message)
    fun w(message: String) = Log.w(TAG, message)
    fun e(message: String) = Log.e(TAG, message)
    fun e(message: String, throwable: Throwable) = Log.e(TAG, message, throwable)

    /** Klick-Events loggen: DebugLog.click("Cart") */
    fun click(source: String) = d("Click: $source")
}
