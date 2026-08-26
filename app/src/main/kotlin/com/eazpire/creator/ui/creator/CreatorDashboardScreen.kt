package com.eazpire.creator.ui.creator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore

@Composable
fun CreatorDashboardScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onOpenSalesModal: () -> Unit,
    onOpenJourney: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onNavigateToGenerator: () -> Unit = {},
    onNavigateToDesigns: () -> Unit = {},
    onNavigateToProducts: () -> Unit = {},
    onNavigateToMarketingHero: () -> Unit = {},
    onNavigateToAutomations: () -> Unit = {},
    onNavigateToResearch: () -> Unit = {},
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
            isLoggedIn = tokenStore.isLoggedIn(),
            onJourneyClick = onOpenJourney,
        )
        EazyDashboardGrid(
            tokenStore = tokenStore,
            translationStore = translationStore,
            onOpenSalesModal = onOpenSalesModal,
            onOpenJourney = onOpenJourney,
            onNavigateToGenerator = onNavigateToGenerator,
            onNavigateToDesigns = onNavigateToDesigns,
            onNavigateToProducts = onNavigateToProducts,
            onNavigateToMarketingHero = onNavigateToMarketingHero,
            onNavigateToAutomations = onNavigateToAutomations,
            onNavigateToResearch = onNavigateToResearch,
        )
        if (!tokenStore.isLoggedIn()) {
            CreatorQuickActionsSection(
                translationStore = translationStore,
                isLoggedIn = false,
                onGeneratorClick = onNavigateToGenerator,
                onDesignsClick = onNavigateToDesigns,
                onContentClick = onNavigateToMarketingHero,
                onProductsClick = onNavigateToProducts,
                onAutomationsClick = onNavigateToAutomations,
                onLoginClick = onLoginClick,
                onRegisterClick = onLoginClick,
            )
        }
    }
}
