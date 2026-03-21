package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.ui.components.GlassCircularFlag

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocaleModal(
    title: String,
    items: List<LocaleModalItem>,
    selectedCode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    searchPlaceholder: String = "Search...",
    sheetContainerColor: Color = Color.White,
    contentColorPrimary: Color = EazColors.TextPrimary,
    contentColorSecondary: Color = EazColors.TextSecondary,
    fieldContainerColor: Color = Color.White,
    outlineUnfocused: Color = EazColors.TopbarBorder
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val filtered = remember(searchQuery, items) {
        if (searchQuery.isBlank()) items
        else items.filter {
            it.code.contains(searchQuery, ignoreCase = true) ||
            it.label.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = sheetContainerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    color = contentColorPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = contentColorPrimary
                    )
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                placeholder = { Text(searchPlaceholder, color = contentColorSecondary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = contentColorSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EazColors.Orange,
                    unfocusedBorderColor = outlineUnfocused,
                    focusedContainerColor = fieldContainerColor,
                    unfocusedContainerColor = fieldContainerColor,
                    cursorColor = EazColors.Orange,
                    focusedTextColor = contentColorPrimary,
                    unfocusedTextColor = contentColorPrimary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filtered) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(item.code)
                                onDismiss()
                            }
                            .background(
                                if (item.code == selectedCode) EazColors.OrangeBg
                                else androidx.compose.ui.graphics.Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassCircularFlag(countryCode = item.flagCode, size = 24.dp)
                        Text(
                            text = item.label,
                            modifier = Modifier.padding(start = 12.dp),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            color = contentColorPrimary
                        )
                        if (item.code == selectedCode) {
                            Text(
                                text = "✓",
                                modifier = Modifier.padding(start = 8.dp),
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                color = EazColors.Orange
                            )
                        }
                    }
                }
            }
        }
    }
}

data class LocaleModalItem(val code: String, val label: String, val flagCode: String)

/** Dialects and scripts for a base language (e.g. de -> Schweizerdeutsch, Bairisch, ...) */
data class LanguageChildren(
    val dialects: List<LocaleModalItem>,
    val scripts: List<LocaleModalItem>
)

/** Returns the dialect/script label if the given code is a dialect or script, else null. */
fun getDialectScriptLabel(code: String, children: Map<String, LanguageChildren>): String? {
    val base = code.lowercase().split("-").first()
    val c = children[base] ?: return null
    return c.dialects.find { it.code.equals(code, ignoreCase = true) }?.label
        ?: c.scripts.find { it.code.equals(code, ignoreCase = true) }?.label
}

/** Short badge text for dialect/script (e.g. "Swiss German" instead of full "Swiss German (Schweizerdeutsch...)"). */
fun getDialectScriptBadge(label: String?): String? {
    if (label.isNullOrBlank()) return null
    val short = label.substringBefore("(").trim()
    return short.ifBlank { label }
}
