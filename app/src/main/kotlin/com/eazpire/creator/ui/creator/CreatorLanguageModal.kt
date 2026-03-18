package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val LANGUAGES = listOf(
    "en" to "English",
    "de" to "Deutsch",
    "fr" to "Français",
    "es" to "Español",
    "it" to "Italiano",
    "nl" to "Nederlands",
    "pt" to "Português",
    "pl" to "Polski"
)

/** Language Dropdown 1:1 wie Web creator-footer-lang-modal */
@Composable
fun CreatorLanguageModal(
    localeStore: LocaleStore,
    translationStore: TranslationStore,
    currentLang: String,
    onDismiss: () -> Unit,
    onLanguageSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = CoroutineScope(Dispatchers.Main)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color(0xFF0B1220))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = translationStore.t("eaz.topbar.select_language", "Select language"),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp)
            ) {
                LANGUAGES.forEach { (code, name) ->
                    val isActive = currentLang.equals(code, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                scope.launch {
                                    localeStore.setLanguageOverride(code)
                                    onLanguageSelected()
                                    onDismiss()
                                }
                            }
                            .then(
                                if (isActive) Modifier.background(
                                    EazColors.Orange.copy(alpha = 0.2f),
                                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                ) else Modifier
                            )
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 15.sp,
                                color = if (isActive) EazColors.Orange else Color.White
                            )
                        )
                        Text(
                            text = code.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                color = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        }
    }
}
