package com.eazpire.creator.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountModalSheet(
    tokenStore: SecureTokenStore,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
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
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                when (AccountTab.entries[selectedTab]) {
                    AccountTab.Profile -> AccountProfileTab(
                        tokenStore = tokenStore
                    )
                    AccountTab.SizeAI -> AccountSizeAITab()
                    AccountTab.Wardrobe -> AccountWardrobeTab()
                    AccountTab.Mockups -> AccountMockupsTab()
                    AccountTab.Creations -> AccountCreationsTab()
                    AccountTab.Community -> AccountCommunityTab()
                    AccountTab.Balance -> AccountBalanceTab()
                }
            }
        }
    }
}
