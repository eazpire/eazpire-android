package com.eazpire.creator.ui.account

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.eazpire.creator.ui.modal.EazBottomSheet
import com.eazpire.creator.ui.modal.EazModalFooterSurface
import com.eazpire.creator.ui.modal.EazModalSheetLayout
import com.eazpire.creator.ui.modal.eazModalBody
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.ui.nav.EazModalTablerIcon

enum class AccountTab(
    val labelKey: String,
    val labelDefault: String,
    val tabId: String,
) {
    Profile("content.account_profile_settings", "Profile Settings", "profile-settings"),
    Notifications("creator.notifications.notifications_tab", "Notifications", "notifications"),
    SizeAI("content.account_size_ai", "Size AI", "size-ai"),
    Wardrobe("content.account_wardrobe", "Wardrobe", "wardrobe"),
    Mockups("content.account_mockups", "My Mockups", "mockups"),
    Creations("content.account_my_creations", "My Creations", "my-creations"),
    Community("content.account_community", "Community", "community"),
    Balance("content.account_balance_payouts", "Balance & Payouts", "balance-payouts"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountModalSheet(
    tokenStore: SecureTokenStore,
    onDismiss: () -> Unit,
    onLogout: () -> Unit = {},
    initialTab: AccountTab? = null,
    modifier: Modifier = Modifier
) {
    val initialIndex = initialTab?.let { AccountTab.entries.indexOf(it).takeIf { i -> i >= 0 } } ?: 0
    var selectedTab by remember(initialTab) { mutableStateOf(initialIndex) }
    var drawerOpen by remember { mutableStateOf(false) }
    var footerSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var footerSaveInProgress by remember { mutableStateOf(false) }
    var sizeAiMeasurementsSubTab by remember { mutableStateOf(true) }
    var wardrobeTotalPrice by remember { mutableStateOf("0,00 €") }
    var wardrobeGenerateAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var wardrobeCanGenerate by remember { mutableStateOf(false) }
    var wardrobeSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var wardrobeCanSave by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val translationStore = LocalTranslationStore.current
    fun t(key: String, default: String) = translationStore?.t(key, default) ?: default

    EazBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        modifier = modifier,
        fullscreen = true,
    ) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            EazModalSheetLayout(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                header = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { drawerOpen = true }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = EazColors.TextPrimary
                            )
                        }
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = t(
                                    AccountTab.entries[selectedTab].labelKey,
                                    AccountTab.entries[selectedTab].labelDefault
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                color = EazColors.TextPrimary
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = EazColors.TextPrimary
                            )
                        }
                    }
                },
                footer = {
                    val showFooter = selectedTab == 0 || (selectedTab == 2 && sizeAiMeasurementsSubTab) || selectedTab == 3
                    if (showFooter) {
                        EazModalFooterSurface(
                            shadowElevation = 8.dp,
                            color = Color.White,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                when (selectedTab) {
                                    0 -> {
                                        Button(
                                            onClick = { footerSaveAction?.invoke() },
                                            enabled = !footerSaveInProgress,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                if (footerSaveInProgress) {
                                                    t("creator.js.saving", "Saving...")
                                                } else {
                                                    t("creator.common.save", "Save")
                                                }
                                            )
                                        }
                                    }
                                    2 -> {
                                        if (sizeAiMeasurementsSubTab) {
                                            Button(
                                                onClick = { footerSaveAction?.invoke() },
                                                enabled = !footerSaveInProgress,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    if (footerSaveInProgress) {
                                                        t("creator.js.saving", "Saving...")
                                                    } else {
                                                        t("creator.settings.profile_save_button", "Save profile settings")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    3 -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = wardrobeTotalPrice,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = EazColors.TextPrimary
                                                )
                                                Text(
                                                    text = "plus shipping",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = EazColors.TextSecondary
                                                )
                                            }
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (wardrobeCanSave) {
                                                    OutlinedButton(
                                                        onClick = { wardrobeSaveAction?.invoke() }
                                                    ) {
                                                        Text("Save")
                                                    }
                                                }
                                                IconButton(
                                                    onClick = { },
                                                    modifier = Modifier
                                                        .background(EazColors.TopbarBorder.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                ) {
                                                    Icon(
                                                        Icons.Default.Favorite,
                                                        contentDescription = "Add to Favorites",
                                                        tint = EazColors.TextSecondary
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { },
                                                    modifier = Modifier
                                                        .background(EazColors.TopbarBorder.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                                ) {
                                                    Icon(
                                                        Icons.Default.ShoppingCart,
                                                        contentDescription = "Add to Cart",
                                                        tint = EazColors.TextSecondary
                                                    )
                                                }
                                                Button(
                                                    onClick = { wardrobeGenerateAction?.invoke() },
                                                    enabled = wardrobeCanGenerate
                                                ) {
                                                    Text("Generate")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                body = {
                    val tabModifier = Modifier.eazModalBody().padding(16.dp)
                    when (AccountTab.entries[selectedTab]) {
                        AccountTab.Profile -> AccountProfileTab(
                            tokenStore = tokenStore,
                            translationStore = translationStore,
                            onSaveActionReady = { footerSaveAction = it },
                            onSavingStateChange = { footerSaveInProgress = it },
                            onLogout = onLogout,
                            modifier = tabModifier
                        )
                        AccountTab.Notifications -> NotificationSettingsContent(
                            scope = NotificationScope.Shop,
                            tokenStore = tokenStore,
                            modifier = tabModifier
                        )
                        AccountTab.SizeAI -> AccountSizeAITab(
                            tokenStore = tokenStore,
                            onSaveActionReady = { action, onMeasurements ->
                                footerSaveAction = if (onMeasurements) action else null
                                sizeAiMeasurementsSubTab = onMeasurements
                            },
                            onSavingStateChange = { footerSaveInProgress = it },
                            modifier = tabModifier
                        )
                        AccountTab.Wardrobe -> AccountWardrobeTab(
                            tokenStore = tokenStore,
                            onTotalPriceChange = { wardrobeTotalPrice = it },
                            onGenerateActionReady = { action, canGen ->
                                wardrobeGenerateAction = action
                                wardrobeCanGenerate = canGen
                            },
                            onSaveActionReady = { action, canSave ->
                                wardrobeSaveAction = action
                                wardrobeCanSave = canSave
                            },
                            modifier = tabModifier
                        )
                        AccountTab.Mockups -> AccountMockupsTab(tokenStore = tokenStore, modifier = tabModifier)
                        AccountTab.Creations -> AccountCreationsTab(tokenStore = tokenStore, modifier = tabModifier)
                        AccountTab.Community -> AccountCommunityTab(tokenStore = tokenStore, modifier = tabModifier)
                        AccountTab.Balance -> AccountBalanceTab(tokenStore = tokenStore, modifier = tabModifier)
                    }
                }
            )

            AnimatedVisibility(
                    visible = drawerOpen,
                    enter = slideInHorizontally(initialOffsetX = { -it }),
                    exit = slideOutHorizontally(targetOffsetX = { -it }),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                        Box(
                            modifier = Modifier
                                .width(260.dp)
                                .fillMaxHeight()
                                .background(Color.White),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 8.dp)
                            ) {
                                AccountTab.entries.forEachIndexed { index, tab ->
                                    val isSelected = selectedTab == index
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedTab = index
                                                drawerOpen = false
                                            }
                                            .padding(horizontal = 20.dp, vertical = 12.dp)
                                            .then(
                                                if (isSelected) Modifier.background(
                                                    EazColors.OrangeBg,
                                                    RoundedCornerShape(4.dp)
                                                ) else Modifier
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        EazModalTablerIcon(
                                            tabId = tab.tabId,
                                            tint = if (isSelected) EazColors.Orange else EazColors.TextSecondary,
                                            iconSize = 18.dp
                                        )
                                        Text(
                                            text = t(tab.labelKey, tab.labelDefault),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (isSelected) EazColors.Orange else EazColors.TextSecondary
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(EazColors.Orange)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { drawerOpen = false }
                        )
                    }
                }
        }
    }
}
