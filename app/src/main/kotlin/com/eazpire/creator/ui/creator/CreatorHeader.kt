package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CreatorHeader(
    currentScreen: Int,
    screenLabels: List<String>,
    onDrawerClick: () -> Unit,
    onBalanceClick: () -> Unit,
    onAccountClick: () -> Unit,
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier
) {
    var balanceText by remember { mutableStateOf("…") }
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }

    LaunchedEffect(ownerId) {
        if (ownerId.isNotBlank()) {
            try {
                val r = withContext(Dispatchers.IO) { api.getBalance(ownerId) }
                if (r.optBoolean("ok", false)) {
                    val bal = r.optDouble("balance", 0.0)
                    balanceText = "%.2f".format(bal)
                } else {
                    balanceText = "0.00"
                }
            } catch (_: Exception) {
                balanceText = "0.00"
            }
        } else {
            balanceText = "0.00"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x14080512))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDrawerClick) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = Color.White
                )
            }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://cdn.shopify.com/s/files/1/0739/5203/5098/files/eazpire-creator-logo.png?v=1763666950")
                    .build(),
                contentDescription = "eazpire creator",
                modifier = Modifier.size(height = 24.dp, width = 108.dp),
                contentScale = ContentScale.Fit
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onBalanceClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.08f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = balanceText,
                        style = MaterialTheme.typography.labelMedium,
                        color = EazColors.Orange
                    )
                }
                IconButton(onClick = onAccountClick) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Account",
                        tint = Color.White
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            screenLabels.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == currentScreen) 8.dp else 6.dp)
                        .background(
                            color = if (index == currentScreen) EazColors.Orange else Color.White.copy(alpha = 0.4f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                )
            }
        }
        Text(
            text = screenLabels.getOrElse(currentScreen) { "" },
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}
