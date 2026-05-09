@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.eazpire.creator.ui.header

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.ShopifyPredictiveSearchApi
import com.eazpire.creator.i18n.LocalTranslationStore
import kotlinx.coroutines.delay

@Composable
fun HeaderSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    /** Native shop search results for query text (suggestions + search submit) */
    onSubmitSearchQuery: (String) -> Unit,
    /** Product URLs from predictive list — open native PDP */
    onNavigateToUrl: (String) -> Unit,
    placeholder: String = "Search...",
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val api = remember { ShopifyPredictiveSearchApi() }
    val store = LocalTranslationStore.current
    val noResultsText = store?.t("eaz.search.no_results", "No results") ?: "No results"
    val suggestionsLabel = store?.t("eaz.search.section_suggestions", "Suggestions") ?: "Suggestions"
    val productsLabel = store?.t("eaz.search.section_products", "Products") ?: "Products"
    val loadingMoreText = store?.t("eaz.search.loading_more", "Loading more results…") ?: "Loading more results…"

    val sectionTitleStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = EazColors.TextSecondary,
        letterSpacing = 0.8.sp
    )

    var focused by remember { mutableStateOf(false) }
    var fieldHeightPx by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ShopifyPredictiveSearchApi.PredictiveSearchState?>(null) }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            result = null
            loading = false
            return@LaunchedEffect
        }
        delay(300)
        loading = true
        result = null
        try {
            api.collectPredictiveSearch(q) { state ->
                result = state
                loading = false
            }
        } finally {
            loading = false
        }
    }

    val showPanel = focused &&
        query.trim().length >= 2 &&
        (loading || result != null)

    Box(modifier = modifier.fillMaxWidth()) {
        Column {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .onGloballyPositioned { coords ->
                        fieldHeightPx = coords.size.height
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
                    val q = query.trim()
                    if (q.isNotBlank()) {
                        onSubmitSearchQuery(q)
                    } else {
                        onSearch()
                    }
                }),
                trailingIcon = {
                    IconButton(onClick = {
                        val q = query.trim()
                        if (q.isNotBlank()) {
                            onSubmitSearchQuery(q)
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

        if (showPanel && fieldHeightPx > 0) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, fieldHeightPx),
                onDismissRequest = { },
                properties = PopupProperties(
                    focusable = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    clippingEnabled = false
                )
            ) {
                val maxH = with(density) { 520.dp }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxH),
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
                        result != null &&
                            result!!.queries.isEmpty() &&
                            result!!.products.isEmpty() &&
                            result!!.sectionStillLoading -> {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = EazColors.Orange,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = loadingMoreText,
                                        style = TextStyle(fontSize = 13.sp, color = EazColors.TextSecondary),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        result != null &&
                            result!!.queries.isEmpty() &&
                            result!!.products.isEmpty() &&
                            !result!!.sectionStillLoading -> {
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
                            val r = result!!
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = maxH),
                                contentPadding = PaddingValues(
                                    horizontal = 12.dp,
                                    vertical = 10.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (r.queries.isNotEmpty()) {
                                    item(span = { GridItemSpan(3) }) {
                                        Column {
                                            Text(
                                                text = suggestionsLabel,
                                                modifier = Modifier.padding(bottom = 8.dp),
                                                style = sectionTitleStyle
                                            )
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                r.queries.forEach { q ->
                                                    SearchSuggestionBadge(
                                                        styledHtml = q.styledText,
                                                        onClick = { onSubmitSearchQuery(q.text.trim()) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (r.products.isNotEmpty() || r.sectionStillLoading) {
                                    item(span = { GridItemSpan(3) }) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                        ) {
                                            Text(text = productsLabel, style = sectionTitleStyle)
                                            if (r.sectionStillLoading) {
                                                Spacer(Modifier.width(10.dp))
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    color = EazColors.Orange,
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    }
                                }
                                items(
                                    items = r.products,
                                    key = { it.handle }
                                ) { p ->
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFF5F5F5))
                                            .clickable { onNavigateToUrl(p.url) }
                                    ) {
                                        PredictiveMockImageGridCell(urls = p.images)
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

@Composable
private fun SearchSuggestionBadge(
    styledHtml: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF7F7F7),
        border = BorderStroke(1.dp, EazColors.TopbarBorder)
    ) {
        Text(
            text = HtmlCompat.fromHtml(styledHtml, HtmlCompat.FROM_HTML_MODE_LEGACY).toString(),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = TextStyle(fontSize = 13.sp, color = EazColors.TextPrimary),
            maxLines = 2
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PredictiveMockImageGridCell(urls: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        when {
            urls.isEmpty() -> Unit
            urls.size == 1 -> {
                AsyncImage(
                    model = urls[0],
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                val state = rememberPagerState(pageCount = { urls.size })
                HorizontalPager(
                    state = state,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    AsyncImage(
                        model = urls[page],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
