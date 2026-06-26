package com.eazpire.creator.ar.poster

import android.content.Context
import android.widget.Toast
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.auth.ShopSessionGuard
import com.eazpire.creator.cart.AppCartStore
import com.eazpire.creator.cart.StorefrontCartStore
import com.eazpire.creator.favorites.FavoritesRefreshTrigger
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Builds a [PosterArSessionConfig] whose callbacks survive modal/sheet dismissal
 * (no captures of [ProductDetailScreen] composable state).
 */
object PosterArSessionActions {

    fun buildOpenableConfig(
        context: Context,
        tokenStore: SecureTokenStore,
        product: ShopifyProductsApi.ProductDetail,
        selectedByIndex: Map<Int, String>,
        scope: CoroutineScope,
        quantity: Int = 1,
        firstImageUrl: String? = null,
    ): PosterArSessionConfig? {
        val base = PosterArCatalog.buildSessionConfig(
            product = product,
            selectedByIndex = selectedByIndex,
            onSelectionChange = { _, _ -> },
            onAddToCart = {},
            onAddToFavorite = {},
        ) ?: return null

        val sizeIdx = AtomicInteger(base.initialSizeIndex)
        val paperIdx = AtomicInteger(base.initialPaperIndex)

        return base.copy(
            onSelectionChange = { s, p ->
                sizeIdx.set(s)
                paperIdx.set(p)
            },
            onAddToCart = {
                val variantId = base.resolveEntry(sizeIdx.get(), paperIdx.get())
                    ?.variantId
                    ?.takeIf { it > 0L }
                if (variantId == null) {
                    Toast.makeText(context, "Pick a product option", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        addToCart(context, tokenStore, variantId, quantity)
                    }
                }
            },
            onAddToFavorite = {
                scope.launch {
                    addFavorite(
                        context = context,
                        tokenStore = tokenStore,
                        product = product,
                        variantId = base.resolveEntry(sizeIdx.get(), paperIdx.get())?.variantId,
                        firstImageUrl = firstImageUrl,
                    )
                }
            },
        )
    }

    private suspend fun addToCart(
        context: Context,
        tokenStore: SecureTokenStore,
        variantId: Long,
        quantity: Int,
    ) {
        val customerToken = resolveValidCustomerAccessToken(context, tokenStore)
        val storefrontCartStore = StorefrontCartStore(context)
        val storefrontCartApi = ShopifyStorefrontCartApi()
        val countryCode = LocaleStore(context).getCountryCodeSync()
        val cartId = storefrontCartStore.cartId
        if (cartId != null) {
            val result = withContext(Dispatchers.IO) {
                storefrontCartApi.addLine(cartId, variantId, quantity, customerToken)
            }
            if (result.ok && result.cart != null) {
                storefrontCartStore.cartId = result.cart.cartId
                AppCartStore.setCount(result.cart.itemCount)
                Toast.makeText(context, "Added to cart", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    result.message ?: "Could not add to cart",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            val result = withContext(Dispatchers.IO) {
                storefrontCartApi.createCart(listOf(variantId to quantity), customerToken, countryCode)
            }
            if (result.ok && result.cartId != null) {
                storefrontCartStore.cartId = result.cartId
                AppCartStore.setCount(quantity)
                Toast.makeText(context, "Added to cart", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    result.message ?: "Could not create cart",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private suspend fun addFavorite(
        context: Context,
        tokenStore: SecureTokenStore,
        product: ShopifyProductsApi.ProductDetail,
        variantId: Long?,
        firstImageUrl: String?,
    ) {
        val ownerId = tokenStore.getOwnerId()?.takeIf { it.isNotBlank() }
        if (ownerId == null) {
            Toast.makeText(context, "Sign in to save favorites", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val creatorApi = CreatorApi(jwt = tokenStore.getJwt())
            val resp = withContext(Dispatchers.IO) {
                creatorApi.addFavorite(
                    customerId = ownerId,
                    productId = product.id.toString(),
                    variantId = variantId?.toString(),
                    productTitle = product.title,
                    productImage = firstImageUrl,
                )
            }
            if (resp.optBoolean("ok", false)) {
                FavoritesRefreshTrigger.trigger()
            }
            Toast.makeText(context, "Saved to favorites", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Could not save favorite", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun resolveValidCustomerAccessToken(
        context: Context,
        tokenStore: SecureTokenStore,
    ): String? {
        withContext(Dispatchers.IO) {
            ShopSessionGuard.refreshAccessTokenIfNeeded(context, tokenStore)
        }
        val token = tokenStore.getAccessToken()?.trim().orEmpty()
        if (token.isBlank()) return null
        val exp = tokenStore.getShopifyAccessExpiresAtEpochMs()
        val bufferMs = 5L * 60L * 1000L
        if (exp > 0L && System.currentTimeMillis() >= exp - bufferMs) return null
        return token
    }
}
