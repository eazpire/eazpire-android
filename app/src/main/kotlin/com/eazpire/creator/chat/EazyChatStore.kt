package com.eazpire.creator.chat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
