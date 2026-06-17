@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.EazColors
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun EazyVerifyPanel(
    ownerId: String?,
    api: CreatorApi,
    t: (String, String) -> String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var entityType by remember { mutableStateOf("design") }
    var viewMode by remember { mutableStateOf("available") }
    var termsAccepted by remember { mutableStateOf(false) }
    var termsChecked by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var currentItem by remember { mutableStateOf<JSONObject?>(null) }
    var rejectReasons by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedReasons by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showReject by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var completedItems by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    fun refresh() {
        val oid = ownerId?.trim().orEmpty()
        if (oid.isBlank()) {
            loading = false
            return
        }
        scope.launch {
            loading = true
            statusMsg = null
            try {
                val st = api.verifyStatus(oid)
                termsAccepted = st.optBoolean("terms_accepted", false)
                if (!termsAccepted) {
                    loading = false
                    return@launch
                }
                if (viewMode == "completed") {
                    val list = api.verifyCompletedList(oid, entityType, "all")
                    val arr = list.optJSONArray("items") ?: JSONArray()
                    completedItems = (0 until arr.length()).map { arr.getJSONObject(it) }
                    currentItem = null
                } else {
                    val next = api.verifyNextItem(oid, entityType)
                    currentItem = next.optJSONObject("item")
                    val reasons = next.optJSONArray("reject_reasons")
                    rejectReasons = if (reasons != null) {
                        (0 until reasons.length()).map { reasons.getString(it) }
                    } else emptyList()
                }
            } catch (_: Exception) {
                statusMsg = t("eazy_verify.vote_error", "Could not load. Try again.")
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(ownerId, entityType, viewMode, termsAccepted) {
        refresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("design" to t("eazy_verify.tab_designs", "Designs"), "product" to t("eazy_verify.tab_products", "Products")).forEach { (key, label) ->
                FilterChip(selected = entityType == key, onClick = { entityType = key }, label = { Text(label, fontSize = 12.sp) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("available" to t("eazy_verify.available", "Available"), "completed" to t("eazy_verify.completed", "Completed")).forEach { (key, label) ->
                FilterChip(selected = viewMode == key, onClick = { viewMode = key }, label = { Text(label, fontSize = 12.sp) })
            }
        }

        if (ownerId.isNullOrBlank()) {
            Text(t("eazy_chat.login_required_text", "Sign in to use this feature."), color = Color.White.copy(0.8f))
            return@Column
        }

        if (!termsAccepted) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(t("eazy_verify.terms_title", "Community verification"), fontWeight = FontWeight.Bold, color = Color.White)
                Text(t("eazy_verify.terms_body", "You must be 16 or older."), color = Color.White.copy(0.85f), fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = termsChecked, onCheckedChange = { termsChecked = it })
                    Text(t("eazy_verify.terms_confirm", "I confirm I am 16+"), color = Color.White, fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        scope.launch {
                            api.verifyAcceptTerms(ownerId)
                            termsAccepted = true
                            refresh()
                        }
                    },
                    enabled = termsChecked,
                    colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(t("eazy_verify.terms_accept", "Continue"))
                }
            }
            return@Column
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EazColors.Orange)
            }
            return@Column
        }

        statusMsg?.let { Text(it, color = Color(0xFFFCA5A5), fontSize = 12.sp) }

        if (viewMode == "completed") {
            if (completedItems.isEmpty()) {
                Text(t("eazy_verify.empty", "Nothing to review right now."), color = Color.White.copy(0.7f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    completedItems.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            SubcomposeAsyncImage(
                                model = row.optString("image_url", ""),
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                contentScale = ContentScale.Crop,
                            )
                            Text(row.optString("title", t("eazy_verify.untitled", "Untitled")), color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
            return@Column
        }

        val item = currentItem
        if (item == null) {
            Text(t("eazy_verify.empty", "Nothing to review right now."), color = Color.White.copy(0.7f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            return@Column
        }

        Text(
            t("eazy_verify.question", "Does this meet our standards?"),
            color = Color.White.copy(0.85f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        var dragX by remember { mutableStateOf(0f) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(item) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                dragX > 80f -> {
                                    scope.launch {
                                        api.verifySubmitVote(ownerId, item.optLong("id"), "approve")
                                        refresh()
                                    }
                                }
                                dragX < -80f -> {
                                    scope.launch {
                                        api.verifySubmitVote(ownerId, item.optLong("id"), "not_sure")
                                        refresh()
                                    }
                                }
                            }
                            dragX = 0f
                        },
                        onHorizontalDrag = { _, delta -> dragX += delta },
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SubcomposeAsyncImage(
                model = item.optString("image_url", ""),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentScale = ContentScale.Fit,
            )
            Text(item.optString("title", t("eazy_verify.untitled", "Untitled")), color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(
                    onClick = {
                        scope.launch {
                            api.verifySubmitVote(ownerId, item.optLong("id"), "approve")
                            refresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                ) { Text(t("eazy_verify.approve", "Approve")) }
                Button(
                    onClick = {
                        scope.launch {
                            api.verifySubmitVote(ownerId, item.optLong("id"), "not_sure")
                            refresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEAB308), contentColor = Color.Black),
                ) { Text(t("eazy_verify.not_sure", "Not Sure")) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = showReject, onCheckedChange = { showReject = it })
                Text(t("eazy_verify.reject_toggle", "Reject"), color = Color.White, fontSize = 12.sp)
            }
            if (showReject) {
                rejectReasons.forEach { reason ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedReasons.contains(reason),
                            onCheckedChange = { on ->
                                selectedReasons = if (on) selectedReasons + reason else selectedReasons - reason
                            },
                        )
                        Text(reason.replace('_', ' '), color = Color.White, fontSize = 12.sp)
                    }
                }
                Button(
                    onClick = {
                        scope.launch {
                            api.verifySubmitVote(ownerId, item.optLong("id"), "reject", selectedReasons.toList())
                            selectedReasons = emptySet()
                            showReject = false
                            refresh()
                        }
                    },
                    enabled = selectedReasons.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(t("eazy_verify.reject_confirm", "Confirm reject")) }
            }
        }
    }
}
