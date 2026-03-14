package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.util.DebugLog

@Composable
fun MainHeader(
    localeStore: LocaleStore,
    modifier: Modifier = Modifier
) {
    val countryCode by localeStore.countryCode.collectAsState(initial = localeStore.getCountryCodeSync())
    val languageCode by localeStore.languageCode.collectAsState(initial = localeStore.getLanguageCodeSync())
    var searchQuery by remember { mutableStateOf("") }
    var languageStandard by remember { mutableStateOf(AVAILABLE_LANGUAGES) }
    var languageChildren by remember { mutableStateOf<Map<String, LanguageChildren>>(emptyMap()) }

    LaunchedEffect(Unit) {
        try {
            val api = CreatorApi()
            val resp = api.getLanguages()
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
    var isCreatorMode by remember { mutableStateOf(false) }
    var cartDrawerVisible by remember { mutableStateOf(false) }

    Box {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderLogo()
            CreatorSwitch(
                isCreatorMode = isCreatorMode,
                onModeChange = { isCreatorMode = it }
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderLocaleRow(
                localeStore = localeStore,
                countryCode = countryCode,
                languageCode = languageCode,
                standardLanguages = languageStandard,
                languageChildren = languageChildren,
                onCountryChange = { },
                onLanguageChange = { }
            )
            HeaderActions(
                cartCount = 0,
                onCartClick = {
                    DebugLog.click("Cart icon")
                    cartDrawerVisible = true
                }
            )
        }
        HeaderSearch(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(EazColors.TopbarBorder)
        )
    }
        CartDrawer(
            visible = cartDrawerVisible,
            onDismiss = { cartDrawerVisible = false }
        )
    }
}
