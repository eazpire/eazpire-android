package com.eazpire.creator.ui.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.i18n.TranslationStore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.ui.components.GlassCircularFlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.util.DebugLog
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

/** Ref links – same as web community_ref_links_v1 */
private const val REF_LINKS_SETTING_KEY = "community_ref_links_v1"
private const val REF_LINKS_MAX = 5
private const val REF_SLUG_MIN_LEN = 4
private const val REF_SLUG_MAX_LEN = 24
private val REF_SLUG_PATTERN = Regex("^[a-z0-9-]+\$")
private val RESERVED_REF_SLUGS = setOf(
    "admin", "api", "app", "apps", "assets", "auth", "account", "accounts",
    "billing", "checkout", "cart", "collections", "collection", "products", "product",
    "pages", "page", "search", "shop", "store", "creator", "creators",
    "network", "analytics", "community", "settings", "help", "support",
    "blog", "blogs", "news", "contact", "about", "privacy", "terms",
    "policy", "policies", "refund", "shipping", "imprint",
    "login", "logout", "register", "signup", "signin",
    "www", "cdn", "static", "media", "img", "images",
    "favicon.ico", "robots.txt", "sitemap.xml"
)

private data class CommunityTabColorScheme(
    val textPrimary: Color,
    val textSecondary: Color,
    val cardSurface: Color,
    val funnelTrack: Color,
    val sheetContainer: Color,
    val outlineBorder: Color,
    val mutedBg: Color,
    val partnerCardBg: Color
) {
    companion object {
        /** Literal colors — companion init cannot rely on theme accessors in all Kotlin/Compose versions */
        val Light = CommunityTabColorScheme(
            textPrimary = Color(0xCF000000),
            textSecondary = Color(0x66000000),
            cardSurface = Color.White,
            funnelTrack = Color(0x0A000000).copy(alpha = 0.3f),
            sheetContainer = Color.White,
            outlineBorder = Color(0x0A000000),
            mutedBg = Color(0x0A000000).copy(alpha = 0.2f),
            partnerCardBg = Color(0x0FF97316).copy(alpha = 0.3f)
        )
        fun creatorDark() = CommunityTabColorScheme(
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.65f),
            cardSurface = Color(0xFF121A2E),
            funnelTrack = Color.White.copy(alpha = 0.12f),
            sheetContainer = Color(0xFF070B14),
            outlineBorder = Color.White.copy(alpha = 0.14f),
            mutedBg = Color.White.copy(alpha = 0.06f),
            partnerCardBg = Color(0x0FF97316).copy(alpha = 0.35f)
        )
    }
}

private val LocalCommunityColors = compositionLocalOf { CommunityTabColorScheme.Light }

/** Resolves `creator.community.*` (and related) keys; default returns English fallback. */
private val LocalCommunityTranslate = compositionLocalOf<(String, String) -> String> { { _, d -> d } }

private data class RefLink(val id: String, val name: String, val slug: String, val description: String = "")

private fun buildNamedRefUrl(baseUrl: String, slug: String): String {
    if (slug.isBlank()) return baseUrl
    return try {
        val u = java.net.URI(baseUrl).toURL()
        val portSuffix = if (u.port in 1..65535 && u.port != 80 && u.port != 443) ":${u.port}" else ""
        "${u.protocol}://${u.host}$portSuffix/${slug.lowercase()}"
    } catch (_: Exception) {
        baseUrl
    }
}

private fun slugifyLabel(name: String): String = name
    .lowercase()
    .replace(Regex("[^a-z0-9\\s-]"), "")
    .trim()
    .replace(Regex("\\s+"), "-")
    .replace(Regex("-+"), "-")
    .take(40)

private fun isValidRefSlug(slug: String): Boolean {
    val s = slug.lowercase()
    return s.length in REF_SLUG_MIN_LEN..REF_SLUG_MAX_LEN && REF_SLUG_PATTERN.matches(s)
}

private fun isReservedRefSlug(slug: String): Boolean = RESERVED_REF_SLUGS.contains(slug.lowercase())

/** Canonical English label from web/API; used for matching stored JSON, not for display. */
private const val EN_MAIN_LINK_LABEL = "Main link"

private fun displayRefLinkName(storedName: String, t: (String, String) -> String): String =
    if (storedName.equals(EN_MAIN_LINK_LABEL, ignoreCase = true)) {
        t("creator.community.ref_link_main_default", EN_MAIN_LINK_LABEL)
    } else {
        storedName
    }

private fun normalizeStoredRefLinks(raw: String?, defaultLinkDisplayName: String): Pair<List<RefLink>, String> {
    val parsed = runCatching { raw?.let { org.json.JSONObject(it) } }.getOrNull()
    val linksArr = parsed?.optJSONArray("links") ?: org.json.JSONArray()
    val activeIdRaw = parsed?.optString("activeId", "") ?: ""
    val defaultNameLc = EN_MAIN_LINK_LABEL.lowercase()
    val normalized = (0 until linksArr.length()).mapNotNull { i ->
        val item = linksArr.optJSONObject(i) ?: return@mapNotNull null
        val label = item.optString("name", "").trim()
        if (label.isBlank()) return@mapNotNull null
        val id = item.optString("id", "id-${System.currentTimeMillis()}-${(0..4).map { ('a'..'z').random() }.joinToString("")}")
        val isDefault = id == "default" || label.lowercase() == defaultNameLc
        val rawSlug = item.optString("slug", "").trim()
        val slug = when {
            isDefault && (rawSlug == "main-link" || rawSlug == slugifyLabel(EN_MAIN_LINK_LABEL)) -> ""
            isDefault -> ""
            rawSlug.isNotBlank() -> slugifyLabel(rawSlug)
            else -> slugifyLabel(label)
        }
        val description = item.optString("description", "").trim()
        RefLink(id = id, name = label, slug = slug, description = description)
    }.filter { it.name.isNotBlank() }.take(REF_LINKS_MAX)
    val links = if (normalized.isEmpty()) listOf(RefLink("default", defaultLinkDisplayName, "")) else normalized
    val activeId = if (links.any { it.id == activeIdRaw }) activeIdRaw else links.first().id
    return Pair(links, activeId)
}

/**
 * Community Tab – native Android UI, matches web (theme/snippets/community-panel-content.liquid).
 * Overview: Referral link, Network info (badge i), You stats (badge 👤), Level 1–10 cards with country filter.
 * Icons and country flags aligned with web version.
 * Analytics: Filters, KPIs, funnel, top links/sources, event feed – all with real backend data.
 */
private val FLAG_EMOJI = mapOf(
    "ALL" to "🌍",
    "DE" to "🇩🇪",
    "AT" to "🇦🇹",
    "CH" to "🇨🇭",
    "NL" to "🇳🇱",
    "SE" to "🇸🇪",
    "US" to "🇺🇸",
    "FR" to "🇫🇷",
    "ES" to "🇪🇸",
    "IT" to "🇮🇹",
    "PL" to "🇵🇱",
    "GB" to "🇬🇧",
    "TR" to "🇹🇷"
)

private fun flagEmojiFor(code: String): String = FLAG_EMOJI[code.uppercase()] ?: "🏳️"

/** ISO 3166-1 alpha-2 codes used in community network (English defaults match theme locale). */
private val COUNTRY_NAME_DEFAULTS = mapOf(
    "ALL" to "All",
    "DE" to "Germany",
    "AT" to "Austria",
    "CH" to "Switzerland",
    "NL" to "Netherlands",
    "SE" to "Sweden",
    "US" to "United States",
    "FR" to "France",
    "ES" to "Spain",
    "IT" to "Italy",
    "PL" to "Poland",
    "GB" to "United Kingdom",
    "TR" to "Turkey"
)

private fun countryNameFor(code: String, t: (String, String) -> String): String {
    val c = code.uppercase()
    val def = COUNTRY_NAME_DEFAULTS[c] ?: return c
    return t("creator.community.country_${c.lowercase()}", def)
}


data class CommunityNetwork(
    val me: MeStats,
    val stats: NetworkStats,
    val level1: List<PartnerItem>,
    val levels2to10: List<LevelInfo>
)

data class LevelInfo(val level: Int, val total: Int)

data class MeStats(
    val name: String,
    val designs: Int,
    val products: Int,
    val sales: Int,
    val profit: String
)

data class NetworkStats(
    val partners: Int,
    val designs: Int,
    val products: Int,
    val sales: Int,
    val profit: String
)

data class PartnerItem(
    val id: String,
    val ownerId: String,
    val name: String,
    val country: String,
    val since: String,
    val designs: Int,
    val products: Int,
    val sales: Int,
    val profitForMe: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountCommunityTab(
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    /** Creator Settings modal content background (dark sheet) */
    darkTheme: Boolean = false,
    /** When null, uses [LocalTranslationStore] (e.g. Shop account modal). */
    translationStore: TranslationStore? = null
) {
    val scheme = if (darkTheme) CommunityTabColorScheme.creatorDark() else CommunityTabColorScheme.Light
    val ts = translationStore ?: LocalTranslationStore.current
    val t: (String, String) -> String = { k, d -> ts?.t(k, d) ?: d }
    val context = LocalContext.current
    val jwt = remember { tokenStore.getJwt() }
    val ownerId = remember { tokenStore.getOwnerId() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }

    var network by remember { mutableStateOf<CommunityNetwork?>(null) }
    var referralBaseUrl by remember { mutableStateOf<String?>(null) }
    var refLinks by remember { mutableStateOf<List<RefLink>>(emptyList()) }
    var refLinksActiveId by remember { mutableStateOf("") }
    var showManageRefModal by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var overviewSubtab by remember { mutableStateOf(true) }
    var networkExpanded by remember { mutableStateOf(true) }
    var youExpanded by remember { mutableStateOf(true) }
    var expandedLevel by remember { mutableStateOf<Int?>(null) }
    var selectedPartner by remember { mutableStateOf<PartnerItem?>(null) }
    var copiedToast by remember { mutableStateOf(false) }
    var selectedCountry by remember { mutableStateOf("ALL") }

    LaunchedEffect(copiedToast) {
        if (copiedToast) {
            delay(2000)
            copiedToast = false
        }
    }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        errorMessage = null
        try {
            coroutineScope {
                val netDeferred = async { api.listCommunityNetwork(ownerId) }
                val refDeferred = async { api.getReferralCode(ownerId) }
                val net = netDeferred.await()
                val ref = refDeferred.await()
                if (net.optBoolean("ok", false)) {
                    network = net.optJSONObject("network")?.let { parseNetwork(it) }
                } else {
                    errorMessage = net.optString("error", t("creator.community.load_failed", "Failed to load community."))
                }
                val baseUrl = ref.optString("short_url", "").ifBlank { ref.optString("url", "") }
                if (ref.optBoolean("ok", false) && baseUrl.isNotBlank()) {
                    referralBaseUrl = baseUrl
                    val settingRes = api.getCustomerSetting(ownerId, REF_LINKS_SETTING_KEY)
                    val raw = if (settingRes.optBoolean("ok", false)) {
                        settingRes.optString("value", "").takeIf { it.isNotBlank() }
                    } else null
                    val (links, activeId) = normalizeStoredRefLinks(
                        raw,
                        t("creator.community.ref_link_main_default", EN_MAIN_LINK_LABEL)
                    )
                    refLinks = links
                    refLinksActiveId = activeId
                }
            }
        } catch (e: Exception) {
            DebugLog.click("Community load error: ${e.message}")
            errorMessage = t("creator.community.load_failed", "Failed to load community.")
        } finally {
            isLoading = false
        }
    }

    val scrollModifier = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
    CompositionLocalProvider(LocalCommunityColors provides scheme) {
    CompositionLocalProvider(LocalCommunityTranslate provides t) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .then(scrollModifier)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Text(
            text = t("creator.community.title", "Network"),
            style = MaterialTheme.typography.titleMedium,
            color = LocalCommunityColors.current.textPrimary
        )
        Text(
            text = t("creator.community.subtitle", "Overview of your network – compact and pressure-free."),
            style = MaterialTheme.typography.bodySmall,
            color = LocalCommunityColors.current.textSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { overviewSubtab = true },
                modifier = Modifier.weight(1f),
                colors = if (overviewSubtab) {
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = EazColors.OrangeBg.copy(alpha = 0.5f)
                    )
                } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            ) {
                Text(t("creator.community.overview_tab", "Overview"), color = if (overviewSubtab) EazColors.Orange else LocalCommunityColors.current.textSecondary)
            }
            OutlinedButton(
                onClick = { overviewSubtab = false },
                modifier = Modifier.weight(1f),
                colors = if (!overviewSubtab) {
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = EazColors.OrangeBg.copy(alpha = 0.5f)
                    )
                } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            ) {
                Text(t("creator.community.analytics_tab", "Analytics"), color = if (!overviewSubtab) EazColors.Orange else LocalCommunityColors.current.textSecondary)
            }
        }

        if (overviewSubtab) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
                }
            } else {
                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                referralBaseUrl?.let { baseUrl ->
                    val activeLink = refLinks.find { it.id == refLinksActiveId } ?: refLinks.firstOrNull()
                    val activeUrl = buildNamedRefUrl(baseUrl, activeLink?.slug ?: "")
                    ReferralSection(
                        url = activeUrl,
                        onClick = { showManageRefModal = true },
                        onCopy = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("referral", activeUrl))
                            copiedToast = true
                        },
                        onShare = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, activeUrl)
                            }
                            context.startActivity(Intent.createChooser(intent, t("creator.community.share_chooser_title", "Share referral link")))
                        },
                        copied = copiedToast
                    )
                    if (showManageRefModal) {
                        ManageRefLinksModal(
                            baseUrl = baseUrl,
                            links = refLinks,
                            activeId = refLinksActiveId,
                            ownerId = ownerId,
                            api = api,
                            onDismiss = { showManageRefModal = false },
                            onUpdate = { links, activeId ->
                                refLinks = links
                                refLinksActiveId = activeId
                            }
                        )
                    }
                }

                network?.let { net ->
                    NetworkInfoSection(
                        stats = net.stats,
                        expanded = networkExpanded,
                        onToggle = { networkExpanded = !networkExpanded }
                    )
                    YouSection(
                        me = net.me,
                        expanded = youExpanded,
                        onToggle = { youExpanded = !youExpanded }
                    )
                    LevelCards(
                        level1 = net.level1,
                        levels2to10 = net.levels2to10,
                        expandedLevel = expandedLevel,
                        selectedCountry = selectedCountry,
                        onLevelToggle = { l -> expandedLevel = if (expandedLevel == l) null else l },
                        onCountrySelect = { selectedCountry = it },
                        onPartnerClick = { selectedPartner = it }
                    )
                    Text(
                        text = t("creator.community.levels_hint", "Level 1–10: direct partners and their downline. Tap a partner for details."),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalCommunityColors.current.textSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            AnalyticsTab(ownerId = ownerId, api = api)
        }
    }

        selectedPartner?.let { partner ->
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { selectedPartner = null },
                sheetState = sheetState,
                containerColor = LocalCommunityColors.current.sheetContainer
            ) {
                PartnerDetailSheet(
                    partner = partner,
                    onDismiss = { selectedPartner = null }
                )
            }
        }
    }
    }
}

@Composable
private fun NetworkInfoSection(
    stats: NetworkStats,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val t = LocalCommunityTranslate.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = LocalCommunityColors.current.cardSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(EazColors.OrangeBg, RoundedCornerShape(6.dp))
                            .border(1.dp, EazColors.Orange.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("i", style = MaterialTheme.typography.labelMedium, color = EazColors.Orange, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text(t("creator.community.network_info", "Network Info"), style = MaterialTheme.typography.titleSmall, color = LocalCommunityColors.current.textPrimary)
                        Text(t("creator.community.network_info_sub", "Users · Designs · Products · Sales · Profit"), style = MaterialTheme.typography.bodySmall, color = LocalCommunityColors.current.textSecondary)
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = LocalCommunityColors.current.textSecondary
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        statItemWithIcon(Icons.Default.Groups, t("creator.community.partners_1_10", "Users"), stats.partners.toString())
                        statItemWithIcon(Icons.Default.Image, t("creator.community.designs", "Designs"), stats.designs.toString())
                        statItemWithIcon(Icons.Default.Inventory2, t("creator.community.products", "Products"), stats.products.toString())
                        statItemWithIcon(Icons.Default.ShoppingCart, t("creator.community.sales", "Sales"), stats.sales.toString())
                        if (stats.profit.isNotBlank()) statItemWithIcon(Icons.Default.MonetizationOn, t("creator.community.profit", "Profit"), stats.profit)
                    }
                }
            }
        }
    }
}

@Composable
private fun YouSection(
    me: MeStats,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val t = LocalCommunityTranslate.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(containerColor = LocalCommunityColors.current.cardSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(EazColors.OrangeBg, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = EazColors.Orange)
                    }
                    Column {
                        Text(t("creator.community.you", "You"), style = MaterialTheme.typography.titleSmall, color = LocalCommunityColors.current.textPrimary)
                        Text(t("creator.community.you_stats_hint", "Your own stats · Designs · Products · Sales · Profit"), style = MaterialTheme.typography.bodySmall, color = LocalCommunityColors.current.textSecondary)
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = LocalCommunityColors.current.textSecondary
                )
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        statItemWithIcon(Icons.Default.Image, t("creator.community.designs", "Designs"), me.designs.toString())
                        statItemWithIcon(Icons.Default.Inventory2, t("creator.community.products", "Products"), me.products.toString())
                        statItemWithIcon(Icons.Default.ShoppingCart, t("creator.community.sales", "Sales"), me.sales.toString())
                        if (me.profit.isNotBlank()) statItemWithIcon(Icons.Default.MonetizationOn, t("creator.community.profit", "Profit"), me.profit)
                    }
                }
            }
        }
    }
}

@Composable
private fun statItemWithIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = EazColors.Orange)
        Text(value, style = MaterialTheme.typography.titleSmall, color = EazColors.Orange)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary)
    }
}

@Composable
private fun LevelCards(
    level1: List<PartnerItem>,
    levels2to10: List<LevelInfo>,
    expandedLevel: Int?,
    selectedCountry: String,
    onLevelToggle: (Int) -> Unit,
    onCountrySelect: (String) -> Unit,
    onPartnerClick: (PartnerItem) -> Unit
) {
    val t = LocalCommunityTranslate.current
    val countryCounts = remember(level1) {
        val map = mutableMapOf<String, Int>()
        level1.forEach { p ->
            val c = (p.country.ifBlank { "DE" }).uppercase().take(2)
            map[c] = (map[c] ?: 0) + 1
        }
        map["ALL"] = level1.size
        map
    }
    val countries = listOf("ALL") + countryCounts.keys.filter { it != "ALL" }.sortedByDescending { countryCounts[it] ?: 0 }
    val level1Info = LevelInfo(1, level1.size)
    val allLevels = listOf(level1Info) + levels2to10
    allLevels.forEach { info ->
        val expanded = expandedLevel == info.level
        val hasPartners = info.level == 1 && level1.isNotEmpty()
        val filteredPartners = if (info.level == 1 && selectedCountry != "ALL") {
            level1.filter { (it.country.ifBlank { "DE" }).uppercase().take(2) == selectedCountry }
        } else level1
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLevelToggle(info.level) },
            colors = CardDefaults.cardColors(containerColor = LocalCommunityColors.current.cardSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        String.format(Locale.US, t("creator.community.level_format", "Level %d"), info.level),
                        style = MaterialTheme.typography.titleSmall,
                        color = LocalCommunityColors.current.textPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            String.format(Locale.US, t("creator.community.users_count", "%d users"), info.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalCommunityColors.current.textSecondary
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = LocalCommunityColors.current.textSecondary
                        )
                    }
                }
                if (expanded && info.level == 1 && level1.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        countries.forEach { code ->
                            val count = countryCounts[code] ?: 0
                            val selected = code == selectedCountry
                            OutlinedButton(
                                onClick = { onCountrySelect(code) },
                                modifier = Modifier.padding(0.dp),
                                colors = if (selected) {
                                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = EazColors.OrangeBg.copy(alpha = 0.5f))
                                } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    GlassCircularFlag(countryCode = code, size = 20.dp)
                                    Text(count.toString(), color = if (selected) EazColors.Orange else LocalCommunityColors.current.textSecondary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = expanded && hasPartners,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        filteredPartners.forEach { partner ->
                            PartnerCard(partner = partner, onClick = { onPartnerClick(partner) })
                        }
                    }
                }
                if (expanded && info.level == 1 && level1.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .background(LocalCommunityColors.current.mutedBg, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.padding(bottom = 8.dp), tint = LocalCommunityColors.current.textSecondary)
                            Text(t("creator.community.no_partners_title", "No partners yet"), style = MaterialTheme.typography.bodyMedium, color = LocalCommunityColors.current.textSecondary)
                            Text(t("creator.community.no_partners_subtitle", "Invite creators to grow your network"), style = MaterialTheme.typography.bodySmall, color = LocalCommunityColors.current.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsTab(
    ownerId: String,
    api: com.eazpire.creator.api.CreatorApi
) {
    val t = LocalCommunityTranslate.current
    var days by remember { mutableStateOf(30) }
    var compare by remember { mutableStateOf(false) }
    var linkId by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var linksRows by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var sourcesRows by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var eventsRows by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var eventsCursor by remember { mutableStateOf<String?>(null) }
    var hasMoreEvents by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var eventsLoadTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(ownerId, days, compare, linkId, source, eventsLoadTrigger) {
        if (ownerId.isBlank()) return@LaunchedEffect
        val loadMore = eventsLoadTrigger > 0
        val cursorForFetch = if (loadMore) eventsCursor else null
        isLoading = true
        try {
            coroutineScope {
                val overviewDeferred = async {
                    if (!loadMore) api.getCommunityAnalyticsOverview(ownerId, days, compare, linkId.ifBlank { null }, source.ifBlank { null })
                    else JSONObject("{}")
                }
                val linksDeferred = async {
                    if (!loadMore) api.getCommunityAnalyticsLinks(ownerId, days, linkId.ifBlank { null }, source.ifBlank { null })
                    else JSONObject("{}")
                }
                val sourcesDeferred = async {
                    if (!loadMore) api.getCommunityAnalyticsSources(ownerId, days, linkId.ifBlank { null }, source.ifBlank { null })
                    else JSONObject("{}")
                }
                val eventsDeferred = async {
                    api.getCommunityAnalyticsEvents(ownerId, days, linkId.ifBlank { null }, source.ifBlank { null }, 20, cursorForFetch)
                }
                val ov = overviewDeferred.await()
                val lnk = linksDeferred.await()
                val src = sourcesDeferred.await()
                val ev = eventsDeferred.await()
                if (!loadMore && ov.optBoolean("ok", false)) overview = ov
                if (!loadMore && lnk.optBoolean("ok", false)) {
                    linksRows = parseAnalyticsRows(lnk.optJSONArray("rows"))
                }
                if (!loadMore && src.optBoolean("ok", false)) {
                    sourcesRows = parseAnalyticsRows(src.optJSONArray("rows"))
                }
                if (ev.optBoolean("ok", false)) {
                    val rows = parseAnalyticsRows(ev.optJSONArray("rows"))
                    eventsRows = if (loadMore) eventsRows + rows else rows
                    eventsCursor = ev.optString("next_cursor", "").ifBlank { null }
                    hasMoreEvents = eventsCursor != null
                } else if (!loadMore) {
                    eventsRows = emptyList()
                }
            }
        } catch (e: Exception) {
            DebugLog.click("Analytics load error: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(ownerId, days, compare, linkId, source) {
        eventsLoadTrigger = 0
        eventsCursor = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(t("creator.community.analytics_title", "Network Analytics"), style = MaterialTheme.typography.titleMedium, color = LocalCommunityColors.current.textPrimary)
        Text(t("creator.community.analytics_subtitle", "Detailed performance of referral traffic, conversions and generated value."), style = MaterialTheme.typography.bodySmall, color = LocalCommunityColors.current.textSecondary)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90, 365).forEach { d ->
                OutlinedButton(
                    onClick = { days = d },
                    modifier = Modifier.weight(1f),
                    colors = if (days == d) {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = EazColors.OrangeBg.copy(alpha = 0.5f))
                    } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(
                        if (d == 365) t("creator.community.analytics_period_y", "1y") else String.format(Locale.US, t("creator.community.analytics_period_days", "%dd"), d),
                        color = if (days == d) EazColors.Orange else LocalCommunityColors.current.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { compare = false },
                modifier = Modifier.weight(1f),
                colors = if (!compare) {
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = EazColors.OrangeBg.copy(alpha = 0.5f))
                } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            ) { Text(t("creator.community.analytics_compare_none", "No compare"), color = if (!compare) EazColors.Orange else LocalCommunityColors.current.textSecondary, style = MaterialTheme.typography.labelSmall) }
            OutlinedButton(
                onClick = { compare = true },
                modifier = Modifier.weight(1f),
                colors = if (compare) {
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = EazColors.OrangeBg.copy(alpha = 0.5f))
                } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            ) { Text(t("creator.community.analytics_compare", "Compare"), color = if (compare) EazColors.Orange else LocalCommunityColors.current.textSecondary, style = MaterialTheme.typography.labelSmall) }
        }

        if (isLoading && overview == null) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
            }
        } else {
            overview?.let { ov ->
                val kpis = ov.optJSONObject("kpis") ?: JSONObject()
                val funnel = ov.optJSONObject("funnel") ?: JSONObject()
                AnalyticsKpis(kpis = kpis)
                AnalyticsFunnel(funnel = funnel)
            }
            AnalyticsBars(title = t("creator.community.analytics_top_links", "Top links"), rows = linksRows, labelKey = "link_label", valueKey = "clicks")
            AnalyticsBars(title = t("creator.community.analytics_top_sources", "Top sources"), rows = sourcesRows, labelKey = "source", valueKey = "clicks")
            AnalyticsEventFeed(events = eventsRows)
            if (hasMoreEvents) {
                OutlinedButton(
                    onClick = { eventsLoadTrigger++ },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(t("creator.community.analytics_load_more", "Load more")) }
            }
        }
    }
}

@Composable
private fun AnalyticsKpis(kpis: JSONObject) {
    val t = LocalCommunityTranslate.current
    val keys = listOf("clicks", "unique_clicks", "new_users", "sales", "revenue", "new_creators")
    val labels = mapOf(
        "clicks" to t("creator.community.kpi_clicks_short", "Clicks"),
        "unique_clicks" to t("creator.community.kpi_unique_short", "Unique"),
        "new_users" to t("creator.community.kpi_users_short", "Users"),
        "sales" to t("creator.community.kpi_sales_short", "Sales"),
        "revenue" to t("creator.community.kpi_revenue_short", "Revenue"),
        "new_creators" to t("creator.community.kpi_creators_short", "Creators")
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keys.chunked(2).forEach { chunk ->
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEach { key ->
                    val k = kpis.optJSONObject(key)
                    val value = k?.opt("value") ?: 0
                    val delta = k?.optDouble("delta", Double.NaN)
                    val deltaVal = if (delta != null && !delta.isNaN()) delta else null
                    val fmtValue = if (key == "revenue") fmtMoney(value) else fmtNumber(value)
                    val fmtDelta = if (deltaVal == null) t("creator.community.em_dash", "–") else "${if (deltaVal > 0) "+" else ""}${(deltaVal * 100).toInt()}%"
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = LocalCommunityColors.current.cardSurface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(labels[key] ?: key, style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary)
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text(fmtValue, style = MaterialTheme.typography.titleSmall, color = EazColors.Orange)
                                Text(fmtDelta, style = MaterialTheme.typography.labelSmall, color = if (deltaVal != null && deltaVal > 0) EazColors.Orange else LocalCommunityColors.current.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsFunnel(funnel: JSONObject) {
    val t = LocalCommunityTranslate.current
    val clicks = funnel.optInt("clicks", funnel.optInt("unique_clicks", 0))
    val users = funnel.optInt("users", 0)
    val buyers = funnel.optInt("buyers", 0)
    val creators = funnel.optInt("creators", 0)
    val steps = listOf(
        t("creator.community.funnel_step_clicks", "Clicks") to clicks,
        t("creator.community.funnel_step_users", "Users") to users,
        t("creator.community.funnel_step_buyers", "Buyers") to buyers,
        t("creator.community.funnel_step_creators", "Creators") to creators
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalCommunityColors.current.cardSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(t("creator.community.funnel_title", "Funnel"), style = MaterialTheme.typography.titleSmall, color = LocalCommunityColors.current.textPrimary)
            steps.forEach { (label, value) ->
                val pct = if (clicks > 0) (value.toDouble() / clicks * 100).toInt() else 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$label ($value)", style = MaterialTheme.typography.bodySmall, color = LocalCommunityColors.current.textPrimary)
                    Text("$pct%", style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(LocalCommunityColors.current.funnelTrack, RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (clicks > 0) value.toFloat() / clicks else 0f)
                            .height(6.dp)
                            .background(EazColors.Orange, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyticsBars(
    title: String,
    rows: List<Map<String, Any>>,
    labelKey: String,
    valueKey: String
) {
    val t = LocalCommunityTranslate.current
    val max = rows.maxOfOrNull { (it[valueKey] as? Number)?.toDouble() ?: 0.0 } ?: 1.0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalCommunityColors.current.cardSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = LocalCommunityColors.current.textPrimary)
            rows.take(8).forEach { row ->
                val label = row[labelKey]?.toString() ?: t("creator.community.em_dash", "–")
                val value = (row[valueKey] as? Number)?.toInt() ?: 0
                val pct = if (max > 0) (value / max * 100).toInt().coerceAtLeast(1) else 0
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = LocalCommunityColors.current.textPrimary, modifier = Modifier.weight(1f))
                    Text(fmtNumber(value), style = MaterialTheme.typography.labelMedium, color = EazColors.Orange)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(LocalCommunityColors.current.funnelTrack, RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct / 100f)
                            .height(6.dp)
                            .background(EazColors.Orange, RoundedCornerShape(3.dp))
                    )
                }
            }
            if (rows.isEmpty()) {
                Text(t("creator.community.no_data", "No data"), style = MaterialTheme.typography.bodySmall, color = LocalCommunityColors.current.textSecondary)
            }
        }
    }
}

@Composable
private fun AnalyticsEventFeed(events: List<Map<String, Any>>) {
    val t = LocalCommunityTranslate.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalCommunityColors.current.cardSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(t("creator.community.analytics_event_feed", "Event feed"), style = MaterialTheme.typography.titleSmall, color = LocalCommunityColors.current.textPrimary)
            events.take(20).forEach { ev ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dash = t("creator.community.em_dash", "–")
                    Text(ev["time_label"]?.toString() ?: dash, style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary, modifier = Modifier.width(80.dp))
                    Text(ev["link_label"]?.toString() ?: dash, style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textPrimary, modifier = Modifier.weight(1f))
                    Text(ev["source"]?.toString() ?: t("creator.community.analytics_source_direct", "direct"), style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary)
                    Text(ev["outcome"]?.toString() ?: t("creator.community.analytics_outcome_click", "click"), style = MaterialTheme.typography.labelSmall, color = EazColors.Orange)
                    val rev = ev["revenue"]
                    Text(if (rev != null) fmtMoney(rev) else dash, style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary)
                }
            }
            if (events.isEmpty()) {
                Text(t("creator.community.no_events", "No events"), style = MaterialTheme.typography.bodySmall, color = LocalCommunityColors.current.textSecondary)
            }
        }
    }
}

private fun fmtNumber(value: Any): String = NumberFormat.getNumberInstance(Locale.GERMANY).format((value as? Number)?.toDouble() ?: 0.0)
private fun fmtMoney(value: Any): String {
    val n = (value as? Number)?.toDouble() ?: 0.0
    return NumberFormat.getCurrencyInstance(Locale.GERMANY).format(n)
}

private fun parseAnalyticsRows(arr: org.json.JSONArray?): List<Map<String, Any>> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        val map = mutableMapOf<String, Any>()
        obj.keys().asSequence().forEach { k ->
            when (val v = obj.get(k)) {
                is Number -> map[k] = v
                is String -> map[k] = v
                else -> map[k] = v.toString()
            }
        }
        map
    }
}

private fun parseOptions(arr: org.json.JSONArray?, valueKey: String, labelKey: String): List<Pair<String, String>> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        val v = obj.optString(valueKey, "")
        val l = obj.optString(labelKey, v)
        if (v.isBlank()) null else Pair(v, l)
    }
}

@Composable
private fun ReferralSection(
    url: String,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    copied: Boolean
) {
    val t = LocalCommunityTranslate.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = LocalCommunityColors.current.cardSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(t("creator.community.referral_link_title", "Your Referral Link"), style = MaterialTheme.typography.titleSmall, color = LocalCommunityColors.current.textPrimary)
            Text(t("creator.community.referral_hint_full", "Share this link – when others register and buy, you earn. Tap link to manage."), style = MaterialTheme.typography.bodySmall, color = LocalCommunityColors.current.textSecondary, modifier = Modifier.padding(top = 4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clickable { onClick() },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(url, style = MaterialTheme.typography.bodySmall, color = LocalCommunityColors.current.textSecondary, modifier = Modifier.weight(1f))
                IconButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = t("creator.common.copy", "Copy"), tint = EazColors.Orange)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = t("creator.common.share", "Share"), tint = EazColors.Orange)
                }
            }
            if (copied) {
                Text(t("creator.community.copied", "Copied!"), style = MaterialTheme.typography.labelSmall, color = EazColors.Orange, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageRefLinksModal(
    baseUrl: String,
    links: List<RefLink>,
    activeId: String,
    ownerId: String,
    api: com.eazpire.creator.api.CreatorApi,
    onDismiss: () -> Unit,
    onUpdate: (List<RefLink>, String) -> Unit
) {
    var localLinks by remember(links) { mutableStateOf(links) }
    var localActiveId by remember(activeId) { mutableStateOf(activeId) }
    var newSlug by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = remember { CoroutineScope(Dispatchers.Main) }
    val t = LocalCommunityTranslate.current

    LaunchedEffect(links, activeId) {
        localLinks = links
        localActiveId = activeId
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalCommunityColors.current.sheetContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(t("creator.community.ref_links_manage_title", "Manage referral links"), style = MaterialTheme.typography.titleLarge, color = LocalCommunityColors.current.textPrimary)
                    Text(t("creator.community.ref_links_manage_subtitle", "Create up to 5 named links."), style = MaterialTheme.typography.bodySmall, color = LocalCommunityColors.current.textSecondary)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = t("creator.common.close", "Close"), tint = LocalCommunityColors.current.textPrimary)
                }
            }

            localLinks.forEach { link ->
                val isActive = link.id == localActiveId
                val isMain = link.id == "default"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(if (isActive) EazColors.OrangeBg.copy(alpha = 0.3f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayRefLinkName(link.name, t), style = MaterialTheme.typography.bodyMedium, color = LocalCommunityColors.current.textPrimary)
                        Text(buildNamedRefUrl(baseUrl, link.slug), style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary)
                        if (link.description.isNotBlank()) {
                            Text(link.description, style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                localActiveId = link.id
                                persistRefLinks(scope, ownerId, api, localLinks, localActiveId) { l, a ->
                                    onUpdate(l, a)
                                }
                            },
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(t("creator.community.ref_button_use", "Use"), style = MaterialTheme.typography.labelSmall)
                        }
                        if (!isMain) {
                            OutlinedButton(
                                onClick = {
                                    val updated = localLinks.filter { it.id != link.id }
                                    val newActive = if (localActiveId == link.id) (updated.firstOrNull()?.id ?: "") else localActiveId
                                    localLinks = updated
                                    localActiveId = newActive
                                    persistRefLinks(scope, ownerId, api, localLinks, localActiveId) { l, a ->
                                        onUpdate(l, a)
                                    }
                                },
                                modifier = Modifier.padding(0.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(t("creator.community.ref_button_remove", "Remove"), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Text(t("creator.community.ref_links_slug_label", "Ref link"), style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary, modifier = Modifier.padding(top = 16.dp))
            OutlinedTextField(
                value = newSlug,
                onValueChange = { newSlug = it; errorMsg = null },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(t("creator.community.ref_links_slug_placeholder", "e.g. instagram-campaign"), color = LocalCommunityColors.current.textSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EazColors.Orange,
                    unfocusedBorderColor = LocalCommunityColors.current.outlineBorder,
                    focusedTextColor = LocalCommunityColors.current.textPrimary,
                    unfocusedTextColor = LocalCommunityColors.current.textPrimary
                )
            )
            Text("${baseUrl.trimEnd('/')}/${newSlug.ifBlank { t("creator.community.ref_preview_placeholder", "your-link") }.lowercase()}", style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary)
            Text(t("creator.community.ref_links_slug_hint", "Allowed: a-z, 0-9, -, length 4-24."), style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary)

            Text(t("creator.community.ref_links_name_label", "Label"), style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it; errorMsg = null },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(t("creator.community.ref_links_name_placeholder", "e.g. Instagram Bio"), color = LocalCommunityColors.current.textSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EazColors.Orange,
                    unfocusedBorderColor = LocalCommunityColors.current.outlineBorder,
                    focusedTextColor = LocalCommunityColors.current.textPrimary,
                    unfocusedTextColor = LocalCommunityColors.current.textPrimary
                )
            )

            Text(t("creator.community.ref_links_description_label", "Description"), style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = newDescription,
                onValueChange = { newDescription = it; errorMsg = null },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(t("creator.community.ref_links_description_placeholder", "Optional info for this link"), color = LocalCommunityColors.current.textSecondary) },
                minLines = 3,
                maxLines = 4,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EazColors.Orange,
                    unfocusedBorderColor = LocalCommunityColors.current.outlineBorder,
                    focusedTextColor = LocalCommunityColors.current.textPrimary,
                    unfocusedTextColor = LocalCommunityColors.current.textPrimary
                )
            )

            OutlinedButton(
                onClick = {
                    val slugNorm = slugifyLabel(newSlug.trim())
                    val label = newName.trim()
                    when {
                        slugNorm.isBlank() -> errorMsg = t("creator.community.ref_err_slug_required", "Please enter the ref link.")
                        label.isBlank() -> errorMsg = t("creator.community.ref_err_label_required", "Please enter a label.")
                        localLinks.any { it.name.equals(label, ignoreCase = true) } -> errorMsg = t("creator.community.ref_err_label_duplicate", "This label already exists.")
                        localLinks.size >= REF_LINKS_MAX -> errorMsg = t("creator.community.ref_err_max_links", "Maximum 5 links reached.")
                        !isValidRefSlug(slugNorm) -> errorMsg = t("creator.community.ref_err_slug_invalid", "Link path must be 4-24 chars, a-z, 0-9, -.")
                        isReservedRefSlug(slugNorm) -> errorMsg = t("creator.community.ref_err_slug_reserved", "This link path is reserved.")
                        localLinks.any { it.slug.equals(slugNorm, ignoreCase = true) } -> errorMsg = t("creator.community.ref_err_slug_in_use", "This link path is already in use.")
                        else -> {
                            val newLink = RefLink("id-${System.currentTimeMillis()}-${(0..5).map { ('a'..'z').random() }.joinToString("")}", label, slugNorm, newDescription.trim())
                            localLinks = localLinks + newLink
                            localActiveId = newLink.id
                            newSlug = ""
                            newName = ""
                            newDescription = ""
                            errorMsg = null
                            persistRefLinks(scope, ownerId, api, localLinks, localActiveId) { l, a ->
                                onUpdate(l, a)
                            }
                        }
                    }
                },
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(t("creator.community.ref_links_add_button", "Add link"))
            }
            errorMsg?.let { msg ->
                Text(msg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

private fun persistRefLinks(
    scope: CoroutineScope,
    ownerId: String,
    api: com.eazpire.creator.api.CreatorApi,
    links: List<RefLink>,
    activeId: String,
    onDone: (List<RefLink>, String) -> Unit
) {
    scope.launch {
        try {
            val payload = org.json.JSONObject().apply {
                put("activeId", activeId)
                put("links", org.json.JSONArray(links.map { org.json.JSONObject(mapOf("id" to it.id, "name" to it.name, "slug" to it.slug, "description" to it.description)) }))
            }
            api.setCustomerSetting(ownerId, REF_LINKS_SETTING_KEY, payload.toString())
            api.syncRefLinkSlugs(ownerId, links.filter { it.slug.isNotBlank() }.map { mapOf("slug" to it.slug, "name" to it.name) })
            onDone(links, activeId)
        } catch (_: Exception) {}
    }
}

@Composable
private fun PartnerCard(partner: PartnerItem, onClick: () -> Unit) {
    val t = LocalCommunityTranslate.current
    val countryCode = partner.country.ifBlank { "DE" }.uppercase().take(2)
    val countryLabel = countryNameFor(countryCode, t)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = LocalCommunityColors.current.partnerCardBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                GlassCircularFlag(countryCode = countryCode, size = 44.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(countryLabel, style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary)
                    Text(partner.name, style = MaterialTheme.typography.bodyMedium, color = LocalCommunityColors.current.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                partnerStatIcon(Icons.Default.Image, partner.designs)
                partnerStatIcon(Icons.Default.Inventory2, partner.products)
                partnerStatIcon(Icons.Default.ShoppingCart, partner.sales)
            }
        }
    }
}

@Composable
private fun partnerStatIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, value: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = LocalCommunityColors.current.textSecondary)
        Text(value.toString(), style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary)
    }
}

@Composable
private fun PartnerDetailSheet(partner: PartnerItem, onDismiss: () -> Unit) {
    val t = LocalCommunityTranslate.current
    val rawCountry = partner.country.trim()
    val countryDisplay =
        if (rawCountry.length == 2) countryNameFor(rawCountry.uppercase(), t) else rawCountry
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(t("creator.community.partner_info", "Partner Information"), style = MaterialTheme.typography.titleMedium, color = LocalCommunityColors.current.textPrimary)
        Text(partner.name, style = MaterialTheme.typography.titleSmall, color = EazColors.Orange, modifier = Modifier.padding(top = 8.dp))
        Text(
            "${flagEmojiFor(partner.country.ifBlank { "DE" }.uppercase().take(2))} $countryDisplay · ${t("creator.community.since_word", "since")} ${partner.since}",
            style = MaterialTheme.typography.bodySmall,
            color = LocalCommunityColors.current.textSecondary
        )
        Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            statItem(t("creator.community.designs", "Designs"), partner.designs.toString())
            statItem(t("creator.community.products", "Products"), partner.products.toString())
            statItem(t("creator.community.sales", "Sales"), partner.sales.toString())
        }
        if (partner.profitForMe.isNotBlank()) {
            Text(
                String.format(Locale.US, t("creator.community.partner_detail_profit", "Profit for you: %s"), partner.profitForMe),
                style = MaterialTheme.typography.bodySmall,
                color = LocalCommunityColors.current.textSecondary,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Text(t("creator.common.close", "Close"))
        }
    }
}

@Composable
private fun statItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, color = EazColors.Orange)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalCommunityColors.current.textSecondary)
    }
}

private fun parseNetwork(obj: org.json.JSONObject): CommunityNetwork? {
    val meObj = obj.optJSONObject("me") ?: return null
    val statsObj = obj.optJSONObject("stats") ?: return null
    val me = MeStats(
        name = meObj.optString("name", "You"),
        designs = meObj.optInt("designs", 0),
        products = meObj.optInt("products", 0),
        sales = meObj.optInt("sales", 0),
        profit = meObj.optString("profit", "")
    )
    val stats = NetworkStats(
        partners = statsObj.optInt("partners", 0),
        designs = statsObj.optInt("designs", 0),
        products = statsObj.optInt("products", 0),
        sales = statsObj.optInt("sales", 0),
        profit = statsObj.optString("profit", "")
    )
    val level1Arr = obj.optJSONArray("level1")
    val level1 = (0 until (level1Arr?.length() ?: 0)).mapNotNull { i ->
        val p = level1Arr?.optJSONObject(i) ?: return@mapNotNull null
        PartnerItem(
            id = p.optString("id", ""),
            ownerId = p.optString("owner_id", ""),
            name = p.optString("name", "–"),
            country = p.optString("country", ""),
            since = p.optString("since", ""),
            designs = p.optInt("designs", 0),
            products = p.optInt("products", 0),
            sales = p.optInt("sales", 0),
            profitForMe = p.optString("profitForMe", "")
        )
    }
    val levels2to10Arr = obj.optJSONArray("levels2to10")
    val levels2to10 = (0 until (levels2to10Arr?.length() ?: 0)).mapNotNull { i ->
        val l = levels2to10Arr?.optJSONObject(i) ?: return@mapNotNull null
        LevelInfo(level = l.optInt("level", i + 2), total = l.optInt("total", 0))
    }
    return CommunityNetwork(me, stats, level1, levels2to10)
}
