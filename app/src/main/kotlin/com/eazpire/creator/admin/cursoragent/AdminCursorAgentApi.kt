package com.eazpire.creator.admin.cursoragent

import com.eazpire.creator.api.CreatorHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thin client for worker ops `admin-cursor-*` and floating-icon prefs.
 * Uses the same Bearer JWT as [com.eazpire.creator.api.CreatorApi].
 */
class AdminCursorAgentApi(
    private val jwt: String?,
    private val baseUrl: String = "https://creator-engine.eazpire.workers.dev",
) {
    private val client = CreatorHttpClient.instance
    private val jsonMedia = "application/json".toMediaType()

    private fun auth(builder: Request.Builder): Request.Builder =
        builder.apply { jwt?.takeIf { it.isNotBlank() }?.let { addHeader("Authorization", "Bearer $it") } }

    private fun url(op: String, query: Map<String, String> = emptyMap()): String =
        buildString {
            append("$baseUrl/apps/creator-dispatch?op=$op&_t=${System.currentTimeMillis()}")
            query.forEach { (k, v) ->
                if (v.isNotBlank()) append("&$k=${java.net.URLEncoder.encode(v, "UTF-8")}")
            }
        }

    private suspend fun get(op: String, query: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val request = auth(Request.Builder().url(url(op, query)).get()).build()
            JSONObject(client.newCall(request).execute().body?.string() ?: "{}")
        }

    private suspend fun postJson(op: String, body: JSONObject, query: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val request =
                auth(
                    Request.Builder()
                        .url(url(op, query))
                        .post(body.toString().toRequestBody(jsonMedia))
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json"),
                ).build()
            JSONObject(client.newCall(request).execute().body?.string() ?: "{}")
        }

    suspend fun me(): JSONObject = get("admin-cursor-me")

    suspend fun listModels(): JSONObject = get("admin-cursor-models")

    suspend fun listChats(): JSONObject = get("admin-cursor-chats")

    suspend fun getChat(chatId: String): JSONObject =
        get("admin-cursor-chat-get", mapOf("chat_id" to chatId))

    suspend fun createChat(modelId: String, mode: String, context: JSONObject): JSONObject =
        postJson(
            "admin-cursor-chat-create",
            JSONObject()
                .put("model_id", modelId)
                .put("mode", mode)
                .put("context", context),
        )

    suspend fun send(
        chatId: String?,
        text: String,
        mode: String,
        modelId: String,
        context: JSONObject,
        images: List<AdminCursorImageRef>,
    ): JSONObject {
        val imagesArr = JSONArray()
        images.forEach { img ->
            imagesArr.put(JSONObject().put("url", img.url).put("mimeType", img.mimeType))
        }
        val body =
            JSONObject()
                .put("text", text)
                .put("mode", mode)
                .put("model_id", modelId)
                .put("context", context)
                .put("images", imagesArr)
        if (!chatId.isNullOrBlank()) body.put("chat_id", chatId)
        return postJson("admin-cursor-send", body)
    }

    suspend fun runGet(chatId: String, runId: String?): JSONObject {
        val q = mutableMapOf("chat_id" to chatId)
        if (!runId.isNullOrBlank()) q["run_id"] = runId
        return get("admin-cursor-run-get", q)
    }

    suspend fun cancel(chatId: String): JSONObject =
        postJson("admin-cursor-cancel", JSONObject().put("chat_id", chatId))

    suspend fun uploadImage(bytes: ByteArray, mimeType: String = "image/png"): JSONObject =
        withContext(Dispatchers.IO) {
            val media = mimeType.toMediaType()
            val body =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image", "android-screenshot.png", bytes.toRequestBody(media))
                    .build()
            val request =
                auth(
                    Request.Builder()
                        .url(url("admin-cursor-upload"))
                        .post(body)
                        .addHeader("Accept", "application/json"),
                ).build()
            JSONObject(client.newCall(request).execute().body?.string() ?: "{}")
        }

    suspend fun loadFabPrefs(): JSONObject = get("admin-floating-icon-prefs")

    suspend fun saveFabPref(key: String, xPct: Float, yPct: Float): JSONObject =
        postJson(
            "admin-floating-icon-prefs-save",
            JSONObject()
                .put("key", key)
                .put("x_pct", xPct.toDouble())
                .put("y_pct", yPct.toDouble()),
        )

    suspend fun clearFabPref(key: String): JSONObject =
        postJson(
            "admin-floating-icon-prefs-save",
            JSONObject().put("key", key).put("clear", true),
        )

    companion object {
        fun parseChats(json: JSONObject): List<AdminCursorChatSummary> {
            val arr = json.optJSONArray("chats") ?: return emptyList()
            val out = ArrayList<AdminCursorChatSummary>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                if (id.isEmpty()) continue
                out.add(
                    AdminCursorChatSummary(
                        id = id,
                        title = o.optString("title", "Chat"),
                        status = o.optString("status", "idle"),
                        mode = o.optString("mode", "agent"),
                        activeRunId = o.optString("active_run_id").takeIf { it.isNotBlank() },
                        updatedAt = o.optString("updated_at", ""),
                    ),
                )
            }
            return out
        }

        fun parseMessages(json: JSONObject): List<AdminCursorMessage> {
            val arr = json.optJSONArray("messages") ?: return emptyList()
            val out = ArrayList<AdminCursorMessage>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim().ifBlank { "msg_$i" }
                val imagesJson = o.optString("images_json", "")
                val urls = mutableListOf<String>()
                if (imagesJson.isNotBlank()) {
                    try {
                        val imgs = JSONArray(imagesJson)
                        for (j in 0 until imgs.length()) {
                            val img = imgs.optJSONObject(j) ?: continue
                            img.optString("url").takeIf { it.isNotBlank() }?.let { urls.add(it) }
                        }
                    } catch (_: Exception) {
                        /* ignore */
                    }
                }
                out.add(
                    AdminCursorMessage(
                        id = id,
                        role = o.optString("role", "assistant"),
                        content = o.optString("content", ""),
                        imageUrls = urls,
                        runId = o.optString("run_id").takeIf { it.isNotBlank() },
                        createdAt = o.optString("created_at", ""),
                    ),
                )
            }
            return out
        }

        fun parseFabPos(prefs: JSONObject, key: String): AdminCursorFabPos? {
            val prefObj = prefs.optJSONObject("prefs") ?: return null
            val pos = prefObj.optJSONObject(key) ?: return null
            if (!pos.has("x_pct") || !pos.has("y_pct")) return null
            val x = pos.optDouble("x_pct", Double.NaN)
            val y = pos.optDouble("y_pct", Double.NaN)
            if (!x.isFinite() || !y.isFinite()) return null
            return AdminCursorFabPos(x.toFloat(), y.toFloat())
        }
    }
}
