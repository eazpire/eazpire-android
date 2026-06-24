package com.eazpire.creator.ar.poster

import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.util.ProductOptionSort

data class PosterArSizeEntry(
    val label: String,
    val imageUrl: String,
    val physicalSize: PosterPhysicalSize,
    val variantId: Long,
    val price: Double,
    val available: Boolean,
)

data class PosterArSessionConfig(
    val productTitle: String,
    val sizeEntries: List<PosterArSizeEntry>,
    val paperValues: List<String>,
    val initialSizeIndex: Int,
    val initialPaperIndex: Int,
    val resolveEntry: (sizeIndex: Int, paperIndex: Int) -> PosterArSizeEntry?,
    val onSelectionChange: (sizeIndex: Int, paperIndex: Int) -> Unit,
    val onAddToCart: () -> Unit,
    val onAddToFavorite: () -> Unit,
)

object PosterArCatalog {

    fun buildSessionConfig(
        product: ShopifyProductsApi.ProductDetail,
        selectedByIndex: Map<Int, String>,
        onSelectionChange: (sizeIndex: Int, paperIndex: Int) -> Unit,
        onAddToCart: () -> Unit,
        onAddToFavorite: () -> Unit,
    ): PosterArSessionConfig? {
        val sizeIndex = product.options.indexOfFirst {
            ProductOptionSort.kindForName(it.name) == ProductOptionSort.Kind.SIZE
        }
        if (sizeIndex < 0) return null

        val paperIndex = product.options.indexOfFirst {
            ProductOptionSort.kindForName(it.name) == ProductOptionSort.Kind.PAPER
        }
        val sizeOption = product.options[sizeIndex]
        val paperValues = if (paperIndex >= 0) product.options[paperIndex].values else emptyList()
        val selectedPaper = if (paperIndex >= 0) selectedByIndex[paperIndex].orEmpty() else ""

        val entries = sizeOption.values.mapNotNull { sizeLabel ->
            buildSizeEntry(product, sizeIndex, paperIndex, sizeLabel, selectedPaper)
        }
        if (entries.isEmpty()) return null

        val selectedSize = selectedByIndex[sizeIndex].orEmpty()
        val initialSizeIndex = sizeOption.values.indexOfFirst { it.equals(selectedSize, ignoreCase = true) }
            .takeIf { it >= 0 } ?: 0
        val initialPaperIndex = if (paperValues.isNotEmpty()) {
            paperValues.indexOfFirst { it.equals(selectedPaper, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
        } else {
            0
        }

        return PosterArSessionConfig(
            productTitle = product.title,
            sizeEntries = entries,
            paperValues = paperValues,
            initialSizeIndex = initialSizeIndex.coerceIn(0, entries.lastIndex),
            initialPaperIndex = initialPaperIndex,
            resolveEntry = resolveEntry@{ sizeIdx, paperIdx ->
                val sizeLabel = sizeOption.values.getOrNull(sizeIdx) ?: return@resolveEntry null
                val paperLabel = paperValues.getOrNull(paperIdx).orEmpty()
                buildSizeEntry(product, sizeIndex, paperIndex, sizeLabel, paperLabel)
            },
            onSelectionChange = onSelectionChange,
            onAddToCart = onAddToCart,
            onAddToFavorite = onAddToFavorite,
        )
    }

    fun resolveVariant(
        product: ShopifyProductsApi.ProductDetail,
        sizeIndex: Int,
        paperIndex: Int,
        sizeLabel: String,
        paperLabel: String,
    ): ShopifyProductsApi.ProductDetail.ProductVariant? {
        return product.variants.find { variant ->
            val vals = listOfNotNull(variant.option1, variant.option2, variant.option3)
            vals.getOrNull(sizeIndex).equals(sizeLabel, ignoreCase = true) &&
                (paperIndex < 0 || vals.getOrNull(paperIndex).equals(paperLabel, ignoreCase = true))
        }
    }

    private fun buildSizeEntry(
        product: ShopifyProductsApi.ProductDetail,
        sizeIndex: Int,
        paperIndex: Int,
        sizeLabel: String,
        selectedPaper: String,
    ): PosterArSizeEntry? {
        val variant = resolveVariant(product, sizeIndex, paperIndex, sizeLabel, selectedPaper)
            ?: product.variants.find { v ->
                val vals = listOfNotNull(v.option1, v.option2, v.option3)
                vals.getOrNull(sizeIndex).equals(sizeLabel, ignoreCase = true)
            }
            ?: return null

        val imageUrl = resolvePreviewImage(product, variant) ?: return null
        return PosterArSizeEntry(
            label = sizeLabel,
            imageUrl = imageUrl,
            physicalSize = PosterArDimensions.parseMeters(sizeLabel),
            variantId = variant.id,
            price = variant.price,
            available = variant.available,
        )
    }

    private fun resolvePreviewImage(
        product: ShopifyProductsApi.ProductDetail,
        variant: ShopifyProductsApi.ProductDetail.ProductVariant,
    ): String? {
        variant.featuredImageSrc?.takeIf { it.isNotBlank() }?.let { return it }

        product.images.firstOrNull { img ->
            img.variantIds.contains(variant.id) &&
                img.alt?.contains("preview-default", ignoreCase = true) == true
        }?.src?.let { return it }

        product.images.firstOrNull { img ->
            img.variantIds.contains(variant.id)
        }?.src?.let { return it }

        return product.images.firstOrNull { img ->
            img.alt?.contains("preview-default", ignoreCase = true) == true
        }?.src ?: product.images.firstOrNull()?.src
    }
}
