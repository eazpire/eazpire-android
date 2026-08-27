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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyPredictiveSearchApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.api.UniversalSearchApi
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.mockup.CustomerMockPreviewStore
import com.eazpire.creator.plp.PlpCardDisplay
import com.eazpire.creator.plp.PlpCardImageResolver
import com.eazpire.creator.ui.components.EazProductCardRotatingImages
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
    onClose: (() -> Unit)? = null,
    ownerId: String = "",
    creatorApi: CreatorApi? = null,
    mockPreviewRevision: Int = 0,
    placeholder: String = "Search...",
    /** Full-screen modal: close X beside search field, results fill remaining height */
    fullscreen: Boolean = false,
    onCreateProductFromRefSearch: (RefSearchCreateProductRequest) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val api = remember { ShopifyPredictiveSearchApi() }
    val universalApi = remember { UniversalSearchApi(creatorApi ?: CreatorApi()) }
    val store = LocalTranslationStore.current
    val noResultsText = store?.t("eaz.search.no_results", "No results") ?: "No results"
    val searchErrorText = store?.t("eaz.search.error", "Search failed. Please try again.") ?: "Search failed. Please try again."
    val closeSearchText = store?.t("eaz.search.close_aria", "Close search") ?: "Close search"
    val suggestionsLabel = store?.t("eaz.search.section_suggestions", "Suggestions") ?: "Suggestions"
    val productsLabel = store?.t("eaz.search.section_products", "Products") ?: "Products"
    val loadingMoreText = store?.t("eaz.search.loading_more", "Loading more results…") ?: "Loading more results…"
    val refSearchAria = store?.t("eaz.reference_search.open_aria", "Reference search with image")
        ?: "Reference search with image"

    val sectionTitleStyle = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = EazColors.TextSecondary,
        letterSpacing = 0.8.sp
    )
    val searchTextStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        color = EazColors.TextPrimary
    )

    var focused by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ShopifyPredictiveSearchApi.PredictiveSearchState?>(null) }
    var showRefSearch by remember { mutableStateOf(false) }
    var refBadgeCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(fullscreen, ownerId, creatorApi) {
        if (!fullscreen || ownerId.isBlank() || creatorApi == null) {
            refBadgeCount = 0
            return@LaunchedEffect
        }
        try {
            val res = creatorApi.referenceSearchBadge(ownerId)
            refBadgeCount = res.optInt("count", 0)
        } catch (_: Exception) {
            refBadgeCount = 0
        }
    }

    LaunchedEffect(fullscreen) {
        if (fullscreen) {
            delay(40)
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            result = null
            loading = false
            searchError = false
            return@LaunchedEffect
        }
        loading = true
        searchError = false
        result = null
        delay(300)
        if (query.trim() != q) return@LaunchedEffect
        try {
            val worker = universalApi.search(q, mode = "products", phase = "suggest", limit = 24)
            if (worker != null && (worker.products.isNotEmpty() || worker.queries.isNotEmpty())) {
                if (query.trim() == q) {
                    result = ShopifyPredictiveSearchApi.PredictiveSearchState(
                        queries = worker.queries.map {
                            ShopifyPredictiveSearchApi.QuerySuggestion(it.text, it.styledText)
                        },
                        products = worker.products.map { p ->
                            ShopifyPredictiveSearchApi.PredictiveProductRow(
                                handle = p.handle,
                                url = p.url.ifBlank { "/products/${p.handle}" },
                                images = listOfNotNull(p.image),
                                title = p.title,
                                priceCents = null,
                                vendor = p.vendor,
                            )
                        },
                        sectionStillLoading = false,
                    )
                    loading = false
                    searchError = false
                }
            } else {
                api.collectPredictiveSearch(q) { state ->
                    if (query.trim() == q) {
                        result = state
                        loading = false
                        searchError = false
                    }
                }
            }
        } catch (_: Exception) {
            if (query.trim() == q) {
                loading = false
                result = null
                searchError = true
            }
        }
    }

    val showPanel = query.trim().length >= 2 && (fullscreen || focused)

    Column(
        modifier = if (fullscreen) modifier.fillMaxSize() else modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 8.dp,
                    end = if (fullscreen && onClose != null) 4.dp else 8.dp,
                    top = if (fullscreen) 8.dp else 0.dp,
                    bottom = if (fullscreen) 8.dp else 0.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
                textStyle = searchTextStyle,
                placeholder = {
                    Text(
                        text = placeholder,
                        style = searchTextStyle.copy(color = EazColors.TextSecondary),
                        maxLines = 1
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (fullscreen && creatorApi != null && ownerId.isNotBlank()) {
                            IconButton(onClick = { showRefSearch = true }) {
                                BadgedBox(
                                    badge = {
                                        if (refBadgeCount > 0) {
                                            Badge { Text(refBadgeCount.coerceAtMost(9).toString()) }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = refSearchAria,
                                        tint = EazColors.Orange
                                    )
                                }
                            }
                        }
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
                }
            )
            if (onClose != null) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onClose()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = closeSearchText,
                        tint = EazColors.TextPrimary
                    )
                }
            }
        }

        if (fullscreen) {
            HorizontalDivider(color = EazColors.TopbarBorder, thickness = 1.dp)
        }

        if (showPanel) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fullscreen) Modifier.weight(1f) else Modifier)
                    .background(Color.White)
            ) {
                PredictiveSearchResultsBody(
                    loading = loading,
                    searchError = searchError,
                    result = result,
                    noResultsText = noResultsText,
                    searchErrorText = searchErrorText,
                    loadingMoreText = loadingMoreText,
                    suggestionsLabel = suggestionsLabel,
                    productsLabel = productsLabel,
                    sectionTitleStyle = sectionTitleStyle,
                    onSubmitSearchQuery = onSubmitSearchQuery,
                    onNavigateToUrl = onNavigateToUrl,
                    ownerId = ownerId,
                    creatorApi = creatorApi,
                    mockPreviewRevision = mockPreviewRevision,
                    fillHeight = fullscreen,
                    searchQuery = query,
                )
            }
        }
    }

    if (showRefSearch && creatorApi != null && ownerId.isNotBlank()) {
        ReferenceSearchModal(
            visible = true,
            ownerId = ownerId,
            creatorApi = creatorApi,
            onDismiss = { showRefSearch = false },
            onNavigateToUrl = onNavigateToUrl,
            onCreateProduct = onCreateProductFromRefSearch,
        )
    }
}

@Composable
private fun PredictiveSearchResultsBody(
    loading: Boolean,
    searchError: Boolean,
    result: ShopifyPredictiveSearchApi.PredictiveSearchState?,
    noResultsText: String,
    searchErrorText: String,
    loadingMoreText: String,
    suggestionsLabel: String,
    productsLabel: String,
    sectionTitleStyle: TextStyle,
    onSubmitSearchQuery: (String) -> Unit,
    onNavigateToUrl: (String) -> Unit,
    ownerId: String,
    creatorApi: CreatorApi?,
    mockPreviewRevision: Int,
    fillHeight: Boolean,
    searchQuery: String = "",
) {
    when {
        searchError && !loading -> {
            Text(
                text = searchErrorText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                style = TextStyle(fontSize = 14.sp, color = EazColors.TextSecondary),
                textAlign = TextAlign.Center
            )
        }
        loading && result == null -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (fillHeight) Modifier.fillMaxSize() else Modifier)
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
            result.queries.isEmpty() &&
            result.products.isEmpty() &&
            result.sectionStillLoading -> {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (fillHeight) Modifier.fillMaxSize() else Modifier)
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
            result.queries.isEmpty() &&
            result.products.isEmpty() &&
            !result.sectionStillLoading -> {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = noResultsText,
                    style = TextStyle(fontSize = 14.sp, color = EazColors.TextSecondary),
                    textAlign = TextAlign.Center
                )
                com.eazpire.creator.ui.designrequest.ShopDesignRequestCta(query = searchQuery)
            }
        }
        result != null -> {
            val r = result
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fillHeight) Modifier.fillMaxSize() else Modifier),
                contentPadding = PaddingValues(
                    horizontal = 12.dp,
                    vertical = 10.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (r.queries.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
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
                    item(span = { GridItemSpan(2) }) {
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
                        PredictiveMockImageGridCell(
                            handle = p.handle,
                            shopUrls = p.images,
                            ownerId = ownerId,
                            creatorApi = creatorApi,
                            mockPreviewRevision = mockPreviewRevision
                        )
                    }
                }
                item(span = { GridItemSpan(2) }, key = "design-request-cta") {
                    com.eazpire.creator.ui.designrequest.ShopDesignRequestCta(query = searchQuery)
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
private fun PredictiveMockImageGridCell(
    handle: String,
    shopUrls: List<String>,
    ownerId: String = "",
    creatorApi: CreatorApi? = null,
    mockPreviewRevision: Int = 0
) {
    val context = LocalContext.current
    val product = remember(handle, shopUrls) {
        ShopifyProductsApi.ProductItem(
            id = 0L,
            title = handle,
            handle = handle,
            images = shopUrls,
            variantImages = shopUrls,
            url = ""
        )
    }
    var display by remember(handle, mockPreviewRevision) {
        mutableStateOf(PlpCardDisplay(shopUrls, isPersonalizedMock = false))
    }
    LaunchedEffect(handle, shopUrls, ownerId, mockPreviewRevision) {
        if (ownerId.isBlank() || creatorApi == null || handle.isBlank()) {
            display = PlpCardDisplay(shopUrls, isPersonalizedMock = false)
            return@LaunchedEffect
        }
        val map = CustomerMockPreviewStore.peekMap(ownerId)
            ?: CustomerMockPreviewStore.loadMap(creatorApi, ownerId)
        val sessionActive = CustomerMockPreviewStore.isTryOnSessionActive(context, handle)
        display = PlpCardImageResolver.resolve(
            context = context,
            creatorApi = creatorApi,
            ownerId = ownerId,
            product = product,
            mockMap = map,
            sessionTryOnActive = sessionActive,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        if (display.urls.isNotEmpty()) {
            EazProductCardRotatingImages(
                imageUrls = display.urls,
                productId = handle,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                targetWidthPx = 400,
                fullResolution = display.isPersonalizedMock,
                autoRotate = display.autoRotate,
            )
        }
    }
}
