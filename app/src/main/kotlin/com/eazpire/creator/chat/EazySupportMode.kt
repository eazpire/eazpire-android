package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

val EazySupportRed = Color(0xFFDC2626)
val EazySupportRedActive = Color(0xFFEF4444)

data class EazyConvMeta(
    val mode: String = "ai",
    val supportStatus: String? = null,
    val supportFirstReplyAt: String? = null,
)

fun parseEazyConvMeta(conv: JSONObject?): EazyConvMeta {
    if (conv == null) return EazyConvMeta()
    return EazyConvMeta(
        mode = conv.optString("mode", "ai").ifBlank { "ai" },
        supportStatus = conv.optString("support_status", "").takeIf { it.isNotBlank() },
        supportFirstReplyAt = conv.optString("support_first_reply_at", "").takeIf { it.isNotBlank() },
    )
}

fun isLiveSupportMeta(meta: EazyConvMeta): Boolean =
    meta.mode == "support" &&
        meta.supportStatus != "closed" &&
        meta.supportStatus != "resolved"

fun supportTabPrefixLabel(prefix: String, baseLabel: String): String {
    val stripped = baseLabel.replace(Regex("^Live Support:\\s*", RegexOption.IGNORE_CASE), "")
    return "$prefix $stripped"
}

enum class SupportSurveyStep {
    SOLVED,
    RATING,
    FEEDBACK_ASK,
    FEEDBACK_TEXT,
}

data class SupportSurveyData(
    var solved: Boolean? = null,
    var rating: Int? = null,
    var feedback: String? = null,
)

suspend fun pollSupportReplies(
    api: CreatorApi,
    userId: String,
    conversationId: String,
    afterId: Int,
): Pair<EazyConvMeta, List<ChatMessage>> {
    val resp = withContext(Dispatchers.IO) {
        api.getEazyConversation(
            userId,
            mapOf(
                "conv_id" to conversationId,
                "after" to afterId.toString(),
            )
        )
    }
    if (!resp.optBoolean("ok", false)) return EazyConvMeta() to emptyList()
    val meta = parseEazyConvMeta(resp.optJSONObject("conversation"))
    val msgs = resp.optJSONArray("messages") ?: JSONArray()
    val supportMsgs = (0 until msgs.length()).mapNotNull { i ->
        val m = msgs.optJSONObject(i) ?: return@mapNotNull null
        if (m.optString("role") != "support") return@mapNotNull null
        val content = m.optString("content", "")
        if (content.isBlank()) return@mapNotNull null
        ChatMessage(
            id = m.opt("id")?.toString() ?: "s$i",
            role = "assistant",
            content = content,
        )
    }
    return meta to supportMsgs
}

suspend fun activateSupportOnServer(
    api: CreatorApi,
    userId: String,
    conversationId: String,
    reason: String,
    t: (String, String) -> String,
) {
    withContext(Dispatchers.IO) {
        api.eazyConvPostMessage(
            userId = userId,
            conversationId = conversationId,
            role = "system",
            content = t("chatSupportRequestPrefix", "Support request: ") +
                (reason.ifBlank { t("chatUserWantsSupport", "User wants to speak to support") }),
            messageType = "support",
        )
    }
}

suspend fun sendSupportMessageOnServer(
    api: CreatorApi,
    userId: String,
    conversationId: String,
    content: String,
) {
    withContext(Dispatchers.IO) {
        api.eazyConvPostMessage(
            userId = userId,
            conversationId = conversationId,
            role = "user",
            content = content,
            messageType = "support",
        )
    }
}

suspend fun submitSupportSurveyOnServer(
    api: CreatorApi,
    userId: String,
    conversationId: String,
    survey: SupportSurveyData,
) {
    withContext(Dispatchers.IO) {
        api.eazySupportSurvey(
            userId = userId,
            conversationId = conversationId,
            solved = survey.solved == true,
            rating = survey.rating,
            feedback = survey.feedback,
        )
    }
}

fun maxNumericMessageId(messages: List<ChatMessage>): Int =
    messages.mapNotNull { it.id.toIntOrNull() }.maxOrNull() ?: 0

@Composable
fun EazySupportSurveyPanel(
    step: SupportSurveyStep,
    survey: SupportSurveyData,
    t: (String, String) -> String,
    onSolved: (Boolean) -> Unit,
    onRating: (Int) -> Unit,
    onFeedbackChoice: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.92f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (step) {
            SupportSurveyStep.SOLVED -> {
                Text(
                    text = t("support_survey_solved", "Could we solve your problem?"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onSolved(true) }) {
                        Text(t("support_yes", "Yes"))
                    }
                    TextButton(onClick = { onSolved(false) }) {
                        Text(t("support_no", "No"))
                    }
                }
            }
            SupportSurveyStep.RATING -> {
                Text(
                    text = t("support_survey_rating", "How satisfied were you with the support?"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { star ->
                        Text(
                            text = "★",
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onRating(star) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = if (survey.rating == star) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            SupportSurveyStep.FEEDBACK_ASK -> {
                Text(
                    text = t("support_survey_feedback_ask", "Would you like to tell us anything else?"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onFeedbackChoice(true) }) {
                        Text(t("support_yes", "Yes"))
                    }
                    TextButton(onClick = { onFeedbackChoice(false) }) {
                        Text(t("support_no", "No"))
                    }
                }
            }
            SupportSurveyStep.FEEDBACK_TEXT -> {
                Text(
                    text = t("chat_support_feedback_prompt", "Please write your message below."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        }
    }
}
