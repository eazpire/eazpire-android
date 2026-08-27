package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.creatorcodes.creatorCodeHintPulse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderActions(
    cartCount: Int = 0,
    favoritesCount: Int = 0,
    favoriteDesignsCount: Int = 0,
    favoriteProductsCount: Int = 0,
    onAccountClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onCartClick: () -> Unit = {},
    profileHintActive: Boolean = false,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val actionSize = if (compact) 40.dp else 48.dp
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                com.eazpire.creator.util.DebugLog.click("Account")
                onAccountClick()
            },
            modifier = Modifier.size(actionSize)
        ) {
            Icon(
                imageVector = Icons.Outlined.PersonOutline,
                contentDescription = "Account",
                tint = EazColors.TextPrimary,
                modifier = Modifier.creatorCodeHintPulse(profileHintActive, cornerRadiusDp = 20f),
            )
        }
        Box {
            IconButton(
                onClick = {
                    com.eazpire.creator.util.DebugLog.click("Favorites")
                    onFavoritesClick()
                },
                modifier = Modifier.size(actionSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorites",
                        tint = EazColors.TextPrimary
                    )
                    val designs = favoriteDesignsCount
                    val products = favoriteProductsCount
                    if (designs > 0) {
                        Text(
                            text = if (designs < 100) "$designs" else "99+",
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = (-6).dp, y = (-6).dp),
                            color = Color(0xFFC4B5FD),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (products > 0) {
                        Text(
                            text = if (products < 100) "$products" else "99+",
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp),
                            color = Color(0xFFFB923C),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Box {
            IconButton(
                onClick = {
                    com.eazpire.creator.util.DebugLog.click("Cart")
                    onCartClick()
                },
                modifier = Modifier.size(actionSize)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ShoppingCart,
                    contentDescription = "Cart",
                    tint = EazColors.TextPrimary
                )
            }
            if (cartCount > 0) {
                Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                ) {
                    Text("$cartCount")
                }
            }
        }
    }
}
