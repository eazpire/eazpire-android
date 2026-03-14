package com.eazpire.creator.ui.header

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.launch

private const val FLAG_CDN = "https://flagcdn.com/w80"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderLocaleRow(
    localeStore: LocaleStore,
    countryCode: String,
    languageCode: String,
    onCountryChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCountryModal by remember { mutableStateOf(false) }
    var showLanguageModal by remember { mutableStateOf(false) }
    val flagCountryForLang = localeStore.getFlagCountryForLanguage(languageCode)
    val scope = rememberCoroutineScope()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedCard(
            onClick = { showCountryModal = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, EazColors.TopbarBorder),
            colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
                containerColor = androidx.compose.ui.graphics.Color.White
            ),
            modifier = Modifier.padding(2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = EazColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("$FLAG_CDN/${countryCode.lowercase()}.png")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = EazColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        OutlinedCard(
            onClick = { showLanguageModal = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, EazColors.TopbarBorder),
            colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
                containerColor = androidx.compose.ui.graphics.Color.White
            ),
            modifier = Modifier.padding(2.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Language,
                    contentDescription = null,
                    tint = EazColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("$FLAG_CDN/${flagCountryForLang.lowercase()}.png")
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = EazColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

    if (showCountryModal) {
        LocaleModal(
            title = "Deliver to",
            items = AVAILABLE_COUNTRIES,
            selectedCode = countryCode,
            onDismiss = { showCountryModal = false },
            onSelect = { code ->
                scope.launch {
                    localeStore.setRegionOverride(code)
                    onCountryChange(code)
                }
            },
            searchPlaceholder = "Search country..."
        )
    }

    if (showLanguageModal) {
        LocaleModal(
            title = "Select language",
            items = AVAILABLE_LANGUAGES,
            selectedCode = languageCode,
            onDismiss = { showLanguageModal = false },
            onSelect = { code ->
                scope.launch {
                    localeStore.setLanguageOverride(code)
                    onLanguageChange(code)
                }
            },
            searchPlaceholder = "Search language..."
        )
    }
}
