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
