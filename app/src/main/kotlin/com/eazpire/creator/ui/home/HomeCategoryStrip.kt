package com.eazpire.creator.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors

data class HomeCategoryChip(
    val id: String,
    val labelKey: String,
    val labelDefault: String,
    val emoji: String,
)

val HOME_CATEGORY_CHIPS: List<HomeCategoryChip> = listOf(
    HomeCategoryChip("all", "eaz.header.all", "All", "◎"),
    HomeCategoryChip("women", "eaz.header.women", "Women", "👩"),
    HomeCategoryChip("men", "eaz.header.men", "Men", "👨"),
    HomeCategoryChip("kids", "eaz.header.kids", "Kids", "🧒"),
    HomeCategoryChip("toddler", "eaz.header.toddler", "Toddler", "👶"),
    HomeCategoryChip("accessories", "eaz.nav.accessories", "Accessories", "👜"),
    HomeCategoryChip("home-living", "eaz.nav.home_living", "Home & Living", "🏡"),
)

@Composable
fun HomeCategoryStrip(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    labelForKey: (String, String) -> String,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            HOME_CATEGORY_CHIPS.forEach { chip ->
                val active = chip.id == selectedCategory
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { onCategorySelected(chip.id) },
                        ),
                ) {
                    val circleModifier = Modifier
                        .size(if (active) 48.dp else 44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFFF5ED), Color(0xFFFEE2CC)),
                            ),
                        )
                        .then(
                            if (active) {
                                Modifier.border(3.dp, EazColors.Orange, CircleShape)
                            } else {
                                Modifier.border(2.dp, Color(0xFFF97316).copy(alpha = 0.18f), CircleShape)
                            },
                        )
                    Column(
                        modifier = circleModifier,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(text = chip.emoji, fontSize = if (active) 22.sp else 20.sp)
                    }
                    Text(
                        text = labelForKey(chip.labelKey, chip.labelDefault),
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (active) Color(0xFFEA580C) else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
