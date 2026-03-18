package com.eazpire.creator.ui.creator

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eazpire.creator.R
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.launch

private val GalaxyGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0x660A0514),
        Color(0x9905020F)
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreatorMainScreen(
    tokenStore: SecureTokenStore,
    localeStore: LocaleStore,
    translationStore: TranslationStore,
    onSwitchToShop: () -> Unit,
    onAccountClick: () -> Unit,
    onEazyChatOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var drawerVisible by remember { mutableStateOf(false) }
    var salesModalVisible by remember { mutableStateOf(false) }
    var currentScreen by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 4 }, initialPage = 0)
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Galaxy background (wie Web: creator-mobile.css)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GalaxyGradient)
        ) {
            Image(
                painter = painterResource(R.drawable.galaxy_nebula_bg),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.85f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0x660A0514),
                                Color(0x9905020F)
                            )
                        )
                    )
            )
        }
        Column(modifier = Modifier.fillMaxSize()) {
            CreatorHeader(
                currentScreen = currentScreen,
                screenLabels = listOf(
                    translationStore.t("creator.mobile.dashboard", "Dashboard"),
                    translationStore.t("creator.mobile.generator", "Generator"),
                    translationStore.t("creator.mobile.creations", "Creations"),
                    translationStore.t("creator.mobile.marketing", "Marketing")
                ),
                onDrawerClick = { drawerVisible = true },
                onBalanceClick = { salesModalVisible = true },
                onAccountClick = onAccountClick,
                tokenStore = tokenStore
            )

            LaunchedEffect(pagerState.currentPage) {
                currentScreen = pagerState.currentPage
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> CreatorDashboardScreen(
                        tokenStore = tokenStore,
                        translationStore = translationStore,
                        onOpenSalesModal = { salesModalVisible = true }
                    )
                    1 -> CreatorPlaceholderScreen(
                        title = translationStore.t("creator.mobile.generator", "Generator"),
                        hint = translationStore.t("creator.common.coming_soon", "Coming soon")
                    )
                    2 -> CreatorPlaceholderScreen(
                        title = translationStore.t("creator.mobile.creations", "Creations"),
                        hint = translationStore.t("creator.common.coming_soon", "Coming soon")
                    )
                    3 -> CreatorPlaceholderScreen(
                        title = translationStore.t("creator.mobile.marketing", "Marketing"),
                        hint = translationStore.t("creator.common.coming_soon", "Coming soon")
                    )
                }
            }

            CreatorFooter(
                localeStore = localeStore,
                tokenStore = tokenStore
            )
        }

        // Drawer + Backdrop: Row-Layout wie Web (drawer z-index 201, backdrop 200)
        // Backdrop nur rechts vom Drawer, damit Drawer-Interaktion möglich ist
        if (drawerVisible) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
            ) {
                CreatorDrawer(
                    visible = true,
                    currentScreen = currentScreen,
                    screenLabels = listOf(
                        translationStore.t("creator.mobile.dashboard", "Dashboard"),
                        translationStore.t("creator.mobile.generator", "Generator"),
                        translationStore.t("creator.mobile.creations", "Creations"),
                        translationStore.t("creator.mobile.marketing", "Marketing")
                    ),
                    onDismiss = { drawerVisible = false },
                    onSwitchToShop = {
                        drawerVisible = false
                        onSwitchToShop()
                    },
                    onScreenSelect = { index ->
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                            drawerVisible = false
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { drawerVisible = false }
                )
            }
        }

        if (salesModalVisible) {
            CreatorSalesModal(
                tokenStore = tokenStore,
                onDismiss = { salesModalVisible = false }
            )
        }
    }
}
