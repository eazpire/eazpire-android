@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    var completedOutcome by remember { mutableStateOf("verified") }
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
                    val list = api.verifyCompletedList(oid, entityType, completedOutcome)
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
                    completedItems = emptyList()
                }
            } catch (_: Exception) {
                statusMsg = t("eazy_verify.vote_error", "Could not load. Try again.")
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(ownerId, entityType, viewMode, completedOutcome, termsAccepted) {
        refresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EazyVerifySubheader(t("eazy_verify.subheader_entity", "Review type"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EazyVerifyIconTab(
                        selected = entityType == "design",
                        label = t("eazy_verify.tab_designs", "Designs"),
                        icon = Icons.Default.Image,
                        onClick = { entityType = "design" },
                    )
                    EazyVerifyIconTab(
                        selected = entityType == "product",
                        label = t("eazy_verify.tab_products", "Products"),
                        icon = Icons.Default.Inventory2,
                        onClick = { entityType = "product" },
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EazyVerifySubheader(t("eazy_verify.subheader_queue", "Queue"))
                EazyVerifySegmentSwitch(
                    selected = viewMode,
                    options = listOf(
                        "available" to t("eazy_verify.available", "Available"),
                        "completed" to t("eazy_verify.completed", "Completed"),
                    ),
                    onSelect = { viewMode = it },
                )
            }
        }

        if (viewMode == "completed") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EazyVerifySubheader(t("eazy_verify.subheader_outcome", "Your decisions"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EazyVerifyIconTab(
                        selected = completedOutcome == "verified",
                        label = t("eazy_verify.verified", "Verified"),
                        icon = Icons.Default.CheckCircle,
                        onClick = { completedOutcome = "verified" },
                    )
                    EazyVerifyIconTab(
                        selected = completedOutcome == "rejected",
                        label = t("eazy_verify.rejected", "Rejected"),
                        icon = Icons.Default.Cancel,
                        onClick = { completedOutcome = "rejected" },
                    )
                    EazyVerifyIconTab(
                        selected = completedOutcome == "not_sure",
                        label = t("eazy_verify.not_sure", "Not Sure"),
                        icon = Icons.Default.HelpOutline,
                        onClick = { completedOutcome = "not_sure" },
                    )
                }
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
                Text(
                    t("eazy_verify.completed_empty", "No completed reviews yet."),
                    color = Color.White.copy(0.7f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    completedItems.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SubcomposeAsyncImage(
                                model = row.optString("image_url_snapshot", row.optString("image_url", "")),
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                contentScale = ContentScale.Crop,
                            )
                            Text(
                                row.optString("title_snapshot", row.optString("title", t("eazy_verify.untitled", "Untitled"))),
                                color = Color.White,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
            return@Column
        }

        val item = currentItem
        if (item == null) {
            Text(
                t("eazy_verify.empty", "Nothing to review right now."),
                color = Color.White.copy(0.7f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            return@Column
        }

        Text(
            t("eazy_verify.question", "Does this meet our standards?"),
            color = Color.White.copy(0.85f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        var dragX by remember(item) { mutableStateOf(0f) }
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

@Composable
private fun EazyVerifySubheader(text: String) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
    )
}

@Composable
private fun EazyVerifyIconTab(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) Color(0xFFF97316) else Color.White.copy(alpha = 0.16f)
    val background = if (selected) Color(0x33F97316) else Color(0x47000000)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EazyVerifySegmentSwitch(
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111827))
            .border(1.dp, Color(0xFF374151), RoundedCornerShape(8.dp))
            .padding(2.dp)
            .height(40.dp)
            .widthIn(min = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (key, label) ->
            val active = selected == key
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) Color(0xFFF97316) else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (active) Color.White else Color(0xFF9CA3AF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}
