package com.eazpire.creator.api

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Shopify Cart API – add to cart, sync cookies for WebView.
 * Cart persists via Shopify cookies (shared with checkout WebView).
 */
class ShopifyCartApi(
    private val storeUrl: String = "https://www.eazpire.com",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()
) {
    private val storeDomain = storeUrl
        .replace(Regex("^https?://"), "")
        .replace(Regex("/.*$"), "")

    data class AddResult(val ok: Boolean, val itemCount: Int, val message: String?)

    /**
     * Add variant to cart. Sets cookie from response for WebView cart/checkout.
     */
    suspend fun addToCart(variantId: Long, quantity: Int): AddResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("id", variantId)
            put("quantity", quantity.coerceAtLeast(1))
        }.toString()
        val request = Request.Builder()
            .url("$storeUrl/cart/add.js")
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Accept", "application/json")
            .build()
        try {
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: "{}"
            val json = try { JSONObject(respBody) } catch (_: Exception) { JSONObject() }

            response.headers("Set-Cookie").forEach { cookieHeader ->
                val cookie = cookieHeader.split(";").firstOrNull()?.trim() ?: return@forEach
                CookieManager.getInstance().setCookie(storeDomain, cookie)
            }

            val itemCount = json.optInt("item_count", 0)
            val ok = response.isSuccessful && !json.has("description")
            AddResult(ok = ok, itemCount = itemCount, message = json.optString("description").takeIf { it.isNotBlank() })
        } catch (e: Exception) {
            AddResult(ok = false, itemCount = 0, message = e.message)
        }
    }

    /**
     * Fetch cart and return item count. Uses cookies from CookieManager (set by addToCart or WebView).
     */
    suspend fun getCartItemCount(): Int = withContext(Dispatchers.IO) {
        val cookie = CookieManager.getInstance().getCookie(storeUrl) ?: ""
        val request = Request.Builder()
            .url("$storeUrl/cart.js")
            .addHeader("Accept", "application/json")
            .addHeader("Cookie", cookie)
            .build()
        try {
            val response = client.newCall(request).execute()
            val json = try { JSONObject(response.body?.string() ?: "{}") } catch (_: Exception) { JSONObject() }
            json.optInt("item_count", 0)
        } catch (_: Exception) {
            0
        }
    }
}
