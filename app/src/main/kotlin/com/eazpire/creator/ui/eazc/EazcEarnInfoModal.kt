package com.eazpire.creator.ui.eazc

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.modal.EazFullScreenDialog

private data class EazcEarnCard(
    val index: String,
    val titleKey: String,
    val titleFallback: String,
    val teaserKey: String,
    val teaserFallback: String,
    val bodyKey: String,
    val bodyFallback: String,
    val community: Boolean
)

private val EARN_CARDS = listOf(
    EazcEarnCard("01", "creator.eazc_earn.card_products_title", "Your products", "creator.eazc_earn.card_products_teaser", "You sell a design you published.", "creator.eazc_earn.card_products_body", "You receive your royalty share of the net profit (10–40%). Community cashback and referrals are paid from the separate community share.", false),
    EazcEarnCard("02", "creator.eazc_earn.card_remix_title", "Remixes", "creator.eazc_earn.card_remix_teaser", "Someone remixed your design — or you remixed theirs.", "creator.eazc_earn.card_remix_body", "From the creator share: the remix seller receives 87.5% and the original creator receives 12.5%.", false),
    EazcEarnCard("03", "creator.eazc_earn.card_qi_title", "Quick Inspirations", "creator.eazc_earn.card_qi_teaser", "Someone sold a design made from your Quick Inspiration.", "creator.eazc_earn.card_qi_body", "The Quick Inspiration author receives 5 royalty points from the seller’s creator share (half of that share at the 10% starter royalty). Automatic trend inspirations do not pay.", false),
    EazcEarnCard("04", "creator.eazc_earn.card_shop_title", "Shop designs", "creator.eazc_earn.card_shop_teaser", "Your design is used on a shop or custom product.", "creator.eazc_earn.card_shop_body", "Up to 3 designs on one product share the creator amount equally. If a slot is a remix, that slot still splits 87.5% / 12.5%.", false),
    EazcEarnCard("05", "creator.eazc_earn.card_community_title", "Creator Community", "creator.eazc_earn.card_community_teaser", "You and your recruiter both opted in.", "creator.eazc_earn.card_community_body", "The seller keeps 70% of the creator share. The recruiter who invited them receives 30%. Publish Assist does not add an extra split.", true),
    EazcEarnCard("06", "creator.eazc_earn.card_referral_title", "Referrals & cashback", "creator.eazc_earn.card_referral_teaser", "Someone buys with your link — or you buy while logged in.", "creator.eazc_earn.card_referral_body", "From the community share: the buyer receives 20% cashback, your direct referral link earns 10%, and further network levels earn smaller shares.", true),
)

@Composable
fun EazcEarnInfoModal(
    visible: Boolean,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    EazFullScreenDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(EazColors.EazcNavyMid, EazColors.EazcNavy)
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(EazColors.Orange.copy(alpha = 0.22f), Color.Transparent),
                            radius = 900f
                        )
                    )
            )
            Column(modifier = Modifier.fillMaxSize()) {
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
                            text = translationStore.t("creator.eazc_earn.kicker", "Sales earnings"),
                            color = EazColors.OrangeHover,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        )
                        Text(
                            text = translationStore.t("creator.eazc_earn.title", "How you earn EAZC"),
                            color = Color.White,
                            fontSize = 26.sp,
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = translationStore.t(
                            "creator.eazc_earn.intro",
                            "EAZC is credited when someone actually buys a product. This page covers sales earnings only — not daily game coins."
                        ),
                        color = Color.White,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    )
                    Text(
                        text = translationStore.t(
                            "creator.eazc_earn.pending_hint",
                            "New sales EAZC stays pending for 30 days (return window), then it becomes available to cash out or convert."
                        ),
                        color = Color(0xFFDBEAFE),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EazColors.EazcBlue.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF93C5FD).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    )
                    EARN_CARDS.forEach { card ->
                        EazcEarnCardBlock(card, translationStore)
                    }
                    Text(
                        text = translationStore.t(
                            "creator.eazc_earn.royalty_footer",
                            "Your creator share starts at 10% of net profit and can rise to 40% in Creator Journey → Royalties."
                        ),
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EazcEarnCardBlock(card: EazcEarnCard, translationStore: TranslationStore) {
    var open by remember { mutableStateOf(false) }
    val accent = if (card.community) EazColors.EazcBlueDeep else EazColors.Orange
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(Color.White, RoundedCornerShape(16.dp))
            .clickable { open = !open }
            .animateContentSize()
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(accent, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
        )
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp).weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = card.index,
                    color = accent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = translationStore.t(card.titleKey, card.titleFallback),
                        color = EazColors.EazcNavy,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = translationStore.t(card.teaserKey, card.teaserFallback),
                        color = Color(0xFF475569),
                        fontSize = 13.sp
                    )
                }
            }
            if (open) {
                Text(
                    text = translationStore.t(card.bodyKey, card.bodyFallback),
                    color = Color(0xFF1E293B),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
