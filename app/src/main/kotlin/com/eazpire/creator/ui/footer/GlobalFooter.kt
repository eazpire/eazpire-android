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

private val FooterBg = Color(0xFFF2F2F2) // light, matches shop content-footer
private val FooterBorder = Color(0xFFE8E8E8)
private val FooterTextSecondary = Color(0xFF6B7280) // text-secondary
private val FooterBrand = EazColors.Orange
private val FooterTextSize = 12.sp

private const val SHOP_BASE_URL = "https://allyoucanpink.com"

/**
 * Global footer – matches shop content-footer (eaz-content-footer.liquid).
 * Terms & Policies | © year eazpire
 * Light background, top border – native implementation.
 */
@Composable
fun GlobalFooter(
    onTermsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
            text = "Terms & Policies",
            color = FooterTextSecondary,
            fontSize = FooterTextSize,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable {
                if (onTermsClick != null) {
                    onTermsClick()
                } else {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$SHOP_BASE_URL/policies/terms-of-service")))
                    } catch (_: Exception) { }
                }
            }
        )
        Text(
            text = "  ·  ",
            color = FooterTextSecondary,
            fontSize = FooterTextSize
        )
        Text(
            text = "© $year ",
            color = FooterTextSecondary,
            fontSize = FooterTextSize
        )
        Text(
            text = "eazpire",
            color = FooterBrand,
            fontSize = FooterTextSize,
            fontWeight = FontWeight.Bold
        )
    }
}
