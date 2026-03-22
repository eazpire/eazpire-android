package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.eazpire.creator.i18n.TranslationStore

/** Creator Settings Modal – fullscreen von unten, icons-only Sidebar wie Web (Profile, Creator Codes, Community, Creator Names, Level, EAZ, Payout, Interests, NFT) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorSettingsModal(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableIntStateOf(0) }
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
        SettingsTabItem(translationStore.t("creator.settings.nav_nft", "NFT"), Icons.Default.Collections)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF070B14),
        modifier = modifier.fillMaxHeight(1f),
        dragHandle = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Icons-only Sidebar (fix, schmal)
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF070B14))
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                tabs.forEachIndexed { i, tab ->
                    val isActive = i == currentTab
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isActive) EazColors.Orange.copy(alpha = 0.2f) else Color.Transparent,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0B1220))
                )
            }
        }
    }
}

private data class SettingsTabItem(val label: String, val icon: ImageVector)
