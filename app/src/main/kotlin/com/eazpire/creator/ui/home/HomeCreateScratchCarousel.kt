package com.eazpire.creator.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazpire.creator.ui.CatalogProduct

private val ScratchShellTop = Color(0xFF111827)
private val ScratchShellBottom = Color(0xFF1F2937)
private val ScratchHeaderBg =
    Brush.linearGradient(
        colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.72f), Color(0xFFEA580C).copy(alpha = 0.62f)),
    )
private val ScratchHeaderBorder = Color(0xFFFBBF24).copy(alpha = 0.5f)
private val ScratchTitleColor = Color(0xFFF9FAFB)
private val ScratchCardTitleBg =
    Brush.linearGradient(
        colors = listOf(Color(0xFF1F2937), Color(0xFF374151)),
    )

/** Home "Create from Scratch" — matches web `eaz-home-create-scratch` dark shell + catalog cards. */
@Composable
fun HomeCreateScratchCarousel(
    title: String,
    products: List<CatalogProduct>,
    onTitleClick: (() -> Unit)? = null,
    onProductClick: (CatalogProduct) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (products.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(18.dp), clip = false)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(ScratchShellTop, ScratchShellBottom),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                .padding(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ScratchHeaderBg)
                    .border(1.dp, ScratchHeaderBorder, RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp, horizontal = 12.dp)
                    .then(
                        if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick)
                        else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ScratchTitleColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(products, key = { it.productKey }) { product ->
                    HomeCreateScratchCard(
                        product = product,
                        onClick = { onProductClick(product) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeCreateScratchCard(
    product: CatalogProduct,
    onClick: () -> Unit,
) {
    val previewUrl = product.mockUrls.firstOrNull().orEmpty()

    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .shadow(2.dp, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center,
        ) {
            if (previewUrl.isNotEmpty()) {
                AsyncImage(
                    model = previewUrl,
                    contentDescription = product.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ScratchCardTitleBg)
                .padding(horizontal = 8.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = product.title,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = ScratchTitleColor,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
