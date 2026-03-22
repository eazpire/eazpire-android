package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.ShopifyPredictiveSearchApi
import com.eazpire.creator.i18n.LocalTranslationStore
import kotlinx.coroutines.delay
import java.net.URLEncoder

@Composable
fun HeaderSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onNavigateToUrl: (String) -> Unit,
    placeholder: String = "Search...",
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val api = remember { ShopifyPredictiveSearchApi() }
    val store = LocalTranslationStore.current
    val noResultsText = store?.t("eaz.search.no_results", "No results") ?: "No results"

    var focused by remember { mutableStateOf(false) }
    var fieldBoundsWindow by remember { mutableStateOf<Rect?>(null) }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ShopifyPredictiveSearchApi.Result?>(null) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            result = null
            loading = false
            return@LaunchedEffect
        }
        delay(300)
        loading = true
        try {
            result = api.fetchSuggestions(q)
        } finally {
            loading = false
        }
    }

    val showPanel = focused &&
        query.trim().length >= 2 &&
        (loading || result != null)

    if (showPanel && fieldBoundsWindow != null) {
        val topPad = with(density) { fieldBoundsWindow!!.bottom.toDp() } + 4.dp
        Dialog(
            onDismissRequest = {
                focusManager.clearFocus()
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.22f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { focusManager.clearFocus() }
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topPad)
                        .heightIn(max = 420.dp),
                    shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
                    shadowElevation = 10.dp,
                    color = Color.White
                ) {
                    when {
                        loading && result == null -> {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = EazColors.Orange,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                        result != null && result!!.queries.isEmpty() && result!!.products.isEmpty() -> {
                            Text(
                                text = noResultsText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                style = TextStyle(fontSize = 14.sp, color = EazColors.TextSecondary),
                                textAlign = TextAlign.Center
                            )
                        }
                        result != null -> {
                            LazyColumn(Modifier.fillMaxWidth()) {
                                if (result!!.queries.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Suggestions",
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            style = TextStyle(
                                                fontSize = 11.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                color = EazColors.TextSecondary,
                                                letterSpacing = 0.8.sp
                                            )
                                        )
                                    }
                                    items(result!!.queries) { q ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    val url =
                                                        "https://www.eazpire.com/search?q=${
                                                            URLEncoder.encode(
                                                                q.text,
                                                                "UTF-8"
                                                            )
                                                        }&type=product"
                                                    focusManager.clearFocus()
                                                    onNavigateToUrl(url)
                                                }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = EazColors.TextSecondary
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                text = HtmlCompat.fromHtml(
                                                    q.styledText,
                                                    HtmlCompat.FROM_HTML_MODE_LEGACY
                                                ).toString(),
                                                style = TextStyle(fontSize = 14.sp, color = EazColors.TextPrimary),
                                                maxLines = 2
                                            )
                                        }
                                    }
                                    if (result!!.products.isNotEmpty()) {
                                        item { Divider(color = EazColors.TopbarBorder) }
                                    }
                                }
                                if (result!!.products.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "Products",
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            style = TextStyle(
                                                fontSize = 11.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                color = EazColors.TextSecondary,
                                                letterSpacing = 0.8.sp
                                            )
                                        )
                                    }
                                    items(result!!.products) { p ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    focusManager.clearFocus()
                                                    onNavigateToUrl(p.url)
                                                }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .background(
                                                        Color(0xFFF5F5F5),
                                                        RoundedCornerShape(6.dp)
                                                    )
                                            ) {
                                                if (!p.image.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = p.image,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    text = p.title,
                                                    style = TextStyle(
                                                        fontSize = 13.sp,
                                                        color = EazColors.TextPrimary,
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                                    ),
                                                    maxLines = 1
                                                )
                                                if (!p.vendor.isNullOrBlank()) {
                                                    Text(
                                                        text = p.vendor,
                                                        style = TextStyle(
                                                            fontSize = 11.sp,
                                                            color = EazColors.TextSecondary
                                                        ),
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            val priceLabel = p.priceCents?.let { c ->
                                                String.format("%.2f", c / 100.0)
                                            }
                                            if (priceLabel != null) {
                                                Text(
                                                    text = priceLabel,
                                                    style = TextStyle(
                                                        fontSize = 13.sp,
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                        color = EazColors.Orange
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .onGloballyPositioned { coords ->
                fieldBoundsWindow = coords.boundsInWindow()
            }
            .onFocusChanged { focused = it.isFocused },
        textStyle = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        placeholder = {
            Text(
                text = placeholder,
                color = EazColors.TextSecondary,
                fontSize = 14.sp
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EazColors.Orange,
            unfocusedBorderColor = EazColors.TopbarBorder,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            cursorColor = EazColors.Orange,
            focusedTextColor = EazColors.TextPrimary,
            unfocusedTextColor = EazColors.TextPrimary
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            focusManager.clearFocus()
            val q = query.trim()
            if (q.isNotBlank()) {
                onNavigateToUrl(
                    "https://www.eazpire.com/search?q=${URLEncoder.encode(q, "UTF-8")}&type=product"
                )
            } else {
                onSearch()
            }
        }),
        trailingIcon = {
            IconButton(onClick = {
                focusManager.clearFocus()
                val q = query.trim()
                if (q.isNotBlank()) {
                    onNavigateToUrl(
                        "https://www.eazpire.com/search?q=${URLEncoder.encode(q, "UTF-8")}&type=product"
                    )
                } else {
                    onSearch()
                }
            }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = EazColors.Orange
                )
            }
        }
    )
}
