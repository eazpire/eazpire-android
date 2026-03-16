package com.eazpire.creator.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Storefront Cart API – native cart via Worker.
 * Uses cartCreate, cartLineAdd, cart query. Cart ID persisted in StorefrontCartStore.
 */
class ShopifyStorefrontCartApi(
    private val workerUrl: String = "https://creator-engine.eazpire.workers.dev",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    data class CartLine(
        val id: String,
        val quantity: Int,
        val variantId: String,
        val title: String,
        val productTitle: String,
        val imageUrl: String?,
        val priceAmount: String,
        val currencyCode: String
    )

    data class CartResult(
        val cartId: String,
        val checkoutUrl: String,
        val lines: List<CartLine>,
        val itemCount: Int
    )

    data class CreateResult(val ok: Boolean, val cartId: String?, val checkoutUrl: String?, val message: String?)
    data class AddResult(val ok: Boolean, val cart: CartResult?, val message: String?)

    private fun parseCart(json: JSONObject): CartResult? {
        val cart = json.optJSONObject("cart") ?: return null
        val id = cart.optString("id").takeIf { it.isNotBlank() } ?: return null
        val checkoutUrl = cart.optString("checkoutUrl", "")
        val linesArr = cart.optJSONObject("lines")?.optJSONArray("nodes") ?: JSONArray()
        val lines = (0 until linesArr.length()).mapNotNull { i ->
            val node = linesArr.optJSONObject(i) ?: return@mapNotNull null
            val merch = node.optJSONObject("merchandise") ?: return@mapNotNull null
            val product = merch.optJSONObject("product") ?: JSONObject()
            val price = merch.optJSONObject("price") ?: JSONObject()
            CartLine(
                id = node.optString("id"),
                quantity = node.optInt("quantity", 1),
                variantId = merch.optString("id").substringAfterLast("/"),
                title = merch.optString("title"),
                productTitle = product.optString("title"),
                imageUrl = merch.optJSONObject("image")?.optString("url")?.takeIf { it.isNotBlank() },
                priceAmount = price.optString("amount", "0"),
                currencyCode = price.optString("currencyCode", "CHF")
            )
        }
        val itemCount = lines.sumOf { it.quantity }
        return CartResult(cartId = id, checkoutUrl = checkoutUrl, lines = lines, itemCount = itemCount)
    }

    suspend fun createCart(
        lines: List<Pair<Long, Int>>,
        customerAccessToken: String? = null
    ): CreateResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("lines", JSONArray().apply {
                lines.forEach { (variantId, qty) ->
                    put(JSONObject().apply {
                        put("variantId", variantId)
                        put("quantity", qty)
                    })
                }
            })
            customerAccessToken?.takeIf { it.isNotBlank() }?.let { put("customerAccessToken", it) }
        }
        val request = Request.Builder()
            .url("$workerUrl/apps/creator-dispatch?op=storefront-cart-create")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()
        try {
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: "{}"
            val json = JSONObject(respBody)
            val ok = json.optBoolean("ok", false)
            CreateResult(
                ok = ok,
                cartId = json.optString("cartId").takeIf { it.isNotBlank() },
                checkoutUrl = json.optString("checkoutUrl").takeIf { it.isNotBlank() },
                message = json.optString("message").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            CreateResult(ok = false, cartId = null, checkoutUrl = null, message = e.message)
        }
    }

    suspend fun addLine(
        cartId: String,
        variantId: Long,
        quantity: Int,
        customerAccessToken: String? = null
    ): AddResult = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("cartId", cartId)
            put("lines", JSONArray().apply {
                put(JSONObject().apply {
                    put("variantId", variantId)
                    put("quantity", quantity.coerceAtLeast(1))
                })
            })
            customerAccessToken?.takeIf { it.isNotBlank() }?.let { put("customerAccessToken", it) }
        }
        val request = Request.Builder()
            .url("$workerUrl/apps/creator-dispatch?op=storefront-cart-line-add")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()
        try {
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: "{}"
            val json = JSONObject(respBody)
            val ok = json.optBoolean("ok", false)
            val cart = parseCart(json)
            AddResult(
                ok = ok,
                cart = cart,
                message = json.optString("message").takeIf { it.isNotBlank() }
            )
        } catch (e: Exception) {
            AddResult(ok = false, cart = null, message = e.message)
        }
    }

    suspend fun getCart(cartId: String): CartResult? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$workerUrl/apps/creator-dispatch?op=storefront-cart&cartId=${java.net.URLEncoder.encode(cartId, "UTF-8")}")
            .build()
        try {
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: "{}"
            val json = JSONObject(respBody)
            if (!json.optBoolean("ok", false)) return@withContext null
            parseCart(json)
        } catch (_: Exception) {
            null
        }
    }
}
