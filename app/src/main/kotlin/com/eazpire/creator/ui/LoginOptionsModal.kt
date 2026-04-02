package com.eazpire.creator.ui

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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.eazpire.creator.EazColors
import com.eazpire.creator.R

/**
 * Same choices as web login modal: Shop app, Google, Email — each starts the in-app OAuth WebView
 * (PKCE); the hosted Shopify page lets the user pick Shop / Google / email.
 */
@Composable
fun LoginOptionsModal(
    onDismiss: () -> Unit,
    /** Called for Shop, Google, and Email — same OAuth entry (matches storefront login UX). */
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Sign in",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = EazColors.TextPrimary
                )
                Text(
                    text = "Choose how you would like to sign in",
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LoginOptionRow(
                        label = "Shop app",
                        onClick = onLoginClick,
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_login_shopify),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified
                            )
                        }
                    )
                    LoginOptionRow(
                        label = "Google",
                        onClick = onLoginClick,
                        icon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_login_google),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color.Unspecified
                            )
                        }
                    )
                    LoginOptionRow(
                        label = "Email",
                        onClick = onLoginClick,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = EazColors.TextPrimary
                            )
                        }
                    )
                }

                Text(
                    text = "You will sign in with Shopify on the next screen (Shop, Google, or email).",
                    style = MaterialTheme.typography.labelSmall,
                    color = EazColors.TextSecondary,
                    modifier = Modifier.padding(top = 14.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = EazColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginOptionRow(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(EazColors.OrangeBg, shape)
            .border(1.dp, EazColors.Orange, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = EazColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
