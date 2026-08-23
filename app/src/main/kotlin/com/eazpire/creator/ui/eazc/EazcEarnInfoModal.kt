package com.eazpire.creator.ui.eazc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.modal.EazFullScreenDialog

private data class EarnNavItem(
    val id: String,
    val labelKey: String,
    val labelFallback: String,
    val pip: String = "",
    val cash: Boolean = false,
)

private val EARN_NAV = listOf(
    EarnNavItem("start", "creator.eazc_earn.nav_start", "Overview"),
    EarnNavItem("products", "creator.eazc_earn.nav_products", "Your designs", "01"),
    EarnNavItem("remix", "creator.eazc_earn.nav_remix", "Remixes", "02"),
    EarnNavItem("qi", "creator.eazc_earn.nav_qi", "Quick Inspirations", "03"),
    EarnNavItem("shop", "creator.eazc_earn.nav_shop", "Shop products", "04"),
    EarnNavItem("community", "creator.eazc_earn.nav_community", "Community", "05"),
    EarnNavItem("referral", "creator.eazc_earn.nav_referral", "Invites & cashback", "06"),
    EarnNavItem("payout", "creator.eazc_earn.nav_payout", "Turn it into money", cash = true),
)

private data class EarnTopic(
    val id: String,
    val eyebrowFb: String,
    val titleFb: String,
    val leadFb: String,
    val bodyFb: String,
    val exampleFb: String,
    val l1Fb: String,
    val v1Fb: String,
    val l2Fb: String,
    val v2Fb: String,
    val l3Fb: String,
    val v3Fb: String,
    val factFb: String,
)

private val EARN_TOPICS = listOf(
    EarnTopic(
        "products",
        "Path 01",
        "Sell your own designs",
        "Publish a design. When it sells, you earn a royalty on the net profit — 10% to start, up to 40% later.",
        "Community cashback and referrals come from a separate community share. They do not take your creator royalty.",
        "A customer pays €34.90 for Lena’s hoodie. After print and shipping, €8.00 net profit remains.",
        "Net profit", "€8.00",
        "Lena at 10% royalty", "about €0.80",
        "Same sale at 40%", "about €3.20",
        "That €0.80 is credited as EAZC. After 30 days Lena converts it into euros (or her payout currency) in Balance & Payouts."
    ),
    EarnTopic(
        "remix",
        "Path 02",
        "Earn on remixes",
        "If someone remixed your design — or you remixed theirs — you still share the creator royalty from that sale.",
        "The remix seller receives 87.5% of the creator share. The original creator receives 12.5%.",
        "Noah remixed Lena’s print and sold the same hoodie. The creator share is still about €0.80.",
        "Noah, remix seller", "€0.70 (87.5%)",
        "Lena, original design", "€0.10 (12.5%)",
        "Community cashback", "paid separately",
        "You earn on both sides: selling a remix, and when others remix you."
    ),
    EarnTopic(
        "qi",
        "Path 03",
        "Quick Inspirations",
        "Save a Quick Inspiration. If someone builds a design from it and that product sells, you share the creator royalty.",
        "The Quick Inspiration author receives 5 royalty points from the seller’s creator share — half of that share at the 10% starter royalty. Automatic trend inspirations do not pay.",
        "Mira saved a Quick Inspiration. Someone used it on a mug. Creator share on that sale: about €0.80 at 10% royalty.",
        "Mira, inspiration author", "€0.40",
        "The seller", "€0.40",
        "Trend auto-inspirations", "€0.00",
        "At higher royalty tiers the seller keeps a larger slice; Mira still gets those 5 points."
    ),
    EarnTopic(
        "shop",
        "Path 04",
        "Shop & custom products",
        "Your design can sit on a shop product or a custom piece someone configures. When that item sells, you share the creator royalty.",
        "Up to 3 designs on one product split the creator amount equally. If a slot is a remix, that slot still splits 87.5% / 12.5%.",
        "A custom hoodie uses three shop designs, including yours. Creator share: about €0.80.",
        "Each design slot", "about €0.27",
        "Your slot if it is a remix", "still 87.5% / 12.5%",
        "Empty slots", "do not dilute you",
        "You do not need to be the seller. The product just has to use your design."
    ),
    EarnTopic(
        "community",
        "Path 05",
        "Creator Community",
        "Invite another creator. If you both opt in, they can publish on products you unlocked and they have not.",
        "They keep 70% of the creator share on those borrowed products or sizes; you receive 30%. On products they unlocked themselves they keep 100%. Publish Assist does not add an extra split.",
        "Lena borrowed extra hoodie sizes from her recruiter. The hoodie creator share is about €0.80.",
        "Lena sells a size she already has", "€0.80 (100%)",
        "Lena sells a size only the recruiter unlocked", "€0.56 Lena / €0.24 recruiter",
        "If only one opted in", "no borrowed products",
        "Both people must opt in. When Lena unlocks that size herself, the recruiter share stops on the next sale."
    ),
    EarnTopic(
        "referral",
        "Path 06",
        "Invites & buyer cashback",
        "Share your link. When someone buys — or when you buy while logged in — money comes from the community share, not from the creator royalty.",
        "From the community share: the buyer receives 20% cashback, your direct referral link earns 10%, and further network levels earn smaller shares.",
        "On Lena’s €8.00 profit, the community share is about €4.00. That pot pays cashback and referrals.",
        "Buyer cashback (20%)", "about €0.80",
        "Your direct invite (10%)", "about €0.40",
        "You buy while logged in", "you get the cashback",
        "This is extra money on top of design royalties — from shopping, not from publishing."
    ),
    EarnTopic(
        "payout",
        "Cash out",
        "Turn it into money",
        "EAZC is only the ledger. The point is fiat: shop credit you can spend, or cash in your bank.",
        "On Creator, tap the amount on your EAZC badge to open Balance & Payouts. Convert available earnings at the lowest pack rate.",
        "After 30 days Lena converts her hoodie sale. She can take shop credit with a +10% bonus, or send euros to her Wise account.",
        "Pending (30 days)", "not cashable yet",
        "Available EAZC", "convert to fiat",
        "Shop credit or Wise", "+10% if you spend it here",
        "The shop wallet pill is fiat / store credit. The orange badge is EAZC waiting to become money. Game coins, Move-to-Earn, and Wear & Earn are separate."
    ),
)

private val Paper = Color(0xFFF4EFE6)
private val Ink = Color(0xFF0B1F3A)

@Composable
fun EazcEarnInfoModal(
    visible: Boolean,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    var page by remember { mutableStateOf("start") }
    EazFullScreenDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(EazColors.EazcNavyMid, EazColors.EazcNavy)
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                EazColors.Orange.copy(alpha = 0.16f),
                                EazColors.EazcBlue.copy(alpha = 0.18f)
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = translationStore.t("creator.eazc_earn.kicker", "Get paid"),
                        color = EazColors.OrangeHover,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        text = translationStore.t("creator.eazc_earn.title", "How you earn real money"),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(44.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = translationStore.t("creator.common.close", "Close"),
                        tint = Color.White
                    )
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= 720.dp
                if (wide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        EarnNavColumn(
                            translationStore = translationStore,
                            page = page,
                            onSelect = { page = it },
                            modifier = Modifier
                                .width(232.dp)
                                .fillMaxHeight()
                                .background(EazColors.EazcNavy.copy(alpha = 0.35f))
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                        EarnStage(
                            translationStore = translationStore,
                            page = page,
                            onSelect = { page = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            EARN_NAV.forEach { item ->
                                EarnNavChip(
                                    item = item,
                                    selected = page == item.id,
                                    translationStore = translationStore,
                                    compact = true,
                                    onClick = { page = item.id }
                                )
                            }
                        }
                        EarnStage(
                            translationStore = translationStore,
                            page = page,
                            onSelect = { page = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EarnNavColumn(
    translationStore: TranslationStore,
    page: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EARN_NAV.forEach { item ->
            EarnNavChip(
                item = item,
                selected = page == item.id,
                translationStore = translationStore,
                compact = false,
                onClick = { onSelect(item.id) }
            )
        }
    }
}

@Composable
private fun EarnNavChip(
    item: EarnNavItem,
    selected: Boolean,
    translationStore: TranslationStore,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val bg = when {
        selected && item.cash -> EazColors.Orange
        selected -> Color.White
        item.cash -> EazColors.Orange.copy(alpha = 0.16f)
        compact -> Color.White.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    val fg = when {
        selected && item.cash -> Color.White
        selected -> Ink
        item.cash -> EazColors.OrangeHover
        else -> Color.White.copy(alpha = 0.82f)
    }
    Row(
        modifier = Modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!compact) {
            if (item.pip.isEmpty()) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (item.cash) EazColors.EazcBlue else EazColors.Orange,
                            CircleShape
                        )
                )
            } else {
                Text(
                    text = item.pip,
                    color = if (selected) fg else EazColors.Orange,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Text(
            text = translationStore.t(item.labelKey, item.labelFallback),
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EarnStage(
    translationStore: TranslationStore,
    page: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        when (page) {
            "start" -> EarnStartPage(translationStore, onSelect)
            else -> {
                val topic = EARN_TOPICS.firstOrNull { it.id == page } ?: EARN_TOPICS.first()
                EarnTopicPage(topic, translationStore, payout = page == "payout")
            }
        }
    }
}

@Composable
private fun EarnStartPage(translationStore: TranslationStore, onSelect: (String) -> Unit) {
    Text(
        text = translationStore.t("creator.eazc_earn.page_start_eyebrow", "Start here"),
        color = EazColors.Orange,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.6.sp
    )
    Text(
        text = translationStore.t(
            "creator.eazc_earn.page_start_title",
            "From a sale to money in your pocket"
        ),
        color = Ink,
        fontSize = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
    )
    Text(
        text = translationStore.t(
            "creator.eazc_earn.page_start_lead",
            "Every path on the left pays you from a real purchase. Open a topic — or jump in from the tiles."
        ),
        color = Color(0xFF1E3A5F),
        fontSize = 16.sp,
        lineHeight = 22.sp
    )
    Text(
        text = translationStore.t(
            "creator.eazc_earn.intro",
            "You get paid when someone actually buys a product. Earnings are tracked as EAZC first, then you convert them into shop credit or cash. This guide is sales money only — not daily game coins."
        ),
        color = Color(0xFF334155),
        fontSize = 15.sp,
        lineHeight = 21.sp,
        modifier = Modifier.padding(top = 12.dp)
    )
    val steps = listOf(
        Triple("1", "creator.eazc_earn.path_1_title", "creator.eazc_earn.path_1_body") to
            ("Someone buys" to "Print and shipping come off first. What’s left is net profit. You earn a creator share of that profit."),
        Triple("2", "creator.eazc_earn.path_2_title", "creator.eazc_earn.path_2_body") to
            ("Wait 30 days" to "New sales stay pending during the return window. Then the amount is yours to convert."),
        Triple("3", "creator.eazc_earn.path_3_title", "creator.eazc_earn.path_3_body") to
            ("Get paid" to "Convert to shop credit (+10% bonus) or send cash via Wise.")
    )
    val stepColors = listOf(EazColors.Orange, EazColors.EazcNavy, EazColors.EazcBlueDeep)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 22.dp)) {
        steps.forEachIndexed { index, (keys, fb) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(stepColors[index], CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(keys.first, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
                Column {
                    Text(
                        text = translationStore.t(keys.second, fb.first),
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = translationStore.t(keys.third, fb.second),
                        color = Color(0xFF334155),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
    val tiles = EARN_NAV.filter { it.pip.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 22.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    val community = item.id == "community" || item.id == "referral"
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .clickable { onSelect(item.id) }
                            .padding(14.dp)
                    ) {
                        Text(
                            text = item.pip,
                            color = if (community) EazColors.EazcBlueDeep else EazColors.Orange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = translationStore.t(item.labelKey, item.labelFallback),
                            color = Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(EazColors.Orange)
            .clickable { onSelect("payout") }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = translationStore.t("creator.eazc_earn.hub_cta", "See how cash-out works"),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Text("→", color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EarnTopicPage(
    topic: EarnTopic,
    translationStore: TranslationStore,
    payout: Boolean,
) {
    fun k(part: String) = "creator.eazc_earn.page_${topic.id}_$part"
    Text(
        text = translationStore.t(k("eyebrow"), topic.eyebrowFb),
        color = EazColors.Orange,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.6.sp
    )
    Text(
        text = translationStore.t(k("title"), topic.titleFb),
        color = Ink,
        fontSize = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
    )
    Text(
        text = translationStore.t(k("lead"), topic.leadFb),
        color = Color(0xFF1E3A5F),
        fontSize = 16.sp,
        lineHeight = 22.sp
    )
    Text(
        text = translationStore.t(k("body"), topic.bodyFb),
        color = Color(0xFF334155),
        fontSize = 15.sp,
        lineHeight = 21.sp,
        modifier = Modifier.padding(top = 12.dp)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .border(1.5.dp, EazColors.Orange.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(18.dp)
    ) {
        Text(
            text = translationStore.t("creator.eazc_earn.example_stamp", "Example"),
            color = if (payout) EazColors.Orange else EazColors.EazcBlueDeep,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.align(Alignment.TopEnd)
        )
        Column {
            Text(
                text = translationStore.t(k("example"), topic.exampleFb),
                color = Color(0xFF1E293B),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                modifier = Modifier.padding(end = 72.dp)
            )
            val lines = listOf(
                Triple("l1", topic.l1Fb, "v1") to topic.v1Fb,
                Triple("l2", topic.l2Fb, "v2") to topic.v2Fb,
                Triple("l3", topic.l3Fb, "v3") to topic.v3Fb,
            )
            lines.forEach { (meta, valueFb) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = translationStore.t(k(meta.first), meta.second),
                        color = Color(0xFF64748B),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Text(
                        text = translationStore.t(k(meta.third), valueFb),
                        color = Ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }
            Text(
                text = translationStore.t(
                    "creator.eazc_earn.example_note",
                    "Illustrative numbers. Your payout depends on the product, print cost, and your royalty tier."
                ),
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
    Text(
        text = translationStore.t(k("fact"), topic.factFb),
        color = Color(0xFF1E3A5F),
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier
            .padding(top = 16.dp)
            .background(EazColors.EazcBlueDeep.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .border(1.dp, EazColors.EazcBlueDeep.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    )
    if (payout) {
        Text(
            text = translationStore.t(
                "creator.eazc_earn.pending_hint",
                "New sales stay pending for 30 days (return window). After that you can convert to real money."
            ),
            color = Color(0xFF9A3412),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier
                .padding(top = 12.dp)
                .background(EazColors.Orange.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .border(1.dp, EazColors.Orange.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        )
        Text(
            text = translationStore.t(
                "creator.eazc_earn.royalty_footer",
                "Your creator share starts at 10% of net profit and can rise to 40% in Creator Journey → Royalties."
            ),
            color = Color(0xFF475569),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
        )
    } else {
        Box(modifier = Modifier.padding(bottom = 24.dp))
    }
}
