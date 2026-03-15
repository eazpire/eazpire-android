package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class CategoryItem(
    val id: String,
    val name: String,
    val emoji: String,
    val gradientColors: List<Color>,
    val url: String
)

private val CATEGORIES = listOf(
    CategoryItem(
        id = "women",
        name = "Women",
        emoji = "👩",
        gradientColors = listOf(Color(0xFFF9A03F), Color(0xFFF97316)),
        url = "https://www.eazpire.com/collections/women"
    ),
    CategoryItem(
        id = "men",
        name = "Men",
        emoji = "👨",
        gradientColors = listOf(Color(0xFF4A8FCE), Color(0xFF2668A8)),
        url = "https://www.eazpire.com/collections/men"
    ),
    CategoryItem(
        id = "kids",
        name = "Kids",
        emoji = "🧒",
        gradientColors = listOf(Color(0xFFA8D8EA), Color(0xFF62B6CB)),
        url = "https://www.eazpire.com/collections/kids"
    ),
    CategoryItem(
        id = "toddler",
        name = "Toddler",
        emoji = "👶",
        gradientColors = listOf(Color(0xFFF8C8DC), Color(0xFFF4A4C0)),
        url = "https://www.eazpire.com/collections/toddler"
    ),
    CategoryItem(
        id = "home-living",
        name = "Home & Living",
        emoji = "🏠",
        gradientColors = listOf(Color(0xFFF9A03F), Color(0xFFF97316)),
        url = "https://www.eazpire.com/collections/home-living"
    ),
    CategoryItem(
        id = "personalize",
        name = "Personalize",
        emoji = "✨",
        gradientColors = listOf(Color(0xFFF9A03F), Color(0xFFEA580C)),
        url = "https://www.eazpire.com/pages/design-generator"
    ),
)

@Composable
fun CategoryGrid(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyVerticalGrid(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(CATEGORIES) { item ->
            CategoryCard(
                name = item.name,
                emoji = item.emoji,
                gradientColors = item.gradientColors,
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                        )
                    } catch (_: Exception) {}
                }
            )
        }
    }
}

@Composable
private fun CategoryCard(
    name: String,
    emoji: String,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(colors = gradientColors)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
        }
    }
}
