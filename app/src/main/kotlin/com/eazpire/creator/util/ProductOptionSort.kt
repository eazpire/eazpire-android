package com.eazpire.creator.util

import com.eazpire.creator.api.ShopifyProductsApi

/**
 * Display order for PDP options: Paper → Color → other → Size.
 */
object ProductOptionSort {

    enum class Kind { PAPER, COLOR, OTHER, SIZE }

    data class Entry(
        val shopifyIndex: Int,
        val option: ShopifyProductsApi.ProductDetail.ProductOption,
        val kind: Kind,
    )

    fun kindForName(name: String): Kind {
        val n = name.trim().lowercase()
        return when (n) {
            "paper", "papier", "material" -> Kind.PAPER
            "color", "colour", "colors", "farbe" -> Kind.COLOR
            "size", "größe", "groesse" -> Kind.SIZE
            else -> Kind.OTHER
        }
    }

    private fun kindOrder(kind: Kind): Int = when (kind) {
        Kind.PAPER -> 0
        Kind.COLOR -> 1
        Kind.OTHER -> 2
        Kind.SIZE -> 3
    }

    fun sort(options: List<ShopifyProductsApi.ProductDetail.ProductOption>): List<Entry> =
        options.mapIndexed { index, option ->
            Entry(index, option, kindForName(option.name))
        }.sortedWith(compareBy({ kindOrder(it.kind) }, { it.shopifyIndex }))
}
