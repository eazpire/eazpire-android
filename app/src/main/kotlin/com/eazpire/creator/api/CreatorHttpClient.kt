package com.eazpire.creator.api

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared OkHttp client — connection reuse across [CreatorApi] instances. */
object CreatorHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Daily mini-games (Simon begin) can be slow on cold D1 — allow extra read time. */
    val dailyGameInstance: OkHttpClient by lazy {
        instance.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Read-mostly home/catalog ops — skip cache-busting query param for HTTP/CDN reuse. */
    val cacheableGetOps: Set<String> = setOf(
        "list-home-carousel-products",
        "list-home-carousel-bootstrap",
        "list-active-shop-promotion-products",
        "get-shop-create-product-catalog",
        "get-catalog-products",
        "get-storefront-products",
    )

    fun shouldCacheBust(op: String): Boolean = op !in cacheableGetOps
}
