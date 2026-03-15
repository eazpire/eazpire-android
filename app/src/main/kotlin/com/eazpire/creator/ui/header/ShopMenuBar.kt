package com.eazpire.creator.ui.header

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors

private data class MenuItem(val label: String, val url: String)

private val MENU_ITEMS = listOf(
    MenuItem("Women", "https://www.eazpire.com/collections/women"),
    MenuItem("Men", "https://www.eazpire.com/collections/men"),
    MenuItem("Kids", "https://www.eazpire.com/collections/kids"),
    MenuItem("Toddler", "https://www.eazpire.com/collections/toddler"),
    MenuItem("Personalize", "https://www.eazpire.com/pages/design-generator"),
    MenuItem("Generate", "https://www.eazpire.com/pages/design-generator"),
)

@Composable
fun ShopMenuBar(
    onAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val menuBg = EazColors.Orange.copy(alpha = 0.95f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(menuBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clickable(onClick = onAllClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "All",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Alle",
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(Color.White.copy(alpha = 0.2f))
                    .align(Alignment.CenterStart)
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(MENU_ITEMS) { item ->
                    Box(
                        modifier = Modifier
                            .clickable {
                                try {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                    )
                                } catch (_: Exception) {}
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = item.label,
                            color = Color.White.copy(alpha = 0.95f),
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
