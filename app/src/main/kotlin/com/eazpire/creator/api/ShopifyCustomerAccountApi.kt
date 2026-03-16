package com.eazpire.creator.api

import com.eazpire.creator.auth.AuthConfig
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
 * Shopify Customer Account API – GraphQL Client.
 * Nutzt den access_token aus SecureTokenStore für direkte Aufrufe.
 * Ermöglicht: Kundendaten, Adressen, Payment Methods (für Checkout-Prefill).
 */
class ShopifyCustomerAccountApi(
    private val accessToken: String?,
    private val shopDomain: String = AuthConfig.SHOP_DOMAIN,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    data class CustomerAddress(
        val id: String,
        val firstName: String,
        val lastName: String,
        val address1: String,
        val address2: String?,
        val city: String,
        val zip: String,
        val countryCode: String,
        val zoneCode: String?,
        val phone: String?
    )

    data class CustomerInfo(
        val id: String,
        val email: String?,
        val firstName: String?,
        val lastName: String?,
        val defaultAddress: CustomerAddress?,
        val addresses: List<CustomerAddress>
    )

    private var graphqlUrl: String? = null

    private suspend fun getGraphqlUrl(): String = withContext(Dispatchers.IO) {
        graphqlUrl ?: run {
            val domain = if (shopDomain.contains(".")) shopDomain else "$shopDomain.myshopify.com"
            val discoveryUrl = "https://$domain/.well-known/customer-account-api"
            val request = Request.Builder().url(discoveryUrl).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
            val url = json.optString("graphql_api", "").takeIf { it.isNotBlank() }
                ?: "https://$domain/customer/api/2024-10/graphql"
            graphqlUrl = url
            url
        }
    }

    /**
     * Führt eine GraphQL-Query aus.
     */
    suspend fun query(query: String, variables: Map<String, Any?> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            if (accessToken.isNullOrBlank()) {
                return@withContext JSONObject().apply { put("errors", JSONArray().apply { put(JSONObject().apply { put("message", "No access token") }) }) }
            }
            val url = getGraphqlUrl()
            val body = JSONObject().apply {
                put("query", query)
                if (variables.isNotEmpty()) {
                    val vars = JSONObject()
                    variables.forEach { (k, v) -> if (v != null) vars.put(k, v) }
                    put("variables", vars)
                }
            }.toString()
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("User-Agent", "EazpireCreator/1.0")
                .build()
            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: "{}"
            JSONObject(respBody)
        }

    /**
     * Holt Kundendaten inkl. Adressen für Checkout-Prefill.
     */
    suspend fun getCustomer(): CustomerInfo? = withContext(Dispatchers.IO) {
        val q = """
            query {
              customer {
                id
                emailAddress { emailAddress }
                firstName
                lastName
                defaultAddress {
                  id
                  firstName
                  lastName
                  address1
                  address2
                  city
                  zip
                  territoryCode
                  zoneCode
                  phoneNumber
                }
                addresses(first: 10) {
                  nodes {
                    id
                    firstName
                    lastName
                    address1
                    address2
                    city
                    zip
                    territoryCode
                    zoneCode
                    phoneNumber
                  }
                }
              }
            }
        """.trimIndent()
        val json = query(q)
        val errors = json.optJSONArray("errors")
        if (errors != null && errors.length() > 0) return@withContext null
        val data = json.optJSONObject("data") ?: return@withContext null
        val customer = data.optJSONObject("customer") ?: return@withContext null
        val id = customer.optString("id", "").takeIf { it.isNotBlank() } ?: return@withContext null
        val emailObj = customer.optJSONObject("emailAddress")
        val email = emailObj?.optString("emailAddress")?.takeIf { it.isNotBlank() }
        val firstName = customer.optString("firstName").takeIf { it.isNotBlank() }
        val lastName = customer.optString("lastName").takeIf { it.isNotBlank() }
        val defaultAddr = customer.optJSONObject("defaultAddress")?.let { parseAddress(it) }
        val addrNodes = customer.optJSONObject("addresses")?.optJSONArray("nodes") ?: JSONArray()
        val addresses = (0 until addrNodes.length()).mapNotNull { i ->
            addrNodes.optJSONObject(i)?.let { parseAddress(it) }
        }
        CustomerInfo(
            id = id,
            email = email,
            firstName = firstName,
            lastName = lastName,
            defaultAddress = defaultAddr,
            addresses = addresses
        )
    }

    private fun parseAddress(obj: JSONObject): CustomerAddress? {
        val id = obj.optString("id", "").takeIf { it.isNotBlank() } ?: return null
        return CustomerAddress(
            id = id,
            firstName = obj.optString("firstName", ""),
            lastName = obj.optString("lastName", ""),
            address1 = obj.optString("address1", ""),
            address2 = obj.optString("address2").takeIf { it.isNotBlank() },
            city = obj.optString("city", ""),
            zip = obj.optString("zip", ""),
            countryCode = obj.optString("territoryCode", obj.optString("countryCodeV2", "")),
            zoneCode = obj.optString("zoneCode").takeIf { it.isNotBlank() },
            phone = obj.optString("phoneNumber").takeIf { it.isNotBlank() }
        )
    }
}
