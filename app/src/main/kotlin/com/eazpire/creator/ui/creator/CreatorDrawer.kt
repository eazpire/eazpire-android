package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.ui.header.CreatorSwitch

/** Drawer wie Web: creator-drawer (rgba(15,12,28,0.65), blur, border) */
@Composable
fun CreatorDrawer(
    visible: Boolean,
    currentScreen: Int,
    screenLabels: List<String>,
    onDismiss: () -> Unit,
    onSwitchToShop: () -> Unit,
    onScreenSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Box(
        modifier = modifier
            .width(280.dp)
            .fillMaxHeight()
            .shadow(40.dp)
            .background(Color(0xFF0F0C1C).copy(alpha = 0.65f))
            .border(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        DrawerAquarium(modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
            CreatorSwitch(
                isCreatorMode = true,
                onModeChange = { if (!it) onSwitchToShop() },
                compact = true
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                screenLabels.forEachIndexed { index, label ->
                    val isActive = index == currentScreen
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onScreenSelect(index) }
                            .background(
                                color = if (isActive) EazColors.Orange.copy(alpha = 0.2f) else Color.Transparent,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}
