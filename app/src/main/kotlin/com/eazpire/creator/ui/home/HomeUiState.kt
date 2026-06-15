package com.eazpire.creator.ui.home

import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.ui.CatalogProduct
import com.eazpire.creator.ui.HeroImage

data class HomeUiState(
    val heroImages: List<HeroImage> = emptyList(),
    val promoProducts: List<ShopifyProductsApi.ProductItem> = emptyList(),
    val sectionPools: Map<String, HomeCategoryPools> = emptyMap(),
    val createScratchCatalog: List<CatalogProduct> = emptyList(),
    val homeCreators: List<ShopCreatorCard> = emptyList(),
    val homeCreatorsSort: String = "recommend",
    val homeCreatorsLoading: Boolean = false,
    val loadCreatorsSection: Boolean = false,
    val loadingCategories: Set<String> = emptySet(),
    /** True while initial bootstrap for current locale is in flight. */
    val bootstrapInProgress: Boolean = false,
)
