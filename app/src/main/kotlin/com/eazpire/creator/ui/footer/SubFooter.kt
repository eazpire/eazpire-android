package com.eazpire.creator.ui.footer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.ui.header.HeaderLocaleRow
import com.eazpire.creator.ui.header.LocaleModalItem
import com.eazpire.creator.ui.header.LanguageChildren
import com.eazpire.creator.ui.header.AVAILABLE_LANGUAGES

private val SubFooterBg = Color.White
private val SubFooterBorder = Color(0xFFE8E8E8)

/**
 * Sub-footer: Location, Language – docked above GlobalFooter.
 * Account, Favorites, Cart sind im Header.
 */
@Composable
fun SubFooter(
    localeStore: LocaleStore,
    translationStore: TranslationStore? = null,
    tokenStore: SecureTokenStore? = null,
    modifier: Modifier = Modifier
) {
    val countryCode by localeStore.countryCode.collectAsState(initial = localeStore.getCountryCodeSync())
    val languageCode by localeStore.languageCode.collectAsState(initial = localeStore.getLanguageCodeSync())
    var languageStandard by remember { mutableStateOf(AVAILABLE_LANGUAGES) }
    var languageChildren by remember { mutableStateOf<Map<String, LanguageChildren>>(emptyMap()) }

    LaunchedEffect(Unit) {
        try {
            val resp = CreatorApi().getLanguages()
            if (resp.standard.isNotEmpty()) {
                languageStandard = resp.standard.map { LocaleModalItem(it.code, it.label, it.flagCode) }
                languageChildren = resp.children.mapValues { (_, v) ->
                    LanguageChildren(
                        dialects = v.dialects.map { LocaleModalItem(it.code, it.label, it.flagCode) },
                        scripts = v.scripts.map { LocaleModalItem(it.code, it.label, it.flagCode) }
                    )
                }.mapKeys { it.key.lowercase() }
            }
        } catch (_: Exception) { /* keep fallback */ }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = SubFooterBorder,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
            }
            .background(SubFooterBg)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderLocaleRow(
            localeStore = localeStore,
            countryCode = countryCode,
            languageCode = languageCode,
            translationStore = translationStore,
            standardLanguages = languageStandard,
            languageChildren = languageChildren,
            onCountryChange = { },
            onLanguageChange = { }
        )
    }
}
