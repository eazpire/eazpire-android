package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors

/** Fixed narrow icons-only sidebar – immer sichtbar, kein Drawer */
@Composable
fun CreatorSidebar(
    currentScreen: Int,
    onScreenSelect: (Int) -> Unit,
    onSwitchToShop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Icons.Default.Dashboard to 0,
        Icons.Default.Brush to 1,
        Icons.Default.Collections to 2,
        Icons.Default.Campaign to 3
    )

    Column(
        modifier = modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(Color(0xFF0F0C1C).copy(alpha = 0.85f))
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Column(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEach { (icon, index) ->
                val isActive = index == currentScreen
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (isActive) EazColors.Orange.copy(alpha = 0.2f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onScreenSelect(index) }
                        .padding(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Default.ShoppingBag,
            contentDescription = "Shop",
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier
                .size(40.dp)
                .clickable { onSwitchToShop() }
                .padding(8.dp)
        )
    }
}
