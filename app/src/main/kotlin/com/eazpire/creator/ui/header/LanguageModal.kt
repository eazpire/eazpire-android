package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.ui.components.GlassCircularFlag

private val DarkBg = Color(0xFF0B1220)
private val DarkTextPrimary = Color.White
private val DarkTextSecondary = Color.White.copy(alpha = 0.7f)
private val DarkBorder = Color.White.copy(alpha = 0.15f)
private val DarkSelectedBg = EazColors.Orange.copy(alpha = 0.2f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageModal(
    title: String,
    standardLanguages: List<LocaleModalItem>,
    languageChildren: Map<String, LanguageChildren>,
    selectedCode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    searchPlaceholder: String = "Search language...",
    darkMode: Boolean = false
) {
    var searchQuery by remember { mutableStateOf("") }
    var dialectModalBaseLang by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    val childCodes = remember(languageChildren) {
        languageChildren.values.flatMap { c ->
            c.dialects.map { it.code.lowercase() } + c.scripts.map { it.code.lowercase() }
        }.toSet()
    }
    val baseOnlyLanguages = remember(standardLanguages, childCodes) {
        standardLanguages.filter { it.code.lowercase() !in childCodes }
    }
    val filtered = remember(searchQuery, baseOnlyLanguages) {
        if (searchQuery.isBlank()) baseOnlyLanguages
        else baseOnlyLanguages.filter {
            it.code.contains(searchQuery, ignoreCase = true) ||
            it.label.contains(searchQuery, ignoreCase = true)
        }
    }

    val containerColor = if (darkMode) DarkBg else Color.White
    val textColor = if (darkMode) DarkTextPrimary else EazColors.TextPrimary
    val textSecondaryColor = if (darkMode) DarkTextSecondary else EazColors.TextSecondary
    val selectedBg = if (darkMode) DarkSelectedBg else EazColors.OrangeBg
    val borderColor = if (darkMode) DarkBorder else EazColors.TopbarBorder

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = containerColor
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
                    color = textColor
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor)
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                placeholder = { Text(searchPlaceholder, color = textSecondaryColor) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = textSecondaryColor, modifier = Modifier.size(20.dp))
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EazColors.Orange,
                    unfocusedBorderColor = borderColor,
                    focusedContainerColor = containerColor,
                    unfocusedContainerColor = containerColor,
                    cursorColor = EazColors.Orange,
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focusManager.clearFocus() })
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filtered) { item ->
                    val baseLang = item.code.lowercase().split("-").first()
                    val children = languageChildren[baseLang]
                    val hasChildren = children != null && (children.dialects.isNotEmpty() || children.scripts.isNotEmpty())

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (item.code.equals(selectedCode, ignoreCase = true) ||
                                    selectedCode.startsWith("$baseLang-") && baseLang == item.code.lowercase()
                                ) selectedBg
                                else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                onSelect(item.code)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassCircularFlag(countryCode = item.flagCode, size = 24.dp)
                        Row(
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.label,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                color = textColor
                            )
                            val dialectBadge = if (selectedCode.startsWith("${baseLang}-") && baseLang == item.code.lowercase())
                                getDialectScriptBadge(getDialectScriptLabel(selectedCode, languageChildren)) else null
                            if (dialectBadge != null) {
                                Text(
                                    text = " · $dialectBadge",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                    color = textSecondaryColor
                                )
                            }
                        }
                        if (hasChildren) {
                            IconButton(
                                onClick = { dialectModalBaseLang = baseLang },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Dialect & Script",
                                    tint = EazColors.Orange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (item.code.equals(selectedCode, ignoreCase = true) ||
                            (selectedCode.startsWith("$baseLang-") && baseLang == item.code.lowercase())
                        ) {
                            Text("✓", color = EazColors.Orange, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    dialectModalBaseLang?.let { base ->
        val children = languageChildren[base]
        if (children != null && (children.dialects.isNotEmpty() || children.scripts.isNotEmpty())) {
            DialectModal(
                baseLang = base,
                baseLabel = standardLanguages.find { it.code.lowercase().split("-").first() == base }?.label ?: base.uppercase(),
                baseFlag = standardLanguages.find { it.code.lowercase().split("-").first() == base }?.flagCode ?: "US",
                children = children,
                selectedCode = selectedCode,
                onDismiss = { dialectModalBaseLang = null },
                onSelect = { code ->
                    onSelect(code)
                    dialectModalBaseLang = null
                    onDismiss()
                },
                darkMode = darkMode
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialectModal(
    baseLang: String,
    baseLabel: String,
    baseFlag: String,
    children: LanguageChildren,
    selectedCode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    darkMode: Boolean = false
) {
    fun selectAndClose(code: String) {
        onSelect(code)
        onDismiss()
    }

    val containerColor = if (darkMode) DarkBg else Color.White
    val textColor = if (darkMode) DarkTextPrimary else EazColors.TextPrimary
    val textSecondaryColor = if (darkMode) DarkTextSecondary else EazColors.TextSecondary
    val selectedBg = if (darkMode) DarkSelectedBg else EazColors.OrangeBg

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassCircularFlag(countryCode = baseFlag, size = 28.dp)
                    Text(
                        text = baseLabel,
                        modifier = Modifier.padding(start = 12.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        color = textColor
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor)
                }
            }

            if (children.dialects.isNotEmpty()) {
                Text(
                    text = "Dialect",
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = textSecondaryColor
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectAndClose(baseLang) }
                            .background(
                                if (!children.dialects.any { it.code.equals(selectedCode, ignoreCase = true) } &&
                                    !children.scripts.any { it.code.equals(selectedCode, ignoreCase = true) }
                                ) selectedBg else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Standard", modifier = Modifier.padding(start = 8.dp), color = textColor)
                    }
                    children.dialects.forEach { d ->
                        val isSelected = d.code.equals(selectedCode, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectAndClose(d.code) }
                                .background(
                                    if (isSelected) selectedBg else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlassCircularFlag(countryCode = d.flagCode, size = 20.dp)
                            Text(
                                d.label,
                                modifier = Modifier.padding(start = 8.dp),
                                color = textColor
                            )
                            if (isSelected) Text("✓", modifier = Modifier.padding(start = 8.dp), color = EazColors.Orange)
                        }
                    }
                }
            }

            if (children.scripts.isNotEmpty()) {
                Text(
                    text = "Script",
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = textSecondaryColor
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectAndClose(baseLang) }
                            .background(
                                if (!children.scripts.any { it.code.equals(selectedCode, ignoreCase = true) } &&
                                    !children.dialects.any { it.code.equals(selectedCode, ignoreCase = true) }
                                ) selectedBg else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Standard", modifier = Modifier.padding(start = 8.dp), color = textColor)
                    }
                    children.scripts.forEach { s ->
                        val isSelected = s.code.equals(selectedCode, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectAndClose(s.code) }
                                .background(
                                    if (isSelected) selectedBg else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.label, modifier = Modifier.padding(start = 8.dp), color = textColor)
                            if (isSelected) Text("✓", modifier = Modifier.padding(start = 8.dp), color = EazColors.Orange)
                        }
                    }
                }
            }
        }
    }
}
