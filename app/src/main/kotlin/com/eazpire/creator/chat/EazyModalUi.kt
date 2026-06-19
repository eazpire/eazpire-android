package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Web: `.creator-chat__view-header` / `.creator-chat__header` orange gradient. */
val EazyOrangeHeaderGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFF97316), Color(0xFFEA580C)),
)

/** Web `eazy-functions.js` CAT_COLORS accents. */
internal fun eazyCategoryAccent(cat: EazyFeatureCategory): Color = when (cat) {
    EazyFeatureCategory.Shared -> Color(0xFFF97316)
    EazyFeatureCategory.Shop -> Color(0xFF3B82F6)
    EazyFeatureCategory.Creator -> Color(0xFF8B5CF6)
}

internal fun eazyCategoryTint(cat: EazyFeatureCategory): Color = when (cat) {
    EazyFeatureCategory.Shared -> Color(0xFFF97316).copy(alpha = 0.06f)
    EazyFeatureCategory.Shop -> Color(0xFF3B82F6).copy(alpha = 0.06f)
    EazyFeatureCategory.Creator -> Color(0xFF8B5CF6).copy(alpha = 0.06f)
}

/** Web: `.creator-chat__feed-scope-tabs` (User / System). */
@Composable
fun EazyFeedScopeTabRow(
    tabs: List<Pair<String, String>>,
    activeKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalEazyModalPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.header)
            .border(
                width = 1.dp,
                color = if (palette.bg == Color.White) palette.border else Color.White.copy(alpha = 0.06f),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { (key, label) ->
            val active = key == activeKey
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (active) palette.accent.copy(alpha = 0.12f)
                        else palette.muted.copy(alpha = if (palette.bg == Color.White) 0.06f else 0.04f),
                    )
                    .border(
                        1.dp,
                        if (active) palette.accent.copy(alpha = 0.45f)
                        else palette.border.copy(alpha = if (palette.bg == Color.White) 1f else 0.12f),
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(key) }
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) palette.accent else palette.muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Web: `.creator-chat__notif-tabs` (Unread / Read underline tabs). */
@Composable
fun EazyUnderlineTabRow(
    tabs: List<Pair<String, String>>,
    activeKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalEazyModalPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.header)
            .border(
                width = 1.dp,
                color = palette.border.copy(alpha = if (palette.bg == Color.White) 1f else 0.35f),
            ),
    ) {
        tabs.forEach { (key, label) ->
            val active = key == activeKey
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(key) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) palette.accent else palette.muted,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (active) 2.dp else 0.dp)
                        .background(if (active) palette.accent else Color.Transparent),
                )
            }
        }
    }
}
