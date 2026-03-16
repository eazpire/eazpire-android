package com.eazpire.creator.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.R

private data class LoginOption(
    val id: String,
    val label: String,
    val iconRes: Int,
    val enabled: Boolean,
    val onClick: (() -> Unit)? = null
)

@Composable
fun LoginOptionsModal(
    onDismiss: () -> Unit,
    onShopifyLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var comingSoonExpanded by remember { mutableStateOf(false) }

    val shopifyOption = LoginOption(
        id = "shopify",
        label = "Continue with Shopify",
        iconRes = R.drawable.ic_login_shopify,
        enabled = true,
        onClick = onShopifyLoginClick
    )

    val comingSoonOptions = listOf(
        LoginOption("google", "Google", R.drawable.ic_login_google, false),
        LoginOption("amazon", "Amazon", R.drawable.ic_login_amazon, false),
        LoginOption("facebook", "Facebook", R.drawable.ic_login_facebook, false),
        LoginOption("apple", "Apple", R.drawable.ic_login_apple, false),
        LoginOption("whatsapp", "WhatsApp", R.drawable.ic_login_whatsapp, false),
        LoginOption("eazy", "eazy", R.drawable.ic_eazy_mascot, false)
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Login-Optionen
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sign in",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = EazColors.TextPrimary
                    )

                    // Shopify - aktiv
                    LoginOptionCard(
                        option = shopifyOption,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Coming soon - einklappbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { comingSoonExpanded = !comingSoonExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "More options",
                            style = MaterialTheme.typography.labelLarge,
                            color = EazColors.TextSecondary
                        )
                        Icon(
                            imageVector = if (comingSoonExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = EazColors.TextSecondary
                        )
                    }

                    AnimatedVisibility(
                        visible = comingSoonExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            comingSoonOptions.forEach { option ->
                                LoginOptionCard(
                                    option = option,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = false
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text("Cancel", color = EazColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun LoginOptionCard(
    option: LoginOption,
    modifier: Modifier = Modifier,
    enabled: Boolean = option.enabled
) {
    val shape = RoundedCornerShape(12.dp)
    val (bgColor, borderColor) = when {
        enabled -> EazColors.OrangeBg to EazColors.Orange
        else -> Color(0xFFF5F5F5) to Color(0xFFE5E7EB)
    }

    Box(
        modifier = modifier
            .then(
                if (enabled && option.onClick != null)
                    Modifier.clickable { option.onClick?.invoke() }
                else Modifier
            )
            .background(bgColor, shape)
            .border(1.dp, borderColor, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                painter = painterResource(id = option.iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified
            )
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) EazColors.TextPrimary else EazColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            if (!enabled) {
                Text(
                    text = "Soon",
                    style = MaterialTheme.typography.labelSmall,
                    color = EazColors.TextSecondary
                )
            }
        }
    }
}
