package com.eazpire.creator.ui.header

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.LocalTranslationStore

/** Synthetic handle: opens shop “Create product” flow (not a Shopify collection). */
const val SHOP_MENU_CREATE_HANDLE = "eaz_shop_create"

private data class MenuItem(
    val label: String,
    val collectionHandle: String?,
    val url: String,
    val megaAudienceHandle: String? = null,
    val megaHomeLiving: Boolean = false,
)

private val MENU_ITEMS = listOf(
    MenuItem("Create", SHOP_MENU_CREATE_HANDLE, ""),
    MenuItem("Promotions", "eaz-promotions", "https://www.eazpire.com/collections/eaz-promotions"),
    MenuItem("Women", "women", "https://www.eazpire.com/collections/women", megaAudienceHandle = "women"),
    MenuItem("Men", "men", "https://www.eazpire.com/collections/men", megaAudienceHandle = "men"),
    MenuItem("Kids", "kids", "https://www.eazpire.com/collections/kids", megaAudienceHandle = "kids"),
    MenuItem("Toddler", "toddler", "https://www.eazpire.com/collections/toddler", megaAudienceHandle = "toddler"),
    MenuItem("Home & Living", "home-living", "https://www.eazpire.com/collections/home-living", megaHomeLiving = true),
)

private val MENU_ITEM_KEYS = mapOf(
    "Create" to "creator.shop_create_product.entry",
    "Promotions" to "eaz.shop.promotions_title",
    "Women" to "sidebar.women", "Men" to "sidebar.men", "Kids" to "sidebar.kids",
    "Toddler" to "eaz.header.toddler", "Home & Living" to "menu.home-living",
)

@Composable
fun ShopMenuBar(
    onAllClick: () -> Unit,
    onCategoryClick: ((title: String, handle: String, productType: String?) -> Unit)? = null,
    selectedHandle: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val store = LocalTranslationStore.current
    val tr = store?.translations?.collectAsState(initial = emptyMap())?.value
    val t = store?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }
    val menuBg = EazColors.Orange.copy(alpha = 0.95f)
    var expandedMegaKey by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(menuBg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onAllClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = t("header.all", "All"),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = t("header.all", "All"),
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                        .align(Alignment.CenterStart)
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(MENU_ITEMS) { item ->
                        val megaKey = item.megaAudienceHandle ?: if (item.megaHomeLiving) "home-living" else null
                        val hasMega = megaKey != null
                        val isCreate = item.collectionHandle == SHOP_MENU_CREATE_HANDLE
                        val isSelected = item.collectionHandle != null && item.collectionHandle == selectedHandle
                        val isMegaExpanded = megaKey != null && expandedMegaKey == megaKey
                        val label = t(MENU_ITEM_KEYS[item.label] ?: item.label, item.label)
                        Box(
                            modifier = Modifier
                                .clickable {
                                    val handle = item.collectionHandle
                                    when {
                                        handle != null && onCategoryClick != null && hasMega -> {
                                            if (isMegaExpanded) {
                                                expandedMegaKey = null
                                                onCategoryClick(item.label, handle, null)
                                            } else {
                                                expandedMegaKey = megaKey
                                            }
                                        }
                                        handle != null && onCategoryClick != null -> {
                                            expandedMegaKey = null
                                            onCategoryClick(item.label, handle, null)
                                        }
                                        item.url.isNotBlank() -> {
                                            expandedMegaKey = null
                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                                )
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                                .then(
                                    if (isCreate) {
                                        Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    } else {
                                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCreate) {
                                ShopCreateNavPill {
                                    Text(
                                        text = label,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                                    )
                                }
                            } else {
                                Text(
                                    text = label,
                                    color = when {
                                        isMegaExpanded || isSelected -> Color.White
                                        else -> Color.White.copy(alpha = 0.95f)
                                    },
                                    fontWeight = if (isMegaExpanded) FontWeight.Bold else FontWeight.Normal,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }

        val megaColumns = when (expandedMegaKey) {
            "women", "men", "kids", "toddler" -> shopMegaColumnsForAudience(expandedMegaKey!!)
            "home-living" -> shopMegaColumnsForHomeLiving()
            else -> null
        }
        if (megaColumns != null && onCategoryClick != null) {
            ShopHeaderMegaPanel(
                columns = megaColumns,
                t = t,
                onLinkClick = { title, handle, productType ->
                    expandedMegaKey = null
                    onCategoryClick(title, handle, productType)
                },
                onCollapse = { expandedMegaKey = null },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
