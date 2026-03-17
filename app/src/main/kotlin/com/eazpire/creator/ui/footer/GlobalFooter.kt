package com.eazpire.creator.ui.footer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.LocalTranslationStore

private val FooterBg = Color(0xFFF2F2F2) // light, matches shop content-footer
private val FooterBorder = Color(0xFFE8E8E8)
private val FooterTextSecondary = Color(0xFF6B7280) // text-secondary
private val FooterBrand = EazColors.Orange

private const val TERMS_URL = "https://allyoucanpink.com/policies/terms-of-service"

/**
 * Global footer – matches shop content-footer (eaz-content-footer.liquid).
 * Terms & Policies | © year eazpire
 * Light background, top border – native implementation.
 */
@Composable
fun GlobalFooter(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val t = com.eazpire.creator.i18n.LocalTranslationStore.current?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }
    val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = FooterBorder,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
            }
            .background(FooterBg)
            .padding(horizontal = 12.dp)
            .padding(vertical = 4.dp)
            .padding(bottom = 12.dp), // safe area
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = t("footer.terms_policies", "Terms & Policies"),
            color = FooterTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TERMS_URL)))
                } catch (_: Exception) { }
            }
        )
        Text(
            text = "  ·  ",
            color = FooterTextSecondary,
            fontSize = 9.sp
        )
        Text(
            text = "© $year ",
            color = FooterTextSecondary,
            fontSize = 9.sp
        )
        Text(
            text = "eazpire",
            color = FooterBrand,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
