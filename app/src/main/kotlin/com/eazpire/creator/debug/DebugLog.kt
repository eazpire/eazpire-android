package com.eazpire.creator.debug

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val INGEST_URL = "http://10.0.2.2:7802/ingest/ef654e46-cfb0-429a-abdd-2c61e906032f"
private const val SESSION_ID = "0ebdba"
private const val LOG_TAG = "ProductModalDebug"

private val client = OkHttpClient.Builder()
    .connectTimeout(2, TimeUnit.SECONDS)
    .writeTimeout(2, TimeUnit.SECONDS)
    .build()

private val logFileRef = AtomicReference<File?>(null)

fun initDebugLog(context: android.content.Context) {
    logFileRef.set(File(context.getExternalFilesDir(null), "debug-0ebdba.log"))
}

fun debugLog(location: String, message: String, data: Map<String, Any?> = emptyMap(), hypothesisId: String? = null) {
    val payload = JSONObject().apply {
        put("sessionId", SESSION_ID)
        put("location", location)
        put("message", message)
        put("timestamp", System.currentTimeMillis())
        data.forEach { (k, v) -> put(k, v?.toString() ?: "null") }
        hypothesisId?.let { put("hypothesisId", it) }
    }
    val line = payload.toString() + "\n"
    Log.d(LOG_TAG, "DEBUG: $message | $data | H=$hypothesisId")
    CoroutineScope(Dispatchers.IO).launch {
        try {
            logFileRef.get()?.appendText(line)
        } catch (_: Exception) { }
        try {
            val body = line.trim().toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(INGEST_URL).addHeader("X-Debug-Session-Id", SESSION_ID).post(body).build()
            client.newCall(req).execute()
        } catch (_: Exception) { }
    }
}
