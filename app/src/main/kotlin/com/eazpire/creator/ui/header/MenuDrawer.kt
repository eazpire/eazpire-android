package com.eazpire.creator.ui.header

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.sidebar.SidebarViewMode
import com.eazpire.creator.sidebar.SidebarViewStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.eazpire.creator.api.CreatorApi

private data class DrawerItem(
    val label: String,
    val collectionHandle: String?,
    val url: String
)

private val AUDIENCE_HANDLES = listOf("women", "men", "kids", "toddler")

private val CLOTHING_SUBMENU = listOf(
    "T-Shirts" to "T-Shirt", "Hoodies" to "Hoodie", "Sweatshirts" to "Sweatshirt",
    "Tank Tops" to "Tank Top", "Jackets" to "Jacket", "Shorts" to "Shorts",
    "Dresses" to "Dress", "Long Sleeves" to "Long Sleeve", "Pants" to "Pants",
    "Joggers" to "Joggers", "Jeans" to "Jeans", "Leggings" to "Leggings",
    "Skirts" to "Skirt", "Socks" to "Sock"
)
private val SHOES_SUBMENU = listOf(
    "Sneakers" to "Sneakers", "Boots" to "Boots", "Sandals" to "Sandals"
)
private val ACCESSORIES_SUBMENU = listOf(
    "Bags" to "Bags", "Jewelry" to "Jewelry", "Hats & Caps" to "Hats", "Scarves" to "Scarves"
)

private val SECTION_IDS = listOf(
    "gutscheine", "audience", "home-living", "personalize", "generate"
)

private val SECTION_LABELS = mapOf(
    "gutscheine" to "Gift Cards & Coupons",
    "audience" to "Audience",
    "home-living" to "Home & Living",
    "personalize" to "Personalize",
    "generate" to "Generate"
)

private val DRAWER_ITEMS = listOf(
    DrawerItem("Women", "women", "https://www.eazpire.com/collections/women"),
    DrawerItem("Men", "men", "https://www.eazpire.com/collections/men"),
    DrawerItem("Kids", "kids", "https://www.eazpire.com/collections/kids"),
    DrawerItem("Toddler", "toddler", "https://www.eazpire.com/collections/toddler"),
    DrawerItem("Home & Living", "home-living", "https://www.eazpire.com/collections/home-living"),
    DrawerItem("Personalize", null, "https://www.eazpire.com/pages/design-generator"),
    DrawerItem("Generate", null, "https://www.eazpire.com/pages/design-generator"),
)

private val LIST_VIEW_ITEMS = listOf(
    "Accessories" to "accessories",
    "Bags" to "bags",
    "Drinkware" to "drinkware",
    "Wall Art" to "wall-art",
    "Home & Living" to "home-living",
    "Jewelry" to "jewelry",
    "Phone Cases" to "phone-cases",
    "Plush Toys" to "plush-toys",
    "Stationery" to "stationery",
    "Tech" to "tech"
)

@Composable
fun MenuDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    tokenStore: SecureTokenStore? = null,
    cartCount: Int = 0,
    onCategoryClick: ((title: String, handle: String, productType: String?) -> Unit)? = null,
    onExternalUrl: ((url: String) -> Unit)? = null,
    onHomeClick: () -> Unit = {},
    onCartClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val context = LocalContext.current
    val viewStore = remember { SidebarViewStore(context) }
    var viewMode by remember { mutableStateOf(viewStore.getViewMode()) }
    var greetingName by remember { mutableStateOf<String?>(null) }
    var hiddenPanelVisible by remember { mutableStateOf(false) }
    var visibilityTrigger by remember { mutableStateOf(0) }
    val visibilityStore = remember { SidebarVisibilityStore(context) }
    val hiddenCount = remember(visibilityTrigger, viewMode) {
        if (viewMode == SidebarViewMode.Grid) SECTION_IDS.count { !visibilityStore.isSectionVisible(it) }
        else 0
    }

    LaunchedEffect(Unit) {
        viewMode = viewStore.getViewMode()
    }
    LaunchedEffect(tokenStore) {
        val ownerId = tokenStore?.getOwnerId()
        if (!ownerId.isNullOrBlank()) {
            try {
                val api = CreatorApi(jwt = tokenStore?.getJwt())
                val resp = withContext(Dispatchers.IO) { api.getCustomerProfile(ownerId) }
                if (resp.optBoolean("ok", false)) {
                    val profile = resp.optJSONObject("profile")
                    greetingName = profile?.optString("first_name", "")?.takeIf { it.isNotBlank() }
                }
            } catch (_: Exception) {}
        } else {
            greetingName = null
        }
    }

    fun dismissWithAnimation() {
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val drawerWidthPx = with(density) {
                minOf(maxWidth * 0.85f, 340.dp).toPx()
            }
            var isEntered by remember { mutableStateOf(false) }
            var isExiting by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) { isEntered = true }

            val offsetXPx by animateFloatAsState(
                targetValue = when {
                    !isEntered -> -drawerWidthPx
                    isExiting -> -drawerWidthPx
                    else -> 0f
                },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )

            LaunchedEffect(isExiting, offsetXPx) {
                if (isExiting && offsetXPx <= -drawerWidthPx + 1f) {
                    onDismiss()
                }
            }

            fun doDismiss() {
                isExiting = true
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { doDismiss() }
                )
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 340.dp)
                        .fillMaxWidth(0.85f)
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(offsetXPx.roundToInt(), 0) }
                        .background(Color(0xFFFEF5ED))
                ) {
                    MenuDrawerHeader(
                        greetingName = greetingName,
                        viewMode = viewMode,
                        onViewModeChange = {
                            viewMode = it
                            viewStore.setViewMode(it)
                        },
                        hiddenPanelVisible = hiddenPanelVisible,
                        onHiddenPanelToggle = { hiddenPanelVisible = !hiddenPanelVisible },
                        hiddenCount = hiddenCount,
                        onClose = { doDismiss() }
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                    ) {
                        when (viewMode) {
                            SidebarViewMode.Grid -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (hiddenPanelVisible && hiddenCount > 0) {
                                        MenuDrawerHiddenPanel(
                                            visibilityStore = visibilityStore,
                                            sectionIds = SECTION_IDS,
                                            sectionLabels = SECTION_LABELS,
                                            onRestore = { visibilityTrigger++ }
                                        )
                                    }
                                    MenuDrawerGiftCardsSection(
                                        tokenStore = tokenStore,
                                        visibilityStore = visibilityStore,
                                        sectionId = "gutscheine",
                                        onVisibilityChange = { visibilityTrigger++ }
                                    )
                                    MenuDrawerGridView(
                                    items = DRAWER_ITEMS,
                                    audienceHandles = AUDIENCE_HANDLES,
                                    context = context,
                                    onCategoryClick = onCategoryClick,
                                    onExternalUrl = onExternalUrl,
                                    dismissWithAnimation = { doDismiss() },
                                    visibilityStore = visibilityStore,
                                    onVisibilityChange = { visibilityTrigger++ }
                                )
                                }
                            }
                            SidebarViewMode.List -> MenuDrawerListView(
                                items = LIST_VIEW_ITEMS,
                                onCategoryClick = onCategoryClick,
                                dismissWithAnimation = { doDismiss() }
                            )
                        }
                    }
                    MenuDrawerFooter(
                        cartCount = cartCount,
                        onHomeClick = {
                            onHomeClick()
                            doDismiss()
                        },
                        onSearchClick = {
                            onSearchClick()
                            doDismiss()
                        },
                        onFavoritesClick = {
                            onFavoritesClick()
                            doDismiss()
                        },
                        onCartClick = {
                            onCartClick()
                            doDismiss()
                        },
                        onAccountClick = {
                            onAccountClick()
                            doDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuDrawerGiftCardsSection(tokenStore: SecureTokenStore?) {
    var expanded by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Gift Cards, 1 = Coupons
    var giftCards by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var coupons by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var loadingGiftCards by remember { mutableStateOf(false) }
    var loadingCoupons by remember { mutableStateOf(false) }
    val ownerId = remember(tokenStore) { tokenStore?.getOwnerId() ?: "" }
    val api = remember { CreatorApi(jwt = tokenStore?.getJwt()) }

    LaunchedEffect(expanded, ownerId, activeTab) {
        if (!expanded || ownerId.isBlank()) return@LaunchedEffect
        if (activeTab == 0 && giftCards.isEmpty() && !loadingGiftCards) {
            loadingGiftCards = true
            try {
                val resp = withContext(Dispatchers.IO) {
                    api.getCustomerGiftCards(ownerId, AuthConfig.SHOP_DOMAIN)
                }
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("gift_cards") ?: resp.optJSONArray("giftCards")
                    giftCards = (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }
                }
            } catch (_: Exception) {}
            loadingGiftCards = false
        }
        if (activeTab == 1 && coupons.isEmpty() && !loadingCoupons) {
            loadingCoupons = true
            try {
                val resp = withContext(Dispatchers.IO) { api.getPromoSlots(ownerId) }
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("slots") ?: resp.optJSONArray("promos")
                    coupons = (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }
                }
            } catch (_: Exception) {}
            loadingCoupons = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Gift Cards & Coupons",
                style = MaterialTheme.typography.titleSmall,
                color = EazColors.TextPrimary
            )
            val badgeCount = giftCards.count { it.optDouble("balance", 0.0) > 0 } +
                coupons.count { it.optString("status") == "active" }
            if (badgeCount > 0) {
                Text(
                    text = "$badgeCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(EazColors.Orange, androidx.compose.foundation.shape.CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = EazColors.TextPrimary,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .rotate(if (expanded) 180f else 0f)
                    .size(20.dp)
            )
        }
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("My Gift Cards", "Coupons").forEachIndexed { i, label ->
                    val selected = activeTab == i
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) EazColors.Orange else EazColors.TextSecondary,
                        modifier = Modifier
                            .clickable { activeTab = i }
                            .padding(vertical = 4.dp)
                    )
                }
            }
            when {
                ownerId.isBlank() -> Text(
                    text = "Sign in to see your gift cards",
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                activeTab == 0 && loadingGiftCards -> Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                activeTab == 0 && giftCards.isEmpty() -> Text(
                    text = "No gift cards yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                activeTab == 0 -> Column {
                    giftCards.forEach { gc ->
                        val bal = gc.optDouble("balance", 0.0)
                        val currency = gc.optString("currency", "EUR")
                        val masked = gc.optString("masked_code", gc.optString("last_characters", "????"))
                        Text(
                            text = "%.2f %s ••••%s".format(bal, currency, masked),
                            style = MaterialTheme.typography.bodySmall,
                            color = EazColors.TextPrimary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                activeTab == 1 && loadingCoupons -> Text(
                    text = "Loading...",
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                activeTab == 1 && coupons.isEmpty() -> Text(
                    text = "No coupons available",
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                activeTab == 1 -> Column {
                    coupons.forEach { s ->
                        val code = s.optString("discount_code", "")
                        val status = s.optString("status", "")
                        Text(
                            text = "$code ($status)",
                            style = MaterialTheme.typography.bodySmall,
                            color = EazColors.TextPrimary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuDrawerHeader(
    greetingName: String?,
    viewMode: SidebarViewMode,
    onViewModeChange: (SidebarViewMode) -> Unit,
    hiddenPanelVisible: Boolean,
    onHiddenPanelToggle: () -> Unit,
    hiddenCount: Int = 0,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EazColors.Orange)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (greetingName != null) "Hello, $greetingName" else "Guest",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onViewModeChange(if (viewMode == SidebarViewMode.Grid) SidebarViewMode.List else SidebarViewMode.Grid) }
            ) {
                Icon(
                    imageVector = if (viewMode == SidebarViewMode.Grid) Icons.Default.List else Icons.Default.GridView,
                    contentDescription = if (viewMode == SidebarViewMode.Grid) "List view" else "Grid view",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Box {
                IconButton(onClick = onHiddenPanelToggle) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Hidden sections",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (hiddenCount > 0) {
                    Text(
                        text = "$hiddenCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .background(EazColors.Orange, androidx.compose.foundation.shape.CircleShape)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun MenuDrawerGridView(
    items: List<DrawerItem>,
    audienceHandles: List<String>,
    context: android.content.Context,
    onCategoryClick: ((title: String, handle: String, productType: String?) -> Unit)?,
    onExternalUrl: ((url: String) -> Unit)?,
    dismissWithAnimation: () -> Unit
) {
    var expandedAudience by remember { mutableStateOf<String?>(null) }
    items.forEach { item ->
        val isAudience = item.collectionHandle != null && item.collectionHandle in audienceHandles
        if (isAudience) {
            val isExpanded = expandedAudience == item.collectionHandle
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (isExpanded) {
                                    onCategoryClick?.invoke(item.label, item.collectionHandle, null)
                                    dismissWithAnimation()
                                } else {
                                    expandedAudience = item.collectionHandle
                                }
                            }
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = EazColors.TextPrimary
                        )
                    }
                    IconButton(
                        onClick = {
                            expandedAudience = if (isExpanded) null else item.collectionHandle
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = EazColors.TextPrimary,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(if (isExpanded) 180f else 0f)
                        )
                    }
                }
                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 20.dp, bottom = 8.dp)
                    ) {
                        listOf(
                            "Clothing" to CLOTHING_SUBMENU,
                            "Shoes" to SHOES_SUBMENU,
                            "Accessories" to ACCESSORIES_SUBMENU
                        ).forEach { (catLabel, subItems) ->
                            Text(
                                text = catLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = EazColors.TextSecondary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            subItems.forEach { (subLabel, productType) ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onCategoryClick?.invoke(
                                                item.label,
                                                item.collectionHandle,
                                                productType
                                            )
                                            dismissWithAnimation()
                                        }
                                        .padding(vertical = 6.dp)
                                ) {
                                    Text(
                                        text = subLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = EazColors.TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (item.collectionHandle != null && onCategoryClick != null) {
                            onCategoryClick(item.label, item.collectionHandle, null)
                            dismissWithAnimation()
                        } else if (onExternalUrl != null) {
                            onExternalUrl(item.url)
                            dismissWithAnimation()
                        } else {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                                dismissWithAnimation()
                            } catch (_: Exception) {}
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = EazColors.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun MenuDrawerListView(
    items: List<Pair<String, String>>,
    onCategoryClick: ((title: String, handle: String, productType: String?) -> Unit)?,
    dismissWithAnimation: () -> Unit
) {
    items.forEach { (label, handle) ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onCategoryClick?.invoke(label, handle, null)
                    dismissWithAnimation()
                }
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = EazColors.TextPrimary
            )
        }
    }
}

@Composable
private fun MenuDrawerFooter(
    cartCount: Int,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onCartClick: () -> Unit,
    onAccountClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF2F2F2))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FooterNavItem(icon = Icons.Default.Home, label = "Home", onClick = onHomeClick)
        FooterNavItem(icon = Icons.Default.Search, label = "Search", onClick = onSearchClick)
        FooterNavItem(icon = Icons.Default.Favorite, label = "Favorites", onClick = onFavoritesClick)
        Box(contentAlignment = Alignment.Center) {
            FooterNavItem(icon = Icons.Default.ShoppingCart, label = "Cart", onClick = onCartClick)
            if (cartCount > 0) {
                Text(
                    text = if (cartCount < 100) "$cartCount" else "99+",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
                        .background(EazColors.Orange, androidx.compose.foundation.shape.CircleShape)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        FooterNavItem(icon = Icons.Default.Person, label = "Account", onClick = onAccountClick)
    }
}

@Composable
private fun FooterNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = EazColors.TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = EazColors.TextPrimary
        )
    }
}
