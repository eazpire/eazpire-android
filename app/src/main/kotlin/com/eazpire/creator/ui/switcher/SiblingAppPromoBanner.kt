package com.eazpire.creator.ui.switcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.shared.EazpireApps
import com.eazpire.shared.switcher.SiblingAppPromo

/**
 * Soft-launch promo when the sibling Play app is not installed (IDEA-093 Phase 4).
 */
@Composable
fun SiblingAppPromoBanner(
    target: EazpireApps.Target,
    onOpenStore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(0.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = SiblingAppPromo.title(target),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = SiblingAppPromo.body(target),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        TextButton(onClick = onOpenStore) {
            Text(SiblingAppPromo.cta(target), color = Color(0xFF93C5FD), fontSize = 12.sp)
        }
        TextButton(onClick = onDismiss) {
            Text("Not now", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}
