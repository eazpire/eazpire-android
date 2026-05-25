package com.eazpire.creator.debug

import android.util.Log
import com.eazpire.creator.BuildConfig
import com.eazpire.creator.auth.AuthConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Auth login diagnostics: local logcat (AuthDebug) + optional remote ingest to creator-engine.
 *
 * Remote (debug builds only): POST to worker `client-log` and `admin-logs-ingest`.
 * Watch live: `npm run logs:auth` in creator-worker repo.
 */
object AuthDebugLog {
    private const val TAG = "AuthDebug"
    private const val REMOTE_SOURCE = "android-auth"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /** Stable per app process; included in every remote payload. */
    var sessionId: String = "auth-${System.currentTimeMillis().toString(36)}"
        private set

    private val remoteEnabled = AtomicBoolean(BuildConfig.DEBUG)

    fun setRemoteEnabled(enabled: Boolean) {
        remoteEnabled.set(enabled)
    }

    fun d(message: String) {
        Log.d(TAG, message)
        remote(message, "debug")
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
        remote(message + throwableSuffix(throwable), "warn")
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        remote(message + throwableSuffix(throwable), "error")
    }

    private fun throwableSuffix(throwable: Throwable?): String =
        throwable?.message?.let { " | $it" } ?: ""

    private fun remote(message: String, level: String) {
        if (!remoteEnabled.get()) return
        CoroutineScope(Dispatchers.IO).launch {
            sendClientLog(message, level)
            sendAdminLog(message, level)
        }
    }

    private fun sendClientLog(message: String, level: String) {
        try {
            val body = JSONObject().apply {
                put("source", REMOTE_SOURCE)
                put("level", level)
                put("event", "auth_debug")
                put("payload", JSONObject().apply {
                    put("session_id", sessionId)
                    put("message", message)
                    put("ts", System.currentTimeMillis())
                })
            }
            post("${AuthConfig.CREATOR_ENGINE_URL}/apps/creator-dispatch?op=client-log", body.toString())
        } catch (_: Exception) {
        }
    }

    private fun sendAdminLog(message: String, level: String) {
        try {
            val body = JSONObject().apply {
                put("source", REMOTE_SOURCE)
                put("category", "login")
                put("level", level)
                put("message", message)
                put("meta", JSONObject().apply {
                    put("session_id", sessionId)
                    put("ts", System.currentTimeMillis())
                })
            }
            post("${AuthConfig.CREATOR_ENGINE_URL}/apps/creator-dispatch?op=admin-logs-ingest", body.toString())
        } catch (_: Exception) {
        }
    }

    private fun post(url: String, json: String) {
        val req = Request.Builder()
            .url(url)
            .post(json.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()
        client.newCall(req).execute().close()
    }
}
