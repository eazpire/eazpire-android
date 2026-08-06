package com.eazpire.creator.admin.cursoragent

/**
 * Admin Cursor Agent shell models (IDEA-066 Android).
 * Admin-only — not a customer product surface.
 */
enum class AdminCursorMode(val apiValue: String, val label: String) {
    ASK("ask", "Ask"),
    AGENT("agent", "Agent"),
    ;

    companion object {
        fun fromApi(value: String?): AdminCursorMode =
            entries.firstOrNull { it.apiValue.equals(value?.trim(), ignoreCase = true) } ?: AGENT
    }
}

data class AdminCursorChatSummary(
    val id: String,
    val title: String,
    val status: String,
    val mode: String,
    val activeRunId: String?,
    val updatedAt: String,
)

data class AdminCursorMessage(
    val id: String,
    val role: String,
    val content: String,
    val imageUrls: List<String> = emptyList(),
    val runId: String? = null,
    val createdAt: String = "",
)

data class AdminCursorFabPos(
    val xPct: Float,
    val yPct: Float,
)

data class AdminCursorImageRef(
    val url: String,
    val mimeType: String = "image/png",
)

object AdminCursorAgentDefaults {
    const val PORTAL = "android_app"
    const val FAB_PREF_KEY = "android_agent_fab"
    const val DEFAULT_MODEL = "composer-2.5"
    const val HREF = "eazpire://android-app"
}
