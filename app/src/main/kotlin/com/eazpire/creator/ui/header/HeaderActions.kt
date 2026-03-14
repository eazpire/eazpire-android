package com.eazpire.creator.ui.header

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeaderActions(
    cartCount: Int = 0,
    onAccountClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onCartClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        IconButton(onClick = {
            com.eazpire.creator.util.DebugLog.click("Account")
            onAccountClick()
        }) {
            Icon(
                imageVector = Icons.Outlined.PersonOutline,
                contentDescription = "Account",
                tint = EazColors.TextPrimary
            )
        }
        IconButton(onClick = {
            com.eazpire.creator.util.DebugLog.click("Favorites")
            onFavoritesClick()
        }) {
            Icon(
                imageVector = Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorites",
                tint = EazColors.TextPrimary
            )
        }
        Box {
            IconButton(onClick = {
                com.eazpire.creator.util.DebugLog.click("Cart")
                onCartClick()
            }) {
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
