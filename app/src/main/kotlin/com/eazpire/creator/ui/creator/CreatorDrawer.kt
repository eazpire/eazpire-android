package com.eazpire.creator.ui.creator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.ui.header.CreatorSwitch

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
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(Color(0xFF0A0514))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                CreatorSwitch(
                    isCreatorMode = true,
                    onModeChange = { if (!it) onSwitchToShop() }
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                screenLabels.forEachIndexed { index, label ->
                    val isActive = index == currentScreen
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onScreenSelect(index)
                            }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        }
    }
}
