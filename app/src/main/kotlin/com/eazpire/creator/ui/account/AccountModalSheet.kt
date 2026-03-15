package com.eazpire.creator.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore

enum class AccountTab(val label: String) {
    Profile("Profile"),
    SizeAI("Size AI"),
    Wardrobe("Wardrobe"),
    Mockups("My Mockups"),
    Creations("My Creations"),
    Community("Community"),
    Balance("Balance & Payouts")
}

@Composable
fun AccountModalSheet(
    tokenStore: SecureTokenStore,
    onDismiss: () -> Unit,
    initialTab: AccountTab? = null,
    modifier: Modifier = Modifier
) {
    val initialIndex = initialTab?.let { AccountTab.entries.indexOf(it).takeIf { i -> i >= 0 } } ?: 0
    var selectedTab by remember(initialTab) { mutableStateOf(initialIndex) }
    var footerSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var footerSaveInProgress by remember { mutableStateOf(false) }
    var sizeAiMeasurementsSubTab by remember { mutableStateOf(true) }
    var wardrobeTotalPrice by remember { mutableStateOf("0,00 €") }
    var wardrobeGenerateAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var wardrobeCanGenerate by remember { mutableStateOf(false) }
    var wardrobeSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var wardrobeCanSave by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(0.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Account",
                        style = MaterialTheme.typography.titleLarge,
                        color = EazColors.TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = EazColors.TextPrimary
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AccountTab.entries.forEachIndexed { index, tab ->
                        val isSelected = selectedTab == index
                        Text(
                            text = tab.label,
                            modifier = Modifier
                                .clickable { selectedTab = index }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .then(
                                    if (isSelected) Modifier.background(
                                        EazColors.OrangeBg,
                                        RoundedCornerShape(8.dp)
                                    ) else Modifier
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) EazColors.Orange else EazColors.TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    when (val tab = AccountTab.entries[selectedTab]) {
                        AccountTab.Profile -> AccountProfileTab(
                            tokenStore = tokenStore,
                            onSaveActionReady = { footerSaveAction = it },
                            onSavingStateChange = { footerSaveInProgress = it }
                        )
                        AccountTab.SizeAI -> AccountSizeAITab(
                            tokenStore = tokenStore,
                            onSaveActionReady = { action, onMeasurements ->
                                footerSaveAction = if (onMeasurements) action else null
                                sizeAiMeasurementsSubTab = onMeasurements
                            },
                            onSavingStateChange = { footerSaveInProgress = it }
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
                            }
                        )
                        AccountTab.Mockups -> AccountMockupsTab(tokenStore = tokenStore)
                        AccountTab.Creations -> AccountCreationsTab(tokenStore = tokenStore)
                        AccountTab.Community -> AccountCommunityTab(tokenStore = tokenStore)
                        AccountTab.Balance -> AccountBalanceTab(tokenStore = tokenStore)
                    }
                }

                val showFooter = selectedTab == 0 || (selectedTab == 1 && sizeAiMeasurementsSubTab) || selectedTab == 2
                if (showFooter) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 8.dp,
                        color = Color.White,
                        tonalElevation = 0.dp
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
                                        Text(if (footerSaveInProgress) "Saving..." else "Save")
                                    }
                                }
                                1 -> {
                                    if (sizeAiMeasurementsSubTab) {
                                        Button(
                                            onClick = { footerSaveAction?.invoke() },
                                            enabled = !footerSaveInProgress,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(if (footerSaveInProgress) "Saving..." else "Save profile")
                                        }
                                    }
                                }
                                2 -> {
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
            }
        }
    }
}
