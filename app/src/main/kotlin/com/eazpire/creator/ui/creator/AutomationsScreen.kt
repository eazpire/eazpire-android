package com.eazpire.creator.ui.creator

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val MAIN_DESIGN = "design-generator"
private const val MAIN_PUBLISH = "publish"
private const val MAIN_MARKETING = "marketing"

/** Same defaults as web `defaultModalDates` in creator-automations-screen.js */
private fun computeDefaultStartEndMillis(): Pair<Long, Long> {
    val cal = Calendar.getInstance()
    cal.add(Calendar.HOUR_OF_DAY, 1)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.set(Calendar.MINUTE, 0)
    val start = cal.timeInMillis
    val endCal = Calendar.getInstance().apply {
        timeInMillis = start
        add(Calendar.DAY_OF_MONTH, 7)
    }
    return start to endCal.timeInMillis
}

private fun formatLocalDateTime(millis: Long): String {
    if (millis <= 0L) return "—"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(millis)
}

private fun openLocalDateTimePicker(
    context: android.content.Context,
    initialMillis: Long,
    onPicked: (Long) -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis.coerceAtLeast(1L) }
    DatePickerDialog(
        context,
        { _, y, m, d ->
            cal.set(Calendar.YEAR, y)
            cal.set(Calendar.MONTH, m)
            cal.set(Calendar.DAY_OF_MONTH, d)
            TimePickerDialog(
                context,
                { _, h, min ->
                    cal.set(Calendar.HOUR_OF_DAY, h)
                    cal.set(Calendar.MINUTE, min)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    onPicked(cal.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                true
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

/**
 * Automations — Design Generator | Publish | Marketing (placeholders), status tabs, grid like web.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationsScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onHeaderTitleChange: (String) -> Unit,
    maxHeight: Dp = Dp.Infinity,
    modifier: Modifier = Modifier
) {
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val scope = rememberCoroutineScope()
    var mainTab by remember { mutableStateOf(MAIN_DESIGN) }
    var statusFilter by remember { mutableStateOf("active") }
    var automations by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    var showCreateSheet by remember { mutableStateOf(false) }
    var formTitle by remember { mutableStateOf("") }
    var formPrompt by remember { mutableStateOf("") }
    var workflowGenerateOnly by remember { mutableStateOf(true) }
    var perDay by remember { mutableIntStateOf(1) }
    var startsAtMillis by remember { mutableLongStateOf(0L) }
    var endsAtMillis by remember { mutableLongStateOf(0L) }
    var createSubmitting by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    fun titleForStatus(): String = when (statusFilter) {
        "scheduled" -> translationStore.t("creator.automations.status_scheduled", "Scheduled")
        "expired" -> translationStore.t("creator.automations.status_expired", "Expired")
        else -> translationStore.t("creator.automations.status_active", "Active")
    }

    LaunchedEffect(mainTab, statusFilter) {
        when (mainTab) {
            MAIN_DESIGN -> onHeaderTitleChange(titleForStatus())
            MAIN_PUBLISH -> onHeaderTitleChange(translationStore.t("creator.automations.tab_publish", "Publish"))
            MAIN_MARKETING -> onHeaderTitleChange(translationStore.t("creator.automations.tab_marketing", "Marketing"))
        }
    }

    LaunchedEffect(ownerId, mainTab, statusFilter, reloadKey) {
        if (ownerId.isBlank() || mainTab != MAIN_DESIGN) return@LaunchedEffect
        loading = true
        err = null
        val rows: JSONArray? = withContext(Dispatchers.IO) {
            try {
                val d = api.listDesignAutomations(ownerId, statusFilter)
                if (d.optBoolean("ok", false)) d.optJSONArray("automations") else null
            } catch (_: Exception) {
                null
            }
        }
        loading = false
        if (rows == null) {
            err = translationStore.t("creator.automations.load_error", "Could not load automations.")
            automations = emptyList()
        } else {
            val list = mutableListOf<JSONObject>()
            for (i in 0 until rows.length()) {
                list.add(rows.getJSONObject(i))
            }
            automations = list
        }
    }

    val createSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    if (showCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!createSubmitting) showCreateSheet = false
            },
            sheetState = createSheetState,
            containerColor = Color(0xFF12141f),
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    translationStore.t("creator.automations.modal_title", "New design automation"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    translationStore.t("creator.automations.modal_title_label", "Title"),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = formTitle,
                    onValueChange = { if (it.length <= 120) formTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EazColors.Orange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        cursorColor = EazColors.Orange,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    translationStore.t("creator.automations.modal_prompt_label", "Prompt"),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = formPrompt,
                    onValueChange = { if (it.length <= 2000) formPrompt = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    minLines = 3,
                    placeholder = {
                        Text(
                            translationStore.t("creator.automations.modal_prompt_placeholder", "Describe what to generate…"),
                            color = Color.White.copy(alpha = 0.4f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EazColors.Orange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        cursorColor = EazColors.Orange,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    translationStore.t("creator.automations.modal_workflow_label", "Workflow"),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { workflowGenerateOnly = true }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = workflowGenerateOnly,
                        onClick = { workflowGenerateOnly = true },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = EazColors.Orange,
                            unselectedColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    Text(
                        translationStore.t("creator.automations.workflow_generate_only", "Generate only"),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { workflowGenerateOnly = false }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = !workflowGenerateOnly,
                        onClick = { workflowGenerateOnly = false },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = EazColors.Orange,
                            unselectedColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    Text(
                        translationStore.t("creator.automations.workflow_generate_publish", "Generate and publish"),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    translationStore.t("creator.automations.modal_per_day_label", "Designs per day"),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedTextField(
                    value = perDay.toString(),
                    onValueChange = { v ->
                        val n = v.filter { it.isDigit() }.toIntOrNull()
                        if (n != null) perDay = n.coerceIn(1, 10)
                        else if (v.isEmpty()) perDay = 1
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EazColors.Orange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        cursorColor = EazColors.Orange,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    translationStore.t("creator.automations.modal_starts_label", "Starts"),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    formatLocalDateTime(startsAtMillis),
                    color = EazColors.Orange,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(enabled = !createSubmitting) {
                            openLocalDateTimePicker(context, startsAtMillis) { startsAtMillis = it }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    translationStore.t("creator.automations.modal_ends_label", "Ends"),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    formatLocalDateTime(endsAtMillis),
                    color = EazColors.Orange,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(enabled = !createSubmitting) {
                            openLocalDateTimePicker(context, endsAtMillis) { endsAtMillis = it }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth()
                )
                createError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFfca5a5), fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = { if (!createSubmitting) showCreateSheet = false },
                        enabled = !createSubmitting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(translationStore.t("creator.automations.modal_cancel", "Cancel"))
                    }
                    Button(
                        onClick = {
                            val titleT = formTitle.trim()
                            if (titleT.isEmpty()) {
                                createError = translationStore.t("creator.automations.create_validation_title", "Please enter a title.")
                                return@Button
                            }
                            if (endsAtMillis <= startsAtMillis) {
                                createError = translationStore.t(
                                    "creator.automations.create_validation_dates",
                                    "End must be after start."
                                )
                                return@Button
                            }
                            createError = null
                            val wf = if (workflowGenerateOnly) "generate_only" else "generate_publish"
                            scope.launch {
                                createSubmitting = true
                                val body = JSONObject().apply {
                                    put("title", titleT)
                                    put("prompt", formPrompt)
                                    put("workflow", wf)
                                    put("designs_per_day", perDay.coerceIn(1, 10))
                                    put("starts_at", startsAtMillis)
                                    put("ends_at", endsAtMillis)
                                }
                                val resp = withContext(Dispatchers.IO) {
                                    try {
                                        api.createDesignAutomation(body)
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                                createSubmitting = false
                                if (resp != null && resp.optBoolean("ok", false)) {
                                    showCreateSheet = false
                                    reloadKey++
                                } else {
                                    createError = resp?.optString("error")?.takeIf { it.isNotBlank() }
                                        ?: translationStore.t("creator.automations.create_error", "Could not create automation.")
                                }
                            }
                        },
                        enabled = !createSubmitting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange, contentColor = Color.Black)
                    ) {
                        if (createSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .height(22.dp)
                                    .padding(2.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(translationStore.t("creator.automations.modal_submit", "Create"))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .heightIn(max = maxHeight)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x660A0514), Color(0x9905020F))
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                MAIN_DESIGN to translationStore.t("creator.automations.tab_design_generator", "Design Generator"),
                MAIN_PUBLISH to translationStore.t("creator.automations.tab_publish", "Publish"),
                MAIN_MARKETING to translationStore.t("creator.automations.tab_marketing", "Marketing")
            ).forEach { (id, label) ->
                Text(
                    text = label,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (mainTab == id) EazColors.Orange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f)
                        )
                        .clickable { mainTab = id }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (mainTab == id) EazColors.Orange else Color.White.copy(alpha = 0.85f),
                    maxLines = 2
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when (mainTab) {
            MAIN_PUBLISH -> {
                Text(
                    translationStore.t("creator.automations.coming_soon_publish", "Publish automations coming soon."),
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(24.dp)
                )
            }
            MAIN_MARKETING -> {
                Text(
                    translationStore.t("creator.automations.coming_soon_marketing", "Marketing automations coming soon."),
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(24.dp)
                )
            }
            MAIN_DESIGN -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("active", "scheduled", "expired").forEach { f ->
                        val lbl = when (f) {
                            "scheduled" -> translationStore.t("creator.automations.status_scheduled", "Scheduled")
                            "expired" -> translationStore.t("creator.automations.status_expired", "Expired")
                            else -> translationStore.t("creator.automations.status_active", "Active")
                        }
                        Text(
                            text = lbl,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (statusFilter == f) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f)
                                )
                                .clickable { statusFilter = f }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = if (statusFilter == f) 0.95f else 0.7f),
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (ownerId.isBlank()) {
                    Text(
                        translationStore.t("creator.automations.login_required", "Log in to manage automations."),
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    return@Column
                }
                if (loading) {
                    Text(
                        translationStore.t("creator.common.loading", "Loading..."),
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
                err?.let {
                    Text(it, color = Color(0xFFfca5a5), modifier = Modifier.padding(bottom = 8.dp))
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                ) {
                    item {
                        AddAutomationTile(
                            label = translationStore.t("creator.automations.add_tile", "Add automation"),
                            onClick = {
                                val (s, e) = computeDefaultStartEndMillis()
                                startsAtMillis = s
                                endsAtMillis = e
                                formTitle = ""
                                formPrompt = ""
                                workflowGenerateOnly = true
                                perDay = 1
                                createError = null
                                showCreateSheet = true
                            }
                        )
                    }
                    items(automations.size) { idx ->
                        val row = automations[idx]
                        val st = row.optString("status", "")
                        val showEnd = statusFilter != "expired" && (st == "active" || st == "scheduled")
                        AutomationCard(
                            row = row,
                            translationStore = translationStore,
                            showEnd = showEnd,
                            onEnd = {
                                val id = row.optLong("id", 0L)
                                if (id > 0) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { api.endDesignAutomation(id) }
                                        reloadKey++
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddAutomationTile(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+", fontSize = 36.sp, color = EazColors.Orange, fontWeight = FontWeight.Bold)
            Text(
                label,
                color = EazColors.Orange,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun AutomationCard(
    row: JSONObject,
    translationStore: TranslationStore,
    showEnd: Boolean,
    onEnd: () -> Unit
) {
    val previews = row.optJSONArray("preview_urls")
    val urls = mutableListOf<String>()
    if (previews != null) {
        for (i in 0 until minOf(4, previews.length())) {
            val u = previews.optString(i, "").trim()
            if (u.isNotEmpty()) urls.add(u)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE6281f2b))
            .padding(bottom = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp)
        ) {
            if (urls.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        translationStore.t("creator.automations.no_designs_yet", "No design generated yet"),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        PreviewCell(urls.getOrNull(0), Modifier.weight(1f))
                        PreviewCell(urls.getOrNull(1), Modifier.weight(1f))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        PreviewCell(urls.getOrNull(2), Modifier.weight(1f))
                        PreviewCell(urls.getOrNull(3), Modifier.weight(1f))
                    }
                }
            }
        }
        Text(
            row.optString("title", "—"),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Text(
            "${translationStore.t("creator.automations.stat_generations", "Generations")}: ${row.optInt("total_generations", 0)}",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        if (showEnd) {
            Text(
                translationStore.t("creator.automations.end_automation", "End"),
                color = Color(0xFFfecaca),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable(onClick = onEnd)
            )
        }
    }
}

@Composable
private fun PreviewCell(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
