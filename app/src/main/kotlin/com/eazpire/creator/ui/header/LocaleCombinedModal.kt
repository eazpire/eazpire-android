package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.ui.modal.EazBottomSheet

enum class LocaleCombinedTab { Location, Language }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocaleCombinedModal(
    locationLabel: String,
    languageLabel: String,
    countryItems: List<LocaleModalItem>,
    selectedCountryCode: String,
    onSelectCountry: (String) -> Unit,
    countrySearchPlaceholder: String,
    standardLanguages: List<LocaleModalItem>,
    languageChildren: Map<String, LanguageChildren>,
    selectedLanguageCode: String,
    onSelectLanguage: (String) -> Unit,
    languageSearchPlaceholder: String,
    onDismiss: () -> Unit,
    initialTab: LocaleCombinedTab = LocaleCombinedTab.Location,
) {
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    EazBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(EazColors.TextPrimary.copy(alpha = 0.15f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.05f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LocaleCombinedTabChip(
                        label = locationLabel,
                        selected = tab == LocaleCombinedTab.Location,
                        onClick = { tab = LocaleCombinedTab.Location },
                        modifier = Modifier.weight(1f)
                    )
                    LocaleCombinedTabChip(
                        label = languageLabel,
                        selected = tab == LocaleCombinedTab.Language,
                        onClick = { tab = LocaleCombinedTab.Language },
                        modifier = Modifier.weight(1f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = EazColors.TextPrimary
                    )
                }
            }

            if (tab == LocaleCombinedTab.Location) {
                LocalePickerBody(
                    items = countryItems,
                    selectedCode = selectedCountryCode,
                    onSelect = onSelectCountry,
                    onDismiss = onDismiss,
                    searchPlaceholder = countrySearchPlaceholder
                )
            } else {
                LanguagePickerBody(
                    standardLanguages = standardLanguages,
                    languageChildren = languageChildren,
                    selectedCode = selectedLanguageCode,
                    onDismiss = onDismiss,
                    onSelect = onSelectLanguage,
                    searchPlaceholder = languageSearchPlaceholder
                )
            }
        }
    }
}

@Composable
private fun LocaleCombinedTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) EazColors.TextPrimary else EazColors.TextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}
