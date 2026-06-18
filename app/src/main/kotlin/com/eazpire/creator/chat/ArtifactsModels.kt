package com.eazpire.creator.chat

import org.json.JSONArray
import org.json.JSONObject

data class ArtifactSlot(
    val id: Int,
    val slotType: String,
    val serial: String,
    val artworkUrl: String?,
    val productTitle: String,
    val status: String,
    val generationStatus: String = "ready",
)

data class ArtifactCharacter(
    val id: Int,
    val serial: String,
    val rarity: String,
    val archetype: String,
    val imageUrl: String?,
    val status: String,
)

data class ArtifactTradeListing(
    val id: Int,
    val sellerId: String,
    val slot: ArtifactSlot,
)

data class ArtifactMarketListing(
    val listingId: Int,
    val priceEaz: Double,
    val sellerId: String,
    val character: ArtifactCharacter,
)

data class ArtifactLoadoutState(
    val slots: Map<String, Int>,
    val visibility: Map<String, Boolean>,
    val setTheme: String?,
    val setComplete: Boolean,
    val activeCharacterId: Int?,
)

object ArtifactsJson {
    val slotKeys = listOf(
        "head",
        "upper_body",
        "layer",
        "pants",
        "feet",
        "socks",
        "accessory_1",
        "accessory_2",
        "one_piece",
    )

    fun parseSlot(o: JSONObject): ArtifactSlot? {
        if (!o.has("id")) return null
        return ArtifactSlot(
            id = o.optInt("id"),
            slotType = o.optString("slot_type", ""),
            serial = o.optString("serial", ""),
            artworkUrl = o.optString("artwork_url", "").takeIf { it.isNotBlank() },
            productTitle = o.optString("product_title", ""),
            status = o.optString("status", ""),
            generationStatus = o.optString("generation_status", "ready").ifBlank { "ready" },
        )
    }

    fun parseSlots(arr: JSONArray?): List<ArtifactSlot> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i -> parseSlot(arr.optJSONObject(i) ?: return@mapNotNull null) }
    }

    fun parseCharacter(o: JSONObject): ArtifactCharacter? {
        if (!o.has("id")) return null
        return ArtifactCharacter(
            id = o.optInt("id"),
            serial = o.optString("serial", ""),
            rarity = o.optString("rarity", ""),
            archetype = o.optString("archetype", ""),
            imageUrl = o.optString("image_url", "").takeIf { it.isNotBlank() },
            status = o.optString("status", ""),
        )
    }

    fun parseCharacters(arr: JSONArray?): List<ArtifactCharacter> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i -> parseCharacter(arr.optJSONObject(i) ?: return@mapNotNull null) }
    }

    fun parseTradeListings(arr: JSONArray?): List<ArtifactTradeListing> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val slot = parseSlot(o.optJSONObject("slot") ?: o) ?: return@mapNotNull null
            ArtifactTradeListing(
                id = o.optInt("id"),
                sellerId = o.optString("seller_id", ""),
                slot = slot,
            )
        }
    }

    fun parseMarketListings(arr: JSONArray?): List<ArtifactMarketListing> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val ch = parseCharacter(o.optJSONObject("character") ?: return@mapNotNull null) ?: return@mapNotNull null
            ArtifactMarketListing(
                listingId = o.optInt("listing_id", o.optInt("id")),
                priceEaz = o.optDouble("price_eaz", 0.0),
                sellerId = o.optString("seller_id", ""),
                character = ch,
            )
        }
    }

    fun parseInstanceMap(json: JSONObject?): Map<String, Int> {
        if (json == null) return emptyMap()
        val out = linkedMapOf<String, Int>()
        json.keys().forEach { key ->
            val ref = json.opt(key)
            val id = when (ref) {
                is JSONObject -> ref.optInt("instance_id", 0)
                is Number -> ref.toInt()
                else -> 0
            }
            if (id > 0) out[key] = id
        }
        return out
    }

    fun parseVisibilityMap(json: JSONObject?): Map<String, Boolean> {
        if (json == null) return emptyMap()
        val out = linkedMapOf<String, Boolean>()
        json.keys().forEach { key ->
            out[key] = json.optBoolean(key, true)
        }
        return out
    }

    fun slotsToJson(slots: Map<String, Int>): JSONObject {
        val o = JSONObject()
        slots.forEach { (key, id) ->
            o.put(key, JSONObject().put("instance_id", id))
        }
        return o
    }

    fun visibilityToJson(visibility: Map<String, Boolean>): JSONObject {
        val o = JSONObject()
        visibility.forEach { (key, value) -> o.put(key, value) }
        return o
    }

    /** Extract artifact claim token from scanned QR payload (web parity). */
    fun parseClaimToken(raw: String): String? {
        val s = raw.trim()
        if (s.isBlank()) return null
        Regex("[?&](?:t|token)=([A-Za-z0-9_-]+)").find(s)?.groupValues?.getOrNull(1)?.let { return it }
        if (s.length >= 16 && !s.contains(' ')) return s
        return null
    }

    fun parseLoadoutResponse(json: JSONObject): ArtifactLoadoutState {
        val setStatus = json.optJSONObject("set_status")
        return ArtifactLoadoutState(
            slots = parseInstanceMap(json.optJSONObject("slots")),
            visibility = parseVisibilityMap(json.optJSONObject("visibility")),
            setTheme = setStatus?.optString("set_theme", null)?.takeIf { it.isNotBlank() },
            setComplete = setStatus?.optBoolean("set_complete", false) == true,
            activeCharacterId = if (json.has("active_character_id") && !json.isNull("active_character_id")) {
                json.optInt("active_character_id")
            } else {
                null
            },
        )
    }
}
