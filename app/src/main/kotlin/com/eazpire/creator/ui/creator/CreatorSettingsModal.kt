package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.eazpire.creator.ui.modal.EazBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.creatorcodes.creatorCodeHintPulse
import com.eazpire.creator.i18n.TranslationStore

/** Creator Settings Modal – fullscreen von unten, icons-only Sidebar wie Web (Profile, Creator Codes, Community, Creator Names, Level, EAZ, Payout, Interests, NFT) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorSettingsModal(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
    onLoginClick: () -> Unit = {},
    initialTab: Int = 0,
    initialEazSub: String? = null,
    initialRedeemCode: String? = null,
    onInitialRedeemCodeConsumed: () -> Unit = {},
    pendingWearPairToken: String? = null,
    creatorCodeHintActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isLoggedIn = tokenStore.isLoggedIn()
    var currentTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 10)) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val tabs = listOf(
        SettingsTabItem(translationStore.t("creator.settings.nav_profile", "Profile"), Icons.Default.Person),
        SettingsTabItem(translationStore.t("creator.settings.nav_notifications", "Notifications"), Icons.Default.Notifications),
        SettingsTabItem(translationStore.t("creator.settings.nav_creator_codes", "Creator Codes"), Icons.Default.Lock),
        SettingsTabItem(translationStore.t("creator.settings.nav_community", "Community"), Icons.Default.Groups),
        SettingsTabItem(translationStore.t("creator.settings.nav_creator_names", "Creator Names"), Icons.Default.Star),
        SettingsTabItem(translationStore.t("creator.settings.nav_level", "Level"), Icons.Default.ExpandLess),
        SettingsTabItem(translationStore.t("creator.settings.nav_eaz", "EAZ"), Icons.Default.Star),
        SettingsTabItem(translationStore.t("creator.settings.nav_payout", "Payout"), Icons.Default.Payments),
        SettingsTabItem(translationStore.t("creator.settings.nav_interests", "Interests"), Icons.Default.Favorite),
        SettingsTabItem(translationStore.t("creator.settings.nav_nft", "NFT"), Icons.Default.Collections),
        SettingsTabItem(translationStore.t("creator.settings.nav_wear", "Creator Wear"), Icons.Default.Watch),
    )
    val codesTabIndex = 2
    val profileTabIndex = 0
    val hintPulseTransition = rememberInfiniteTransition(label = "settingsCodesHint")
    val codesHintPulse by hintPulseTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(animation = tween(2000), repeatMode = RepeatMode.Reverse),
        label = "settingsCodesHintValue",
    )

    EazBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF070B14),
        modifier = modifier,
        fullscreen = true,
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Icons-only Sidebar (fix, schmal)
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .background(Color(0xFF070B14))
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                tabs.forEachIndexed { i, tab ->
                    val isActive = i == currentTab
                    val showProfileHint = creatorCodeHintActive && i == profileTabIndex && !isActive
                    val showCodesHint = creatorCodeHintActive && i == codesTabIndex && !isActive
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(40.dp)
                            .then(
                                if (showProfileHint) {
                                    Modifier.creatorCodeHintPulse(true, cornerRadiusDp = 10f)
                                } else {
                                    Modifier
                                }
                            )
                            .background(
                                when {
                                    isActive -> EazColors.Orange.copy(alpha = 0.2f)
                                    showCodesHint -> EazColors.Orange.copy(alpha = codesHintPulse)
                                    else -> Color.Transparent
                                },
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { currentTab = i }
                            .padding(8.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070B14))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tabs[currentTab].label,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                CreatorSettingsTabContent(
                    currentTab = currentTab,
                    tokenStore = tokenStore,
                    translationStore = translationStore,
                    pendingWearPairToken = pendingWearPairToken,
                    initialRedeemCode = if (currentTab == codesTabIndex) initialRedeemCode else null,
                    onInitialRedeemCodeConsumed = onInitialRedeemCodeConsumed,
                    onRequestSettingsTab = { currentTab = it },
                    initialEazSub = if (currentTab == 6) initialEazSub else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0B1220))
                )
            }
        }
        if (!isLoggedIn) {
            CreatorGuestLockOverlay(
                translationStore = translationStore,
                onLoginClick = onLoginClick,
            )
        }
        }
    }
}

private data class SettingsTabItem(val label: String, val icon: ImageVector)
