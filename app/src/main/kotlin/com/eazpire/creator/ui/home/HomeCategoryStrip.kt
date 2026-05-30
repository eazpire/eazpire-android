package com.eazpire.creator.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HomeCategoryChip(
    val id: String,
    val labelKey: String,
    val labelDefault: String,
)

val HOME_CATEGORY_CHIPS: List<HomeCategoryChip> = listOf(
    HomeCategoryChip("all", "eaz.header.all", "All"),
    HomeCategoryChip("women", "eaz.header.women", "Women"),
    HomeCategoryChip("men", "eaz.header.men", "Men"),
    HomeCategoryChip("kids", "eaz.header.kids", "Kids"),
    HomeCategoryChip("toddler", "eaz.header.toddler", "Toddler"),
    HomeCategoryChip("accessories", "eaz.nav.accessories", "Accessories"),
    HomeCategoryChip("home-living", "eaz.nav.home_living", "Home & Living"),
)

private val IconOrange = Color(0xFFF97316)
private val LabelActive = Color(0xFFEA580C)
private val StripBorder = Color(0x0F000000)

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
            .padding(top = 10.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            HOME_CATEGORY_CHIPS.forEach { chip ->
                val active = chip.id == selectedCategory
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .widthIn(min = 62.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { onCategorySelected(chip.id) },
                        ),
                ) {
                    val circleSize = if (active) 48.dp else 44.dp
                    val iconSize = if (active) 22.dp else 20.dp
                    val circleBrush = if (active) {
                        Brush.linearGradient(
                            listOf(Color(0xFFFFF0E0), Color(0xFFFED7AA)),
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(Color(0xFFFFF5ED), Color(0xFFFEE2CC)),
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .scale(if (active) 1.06f else 1f)
                            .then(
                                if (active) {
                                    Modifier.shadow(
                                        elevation = 6.dp,
                                        shape = CircleShape,
                                        spotColor = IconOrange.copy(alpha = 0.28f),
                                        ambientColor = IconOrange.copy(alpha = 0.12f),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .size(circleSize)
                            .clip(CircleShape)
                            .background(circleBrush)
                            .border(
                                width = if (active) 3.dp else 2.dp,
                                color = if (active) IconOrange else IconOrange.copy(alpha = 0.18f),
                                shape = CircleShape,
                            ),
                    ) {
                        HomeCategoryTablerIcon(
                            categoryId = chip.id,
                            iconSize = iconSize,
                            tint = IconOrange,
                        )
                    }
                    Text(
                        text = labelForKey(chip.labelKey, chip.labelDefault),
                        fontSize = 10.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (active) LabelActive else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .height(1.dp)
                .background(StripBorder),
        )
    }
}
