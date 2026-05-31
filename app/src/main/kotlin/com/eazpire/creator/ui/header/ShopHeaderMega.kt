package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors

data class ShopMegaLink(
    val label: String,
    val collectionHandle: String,
    val productType: String? = null
)

data class ShopMegaColumn(
    val headerLabel: String,
    val headerProductType: String? = null,
    val links: List<ShopMegaLink>
)

/** Matches web eaz-header-nav audience mega panels (mobile: first tap opens, second navigates to collection). */
fun shopMegaColumnsForAudience(audienceHandle: String): List<ShopMegaColumn> {
    val base = audienceHandle.trim().lowercase()
    return listOf(
        ShopMegaColumn(
            headerLabel = "Clothing",
            links = listOf(
                ShopMegaLink("T-Shirts", base, "t-shirt"),
                ShopMegaLink("Hoodies", base, "hoodie"),
                ShopMegaLink("Sweatshirts", base, "sweatshirt"),
                ShopMegaLink("Tank Tops", base, "tank-top"),
                ShopMegaLink("Jackets", base, "jacket"),
                ShopMegaLink("Shorts", base, "shorts"),
                ShopMegaLink("Dresses", base, "dress"),
            )
        ),
        ShopMegaColumn(
            headerLabel = "Shoes",
            headerProductType = "shoes",
            links = listOf(
                ShopMegaLink("All Shoes", base, "shoes"),
                ShopMegaLink("Sneakers", base, "sneakers"),
                ShopMegaLink("Boots", base, "boots"),
                ShopMegaLink("Sandals", base, "sandals"),
            )
        ),
        ShopMegaColumn(
            headerLabel = "Accessories",
            headerProductType = "accessories",
            links = listOf(
                ShopMegaLink("Bags", base, "bags"),
                ShopMegaLink("Jewelry", base, "jewelry"),
                ShopMegaLink("Hats & Caps", base, "hats"),
                ShopMegaLink("Scarves", base, "scarves"),
            )
        ),
    )
}

fun shopMegaColumnsForHomeLiving(): List<ShopMegaColumn> = listOf(
    ShopMegaColumn(
        headerLabel = "Home & Living",
        links = listOf(
            ShopMegaLink("Home & Living", "home-living"),
            ShopMegaLink("Plush Toys", "plush-toys"),
            ShopMegaLink("Drinkware", "drinkware"),
            ShopMegaLink("Wall Art", "wall-art"),
            ShopMegaLink("Stationery", "stationery"),
            ShopMegaLink("Tech", "tech"),
        )
    )
)

@Composable
fun ShopHeaderMegaPanel(
    columns: List<ShopMegaColumn>,
    t: (String, String) -> String,
    onLinkClick: (title: String, collectionHandle: String, productType: String?) -> Unit,
    onCollapse: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()
    Column(modifier = modifier.fillMaxWidth().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            columns.forEach { col ->
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = col.headerLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = EazColors.TextPrimary,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .clickable {
                                onLinkClick(
                                    col.headerLabel,
                                    col.links.firstOrNull()?.collectionHandle ?: "home-living",
                                    col.headerProductType
                                )
                            }
                    )
                    col.links.forEach { link ->
                        Text(
                            text = link.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = EazColors.TextSecondary,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onLinkClick(link.label, link.collectionHandle, link.productType)
                                }
                        )
                    }
                }
            }
        }
        if (onCollapse != null) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = t("eaz.header.collapse_menu", "Collapse menu"),
                    tint = EazColors.Orange,
                )
            }
        }
    }
}
