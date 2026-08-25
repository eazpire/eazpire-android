package com.eazpire.creator.chat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eazpire.creator.billing.EazBalanceRefreshBus
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.eazyChatDataStore: DataStore<Preferences> by preferencesDataStore(name = "eazy_chat")

/**
 * Store for Eazy chat: user_id (guest or customer), conversation_id, messages.
 * Mirrors web: localStorage eazy_user_id, session messages.
 */
class EazyChatStore(private val context: Context) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _conversationId = MutableStateFlow<String?>(null)
    val conversationId: StateFlow<String?> = _conversationId.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isTyping = MutableStateFlow(false)
    val isTyping: StateFlow<Boolean> = _isTyping.asStateFlow()

    private val _rateLimit = MutableStateFlow<RateLimitState?>(null)
    val rateLimit: StateFlow<RateLimitState?> = _rateLimit.asStateFlow()

    private val _limitReached = MutableStateFlow(false)
    val limitReached: StateFlow<Boolean> = _limitReached.asStateFlow()

    private val _heroJobState = MutableStateFlow<HeroJobState?>(null)
    val heroJobState: StateFlow<HeroJobState?> = _heroJobState.asStateFlow()

    private val _videoJobState = MutableStateFlow<VideoJobState?>(null)
    val videoJobState: StateFlow<VideoJobState?> = _videoJobState.asStateFlow()

    private val _designJobState = MutableStateFlow<DesignJobState?>(null)
    val designJobState: StateFlow<DesignJobState?> = _designJobState.asStateFlow()

    private val _designSaveComplete = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val designSaveComplete: SharedFlow<String> = _designSaveComplete.asSharedFlow()

    /** Generate (not save) finished — switch Eazy modal to Notifications. */
    private val _designJobComplete = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val designJobComplete: SharedFlow<String> = _designJobComplete.asSharedFlow()

    /** Any async job finished (hero/video/generate) — refresh notifications tab. */
    private val _asyncJobComplete = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val asyncJobComplete: SharedFlow<Unit> = _asyncJobComplete.asSharedFlow()

    /** Mirrors web localStorage eazy_fn_visibility: feature id → false = hidden in carousel. */
    private val _fnVisibility = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val fnVisibility: StateFlow<Map<String, Boolean>> = _fnVisibility.asStateFlow()

    suspend fun loadFnVisibilityFromStorage() {
        val raw = context.eazyChatDataStore.data.map { it[FN_VISIBILITY_KEY] }.first() ?: return
        try {
            val o = JSONObject(raw)
            val m = mutableMapOf<String, Boolean>()
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                m[k] = o.optBoolean(k, true)
            }
            _fnVisibility.value = m
        } catch (_: Exception) {}
    }

    fun isFeatureInCarousel(featureId: String): Boolean = _fnVisibility.value[featureId] != false

    fun toggleFeatureCarouselVisibility(featureId: String) {
        val cur = _fnVisibility.value.toMutableMap()
        val visible = cur[featureId] != false
        if (visible) cur[featureId] = false else cur.remove(featureId)
        _fnVisibility.value = cur
    }

    fun setCategoryCarouselVisibility(featureIds: List<String>, visible: Boolean) {
        val cur = _fnVisibility.value.toMutableMap()
        featureIds.forEach { id ->
            if (visible) cur.remove(id) else cur[id] = false
        }
        _fnVisibility.value = cur
    }

    suspend fun persistFnVisibility() {
        val jo = JSONObject()
        _fnVisibility.value.forEach { (k, v) ->
            if (!v) jo.put(k, false)
        }
        context.eazyChatDataStore.edit { it[FN_VISIBILITY_KEY] = jo.toString() }
    }

    data class SeenUserFeed(val notifs: Map<String, Long>, val jobs: Map<String, Long>)

    suspend fun loadSeenUserFeed(ownerId: String): SeenUserFeed {
        val raw = context.eazyChatDataStore.data.map { it[SEEN_FEED_KEY] }.first() ?: return SeenUserFeed(emptyMap(), emptyMap())
        return try {
            val all = JSONObject(raw)
            val rec = all.optJSONObject(ownerId) ?: return SeenUserFeed(emptyMap(), emptyMap())
            SeenUserFeed(jsonToLongMap(rec.optJSONObject("notifs")), jsonToLongMap(rec.optJSONObject("jobs")))
        } catch (_: Exception) {
            SeenUserFeed(emptyMap(), emptyMap())
        }
    }

    suspend fun markSeen(ownerId: String, notifIds: Collection<String>, jobIds: Collection<String>) {
        if (ownerId.isBlank()) return
        val now = System.currentTimeMillis()
        val cur = loadSeenUserFeed(ownerId)
        val notifs = cur.notifs.toMutableMap()
        val jobs = cur.jobs.toMutableMap()
        notifIds.filter { it.isNotBlank() }.forEach { notifs[it] = now }
        jobIds.filter { it.isNotBlank() }.forEach { jobs[it] = now }
        val rec = JSONObject()
            .put("notifs", longMapToJson(pruneMap(notifs)))
            .put("jobs", longMapToJson(pruneMap(jobs)))
        val raw = context.eazyChatDataStore.data.map { it[SEEN_FEED_KEY] }.first()
        val all = try { if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw) } catch (_: Exception) { JSONObject() }
        all.put(ownerId, rec)
        context.eazyChatDataStore.edit { it[SEEN_FEED_KEY] = all.toString() }
    }

    /**
     * Returns Jobs or Notifications if a new unseen user item exists; marks those IDs seen.
     */
    suspend fun consumeUnseenUserFeed(
        api: com.eazpire.creator.api.CreatorApi,
        ownerId: String,
    ): EazySidebarTab? {
        if (ownerId.isBlank()) return null
        val seen = loadSeenUserFeed(ownerId)
        val jobsRes = runCatching { api.listJobs(ownerId, 50) }.getOrNull()
        val jobIds = mutableListOf<String>()
        run {
            val arr = jobsRes?.optJSONArray("items") ?: org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("job_id", o.optString("id", "")).trim()
                val done = o.optBoolean("done", false)
                val saving = o.optBoolean("saving", false)
                val saved = o.optBoolean("saved", false)
                val active = if (o.has("active")) o.optBoolean("active") else (!done || (saving && !saved))
                if (id.isNotBlank() && active) jobIds += id
            }
        }
        val unseenJobs = jobIds.filter { it !in seen.jobs }
        if (unseenJobs.isNotEmpty()) {
            markSeen(ownerId, emptyList(), unseenJobs)
            return EazySidebarTab.Jobs
        }
        val notifRes = runCatching { api.getNotifications(ownerId) }.getOrNull()
        val unseenNotifs = mutableListOf<String>()
        run {
            val arr = notifRes?.optJSONArray("notifications") ?: org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("notification_id", o.optString("id", "")).trim()
                val read = o.optBoolean("is_read", false) || o.optInt("is_read", 0) == 1 ||
                    o.optString("is_read", "") == "1" || o.optString("is_read", "").equals("true", true)
                if (id.isNotBlank() && !read && id !in seen.notifs) unseenNotifs += id
            }
        }
        if (unseenNotifs.isNotEmpty()) {
            markSeen(ownerId, unseenNotifs, emptyList())
            return EazySidebarTab.Notifications
        }
        return null
    }

    private fun jsonToLongMap(obj: org.json.JSONObject?): Map<String, Long> {
        if (obj == null) return emptyMap()
        val m = mutableMapOf<String, Long>()
        val it = obj.keys()
        while (it.hasNext()) {
            val k = it.next()
            m[k] = obj.optLong(k, 0L)
        }
        return m
    }

    private fun longMapToJson(map: Map<String, Long>): org.json.JSONObject {
        val o = org.json.JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        return o
    }

    private fun pruneMap(map: Map<String, Long>): Map<String, Long> {
        if (map.size <= 250) return map
        return map.entries.sortedBy { it.value }.takeLast(200).associate { it.key to it.value }
    }

    fun startHeroJob(jobId: String, summary: String) {
        _heroJobState.value = HeroJobState(jobId = jobId, summary = summary, progress = 0, message = null)
        EazBalanceRefreshBus.requestRefresh()
    }

    fun updateHeroJobPoll(progress: Int, message: String?) {
        val cur = _heroJobState.value ?: return
        if (cur.terminal) return
        _heroJobState.value = cur.copy(progress = progress.coerceIn(0, 100), message = message)
    }

    fun completeHeroJob(imageUrl: String?) {
        val cur = _heroJobState.value ?: return
        _heroJobState.value = cur.copy(
            completed = true,
            progress = 100,
            resultImageUrl = imageUrl
        )
        _heroJobState.value = null
        _asyncJobComplete.tryEmit(Unit)
        EazBalanceRefreshBus.requestRefresh()
    }

    fun failHeroJob(message: String) {
        val cur = _heroJobState.value ?: return
        _heroJobState.value = cur.copy(failed = true, errorMessage = message)
    }

    fun clearHeroJob() {
        _heroJobState.value = null
    }

    fun startVideoJob(jobId: String, summary: String) {
        _videoJobState.value = VideoJobState(jobId = jobId, summary = summary, progress = 0, message = null)
        EazBalanceRefreshBus.requestRefresh()
    }

    fun updateVideoJobPoll(progress: Int, message: String?) {
        val cur = _videoJobState.value ?: return
        if (cur.terminal) return
        _videoJobState.value = cur.copy(progress = progress.coerceIn(0, 100), message = message)
    }

    fun completeVideoJob(videoUrl: String?) {
        val cur = _videoJobState.value ?: return
        _videoJobState.value = cur.copy(completed = true, progress = 100, resultVideoUrl = videoUrl)
        _videoJobState.value = null
        _asyncJobComplete.tryEmit(Unit)
        EazBalanceRefreshBus.requestRefresh()
    }

    fun failVideoJob(message: String) {
        val cur = _videoJobState.value ?: return
        _videoJobState.value = cur.copy(failed = true, errorMessage = message)
    }

    fun clearVideoJob() {
        _videoJobState.value = null
    }

    fun startDesignJob(jobId: String, summary: String) {
        _designJobState.value = DesignJobState(jobId = jobId, summary = summary, progress = 0, message = null)
        EazBalanceRefreshBus.requestRefresh()
    }

    fun updateDesignJobPoll(progress: Int, message: String?) {
        val cur = _designJobState.value ?: return
        if (cur.terminal) return
        _designJobState.value = cur.copy(progress = progress.coerceIn(0, 100), message = message)
    }

    fun clearDesignJob() {
        _designJobState.value = null
    }

    /** Generate finished (design in inactive library) — notify Eazy modal. */
    fun completeDesignJob(jobId: String) {
        _designJobState.value = null
        if (jobId.isNotBlank()) {
            _designJobComplete.tryEmit(jobId)
            _asyncJobComplete.tryEmit(Unit)
            EazBalanceRefreshBus.requestRefresh()
        }
    }

    /** Full save finished (inactive library row persisted) — notify Eazy modal to show saved notification. */
    fun completeDesignSave(jobId: String) {
        _designJobState.value = null
        if (jobId.isNotBlank()) {
            _designSaveComplete.tryEmit(jobId)
            _asyncJobComplete.tryEmit(Unit)
        }
    }

    fun failDesignJob(message: String) {
        val cur = _designJobState.value ?: return
        _designJobState.value = cur.copy(failed = true, errorMessage = message)
    }

    suspend fun getUserId(customerId: String?): String {
        val realId = customerId?.takeIf { it.isNotBlank() }
        if (realId != null) {
            context.eazyChatDataStore.edit { prefs ->
                prefs[USER_ID_KEY] = realId
            }
            return realId
        }
        val existing = context.eazyChatDataStore.data.map { it[USER_ID_KEY] }.first()
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString()
        context.eazyChatDataStore.edit { it[USER_ID_KEY] = newId }
        return newId
    }

    fun setMessages(list: List<ChatMessage>) {
        _messages.value = list
    }

    fun addMessage(msg: ChatMessage) {
        _messages.value = _messages.value + msg
    }

    fun setConversationId(id: String?) {
        _conversationId.value = id
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun setTyping(typing: Boolean) {
        _isTyping.value = typing
    }

    fun setRateLimit(rl: RateLimitState?) {
        _rateLimit.value = rl
    }

    fun setLimitReached(reached: Boolean) {
        _limitReached.value = reached
    }

    fun clearMessages() {
        _messages.value = emptyList()
        _conversationId.value = null
    }

    companion object {
        private val USER_ID_KEY = stringPreferencesKey("eazy_user_id")
        private val FN_VISIBILITY_KEY = stringPreferencesKey("eazy_fn_visibility")
        private val SEEN_FEED_KEY = stringPreferencesKey("eazy_seen_user_feed_v1")
    }
}

data class ChatMessage(
    val id: String,
    val role: String, // "user" | "assistant"
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class RateLimitState(
    val remaining: Int,
    val limit: Int,
    val resetAt: Long,
    val resetIn: Int
)

/** Async video-generate job (marketing videos). */
data class VideoJobState(
    val jobId: String,
    val summary: String,
    val progress: Int = 0,
    val message: String? = null,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val resultVideoUrl: String? = null,
    val errorMessage: String? = null
) {
    val isActive: Boolean get() = !completed && !failed
    val terminal: Boolean get() = completed || failed
}

/** Async design-generate job (design generator) in Eazy → Active Jobs. */
data class DesignJobState(
    val jobId: String,
    val summary: String,
    val progress: Int = 0,
    val message: String? = null,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val errorMessage: String? = null
) {
    val isActive: Boolean get() = !completed && !failed
    val terminal: Boolean get() = completed || failed
}

/** Async hero-generate job shown under Eazy chat → Active Jobs / Notifications. */
data class HeroJobState(
    val jobId: String,
    val summary: String,
    val progress: Int = 0,
    val message: String? = null,
    val completed: Boolean = false,
    val failed: Boolean = false,
    val resultImageUrl: String? = null,
    val errorMessage: String? = null
) {
    val isActive: Boolean get() = !completed && !failed
    val terminal: Boolean get() = completed || failed
}
