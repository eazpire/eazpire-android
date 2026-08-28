package com.eazpire.creator.ui.creator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.i18n.TranslationStore

internal const val RESEARCH_ANALYZE_SLOT_CAP = 20
internal const val RESEARCH_JUST_ADDED_MS = 30_000L
internal const val RESEARCH_DONE_TOAST_MS = 4_000L

private val TextMain = Color(0xFFF4F1FF)
private val TextDim = Color(0xC7F4F1FF)
private val Accent = Color(0xFF7C5CFF)
private val AccentOrange = Color(0xFFF97316)
private val PanelBg = Color(0xFF120C22)

internal data class ResearchChip(
    val id: String,
    val label: String,
)

@Composable
internal fun ResearchFunnelButton(
    translationStore: TranslationStore,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = translationStore.t("creator.research.filter_funnel", "Filters")
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.FilterList,
            contentDescription = label,
            tint = TextMain,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun ResearchActiveChips(
    chips: List<ResearchChip>,
    onRemove: (String) -> Unit,
) {
    if (chips.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            Text(
                chip.label,
                color = TextMain,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Accent.copy(alpha = 0.28f))
                    .clickable { onRemove(chip.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
internal fun ResearchRightDrawer(
    open: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!open) return
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99080618))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(0.88f)
                .width(320.dp)
                .background(PanelBg)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { },
        ) {
            content()
        }
    }
}

@Composable
internal fun ResearchDrawerFooter(
    translationStore: TranslationStore,
    onApply: () -> Unit,
    onAnalyze: () -> Unit,
    analyzeEnabled: Boolean,
    remaining: Int,
    limit: Int,
    analyzing: Boolean,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xE610122A))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            tr("creator.research.filter_apply", "Apply"),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Accent)
                .clickable(onClick = onApply)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (analyzeEnabled && !analyzing) AccentOrange else Color.White.copy(alpha = 0.12f))
                .clickable(enabled = analyzeEnabled && !analyzing, onClick = onAnalyze)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (analyzing) tr("creator.research.analyze_loading", "Analyzing…")
                else tr("creator.research.analyze", "Analyze"),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                tr("creator.research.analyze_quota", "{remaining}/{limit}")
                    .replace("{remaining}", remaining.toString())
                    .replace("{limit}", limit.toString()),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun ResearchAnalyzeModal(
    translationStore: TranslationStore,
    query: String,
    onQuery: (String) -> Unit,
    countryLabel: String,
    countryFlag: String?,
    countryOptions: List<Triple<String, String, String?>>,
    onCountry: (String) -> Unit,
    languageLabel: String,
    languageFlag: String?,
    languageOptions: List<Triple<String, String, String?>>,
    onLanguage: (String) -> Unit,
    remaining: Int,
    limit: Int,
    analyzeEnabled: Boolean,
    onCancel: () -> Unit,
    onAnalyze: () -> Unit,
    searchPlaceholder: String,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xC7080618))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onCancel,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PanelBg)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { }
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    tr("creator.research.analyze", "Analyze"),
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    CenterChoiceField(
                        label = tr("creator.research.country", "Country"),
                        value = countryLabel,
                        flagCode = countryFlag,
                        options = countryOptions,
                        modifier = Modifier.weight(1f),
                        onPick = onCountry,
                    )
                    CenterChoiceField(
                        label = tr("creator.research.language", "Language"),
                        value = languageLabel,
                        flagCode = languageFlag,
                        options = languageOptions,
                        modifier = Modifier.weight(1f),
                        onPick = onLanguage,
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(searchPlaceholder, color = TextDim) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain,
                        focusedBorderColor = AccentOrange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        tr("creator.research.analyze_cancel", "Cancel"),
                        color = TextMain,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .clickable(onClick = onCancel)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (analyzeEnabled) AccentOrange else Color.White.copy(alpha = 0.12f))
                            .clickable(enabled = analyzeEnabled, onClick = onAnalyze)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tr("creator.research.analyze", "Analyze"),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            tr("creator.research.analyze_quota", "{remaining}/{limit}")
                                .replace("{remaining}", remaining.toString())
                                .replace("{limit}", limit.toString()),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ResearchLockOverlay(
    translationStore: TranslationStore,
    visible: Boolean,
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99080618))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            translationStore.t("creator.research.analyze_loading", "Analyzing…"),
            color = TextMain,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(PanelBg)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

@Composable
internal fun ResearchDoneToast(
    translationStore: TranslationStore,
    query: String,
    count: Int,
    showGoToResearch: Boolean,
    onGoToResearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PanelBg)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tr("creator.research.analyze_done", "Analyze \"{q}\" finished, {n} results found.")
                    .replace("{q}", query)
                    .replace("{n}", count.toString()),
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (showGoToResearch) {
                Text(
                    tr("creator.research.go_to_research", "Go to Research"),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentOrange)
                        .clickable(onClick = onGoToResearch)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            Text(
                tr("creator.research.filter_close", "Close filters"),
                color = TextDim,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }
    }
}

internal fun researchQuotaLabel(remaining: Int, limit: Int): String = "$remaining/$limit"
