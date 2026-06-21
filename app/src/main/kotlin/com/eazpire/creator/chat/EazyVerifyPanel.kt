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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
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
import com.eazpire.creator.ui.modal.EazFullScreenDialog
import com.eazpire.creator.ui.modal.EazModalSheetLayout
import com.eazpire.creator.ui.modal.eazModalBody
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
    var showTermsModal by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var currentItem by remember { mutableStateOf<JSONObject?>(null) }
    var rejectReasons by remember { mutableStateOf<List<String>>(emptyList()) }
    var qualitySubReasons by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedReasons by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedQualitySubs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showReject by remember { mutableStateOf(false) }
    var reasonInfoTitle by remember { mutableStateOf<String?>(null) }
    var reasonInfoBody by remember { mutableStateOf<String?>(null) }
    var otherReasonNote by remember { mutableStateOf("") }
    var qualityOtherNote by remember { mutableStateOf("") }
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
                    if (next.optString("error") == "terms_not_accepted") {
                        termsAccepted = false
                        currentItem = null
                        completedItems = emptyList()
                        return@launch
                    }
                    currentItem = next.optJSONObject("item")
                    val reasons = next.optJSONArray("reject_reasons")
                    rejectReasons = if (reasons != null) {
                        (0 until reasons.length()).map { reasons.getString(it) }
                    } else emptyList()
                    val qualitySubs = next.optJSONArray("quality_sub_reasons")
                    qualitySubReasons = if (qualitySubs != null) {
                        (0 until qualitySubs.length()).map { qualitySubs.getString(it) }
                    } else emptyList()
                    selectedReasons = emptySet()
                    selectedQualitySubs = emptySet()
                    otherReasonNote = ""
                    qualityOtherNote = ""
                    showReject = false
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

    if (showTermsModal) {
        EazyVerifyTermsDialog(
            t = t,
            onDismiss = { showTermsModal = false },
            onAccept = {
                termsAccepted = true
                showTermsModal = false
                scope.launch {
                    val oid = ownerId?.trim().orEmpty()
                    if (oid.isBlank()) return@launch
                    try {
                        api.verifyAcceptTerms(oid)
                    } catch (_: Exception) {
                    }
                }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (termsAccepted) {
            EazyVerifyPrimaryBar(
                entityType = entityType,
                viewMode = viewMode,
                onEntity = { entityType = it },
                onView = { viewMode = it },
                t = t,
            )
            if (viewMode == "completed") {
                EazyVerifyOutcomeBar(
                    completedOutcome = completedOutcome,
                    onOutcome = { completedOutcome = it },
                    t = t,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            if (ownerId.isNullOrBlank()) {
                Text(t("eazy_chat.login_required_text", "Sign in to use this feature."), color = Color.White.copy(0.8f))
                return@Column
            }

            if (!termsAccepted) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.White.copy(0.16f), RoundedCornerShape(12.dp))
                            .background(Color(0x47000000))
                            .clickable { showTermsModal = true }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .border(2.dp, Color.White.copy(0.45f), RoundedCornerShape(4.dp)),
                        )
                        Text(
                            t("eazy_verify.terms_confirm", "I confirm I am 16+ and accept the community guidelines"),
                            color = Color.White.copy(0.92f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
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

            val itemEntity = item.optString("entity_type", entityType)
            val questionKey = if (itemEntity == "product") {
                "eazy_verify.question_product"
            } else {
                "eazy_verify.question_design"
            }
            Text(
                t(questionKey, "Does this meet our standards?"),
                color = Color.White.copy(0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            val variantLabel = item.optString("variant_label", "").trim()
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
                if (variantLabel.isNotEmpty()) {
                    Text(
                        t("eazy_verify.variant_label", "Variant: {{ label }}").replace("{{ label }}", variantLabel),
                        color = Color.White.copy(0.75f),
                        fontSize = 12.sp,
                    )
                }
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
                    Checkbox(
                        checked = showReject,
                        onCheckedChange = {
                            showReject = it
                            if (!it) {
                                selectedReasons = emptySet()
                                selectedQualitySubs = emptySet()
                                otherReasonNote = ""
                                qualityOtherNote = ""
                            }
                        },
                    )
                    Text(t("eazy_verify.reject_toggle", "Reject"), color = Color.White, fontSize = 12.sp)
                }
                if (showReject) {
                    val rejectScroll = rememberScrollState()
                    val subs = qualitySubReasons.ifEmpty {
                        listOf(
                            "color_combination",
                            "theme_doesnt_fit",
                            "design_too_small",
                            "low_contrast_on_mockup",
                            "placement_issue",
                            "other",
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x26DC2626))
                            .border(1.dp, Color(0x73DC2626), RoundedCornerShape(10.dp))
                            .padding(8.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rejectScroll),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                t("eazy_verify.reject_select", "Please select the main reason:"),
                                color = Color.White.copy(0.9f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                            rejectReasons.forEach { reason ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = selectedReasons.contains(reason),
                                        onCheckedChange = { on ->
                                            selectedReasons = if (on) selectedReasons + reason else selectedReasons - reason
                                            if (reason == "quality_issue" && !on) {
                                                selectedQualitySubs = emptySet()
                                                qualityOtherNote = ""
                                            }
                                            if (reason == "other_reason" && !on) {
                                                otherReasonNote = ""
                                            }
                                        },
                                    )
                                    Text(
                                        t("eazy_verify.reason_$reason", reason.replace('_', ' ')),
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconButton(onClick = {
                                        val info = t("eazy_verify.reason_${reason}_info", "")
                                        if (info.isNotBlank()) {
                                            reasonInfoTitle = t("eazy_verify.reason_$reason", reason.replace('_', ' '))
                                            reasonInfoBody = info
                                        }
                                    }) {
                                        Icon(Icons.Default.Info, contentDescription = null, tint = Color.White.copy(0.75f), modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (reason == "other_reason" && selectedReasons.contains("other_reason")) {
                                    OutlinedTextField(
                                        value = otherReasonNote,
                                        onValueChange = { otherReasonNote = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 36.dp, bottom = 6.dp),
                                        placeholder = {
                                            Text(
                                                t("eazy_verify.reject_other_placeholder", "Describe the issue…"),
                                                color = Color.White.copy(0.45f),
                                                fontSize = 12.sp,
                                            )
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color.White.copy(0.35f),
                                            unfocusedBorderColor = Color.White.copy(0.2f),
                                            cursorColor = EazColors.Orange,
                                        ),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                        minLines = 2,
                                    )
                                }
                            }
                            if (selectedReasons.contains("quality_issue")) {
                                Text(
                                    t("eazy_verify.quality_sub_select", "What quality issue applies? (select all that fit)"),
                                    color = Color.White.copy(0.85f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                                )
                                subs.forEach { sub ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = selectedQualitySubs.contains(sub),
                                            onCheckedChange = { on ->
                                                selectedQualitySubs = if (on) selectedQualitySubs + sub else selectedQualitySubs - sub
                                                if (sub == "other" && !on) qualityOtherNote = ""
                                            },
                                        )
                                        Text(
                                            t("eazy_verify.quality_sub_$sub", sub.replace('_', ' ')),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(onClick = {
                                            val info = t("eazy_verify.quality_sub_${sub}_info", "")
                                            if (info.isNotBlank()) {
                                                reasonInfoTitle = t("eazy_verify.quality_sub_$sub", sub.replace('_', ' '))
                                                reasonInfoBody = info
                                            }
                                        }) {
                                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.White.copy(0.75f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (sub == "other" && selectedQualitySubs.contains("other")) {
                                        OutlinedTextField(
                                            value = qualityOtherNote,
                                            onValueChange = { qualityOtherNote = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 44.dp, bottom = 6.dp),
                                            placeholder = {
                                                Text(
                                                    t("eazy_verify.reject_other_placeholder", "Describe the issue…"),
                                                    color = Color.White.copy(0.45f),
                                                    fontSize = 12.sp,
                                                )
                                            },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedBorderColor = Color.White.copy(0.35f),
                                                unfocusedBorderColor = Color.White.copy(0.2f),
                                                cursorColor = EazColors.Orange,
                                            ),
                                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                            minLines = 2,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }

            if (showReject && currentItem != null && viewMode == "available") {
                val rejectReady = selectedReasons.isNotEmpty() &&
                    (!selectedReasons.contains("quality_issue") || selectedQualitySubs.isNotEmpty())
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = {
                            val activeItem = currentItem ?: return@Button
                            val oid = ownerId ?: return@Button
                            scope.launch {
                                val payload = selectedReasons.toMutableList()
                                selectedQualitySubs.forEach { sub ->
                                    payload.add("quality_sub:$sub")
                                }
                                val noteParts = buildList {
                                    if (selectedReasons.contains("other_reason") && otherReasonNote.isNotBlank()) {
                                        add(otherReasonNote.trim())
                                    }
                                    if (selectedQualitySubs.contains("other") && qualityOtherNote.isNotBlank()) {
                                        add(qualityOtherNote.trim())
                                    }
                                }
                                api.verifySubmitVote(
                                    oid,
                                    activeItem.optLong("id"),
                                    "reject",
                                    payload,
                                    noteParts.joinToString("\n").ifBlank { null },
                                )
                                selectedReasons = emptySet()
                                selectedQualitySubs = emptySet()
                                otherReasonNote = ""
                                qualityOtherNote = ""
                                showReject = false
                                refresh()
                            }
                        },
                        enabled = rejectReady,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
                    ) {
                        Text(t("eazy_verify.confirm_rejection", "Confirm rejection"))
                    }
                }
            }
        }
    }

    if (reasonInfoTitle != null && reasonInfoBody != null) {
        AlertDialog(
            onDismissRequest = { reasonInfoTitle = null; reasonInfoBody = null },
            title = { Text(reasonInfoTitle.orEmpty()) },
            text = { Text(reasonInfoBody.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { reasonInfoTitle = null; reasonInfoBody = null }) {
                    Text(t("eazy_verify.reason_info_close", "Close"))
                }
            },
        )
    }
}

@Composable
private fun EazyVerifyPrimaryBar(
    entityType: String,
    viewMode: String,
    onEntity: (String) -> Unit,
    onView: (String) -> Unit,
    t: (String, String) -> String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EazyVerifyFeedTab(
                selected = entityType == "design",
                label = t("eazy_verify.tab_designs", "Designs"),
                icon = Icons.Default.Image,
                onClick = { onEntity("design") },
            )
            EazyVerifyFeedTab(
                selected = entityType == "product",
                label = t("eazy_verify.tab_products", "Products"),
                icon = Icons.Default.Inventory2,
                onClick = { onEntity("product") },
            )
        }
        Divider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            EazyVerifySegmentSwitch(
                selected = viewMode,
                options = listOf(
                    "available" to t("eazy_verify.available", "Available"),
                    "completed" to t("eazy_verify.completed", "Completed"),
                ),
                onSelect = onView,
            )
        }
    }
}

@Composable
private fun EazyVerifyOutcomeBar(
    completedOutcome: String,
    onOutcome: (String) -> Unit,
    t: (String, String) -> String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EazyVerifyFeedTab(
                selected = completedOutcome == "verified",
                label = t("eazy_verify.verified", "Verified"),
                icon = Icons.Default.CheckCircle,
                onClick = { onOutcome("verified") },
            )
            EazyVerifyFeedTab(
                selected = completedOutcome == "rejected",
                label = t("eazy_verify.rejected", "Rejected"),
                icon = Icons.Default.Cancel,
                onClick = { onOutcome("rejected") },
            )
            EazyVerifyFeedTab(
                selected = completedOutcome == "not_sure",
                label = t("eazy_verify.not_sure", "Not Sure"),
                icon = Icons.Default.HelpOutline,
                onClick = { onOutcome("not_sure") },
            )
        }
    }
}

@Composable
private fun EazyVerifyFeedTab(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) Color(0x73F97316) else Color.White.copy(alpha = 0.12f)
    val background = if (selected) Color(0x1FF97316) else Color.White.copy(alpha = 0.04f)
    val textColor = if (selected) Color(0xFFF97316) else Color(0xFF94A3B8)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
        Text(label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun EazyVerifySegmentSwitch(
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .widthIn(max = 220.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111827))
            .border(1.dp, Color(0xFF374151), RoundedCornerShape(8.dp))
            .padding(2.dp)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (key, label) ->
            val active = selected == key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) Color(0xFFF97316) else Color.Transparent)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (active) Color.White else Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EazyVerifyTermsDialog(
    t: (String, String) -> String,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
) {
    var rulesChecked by remember { mutableStateOf(false) }
    val summaryPoints = listOf(
        t("eazy_verify.terms_point_fair", "Review items honestly and without bias."),
        t("eazy_verify.terms_point_reject", "Use Reject only when content clearly breaks our community rules."),
        t("eazy_verify.terms_point_privacy", "Do not share screenshots or personal data from review items."),
        t("eazy_verify.terms_point_limit", "Daily review limits apply to keep the system fair for everyone."),
    )
    val detailedRules = listOf(
        t("eazy_verify.terms_rules_age", "Age requirement: You must be 16 or older to participate in community verification."),
        t("eazy_verify.terms_rules_quality", "Quality: Designs and products should be clear, complete, and suitable for print-on-demand."),
        t("eazy_verify.terms_rules_copyright", "Copyright: No copied logos, trademarks, or artwork you do not own or have rights to use."),
        t("eazy_verify.terms_rules_offensive", "Respect: No hateful, harassing, or discriminatory content."),
        t("eazy_verify.terms_rules_adult", "Family-friendly: No adult or sexual content; content must be suitable for general audiences."),
        t("eazy_verify.terms_rules_safety", "Safety: No content promoting violence, self-harm, or illegal activity."),
        t("eazy_verify.terms_rules_misleading", "Honesty: Titles and presentation must accurately represent the design or product."),
        t("eazy_verify.terms_rules_privacy", "Privacy: Do not share screenshots, creator names, or personal details outside the review flow."),
        t("eazy_verify.terms_rules_fairness", "Fair play: Daily vote limits apply. Vote honestly — do not coordinate to manipulate outcomes."),
    )

    EazFullScreenDialog(onDismissRequest = onDismiss) {
        EazModalSheetLayout(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A)),
            header = {
                Text(
                    t("eazy_verify.terms_title", "Community verification"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            },
            footer = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { rulesChecked = !rulesChecked },
                    ) {
                        Checkbox(checked = rulesChecked, onCheckedChange = { rulesChecked = it })
                        Text(
                            t("eazy_verify.terms_accept_checkbox", "Accept Community Rules"),
                            color = Color.White.copy(0.92f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                    Button(
                        onClick = onAccept,
                        enabled = rulesChecked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EazColors.Orange,
                            disabledContainerColor = EazColors.Orange.copy(alpha = 0.4f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(t("eazy_verify.terms_accept", "Continue"))
                    }
                }
            },
            body = {
                Column(
                    modifier = Modifier
                        .eazModalBody()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            t(
                                "eazy_verify.terms_body",
                                "You must be 16 or older. Help keep our marketplace safe by reviewing designs and products fairly.",
                            ),
                            color = Color.White.copy(0.9f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        summaryPoints.forEach { point ->
                            Text("• $point", color = Color.White.copy(0.88f), fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            t("eazy_verify.terms_rules_heading", "Community Rules"),
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        )
                        Text(
                            t(
                                "eazy_verify.terms_rules_intro",
                                "When reviewing designs and products, apply these standards consistently. Your votes help creators publish quality work safely.",
                            ),
                            color = Color.White.copy(0.88f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                        detailedRules.forEach { rule ->
                            Text("• $rule", color = Color.White.copy(0.85f), fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            },
        )
    }
}
