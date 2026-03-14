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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors

private const val FLAG_CDN = "https://flagcdn.com/w80"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageModal(
    title: String,
    standardLanguages: List<LocaleModalItem>,
    languageChildren: Map<String, LanguageChildren>,
    selectedCode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    searchPlaceholder: String = "Search language..."
) {
    var searchQuery by remember { mutableStateOf("") }
    var dialectModalBaseLang by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    val filtered = remember(searchQuery, standardLanguages) {
        if (searchQuery.isBlank()) standardLanguages
        else standardLanguages.filter {
            it.code.contains(searchQuery, ignoreCase = true) ||
            it.label.contains(searchQuery, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color.White
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
                    color = EazColors.TextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = EazColors.TextPrimary)
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                placeholder = { Text(searchPlaceholder, color = EazColors.TextSecondary) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = EazColors.TextSecondary, modifier = Modifier.size(20.dp))
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EazColors.Orange,
                    unfocusedBorderColor = EazColors.TopbarBorder,
                    focusedContainerColor = androidx.compose.ui.graphics.Color.White,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.White,
                    cursorColor = EazColors.Orange,
                    focusedTextColor = EazColors.TextPrimary,
                    unfocusedTextColor = EazColors.TextPrimary
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
                                ) EazColors.OrangeBg
                                else androidx.compose.ui.graphics.Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                onSelect(item.code)
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data("$FLAG_CDN/${item.flagCode.lowercase()}.png")
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            text = item.label,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            color = EazColors.TextPrimary
                        )
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
                onApply = { code ->
                    onSelect(code)
                    dialectModalBaseLang = null
                    onDismiss()
                }
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
    onApply: (String) -> Unit
) {
    val isDialect = children.dialects.any { it.code.equals(selectedCode, ignoreCase = true) }
    val isScript = children.scripts.any { it.code.equals(selectedCode, ignoreCase = true) }
    var dialectChoice by remember(selectedCode) {
        mutableStateOf(if (isDialect) selectedCode else "")
    }
    var scriptChoice by remember(selectedCode) {
        mutableStateOf(if (isScript) selectedCode else "")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color.White
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
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("$FLAG_CDN/${baseFlag.lowercase()}.png")
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = baseLabel,
                        modifier = Modifier.padding(start = 12.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                        color = EazColors.TextPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = EazColors.TextPrimary)
                }
            }

            if (children.dialects.isNotEmpty()) {
                Text(
                    text = "Dialect",
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = EazColors.TextSecondary
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dialectChoice = ""; scriptChoice = "" }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = dialectChoice.isEmpty() && scriptChoice.isEmpty(),
                            onClick = { dialectChoice = ""; scriptChoice = "" },
                            colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                        )
                        Text("Standard", modifier = Modifier.padding(start = 8.dp), color = EazColors.TextPrimary)
                    }
                    children.dialects.forEach { d ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dialectChoice = d.code; scriptChoice = "" }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = dialectChoice == d.code,
                                onClick = { dialectChoice = d.code; scriptChoice = "" },
                                colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                            )
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data("$FLAG_CDN/${d.flagCode.lowercase()}.png")
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Text(d.label, modifier = Modifier.padding(start = 8.dp), color = EazColors.TextPrimary)
                        }
                    }
                }
            }

            if (children.scripts.isNotEmpty()) {
                Text(
                    text = "Script",
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = EazColors.TextSecondary
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scriptChoice = "" }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = scriptChoice.isEmpty(),
                            onClick = { scriptChoice = "" },
                            colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                        )
                        Text("Standard", modifier = Modifier.padding(start = 8.dp), color = EazColors.TextPrimary)
                    }
                    children.scripts.forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { scriptChoice = s.code }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = scriptChoice == s.code,
                                onClick = { scriptChoice = s.code },
                                colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                            )
                            Text(s.label, modifier = Modifier.padding(start = 8.dp), color = EazColors.TextPrimary)
                        }
                    }
                }
            }

            TextButton(
                onClick = {
                    val code = dialectChoice.ifBlank { scriptChoice.ifBlank { baseLang } }
                    onApply(code)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            ) {
                Text("Apply", color = EazColors.Orange)
            }
        }
    }
}
