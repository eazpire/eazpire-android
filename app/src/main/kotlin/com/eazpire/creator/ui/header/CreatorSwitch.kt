package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.EazpireCreatorTheme

@Composable
fun CreatorSwitch(
    isCreatorMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val trackWidth = 160.dp
    val trackHeight = 40.dp
    val segmentWidth = trackWidth / 2
    val pillRadius = 20.dp
    val glassBg = Color.White.copy(alpha = 0.3f)
    val glassBorder = Color.White.copy(alpha = 0.45f)
    val shapeLeft = RoundedCornerShape(topStart = pillRadius, bottomStart = pillRadius, topEnd = 0.dp, bottomEnd = 0.dp)

    Row(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(glassBg)
            .border(1.dp, glassBorder, RoundedCornerShape(percent = 50)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shop segment – orange slide when selected, text centered
        Box(
            modifier = Modifier
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(shapeLeft)
                .then(
                    if (!isCreatorMode) Modifier.background(EazColors.Orange)
                    else Modifier
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (isCreatorMode) onModeChange(false) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Shop",
                style = MaterialTheme.typography.labelLarge,
                color = if (isCreatorMode) EazColors.TextSecondary else Color.White
            )
        }
        // Creator segment
        Box(
            modifier = Modifier
                .width(segmentWidth)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!isCreatorMode) onModeChange(true) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Creator",
                style = MaterialTheme.typography.labelLarge,
                color = if (isCreatorMode) Color.White else EazColors.TextSecondary
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
private fun CreatorSwitchPreviewShop() {
    EazpireCreatorTheme {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .background(Color(0xFFF5F5F5))
        ) {
            CreatorSwitch(isCreatorMode = false, onModeChange = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
private fun CreatorSwitchPreviewCreator() {
    EazpireCreatorTheme {
        Box(
            modifier = Modifier
                .padding(24.dp)
                .background(Color(0xFFF5F5F5))
        ) {
            CreatorSwitch(isCreatorMode = true, onModeChange = {})
        }
    }
}
