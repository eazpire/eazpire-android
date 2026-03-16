package com.eazpire.creator.ui.header

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import kotlin.math.roundToInt

private data class DrawerItem(
    val label: String,
    val collectionHandle: String?,
    val url: String
)

private val DRAWER_ITEMS = listOf(
    DrawerItem("Women", "women", "https://www.eazpire.com/collections/women"),
    DrawerItem("Men", "men", "https://www.eazpire.com/collections/men"),
    DrawerItem("Kids", "kids", "https://www.eazpire.com/collections/kids"),
    DrawerItem("Toddler", "toddler", "https://www.eazpire.com/collections/toddler"),
    DrawerItem("Home & Living", "home-living", "https://www.eazpire.com/collections/home-living"),
    DrawerItem("Personalize", null, "https://www.eazpire.com/pages/design-generator"),
    DrawerItem("Generate", null, "https://www.eazpire.com/pages/design-generator"),
)

@Composable
fun MenuDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    onCategoryClick: ((title: String, handle: String, productType: String?) -> Unit)? = null,
    onExternalUrl: ((url: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val drawerWidthPx = with(density) {
                minOf(maxWidth * 0.85f, 340.dp).toPx()
            }
            var isEntered by remember { mutableStateOf(false) }
            var isExiting by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) { isEntered = true }

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

            fun dismissWithAnimation() {
                isExiting = true
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { dismissWithAnimation() }
                )
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 340.dp)
                        .fillMaxWidth(0.85f)
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(offsetXPx.roundToInt(), 0) }
                        .background(Color(0xFFFEF5ED))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(EazColors.Orange)
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Alle",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        IconButton(onClick = { dismissWithAnimation() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp)
                    ) {
                        DRAWER_ITEMS.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (item.collectionHandle != null && onCategoryClick != null) {
                                            onCategoryClick(item.label, item.collectionHandle, null)
                                            dismissWithAnimation()
                                        } else if (onExternalUrl != null) {
                                            onExternalUrl(item.url)
                                            dismissWithAnimation()
                                        } else {
                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                                )
                                                dismissWithAnimation()
                                            } catch (_: Exception) {}
                                        }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = EazColors.TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
