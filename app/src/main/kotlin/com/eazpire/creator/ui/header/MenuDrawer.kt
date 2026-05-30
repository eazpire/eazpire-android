package com.eazpire.creator.ui.header

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.eazpire.creator.ui.vouchers.VoucherModalTab
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.shop.sidebar.AudienceCard
import com.eazpire.creator.shop.sidebar.AudienceCategoryColumn
import com.eazpire.creator.shop.sidebar.AudienceDetailLine
import com.eazpire.creator.shop.sidebar.AudiencePanelBody
import com.eazpire.creator.shop.sidebar.AudienceSidebarSection
import com.eazpire.creator.shop.sidebar.CategoryTile
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.shop.sidebar.CreatePromoSection
import com.eazpire.creator.shop.sidebar.SidebarNavVisuals
import com.eazpire.creator.shop.sidebar.ExpandCell
import com.eazpire.creator.shop.sidebar.GroupedCategorySection
import com.eazpire.creator.shop.sidebar.GutscheineGridSection
import com.eazpire.creator.shop.sidebar.ParsedMenu
import com.eazpire.creator.shop.sidebar.ParsedNavItem
import com.eazpire.creator.shop.sidebar.RemainingTopSection
import com.eazpire.creator.shop.sidebar.RemainderBody
import com.eazpire.creator.shop.sidebar.ShopSidebarConstants
import com.eazpire.creator.shop.sidebar.ShopSidebarLayoutEngine
import com.eazpire.creator.shop.sidebar.ShopSidebarMenuParser
import com.eazpire.creator.shop.sidebar.ShopSidebarPersonalizationStore
import com.eazpire.creator.shop.sidebar.SidebarGridSection
import com.eazpire.creator.shop.sidebar.ProductCatalogPreferences
import com.eazpire.creator.shop.sidebar.SidebarHiddenState
import com.eazpire.creator.sidebar.SidebarViewMode
import com.eazpire.creator.sidebar.SidebarViewStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MenuDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    translationStore: TranslationStore? = null,
    tokenStore: SecureTokenStore? = null,
    cartCount: Int = 0,
    onCategoryClick: ((title: String, handle: String, productType: String?) -> Unit)? = null,
    onExternalUrl: ((url: String) -> Unit)? = null,
    onHomeClick: () -> Unit = {},
    onCartClick: () -> Unit = {},
    onFavoritesClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onVouchersClick: (VoucherModalTab) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val context = LocalContext.current
    val viewStore = remember { SidebarViewStore(context) }
    val personalStore = remember { ShopSidebarPersonalizationStore(context) }
    var viewMode by remember { mutableStateOf(viewStore.getViewMode()) }

    LaunchedEffect(Unit) {
        viewMode = viewStore.getViewMode()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        CompositionLocalProvider(LocalTranslationStore provides translationStore) {
            val t = (translationStore ?: LocalTranslationStore.current)?.let { store ->
                { k: String, d: String -> store.t(k, d) }
            } ?: { _: String, d: String -> d }

            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val drawerWidthPx = with(density) { maxWidth.toPx() }

                var isEntered by remember { mutableStateOf(false) }
                var isExiting by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    isEntered = true
                }

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

                MenuDrawerInteractiveRoot(
                    onDimClick = { doDismiss() },
                    offsetXPx = offsetXPx,
                    viewMode = viewMode,
                    onViewModeChange = {
                        viewMode = it
                        viewStore.setViewMode(it)
                    },
                    onCloseClick = { doDismiss() },
                    cartCount = cartCount,
                    t = t,
                    tokenStore = tokenStore,
                    personalStore = personalStore,
                    onHomeClick = { onHomeClick(); doDismiss() },
                    onSearchClick = { onSearchClick(); doDismiss() },
                    onFavoritesClick = { onFavoritesClick(); doDismiss() },
                    onCartClick = { onCartClick(); doDismiss() },
                    onAccountClick = { onAccountClick(); doDismiss() },
                    onCategoryClick = onCategoryClick,
                    onExternalUrl = onExternalUrl,
                    dismissDrawer = { doDismiss() },
                    onVouchersClick = { tab -> onVouchersClick(tab); doDismiss() }
                )
            }
        }
    }
}

private fun memoryPreferences(memory: JSONObject?): JSONObject {
    if (memory == null) return JSONObject()
    val prefRaw = memory.opt("preferences") ?: return JSONObject()
    return when (prefRaw) {
        is String ->
            prefRaw.trim().takeIf { it.isNotEmpty() }?.let {
                try {
                    JSONObject(it)
                } catch (_: Exception) {
                    JSONObject()
                }
            } ?: JSONObject()

        is JSONObject -> prefRaw
        else -> JSONObject()
    }
}

private fun ParsedNavItem.squashDuplicateParents(): ParsedNavItem {
    var cur = this
    while (cur.links.size == 1 && cur.links.first().handle == cur.handle) {
        val inner = cur.links.first()
        cur = copy(url = inner.url.ifBlank { cur.url }, links = inner.links)
    }
    return copy(links = cur.links.map { it.squashDuplicateParents() })
}

private fun absolutizeShopUrl(raw: String): String {
    val u = raw.trim()
    return when {
        u.isEmpty() -> ""
        u.startsWith("//") -> "https:$u"
        u.startsWith("/") -> "https://www.eazpire.com$u"
        Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(u) -> u
        else -> u
    }
}

private fun collectionHandleFromAbsoluteUrl(abs: String): String? {
    if (abs.isBlank()) return null
    try {
        val path = Uri.parse(abs).path ?: return null
        val m = Regex("/collections/([^/?#]+)", RegexOption.IGNORE_CASE).find(path) ?: return null
        return m.groupValues[1].trim().takeIf { it.isNotEmpty() }?.lowercase()
    } catch (_: Exception) {
        return null
    }
}

private fun firstProductTypeFromUrl(abs: String): String? {
    if (!abs.contains("filter")) return null
    return try {
        val uri = Uri.parse(abs)
        val direct = uri.getQueryParameter("filter.p.product_type")
        if (!direct.isNullOrBlank()) {
            return URLDecoder.decode(direct, StandardCharsets.UTF_8.name()).trim().takeIf { it.isNotEmpty() }
        }
        val needle = Regex("filter\\.p\\.product_type=([^&]+)")
        val qStr = uri.encodedQuery ?: abs
        val enc = needle.find(qStr)?.groupValues?.getOrNull(1) ?: return null
        try {
            URLDecoder.decode(enc, StandardCharsets.UTF_8.name()).trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            enc.replace('+', ' ').takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        null
    }
}

private fun openParsedNavLeaf(
    item: ParsedNavItem,
    onCategoryClick: ((title: String, handle: String, productType: String?) -> Unit)?,
    onExternalUrl: ((url: String) -> Unit)?,
    context: android.content.Context,
    dismiss: () -> Unit
) {
    val abs = absolutizeShopUrl(item.url)
    val title = item.title
    val handle = collectionHandleFromAbsoluteUrl(abs)
    val pt = firstProductTypeFromUrl(abs)

    if (handle != null && onCategoryClick != null) {
        onCategoryClick(title, handle, pt)
        dismiss()
        return
    }

    if (abs.startsWith("/collections/")) {
        val h =
            Regex("/collections/([^/?#]+)", RegexOption.IGNORE_CASE).find(abs)?.groupValues?.getOrNull(1)?.lowercase()
        if (!h.isNullOrEmpty() && onCategoryClick != null) {
            onCategoryClick(title, h, pt)
            dismiss()
            return
        }
    }

    if (abs.isNotBlank()) {
        if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(abs)) {
            if (onExternalUrl != null) {
                onExternalUrl(abs)
            } else {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(abs)))
                } catch (_: Exception) {}
            }
            dismiss()
        }
    }
}

private fun openNavAbsolute(
    absoluteUrl: String,
    fallbackLabel: String,
    onCategoryClick: ((title: String, handle: String, productType: String?) -> Unit)?,
    onExternalUrl: ((url: String) -> Unit)?,
    context: android.content.Context,
    dismiss: () -> Unit
) {
    val abs = absolutizeShopUrl(absoluteUrl).ifBlank { return }
    val handle = collectionHandleFromAbsoluteUrl(abs)
    val pt = firstProductTypeFromUrl(abs)
    if (handle != null && onCategoryClick != null) {
        onCategoryClick(fallbackLabel, handle, pt)
        dismiss()
        return
    }
    if (onExternalUrl != null) onExternalUrl(abs)
    else {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(abs)))
        } catch (_: Exception) {}
    }
    dismiss()
}

private fun movableOrderAfterSwap(
    sections: List<SidebarGridSection>,
    id: String,
    delta: Int
): List<String>? {
    val cur = sections.mapNotNull { ShopSidebarLayoutEngine.draggableSectionId(it) }.toMutableList()
    val ix = cur.indexOf(id)
    if (ix < 0) return null
    val j = ix + delta
    if (j < 0 || j >= cur.size) return null
    val tmp = cur[ix]
    cur[ix] = cur[j]
    cur[j] = tmp
    return cur
}

private fun SidebarHiddenState.totalHiddenBadge(): Int =
    containers.size + categories.size + midcategories.size

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuDrawerInteractiveRoot(
    onDimClick: () -> Unit,
    offsetXPx: Float,
    viewMode: SidebarViewMode,
    onViewModeChange: (SidebarViewMode) -> Unit,
    onCloseClick: () -> Unit,
    cartCount: Int,
    t: (String, String) -> String,
    tokenStore: SecureTokenStore?,
    personalStore: ShopSidebarPersonalizationStore,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onCartClick: () -> Unit,
    onAccountClick: () -> Unit,
    onCategoryClick: ((title: String, handle: String, productType: String?) -> Unit)?,
    onExternalUrl: ((url: String) -> Unit)?,
    dismissDrawer: () -> Unit,
    onVouchersClick: (VoucherModalTab) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ownerId = remember(tokenStore) { tokenStore?.getOwnerId()?.trim().orEmpty() }
    val api = remember(tokenStore) {
        CreatorApi(jwt = tokenStore?.getJwt())
    }

    var greetingName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tokenStore, ownerId) {
        if (ownerId.isBlank()) {
            greetingName = null
            return@LaunchedEffect
        }
        try {
            val resp = withContext(Dispatchers.IO) { api.getCustomerProfile(ownerId) }
            if (resp.optBoolean("ok", false)) {
                val profile = resp.optJSONObject("profile")
                greetingName = profile?.optString("first_name", "")?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {}
    }

    var mainMenu by remember { mutableStateOf<ParsedMenu?>(null) }
    var navLoading by remember { mutableStateOf(false) }
    var navError by remember { mutableStateOf<String?>(null) }

    var hidden by remember { mutableStateOf(personalStore.loadHidden()) }
    var catalogPrefs by remember { mutableStateOf<ProductCatalogPreferences?>(null) }
    var eyeRevealHidden by remember { mutableStateOf(personalStore.getEyeRevealHiddenItems()) }
    var sectionOrder by remember { mutableStateOf(personalStore.loadSectionOrder()) }

    var memoryFlushJob by remember { mutableStateOf<Job?>(null) }

    fun persistHidden(state: SidebarHiddenState) {
        hidden = state
        personalStore.saveHidden(state)
        memoryFlushJob?.cancel()
        if (ownerId.isBlank()) return
        memoryFlushJob =
            scope.launch {
                delay(450)
                withContext(Dispatchers.IO) {
                    try {
                        api.postEazyMemory(ownerId, JSONObject().put("sidebar_hidden", state.toJsonObject()))
                    } catch (_: Exception) {}
                }
            }
    }

    fun persistSectionOrder(ids: List<String>) {
        sectionOrder = ids
        personalStore.saveSectionOrder(ids)
        memoryFlushJob?.cancel()
        if (ownerId.isBlank()) return
        memoryFlushJob =
            scope.launch {
                delay(450)
                withContext(Dispatchers.IO) {
                    try {
                        val prefs =
                            JSONObject()
                                .put("sidebar_hidden", hidden.toJsonObject())
                                .put("sidebar_order", JSONArray(ids))
                        api.postEazyMemory(ownerId, prefs)
                    } catch (_: Exception) {}
                }
            }
    }

    LaunchedEffect(ownerId) {
        navLoading = true
        navError = null
        try {
            var local = personalStore.loadHidden()
            sectionOrder = personalStore.loadSectionOrder()
            if (ownerId.isNotBlank()) {
                try {
                    val memResp = withContext(Dispatchers.IO) { api.getEazyMemory(ownerId) }
                    if (memResp.optBoolean("ok", false)) {
                        val prefs = memoryPreferences(memResp.optJSONObject("memory"))
                        val shObj = prefs.optJSONObject("sidebar_hidden")
                        local = SidebarHiddenState.mergePreferRemote(local, shObj)
                    }
                } catch (_: Exception) {}
            }
            hidden = local
            personalStore.saveHidden(local)

            if (ownerId.isNotBlank()) {
                try {
                    val catResp =
                        withContext(Dispatchers.IO) { api.getProductCatalogPreferences(ownerId) }
                    if (catResp.optBoolean("ok", false)) {
                        catalogPrefs =
                            ProductCatalogPreferences.fromJson(catResp.optJSONObject("preferences"))
                    }
                } catch (_: Exception) {}
            }

            val navResp = withContext(Dispatchers.IO) { api.getShopNavigation() }
            if (!navResp.optBoolean("ok", false)) {
                navError =
                    navResp.optString("message", "")
                        .ifBlank { t("eaz.sidebar.menu_reload", "Could not load menu") }
                mainMenu = null
                return@LaunchedEffect
            }
            val (main, _) = ShopSidebarMenuParser.parseMenusResponse(navResp)
            mainMenu = main
        } catch (e: Exception) {
            navError = e.message ?: t("eaz.sidebar.menu_reload", "Could not load menu")
            mainMenu = null
        } finally {
            navLoading = false
        }
    }

    val (_, listRootsRaw, sections) =
        remember(mainMenu, sectionOrder) {
            ShopSidebarLayoutEngine.buildGridSections(mainMenu, null, false, sectionOrder)
        }
    val listRoots = remember(listRootsRaw) { listRootsRaw.map { it.squashDuplicateParents() } }

    var collectionProductCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(listRoots) {
        val handles = SidebarNavVisuals.collectCollectionHandles(listRoots)
        if (handles.isEmpty()) {
            collectionProductCounts = emptyMap()
            return@LaunchedEffect
        }
        val apiProducts = ShopifyProductsApi()
        val loaded = mutableMapOf<String, Int>()
        handles.chunked(8).forEach { batch ->
            coroutineScope {
                batch
                    .map { handle ->
                        async(Dispatchers.IO) {
                            handle to apiProducts.getCollectionProductCount(handle)
                        }
                    }.awaitAll()
                    .forEach { (handle, count) ->
                        count?.let { loaded[handle] = it }
                    }
            }
        }
        collectionProductCounts = loaded.toMap()
    }

    /** Web parity ([theme/assets/eaz-redesign-sidebar.js]): hidden restore strip when eye is “open” and mid/cat items are hidden */
    val showHiddenRestorePanel = eyeRevealHidden && hidden.hiddenCategoryBadgeCount() > 0

    val listExpandedMap = remember { mutableStateMapOf<String, Boolean>() }
    val tileExpandMap = remember { mutableStateMapOf<String, Boolean>() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { onDimClick() }
        )

        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(offsetXPx.roundToInt(), 0) }
                    .background(Color(0xFFFEF5ED))
        ) {
            MenuDrawerHeaderShell(
                greetingName = greetingName,
                viewMode = viewMode,
                onViewModeToggle = {
                    val next =
                        if (viewMode == SidebarViewMode.Grid) SidebarViewMode.List else SidebarViewMode.Grid
                    onViewModeChange(next)
                },
                eyeRevealHidden = eyeRevealHidden,
                onEyeRevealToggle = {
                    eyeRevealHidden = !eyeRevealHidden
                    personalStore.setEyeRevealHiddenItems(eyeRevealHidden)
                },
                eyeBadgeCount = hidden.hiddenCategoryBadgeCount(),
                t = t,
                onClose = onCloseClick
            )

            AnimatedVisibility(visible = showHiddenRestorePanel) {
                SidebarHiddenRestorePanel(
                    hidden = hidden,
                    t = t,
                    onDismiss = null,
                    onRestoreContainer = { persistHidden(hidden.removeContainer(it)) },
                    onRestoreMid = { persistHidden(hidden.removeMid(it)) },
                    onRestoreCat = { persistHidden(hidden.removeCategory(it)) }
                )
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
            ) {
                when {
                    navLoading -> {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(Modifier.size(32.dp), color = EazColors.Orange)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                t("eaz.sidebar.menu_loading", "Loading navigation…"),
                                style = MaterialTheme.typography.bodySmall,
                                color = EazColors.TextSecondary
                            )
                        }
                    }
                    navError != null -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                navError ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        navLoading = true
                                        navError = null
                                        try {
                                            val navResp = withContext(Dispatchers.IO) { api.getShopNavigation() }
                                            if (navResp.optBoolean("ok", false)) {
                                                val (main, _) =
                                                    ShopSidebarMenuParser.parseMenusResponse(navResp)
                                                mainMenu = main
                                                navError = null
                                            } else {
                                                navError =
                                                    navResp.optString("message", "").ifBlank {
                                                        t(
                                                            "eaz.sidebar.menu_reload",
                                                            "Could not load menu"
                                                        )
                                                    }
                                            }
                                        } catch (e: Exception) {
                                            navError = e.message
                                        } finally {
                                            navLoading = false
                                        }
                                    }
                                }
                            ) {
                                Text(t("eaz.sidebar.menu_reload", "Reload menu"))
                            }
                        }
                    }
                    else ->
                        when (viewMode) {
                            SidebarViewMode.Grid ->
                                SidebarDrawerGridEngine(
                                    sections = sections,
                                    hidden = hidden,
                                    catalog = catalogPrefs,
                                    eyeReveal = eyeRevealHidden,
                                    t = t,
                                    ownerId = ownerId,
                                    api = api,
                                    persistHidden = { persistHidden(it) },
                                    onMoveSection = { id, delta ->
                                        movableOrderAfterSwap(sections, id, delta)
                                            ?.let { persistSectionOrder(it) }
                                    },
                                    onCategoryClick = onCategoryClick,
                                    onExternalUrl = onExternalUrl,
                                    dismiss = dismissDrawer,
                                    context = context,
                                    onVouchersClickFull = onVouchersClick,
                                    collapsedMap = tileExpandMap,
                                )

                            SidebarViewMode.List ->
                                Column(Modifier.fillMaxWidth()) {
                                    listRoots.forEach { root ->
                                        MenuDrawerRecursiveListItems(
                                            item = root,
                                            depth = 0,
                                            expanded = listExpandedMap,
                                            path = "r",
                                            t = t,
                                            collectionProductCounts = collectionProductCounts,
                                            onLeafClick = {
                                                openParsedNavLeaf(it, onCategoryClick, onExternalUrl, context, dismissDrawer)
                                            },
                                        )
                                    }
                                }
                        }
                }
            }

            MenuDrawerFooter(
                cartCount = cartCount,
                t = t,
                onHomeClick = onHomeClick,
                onSearchClick = onSearchClick,
                onFavoritesClick = onFavoritesClick,
                onCartClick = onCartClick,
                onAccountClick = onAccountClick
            )
        }
    }
}

@Composable
private fun MenuDrawerHeaderShell(
    greetingName: String?,
    viewMode: SidebarViewMode,
    onViewModeToggle: () -> Unit,
    eyeRevealHidden: Boolean,
    onEyeRevealToggle: () -> Unit,
    eyeBadgeCount: Int,
    t: (String, String) -> String,
    onClose: () -> Unit,
) {
    val greetingText =
        greetingName?.let { n ->
            "${t("eaz.sidebar.drawer_hello", "Hello,")} $n".trim()
        }
            ?: t("eaz.sidebar.greeting_guest", "Hello!")

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(EazColors.Orange)
                .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = t("eaz.topbar.profile", "Profile"),
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = greetingText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color.White.copy(alpha = 0.22f), shape = RoundedCornerShape(8.dp)) {
                Row {
                    IconButton(onClick = { if (viewMode != SidebarViewMode.Grid) onViewModeToggle() }) {
                        Icon(
                            Icons.Default.GridView,
                            contentDescription = t("eaz.sidebar.grid_view", "Grid view"),
                            tint =
                                if (viewMode == SidebarViewMode.Grid) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.55f)
                                },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    IconButton(onClick = { if (viewMode != SidebarViewMode.List) onViewModeToggle() }) {
                        Icon(
                            Icons.Default.List,
                            contentDescription = t("eaz.sidebar.list_view", "List view"),
                            tint =
                                if (viewMode == SidebarViewMode.List) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.55f)
                                },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = onEyeRevealToggle) {
                    Icon(
                        imageVector =
                            if (eyeRevealHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = t("eaz.sidebar.eye_toggle", "Toggle hidden items"),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (eyeBadgeCount > 0) {
                    Text(
                        text = if (eyeBadgeCount < 99) "$eyeBadgeCount" else "99+",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-4).dp)
                                .clip(CircleShape)
                                .background(EazColors.OrangeDark)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription =
                        t("accessibility.close_dialog", "Close dialog"),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SidebarHiddenRestorePanel(
    hidden: SidebarHiddenState,
    t: (String, String) -> String,
    onDismiss: (() -> Unit)?,
    onRestoreContainer: (String) -> Unit,
    onRestoreMid: (String) -> Unit,
    onRestoreCat: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF2EDE6))
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically
        ) {
            Text(
                text =
                    "${t("eaz.sidebar.hidden", "Hidden")} (${hidden.hiddenCategoryBadgeCount()})",
                fontWeight = FontWeight.SemiBold
            )
            if (onDismiss != null) {
                TextButton(onClick = onDismiss) {
                    Text(t("accessibility.close_dialog", "Close dialog"))
                }
            }
        }
        if (hidden.containers.isEmpty() && hidden.midcategories.isEmpty() && hidden.categories.isEmpty()) {
            Text(
                text = t("eaz.sidebar.no_hidden_yet", "No hidden rows."),
                style = MaterialTheme.typography.bodySmall,
                color = EazColors.TextSecondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            return
        }
        hidden.containers.forEach { id ->
            TextButton(onClick = { onRestoreContainer(id) }) {
                Text("${t("eaz.sidebar.restore_item", "Show again")}: $id", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        hidden.midcategories.forEach { id ->
            TextButton(onClick = { onRestoreMid(id) }) {
                Text("${t("eaz.sidebar.restore_item", "Show again")}: $id")
            }
        }
        hidden.categories.forEach { id ->
            TextButton(onClick = { onRestoreCat(id) }) {
                Text("${t("eaz.sidebar.restore_item", "Show again")}: $id")
            }
        }
    }
}

@Composable
private fun MenuDrawerRecursiveListItems(
    item: ParsedNavItem,
    depth: Int,
    expanded: SnapshotStateMap<String, Boolean>,
    path: String,
    t: (String, String) -> String,
    collectionProductCounts: Map<String, Int>,
    onLeafClick: (ParsedNavItem) -> Unit,
) {
    val key = "${path}_${depth}_${item.handle}_${item.title}"
    val branch = item.links.isNotEmpty()
    val open = expanded[key] ?: (depth == 0 && branch)
    val padStart = (16 + depth * 14).dp
    val handleKey = item.handle.ifBlank { SidebarNavVisuals.handleFromNavUrl(item.url).orEmpty() }
    val collectionHandle = SidebarNavVisuals.handleFromNavUrl(item.url)
    val productCount = collectionHandle?.let { collectionProductCounts[it] }
    val menuEmoji = SidebarNavVisuals.emojiForHandle(handleKey.ifBlank { collectionHandle.orEmpty() })
    val titleStyle =
        if (depth == 0) {
            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
        } else {
            MaterialTheme.typography.titleSmall.copy(fontWeight = if (branch) FontWeight.SemiBold else FontWeight.Normal)
        }

    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = padStart, end = 12.dp),
        ) {
            Divider(color = Color(0xFFDED5CA), thickness = 0.5.dp)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 1.dp)
                        .height(0.5.dp)
                        .background(Color.White.copy(alpha = 0.55f)),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        when {
                            !branch -> onLeafClick(item)
                            else -> expanded[key] = !open
                        }
                    }
                    .padding(start = padStart, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (item.title.isBlank()) item.handle else item.title,
                style = titleStyle,
                color = EazColors.TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (productCount != null && productCount > 0) {
                Text(
                    text = productCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = EazColors.TextSecondary,
                )
            }
            Text(
                text = menuEmoji,
                style = MaterialTheme.typography.titleMedium,
            )
            if (branch) {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription =
                        if (open) {
                            t("eaz.sidebar.accordion_collapse", "Collapse submenu")
                        } else {
                            t("eaz.sidebar.accordion_expand", "Expand submenu")
                        },
                    Modifier
                        .size(22.dp)
                        .rotate(if (open) 180f else 0f),
                    tint = EazColors.TextSecondary,
                )
            }
        }

        AnimatedVisibility(open && branch) {
            Column {
                item.links.forEach { child ->
                    MenuDrawerRecursiveListItems(
                        item = child,
                        depth = depth + 1,
                        expanded = expanded,
                        path = key,
                        t = t,
                        collectionProductCounts = collectionProductCounts,
                        onLeafClick = onLeafClick,
                    )
                }
            }
        }
    }
}

/** Matches `eaz-redesign-sidebar.css` `.sb-ziel-card__header--*` gradients */
private fun audienceCardHeaderBrush(norm: String): Brush {
    val (a, b) =
        when (norm) {
            "men" -> Color(0xFF4A8FCE) to Color(0xFF2668A8)
            "kids" -> Color(0xFFA8D8EA) to Color(0xFF62B6CB)
            "toddler" -> Color(0xFFF8C8DC) to Color(0xFFF4A4C0)
            else -> Color(0xFFF9A03F) to Color(0xFFF97316) // women + default
        }
    return Brush.linearGradient(colors = listOf(a, b))
}

/** Matches `.sb-aud-panel[data-active-aud]` header gradients on web */
private fun audiencePanelHeaderBrush(norm: String): Brush {
    val (a, b) =
        when (norm) {
            "men" -> Color(0xFF4A8FCE) to Color(0xFF2668A8)
            "kids" -> Color(0xFF62B6CB) to Color(0xFF3F9EB7)
            "toddler" -> Color(0xFFF4A4C0) to Color(0xFFEA89AE)
            else -> Color(0xFFF9A03F) to Color(0xFFF97316)
        }
    return Brush.linearGradient(colors = listOf(a, b))
}

private fun normalizedAudiencePaletteKey(raw: String): String {
    val h = raw.lowercase()
    return when {
        h in setOf("women", "female", "frauen") -> "women"
        h in setOf("men", "male", "manner", "männer") -> "men"
        h in setOf("kids", "kinder") -> "kids"
        h in setOf("toddler", "baby", "babys") -> "toddler"
        h == "men" || h == "women" || h == "kids" || h == "toddler" -> h
        else -> "women"
    }
}

@Composable
private fun AudienceZielCard(
    card: AudienceCard,
    isExpanded: Boolean,
    hidden: SidebarHiddenState,
    eyeReveal: Boolean,
    t: (String, String) -> String,
    persistHidden: (SidebarHiddenState) -> Unit,
    onToggleExpand: () -> Unit,
    onOpenBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hidMid = hidden.midcategories.contains(card.midId)
    if (hidMid && !eyeReveal) {
        Spacer(Modifier.fillMaxWidth().height(48.dp))
        return
    }

    val norm = normalizedAudiencePaletteKey(card.audHandle)
    val label =
        if (card.navTitleKey.isNotBlank()) {
            t(card.navTitleKey, card.title).uppercase()
        } else {
            card.title.uppercase()
        }

    Surface(
        modifier =
            modifier
                .alpha(if (hidMid && eyeReveal) 0.45f else 1f)
                .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xD9FFFFFF),
        shadowElevation = 1.dp,
        border =
            if (isExpanded) {
                BorderStroke(2.dp, Color.White)
            } else {
                null
            },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(audienceCardHeaderBrush(norm))
                    .padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = { persistHidden(hidden.toggledMid(card.midId)) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector =
                        if (hidMid) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                    contentDescription = t("eaz.sidebar.toggle_row", "Toggle row visibility"),
                    modifier = Modifier.size(14.dp),
                    tint =
                        if (hidMid) {
                            Color(0xFFFCA5A5)
                        } else {
                            Color.White.copy(alpha = 0.45f)
                        },
                )
            }
            Text(
                text = card.emoji,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = label,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.35.sp,
                    ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                modifier =
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenBrowse),
            )
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.16f))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.24f),
                            shape = RoundedCornerShape(6.dp),
                        ).clickable(onClick = onToggleExpand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription =
                        if (isExpanded) {
                            t("eaz.sidebar.accordion_collapse", "Collapse submenu")
                        } else {
                            t("eaz.sidebar.accordion_expand", "Expand submenu")
                        },
                    tint = Color.White.copy(alpha = 0.95f),
                    modifier = Modifier.size(16.dp).rotate(if (isExpanded) 180f else 0f),
                )
            }
        }
    }
}

private fun isMenuPathHidden(
    pathId: String,
    hidden: SidebarHiddenState,
    catalog: ProductCatalogPreferences?,
): Boolean {
    if (catalog != null) return !catalog.isPathVisible(pathId)
    return hidden.categories.contains(pathId) ||
        hidden.midcategories.contains(pathId) ||
        hidden.containers.contains(pathId)
}

@Composable
private fun AudienceInlinePanel(
    body: AudiencePanelBody,
    activeCard: AudienceCard,
    hidden: SidebarHiddenState,
    catalog: ProductCatalogPreferences?,
    eyeReveal: Boolean,
    t: (String, String) -> String,
    persistHidden: (SidebarHiddenState) -> Unit,
    onLineClick: (AudienceDetailLine) -> Unit,
    onCategoryTitleClick: (AudienceCategoryColumn) -> Unit,
) {
    val norm = normalizedAudiencePaletteKey(body.audHandle)
    var expandedCatKey by remember(body.audHandle) { mutableStateOf<String?>(null) }

    val titleTranslated =
        if (activeCard.navTitleKey.isNotBlank()) {
            t(activeCard.navTitleKey, activeCard.title)
        } else {
            activeCard.title
        }
    val activeWord = t("eaz.sidebar.audience_active", "Active")
    val bannerText = "${activeCard.emoji} ${titleTranslated.trim()} ${activeWord.trim()}".trim()

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xEFFFFFFF),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.06f)),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(audiencePanelHeaderBrush(norm))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = bannerText.uppercase(),
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                        ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                body.categories.forEach { col ->
                    val catHiddenWhole = isMenuPathHidden(col.catHidePrefix, hidden, catalog)
                    if (catHiddenWhole && !eyeReveal) return@forEach

                    val catExpanded = expandedCatKey == col.rowKey
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.04f)),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 2.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                            ) {
                                IconButton(
                                    onClick = {
                                        persistHidden(hidden.toggledCategory(col.catHidePrefix))
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector =
                                            if (isMenuPathHidden(col.catHidePrefix, hidden, catalog)) {
                                                Icons.Default.VisibilityOff
                                            } else {
                                                Icons.Default.Visibility
                                            },
                                        contentDescription =
                                            t("eaz.sidebar.toggle_row", "Toggle row visibility"),
                                        tint = Color(0xFFB8956F).copy(alpha = 0.35f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Text(
                                    text =
                                        if (col.navTitleKey.isNotBlank()) {
                                            t(col.navTitleKey, col.title)
                                        } else {
                                            col.title
                                        },
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .alpha(
                                                if (catHiddenWhole && eyeReveal) {
                                                    0.45f
                                                } else {
                                                    1f
                                                },
                                            ).clickable { onCategoryTitleClick(col) },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = EazColors.TextPrimary,
                                )
                                IconButton(
                                    onClick = {
                                        expandedCatKey =
                                            if (expandedCatKey == col.rowKey) {
                                                null
                                            } else {
                                                col.rowKey
                                            }
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription =
                                            if (catExpanded) {
                                                t(
                                                    "eaz.sidebar.accordion_collapse",
                                                    "Collapse submenu",
                                                )
                                            } else {
                                                t(
                                                    "eaz.sidebar.accordion_expand",
                                                    "Expand submenu",
                                                )
                                            },
                                        tint = Color(0xFFC8AE9A),
                                        modifier =
                                            Modifier
                                                .size(18.dp)
                                                .rotate(if (catExpanded) 180f else 0f),
                                    )
                                }
                            }
                            AnimatedVisibility(visible = catExpanded && col.lines.isNotEmpty()) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                                ) {
                                    Divider(
                                        color = Color.Black.copy(alpha = 0.06f),
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                    col.lines.forEach { line ->
                                        val lnHidden =
                                            isMenuPathHidden(line.hideCatId, hidden, catalog)
                                        if (lnHidden && !eyeReveal) return@forEach
                                        Row(
                                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    persistHidden(
                                                        hidden.toggledCategory(line.hideCatId),
                                                    )
                                                },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    imageVector =
                                                        if (lnHidden) {
                                                            Icons.Default.VisibilityOff
                                                        } else {
                                                            Icons.Default.Visibility
                                                        },
                                                    contentDescription =
                                                        t(
                                                            "eaz.sidebar.toggle_row",
                                                            "Toggle row visibility",
                                                        ),
                                                    modifier = Modifier.size(18.dp),
                                                    tint = Color(0xFFB8956F).copy(alpha = 0.38f),
                                                )
                                            }
                                            Row(
                                                modifier =
                                                    Modifier
                                                        .weight(1f)
                                                        .alpha(
                                                            if (lnHidden && eyeReveal) {
                                                                0.45f
                                                            } else {
                                                                1f
                                                            },
                                                        ).clickable { onLineClick(line) },
                                                verticalAlignment =
                                                    Alignment.CenterVertically,
                                                horizontalArrangement =
                                                    Arrangement.SpaceBetween,
                                            ) {
                                                Text(
                                                    text =
                                                        if (
                                                            line.navTitleKey.isNotBlank()
                                                        ) {
                                                            t(
                                                                line.navTitleKey,
                                                                line.labelRaw,
                                                            )
                                                        } else {
                                                            line.labelRaw
                                                        },
                                                    style =
                                                        MaterialTheme.typography.bodyMedium
                                                            .copy(
                                                                fontWeight = FontWeight.Medium,
                                                            ),
                                                    modifier = Modifier.padding(end = 8.dp),
                                                )
                                                Icon(
                                                    Icons.Default.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint =
                                                        Color(0xFFB8956F).copy(alpha = 0.55f),
                                                    modifier =
                                                        Modifier.size(
                                                            16.dp,
                                                        ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun draggableIdsMemo(sections: List<SidebarGridSection>): List<String> =
    sections.mapNotNull { ShopSidebarLayoutEngine.draggableSectionId(it) }

@Composable
private fun SidebarDrawerGridEngine(
    sections: List<SidebarGridSection>,
    hidden: SidebarHiddenState,
    catalog: ProductCatalogPreferences?,
    eyeReveal: Boolean,
    t: (String, String) -> String,
    ownerId: String,
    api: CreatorApi,
    persistHidden: (SidebarHiddenState) -> Unit,
    onMoveSection: (String, Int) -> Unit,
    onCategoryClick: ((title: String, handle: String, productType: String?) -> Unit)?,
    onExternalUrl: ((url: String) -> Unit)?,
    dismiss: () -> Unit,
    context: android.content.Context,
    onVouchersClickFull: (VoucherModalTab) -> Unit,
    collapsedMap: SnapshotStateMap<String, Boolean>,
) {
    val movable = remember(sections) { draggableIdsMemo(sections) }
    var audienceExpandedAudHandle by remember { mutableStateOf<String?>(null) }

    fun containerHiddenSkip(cid: String): Pair<Boolean, Float> =
        when {
            !hidden.containers.contains(cid) -> false to 1f
            eyeReveal -> false to 0.42f
            else -> true to 1f
        }

    Column(Modifier.fillMaxWidth()) {
        sections.forEach { sec ->
            when (sec) {
                is GutscheineGridSection -> {
                    val id = ShopSidebarConstants.CONTAINER_GUTSCHEINE
                    val (skip, _) = containerHiddenSkip(id)
                    if (skip) return@forEach

                    SidebarSectionChrome(
                        title = t("eaz.sidebar.gift_cards_coupons", "Gift Cards & Coupons"),
                        draggableId = id,
                        movableIds = movable,
                        containerEyeId = id,
                        hidden = hidden,
                        onMoveSection = onMoveSection,
                        persistHidden = persistHidden,
                        contentAlpha = containerHiddenSkip(id).second,
                        t = t,
                    )
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        SidebarGutscheineInline(
                            ownerId = ownerId,
                            api = api,
                            t = t,
                            onOpenWallet = onVouchersClickFull,
                        )
                    }
                }

                is CreatePromoSection -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onCategoryClick?.invoke("", SHOP_MENU_CREATE_HANDLE, null)
                                dismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        ShopCreateNavPill {
                            Text(
                                t("creator.shop_create_product.entry", "Create"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }

                is AudienceSidebarSection -> {
                    val id = ShopSidebarConstants.CONTAINER_AUDIENCE
                    val (skip, alphaBg) = containerHiddenSkip(id)
                    if (skip) return@forEach
                    SidebarSectionChrome(
                        title = t("creator.shop_create_product.catalog_filter_audience", "Audience"),
                        draggableId = id,
                        movableIds = movable,
                        containerEyeId = id,
                        hidden = hidden,
                        onMoveSection = onMoveSection,
                        persistHidden = persistHidden,
                        contentAlpha = alphaBg,
                        t = t,
                    )
                    Column(
                        Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .alpha(alphaBg),
                    ) {
                        sec.cards.chunked(2).forEach { rowCards ->
                            Row(
                                Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowCards.forEach { card ->
                                    Box(Modifier.weight(1f)) {
                                        AudienceZielCard(
                                            card = card,
                                            isExpanded =
                                                audienceExpandedAudHandle ==
                                                    card.audHandle,
                                            hidden = hidden,
                                            eyeReveal = eyeReveal,
                                            t = t,
                                            persistHidden = persistHidden,
                                            onToggleExpand = {
                                                audienceExpandedAudHandle =
                                                    if (
                                                        audienceExpandedAudHandle ==
                                                            card.audHandle
                                                    ) {
                                                        null
                                                    } else {
                                                        card.audHandle
                                                    }
                                            },
                                            onOpenBrowse = {
                                                openNavAbsolute(
                                                    card.collectionUrl,
                                                    card.title,
                                                    onCategoryClick,
                                                    onExternalUrl,
                                                    context,
                                                    dismiss,
                                                )
                                            },
                                        )
                                    }
                                }
                                if (rowCards.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        val ah = audienceExpandedAudHandle
                        AnimatedVisibility(
                            visible =
                                ah != null &&
                                    sec.panelBodies.any { it.audHandle == ah } &&
                                    sec.cards.any { it.audHandle == ah },
                        ) {
                            val panel =
                                ah?.let { h ->
                                    sec.panelBodies.firstOrNull { it.audHandle == h }
                                }
                            val bannerCard =
                                ah?.let { h ->
                                    sec.cards.firstOrNull { it.audHandle == h }
                                }
                            if (panel != null && bannerCard != null) {
                                AudienceInlinePanel(
                                    body = panel,
                                    activeCard = bannerCard,
                                    hidden = hidden,
                                    catalog = catalog,
                                    eyeReveal = eyeReveal,
                                    t = t,
                                    persistHidden = persistHidden,
                                    onLineClick = { line ->
                                        openNavAbsolute(
                                            line.url,
                                            line.labelRaw,
                                            onCategoryClick,
                                            onExternalUrl,
                                            context,
                                            dismiss,
                                        )
                                    },
                                    onCategoryTitleClick = { col ->
                                        openNavAbsolute(
                                            col.titleUrl,
                                            col.title,
                                            onCategoryClick,
                                            onExternalUrl,
                                            context,
                                            dismiss,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                is GroupedCategorySection -> {
                    val (skip, alphaBg) = containerHiddenSkip(sec.containerId)
                    if (skip) return@forEach
                    val headingTitle =
                        if (sec.sectionEmoji.isNotBlank()) {
                            "${sec.sectionEmoji.trim()} ${t(sec.sectionTitleKey, sec.sectionTitleKey)}".trim()
                        } else {
                            t(sec.sectionTitleKey, sec.sectionTitleKey)
                        }
                    SidebarSectionChrome(
                        title = headingTitle,
                        draggableId = sec.containerId,
                        movableIds = movable,
                        containerEyeId = sec.containerId,
                        hidden = hidden,
                        onMoveSection = onMoveSection,
                        persistHidden = persistHidden,
                        contentAlpha = alphaBg,
                        t = t,
                    )
                    SidebarCategoryTiles(
                        tiles = sec.tiles,
                        hidden = hidden,
                        eyeReveal = eyeReveal,
                        collapsedMap = collapsedMap,
                        t = t,
                        persistHidden = persistHidden,
                        onExternalUrl = onExternalUrl,
                        onCategoryClick = onCategoryClick,
                        dismiss = dismiss,
                        context = context,
                        modifier = Modifier.padding(horizontal = 12.dp).alpha(alphaBg),
                    )
                }

                is RemainingTopSection -> {
                    val (skip, alphaBg) = containerHiddenSkip(sec.containerId)
                    if (skip) return@forEach
                    SidebarSectionChrome(
                        title =
                            if (sec.navTitleKey.isNotBlank()) {
                                t(sec.navTitleKey, sec.title)
                            } else sec.title,
                        draggableId = sec.containerId,
                        movableIds = movable,
                        containerEyeId = sec.containerId,
                        hidden = hidden,
                        onMoveSection = onMoveSection,
                        persistHidden = persistHidden,
                        contentAlpha = alphaBg,
                        t = t,
                    )

                    Column(Modifier.padding(horizontal = 12.dp).alpha(alphaBg)) {
                        when (val b = sec.body) {
                            is RemainderBody.SingleTrending -> {
                                val midHidden = hidden.midcategories.contains(b.midHideId)
                                if (midHidden && !eyeReveal) return@Column
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .alpha(if (midHidden && eyeReveal) 0.45f else 1f)
                                        .clickable {
                                            openNavAbsolute(
                                                b.url,
                                                b.label,
                                                onCategoryClick,
                                                onExternalUrl,
                                                context,
                                                dismiss,
                                            )
                                        }
                                        .padding(vertical = 10.dp),
                                    Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text =
                                            if (b.navKey.isNotBlank()) t(b.navKey, b.label) else b.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier =
                                            Modifier
                                                .weight(1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    IconButton(onClick = { persistHidden(hidden.toggledMid(b.midHideId)) }) {
                                        Icon(
                                            if (midHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription =
                                                t("eaz.sidebar.toggle_row", "Toggle row visibility"),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }

                            is RemainderBody.Tiles -> {
                                SidebarCategoryTiles(
                                    tiles = b.tiles,
                                    hidden = hidden,
                                    eyeReveal = eyeReveal,
                                    collapsedMap = collapsedMap,
                                    t = t,
                                    persistHidden = persistHidden,
                                    onExternalUrl = onExternalUrl,
                                    onCategoryClick = onCategoryClick,
                                    dismiss = dismiss,
                                    context = context,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarSectionChrome(
    title: String,
    draggableId: String?,
    movableIds: List<String>,
    containerEyeId: String?,
    hidden: SidebarHiddenState,
    onMoveSection: (String, Int) -> Unit,
    persistHidden: (SidebarHiddenState) -> Unit,
    contentAlpha: Float,
    t: (String, String) -> String,
) {
    val ix = draggableId?.let { d -> movableIds.indexOf(d) } ?: -1
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = EazColors.TextPrimary,
            modifier = Modifier.weight(1f).alpha(contentAlpha.coerceIn(0f, 1f)),
        )
        draggableId?.let {
            IconButton(onClick = { onMoveSection(it, -1) }, enabled = ix > 0) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription =
                        t("eaz.sidebar.section_move_up", "Move section up"),
                )
            }
            IconButton(
                onClick = { onMoveSection(it, +1) },
                enabled = ix >= 0 && ix < movableIds.lastIndex,
            ) {
                Icon(Icons.Default.KeyboardArrowDown, t("eaz.sidebar.section_move_down", "Move section down"))
            }
        }
        containerEyeId?.let { cid ->
            val hiddenC = hidden.containers.contains(cid)
            IconButton(onClick = { persistHidden(hidden.toggledContainer(cid)) }) {
                Icon(
                    if (hiddenC) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = t("eaz.sidebar.toggle_row", "Toggle row visibility"),
                    tint = EazColors.TextSecondary,
                )
            }
        }
    }
}


@Composable
private fun SidebarCategoryTiles(
    tiles: List<CategoryTile>,
    hidden: SidebarHiddenState,
    eyeReveal: Boolean,
    collapsedMap: SnapshotStateMap<String, Boolean>,
    t: (String, String) -> String,
    persistHidden: (SidebarHiddenState) -> Unit,
    onExternalUrl: ((url: String) -> Unit)?,
    onCategoryClick: ((title: String, handle: String, productType: String?) -> Unit)?,
    dismiss: () -> Unit,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile ->
                    SidebarCategoryTileSingle(
                        tile = tile,
                        hidden = hidden,
                        eyeReveal = eyeReveal,
                        collapsedMap = collapsedMap,
                        t = t,
                        persistHidden = persistHidden,
                        onExternalUrl = onExternalUrl,
                        onCategoryClick = onCategoryClick,
                        dismiss = dismiss,
                        context = context,
                        Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SidebarCategoryTileSingle(
    tile: CategoryTile,
    hidden: SidebarHiddenState,
    eyeReveal: Boolean,
    collapsedMap: SnapshotStateMap<String, Boolean>,
    t: (String, String) -> String,
    persistHidden: (SidebarHiddenState) -> Unit,
    onExternalUrl: ((url: String) -> Unit)?,
    onCategoryClick: ((title: String, handle: String, productType: String?) -> Unit)?,
    dismiss: () -> Unit,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    val hidMid = hidden.midcategories.contains(tile.midId)
    if (!(hidMid && !eyeReveal)) {
        val expanded = collapsedMap[tile.midId] ?: true

        Surface(
            modifier = modifier.alpha(if (hidMid && eyeReveal) 0.45f else 1f),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
        ) {
            Box(Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = { persistHidden(hidden.toggledMid(tile.midId)) },
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp)
                            .size(32.dp),
                ) {
                    Icon(
                        if (hidMid) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription =
                            t("eaz.sidebar.toggle_row", "Toggle row visibility"),
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFB8956F).copy(alpha = 0.28f),
                    )
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(top = 8.dp, bottom = 10.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (tile.expandable) {
                                    val cur = collapsedMap[tile.midId] ?: true
                                    collapsedMap[tile.midId] = !cur
                                } else if (tile.leafUrl != null) {
                                    openNavAbsolute(
                                        tile.leafUrl,
                                        tile.titleRaw,
                                        onCategoryClick,
                                        onExternalUrl,
                                        context,
                                        dismiss,
                                    )
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text = tile.emoji, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text =
                                (if (tile.navTitleKey.isNotBlank()) {
                                    t(tile.navTitleKey, tile.titleRaw)
                                } else {
                                    tile.titleRaw
                                }).uppercase(Locale.getDefault()),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.3.sp,
                                ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (tile.expandable && tile.expandCells.isNotEmpty()) {
                        if (!(collapsedMap[tile.midId] ?: true)) {
                            TextButton(onClick = { collapsedMap[tile.midId] = true }) {
                                Text(t("eaz.sidebar.show_subcategories", "Show subcategories"))
                            }
                        }
                    }

                    if (expanded && tile.expandable && tile.expandCells.isNotEmpty()) {
                        Divider(Modifier.padding(vertical = 6.dp))
                        tile.expandCells.forEach { cell ->
                            SidebarExpandCellRow(
                                cell = cell,
                                hidden = hidden,
                                eyeReveal = eyeReveal,
                                t = t,
                                persistHidden = persistHidden,
                                onExternalUrl = onExternalUrl,
                                onCategoryClick = onCategoryClick,
                                dismiss = dismiss,
                                context = context,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarExpandCellRow(
    cell: ExpandCell,
    hidden: SidebarHiddenState,
    eyeReveal: Boolean,
    t: (String, String) -> String,
    persistHidden: (SidebarHiddenState) -> Unit,
    onExternalUrl: ((url: String) -> Unit)?,
    onCategoryClick: ((title: String, handle: String, productType: String?) -> Unit)?,
    dismiss: () -> Unit,
    context: android.content.Context,
) {
    val hid = hidden.categories.contains(cell.hideCatId)
    if (!(hid && !eyeReveal)) {
        Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (hid && eyeReveal) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text =
                if (cell.navTitleKey.isNotBlank()) t(cell.navTitleKey, cell.labelRaw) else cell.labelRaw,
            style = MaterialTheme.typography.bodySmall,
            modifier =
                Modifier
                    .weight(1f)
                    .clickable {
                        openNavAbsolute(cell.url, cell.labelRaw, onCategoryClick, onExternalUrl, context, dismiss)
                    },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = { persistHidden(hidden.toggledCategory(cell.hideCatId)) }) {
            Icon(
                if (hid) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = t("eaz.sidebar.toggle_row", "Toggle row visibility"),
                modifier = Modifier.size(18.dp),
            )
        }
    }
    }
}

private fun gutterGiftPurchased(gc: JSONObject): Boolean {
    val reward = gc.optString("gift_card_origin", "") == "reward"
    val sent =
        gc.optBoolean("is_buyer", false) &&
            gc.optJSONObject("email_template")?.optString("status") == "sent"
    return !reward && !sent
}

private fun gutterGiftMoneyLine(gc: JSONObject): String {
    val inner = gc.optJSONObject("gift_card") ?: gc
    val bal = inner.optString("balance", "")
    val cur = inner.optString("currency", "EUR").uppercase()
    val parts = mutableListOf<String>()
    if (bal.isNotBlank()) parts += bal
    if (cur.isNotBlank()) parts += cur
    return parts.joinToString(" ").ifBlank { inner.optString("last_characters", "••••") }
}

@Composable
private fun SidebarGutscheineInline(
    ownerId: String,
    api: CreatorApi,
    t: (String, String) -> String,
    onOpenWallet: (VoucherModalTab) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var giftCards by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var promo by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val giftCardBuyUrl = "https://www.eazpire.com/products/gift-card"

    LaunchedEffect(ownerId) {
        loading = true
        err = null
        giftCards = emptyList()
        promo = null
        if (ownerId.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        try {
            val g =
                withContext(Dispatchers.IO) {
                    api.getCustomerGiftCards(ownerId, AuthConfig.SHOP_DOMAIN)
                }
            val pr = withContext(Dispatchers.IO) { api.getPromoSlots(ownerId) }
            giftCards =
                if (g.optBoolean("ok", false)) {
                    val arr = g.optJSONArray("gift_cards") ?: JSONArray()
                    buildList(arr.length()) {
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            if (gutterGiftPurchased(o)) add(o)
                        }
                    }
                } else emptyList()
            promo = if (pr.optBoolean("ok", false)) pr else null
        } catch (e: Exception) {
            err = e.message
        } finally {
            loading = false
        }
    }

    val couponCount =
        remember(promo) {
            val p = promo ?: return@remember 0
            val slotsTotal = p.optInt("slots_total", 5)
            val active = p.optJSONArray("active") ?: JSONArray()
            (slotsTotal - active.length()).coerceAtLeast(0)
        }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SidebarWalletTabChip(
                modifier = Modifier.weight(1f),
                label = t("eaz.sidebar.my_gift_cards", "My Gift Cards"),
                count = giftCards.size,
                selected = tab == 0,
                onClick = { tab = 0 },
                onCountClick = { onOpenWallet(VoucherModalTab.GIFT_CARDS) },
            )
            SidebarWalletTabChip(
                modifier = Modifier.weight(1f),
                label = t("eaz.sidebar.coupons", "Coupons"),
                count = couponCount,
                selected = tab == 1,
                onClick = { tab = 1 },
                onCountClick = { onOpenWallet(VoucherModalTab.PROMO_CODES) },
            )
            SidebarGiftBuyNowButton(t = t) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(giftCardBuyUrl)))
                } catch (_: Exception) {
                }
            }
        }

        if (ownerId.isBlank()) {
            Text(
                t("eaz.sidebar.wallet_sign_in_hint", "Sign in to see gift cards and coupons here."),
                Modifier.padding(top = 12.dp),
                color = EazColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (loading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        } else if (err != null) {
            Text(err ?: "", modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
        } else if (tab == 0) {
            if (giftCards.isEmpty()) {
                Text(
                    t("eaz.sidebar.no_gift_cards_yet", "No gift cards yet"),
                    Modifier.padding(top = 12.dp),
                    color = EazColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                val visibleCards = if (expanded) giftCards else giftCards.take(1)
                visibleCards.forEachIndexed { index, gc ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF5F0E8))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .weight(1f),
                        ) {
                            Icon(
                                Icons.Default.CardGiftcard,
                                contentDescription = null,
                                tint = EazColors.Orange,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                gutterGiftMoneyLine(gc),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!expanded && index == 0 && giftCards.size > 1) {
                            Text(
                                text =
                                    t("eaz.sidebar.show_more_gift_cards", "Show all ({count})")
                                        .replace("{count}", giftCards.size.toString()),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EazColors.Orange,
                                modifier = Modifier.clickable { expanded = true },
                            )
                        }
                    }
                }
                if (expanded && giftCards.size > 1) {
                    Text(
                        text = t("eaz.sidebar.show_less_gift_cards", "Show less"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = EazColors.Orange,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable { expanded = false },
                    )
                }
            }
        } else {
            val p = promo
            if (p == null || couponCount <= 0) {
                Text(
                    t("eaz.sidebar.no_coupons_available", "No coupons available"),
                    Modifier.padding(top = 12.dp),
                    color = EazColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    t(
                        "creator.voucher_page.promo_slots_counter",
                        "{available} / {total} slots available",
                    ).replace("{available}", couponCount.toString())
                        .replace("{total}", p.optInt("slots_total", 5).toString()),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun RowScope.SidebarWalletTabChip(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onCountClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) EazColors.Orange else EazColors.TextPrimary,
            )
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) EazColors.Orange.copy(alpha = 0.12f) else Color(0xFFF3F4F6))
                        .clickable(onClick = onCountClick)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) EazColors.Orange else EazColors.TextSecondary,
                    )
                }
            }
        }
        Box(
            Modifier
                .padding(top = 6.dp)
                .height(if (selected) 2.dp else 1.dp)
                .fillMaxWidth()
                .background(if (selected) EazColors.Orange else Color(0xFFE8E8E8)),
        )
    }
}

@Composable
private fun SidebarGiftBuyNowButton(t: (String, String) -> String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827), contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            t("eaz.sidebar.gift_card_buy_now", "Buy now"),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MenuDrawerFooter(
    cartCount: Int,
    t: (String, String) -> String,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onCartClick: () -> Unit,
    onAccountClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FooterNavItem(icon = Icons.Default.Home, label = t("eaz.sidebar.nav_home", "Home"), onClick = onHomeClick)
        FooterNavItem(icon = Icons.Default.Search, label = t("eaz.sidebar.nav_search", "Search"), onClick = onSearchClick)
        FooterNavItem(icon = Icons.Default.Favorite, label = t("eaz.sidebar.nav_favorites", "Favorites"), onClick = onFavoritesClick)
        Box(contentAlignment = Alignment.Center) {
            FooterNavItem(icon = Icons.Default.ShoppingCart, label = t("eaz.sidebar.nav_cart", "Cart"), onClick = onCartClick)
            if (cartCount > 0) {
                Text(
                    text = if (cartCount < 100) "$cartCount" else "99+",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 8.dp, y = (-4).dp)
                            .background(EazColors.Orange, CircleShape)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        FooterNavItem(icon = Icons.Default.Person, label = t("eaz.sidebar.nav_account", "Account"), onClick = onAccountClick)
    }
}

@Composable
private fun FooterNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = EazColors.TextPrimary,
            modifier = Modifier.size(24.dp),
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = EazColors.TextPrimary)
    }
}
