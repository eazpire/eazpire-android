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

    /** Read-mostly home carousel ops — skip cache-busting query param for HTTP/CDN reuse. */
    val cacheableGetOps: Set<String> = setOf(
        "list-home-carousel-products",
        "list-home-carousel-bootstrap",
        "list-active-shop-promotion-products",
    )

    fun shouldCacheBust(op: String): Boolean = op !in cacheableGetOps
}
