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

    /** Unlock Tree can exceed 30s when catalog sync is cold; keep a dedicated client. */
    val journeyInstance: OkHttpClient by lazy {
        instance.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
    val dailyGameInstance: OkHttpClient by lazy {
        instance.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /** Saved-design crop (decode + PNG crop + preview encode) can exceed 30s. */
    val longEditInstance: OkHttpClient by lazy {
        instance.newBuilder()
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Live generate NDJSON can run longer than a normal JSON call. */
    val streamInstance: OkHttpClient by lazy {
        instance.newBuilder()
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
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
