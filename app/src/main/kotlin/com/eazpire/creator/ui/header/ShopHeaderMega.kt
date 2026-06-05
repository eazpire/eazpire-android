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
import com.eazpire.creator.ui.nav.EazNavTablerIcon

data class ShopMegaLink(
    val label: String,
    val collectionHandle: String,
    val productType: String? = null,
    val iconHandle: String? = null,
)

data class ShopMegaColumn(
    val headerLabel: String,
    val headerProductType: String? = null,
    val headerIconHandle: String? = null,
    val links: List<ShopMegaLink>
)

/** Matches web eaz-header-nav audience mega panels (mobile: first tap opens, second navigates to collection). */
fun shopMegaColumnsForAudience(audienceHandle: String): List<ShopMegaColumn> {
    val base = audienceHandle.trim().lowercase()
    return listOf(
        ShopMegaColumn(
            headerLabel = "Clothing",
            headerIconHandle = "clothing",
            links = listOf(
                ShopMegaLink("T-Shirts", base, "t-shirt", "t-shirt"),
                ShopMegaLink("Hoodies", base, "hoodie", "hoodie"),
                ShopMegaLink("Sweatshirts", base, "sweatshirt", "sweatshirt"),
                ShopMegaLink("Tank Tops", base, "tank-top", "tank-top"),
                ShopMegaLink("Jackets", base, "jacket", "jacket"),
                ShopMegaLink("Shorts", base, "shorts", "shorts"),
                ShopMegaLink("Dresses", base, "dress", "dress"),
            )
        ),
        ShopMegaColumn(
            headerLabel = "Shoes",
            headerProductType = "shoes",
            headerIconHandle = "shoes",
            links = listOf(
                ShopMegaLink("All Shoes", base, "shoes", "shoes"),
                ShopMegaLink("Sneakers", base, "sneakers", "sneakers"),
                ShopMegaLink("Boots", base, "boots", "boots"),
                ShopMegaLink("Sandals", base, "sandals", "sandals"),
            )
        ),
        ShopMegaColumn(
            headerLabel = "Accessories",
            headerProductType = "accessories",
            headerIconHandle = "accessories",
            links = listOf(
                ShopMegaLink("Bags", base, "bags", "bags"),
                ShopMegaLink("Jewelry", base, "jewelry", "jewelry"),
                ShopMegaLink("Hats & Caps", base, "hats", "hats"),
                ShopMegaLink("Scarves", base, "scarves", "scarves"),
            )
        ),
    )
}

fun shopMegaColumnsForHomeLiving(): List<ShopMegaColumn> = listOf(
    ShopMegaColumn(
        headerLabel = "",
        links = listOf(
            ShopMegaLink("Plush Toys", "plush-toys", iconHandle = "plush-toys"),
            ShopMegaLink("Drinkware", "drinkware", iconHandle = "drinkware"),
            ShopMegaLink("Wall Art", "wall-art", iconHandle = "wall-art"),
            ShopMegaLink("Stationery", "stationery", iconHandle = "stationery"),
            ShopMegaLink("Tech", "tech", iconHandle = "tech"),
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
                    if (col.headerLabel.isNotBlank()) {
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clickable {
                                    onLinkClick(
                                        col.headerLabel,
                                        col.links.firstOrNull()?.collectionHandle ?: "home-living",
                                        col.headerProductType
                                    )
                                },
                        ) {
                            EazNavTablerIcon(
                                handle = col.headerIconHandle ?: col.headerLabel,
                                iconSize = 16.dp,
                            )
                            Text(
                                text = col.headerLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = EazColors.TextPrimary,
                            )
                        }
                    }
                    col.links.forEach { link ->
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onLinkClick(link.label, link.collectionHandle, link.productType)
                                },
                        ) {
                            EazNavTablerIcon(
                                handle = link.iconHandle ?: link.productType ?: link.collectionHandle,
                                iconSize = 14.dp,
                            )
                            Text(
                                text = link.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = EazColors.TextSecondary,
                            )
                        }
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
