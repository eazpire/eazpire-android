package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore

@Composable
fun CreatorDashboardScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onOpenSalesModal: () -> Unit,
    maxHeight: Dp = Dp.Infinity,
    modifier: Modifier = Modifier
) {
    val boundedHeight = if (maxHeight == Dp.Infinity) 4000.dp else maxHeight
    Column(
        modifier = modifier
            .fillMaxSize()
            .heightIn(max = boundedHeight)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp)
    ) {
        CreatorLevelBadge(
            translationStore = translationStore,
            tokenStore = tokenStore,
            ownerId = tokenStore.getOwnerId(),
            isLoggedIn = tokenStore.isLoggedIn()
        )
        CreatorJourneySection(
            translationStore = translationStore,
            ownerId = tokenStore.getOwnerId(),
            isLoggedIn = tokenStore.isLoggedIn()
        )
        CreatorStatsSection(
            translationStore = translationStore,
            tokenStore = tokenStore,
            ownerId = tokenStore.getOwnerId(),
            isLoggedIn = tokenStore.isLoggedIn(),
            onOpenSalesModal = onOpenSalesModal
        )
        CreatorQuickActionsSection(
            translationStore = translationStore,
            isLoggedIn = tokenStore.isLoggedIn()
        )
    }
}
