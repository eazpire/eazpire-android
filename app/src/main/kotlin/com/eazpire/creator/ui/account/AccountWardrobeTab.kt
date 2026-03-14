package com.eazpire.creator.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.util.DebugLog
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Wardrobe Tab – native Android UI.
 * Web reference: theme/sections/wardrobe-panel.liquid, theme/assets/wardrobe.js
 * Lists outfits, create new, delete, select. Footer shows total price for selected outfit.
 */
data class WardrobeOutfit(
    val id: String,
    val name: String,
    val gender: String,
    val ageGroup: String,
    val slots: Map<String, SlotData>,
    val generatedImageUrl: String?,
    val status: String,
    val userImageUrl: String?
)

data class SlotData(
    val productId: String,
    val variantId: String,
    val title: String,
    val variantTitle: String,
    val price: String,
    val currency: String,
    val imageUrl: String
)

@Composable
fun AccountWardrobeTab(
    tokenStore: SecureTokenStore,
    onTotalPriceChange: (String) -> Unit = {},
    onGenerateActionReady: ((() -> Unit)?, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val jwt = remember { tokenStore.getJwt() }
    val ownerId = remember { tokenStore.getOwnerId() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }

    var outfits by remember { mutableStateOf<List<WardrobeOutfit>>(emptyList()) }
    var selectedOutfitId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadOutfits() {
        scope.launch {
            if (ownerId.isBlank()) return@launch
            isLoading = true
            errorMessage = null
            try {
                val resp = api.wardrobeList(ownerId)
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("outfits")
                    outfits = parseOutfitsFromJson(arr)
                    when {
                        selectedOutfitId != null && outfits.none { it.id == selectedOutfitId } ->
                            selectedOutfitId = outfits.firstOrNull()?.id
                        selectedOutfitId == null && outfits.isNotEmpty() ->
                            selectedOutfitId = outfits.first().id
                        else -> {}
                    }
                } else {
                    errorMessage = resp.optString("error", "Failed to load outfits")
                }
            } catch (e: Exception) {
                DebugLog.click("Wardrobe load error: ${e.message}")
                errorMessage = "Failed to load outfits"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(ownerId) {
        loadOutfits()
    }

    val selectedOutfit = remember(outfits, selectedOutfitId) {
        outfits.find { it.id == selectedOutfitId }
    }

    val canGenerate = selectedOutfit != null && selectedOutfit.slots.values.any { it.productId.isNotBlank() }
    LaunchedEffect(selectedOutfit, canGenerate) {
        if (canGenerate && selectedOutfit != null) {
            val outfit = selectedOutfit
            onGenerateActionReady({
                scope.launch {
                    try {
                        val slotsMap = outfit.slots.mapValues { (_, s) ->
                            mapOf(
                                "product_id" to s.productId,
                                "variant_id" to s.variantId,
                                "title" to s.title,
                                "variant_title" to s.variantTitle,
                                "price" to s.price,
                                "currency" to s.currency,
                                "image_url" to s.imageUrl
                            )
                        }
                        api.wardrobeGenerate(ownerId, mapOf(
                            "outfit_id" to outfit.id,
                            "slots" to slotsMap,
                            "gender" to outfit.gender,
                            "age_group" to outfit.ageGroup,
                            "name" to outfit.name
                        ))
                        loadOutfits()
                    } catch (e: Exception) {
                        DebugLog.click("Wardrobe generate error: ${e.message}")
                    }
                }
            }, true)
        } else {
            onGenerateActionReady(null, false)
        }
    }

    LaunchedEffect(selectedOutfit) {
        val total = selectedOutfit?.let { outfit ->
            outfit.slots.values.sumOf { slot ->
                slot.price.replace(",", ".").toDoubleOrNull() ?: 0.0
            }
        } ?: 0.0
        onTotalPriceChange("%.2f €".format(total))
    }

    fun deleteOutfit(id: String) {
        scope.launch {
            try {
                val resp = api.wardrobeDelete(ownerId, id)
                if (resp.optBoolean("ok", false)) {
                    loadOutfits()
                    if (selectedOutfitId == id) selectedOutfitId = outfits.firstOrNull { it.id != id }?.id
                }
            } catch (e: Exception) {
                DebugLog.click("Wardrobe delete error: ${e.message}")
            }
        }
    }

    fun createNewOutfit() {
        scope.launch {
            try {
                val resp = api.wardrobeSave(
                    ownerId,
                    mapOf(
                        "name" to "New Outfit",
                        "gender" to "male",
                        "age_group" to "adult",
                        "slots" to emptyMap<String, Any>()
                    )
                )
                if (resp.optBoolean("ok", false)) {
                    val newId = resp.optString("outfit_id", "")
                    loadOutfits()
                    selectedOutfitId = newId
                }
            } catch (e: Exception) {
                DebugLog.click("Wardrobe create error: ${e.message}")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Wardrobe",
            style = MaterialTheme.typography.titleMedium,
            color = EazColors.TextPrimary
        )
        Text(
            text = "Manage your outfits and see how items fit together.",
            style = MaterialTheme.typography.bodySmall,
            color = EazColors.TextSecondary
        )

        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
            }
        } else {
            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Outfits",
                    style = MaterialTheme.typography.labelLarge,
                    color = EazColors.TextSecondary
                )
                IconButton(
                    onClick = { createNewOutfit() },
                    modifier = Modifier
                        .background(EazColors.OrangeBg, RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New outfit", tint = EazColors.Orange)
                }
            }

            if (outfits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(EazColors.TopbarBorder.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Checkroom,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = EazColors.TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No outfits yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EazColors.TextSecondary
                        )
                        Text(
                            text = "Tap + to create your first outfit",
                            style = MaterialTheme.typography.bodySmall,
                            color = EazColors.TextSecondary
                        )
                    }
                }
            } else {
                outfits.forEach { outfit ->
                    val isSelected = selectedOutfitId == outfit.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOutfitId = outfit.id }
                            .then(
                                if (isSelected) Modifier.background(
                                    EazColors.OrangeBg.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                ) else Modifier
                            ),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(EazColors.TopbarBorder.copy(alpha = 0.3f))
                            ) {
                                val thumbUrl = outfit.generatedImageUrl ?: outfit.userImageUrl
                                if (thumbUrl != null) {
                                    AsyncImage(
                                        model = thumbUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Checkroom,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        tint = EazColors.TextSecondary
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = outfit.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EazColors.TextPrimary
                                )
                                val slotCount = outfit.slots.values.count { it.productId.isNotBlank() }
                                Text(
                                    text = "$slotCount item(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EazColors.TextSecondary
                                )
                            }
                            IconButton(
                                onClick = { deleteOutfit(outfit.id) }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = EazColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            selectedOutfit?.let { outfit ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Items in this outfit",
                    style = MaterialTheme.typography.labelMedium,
                    color = EazColors.TextSecondary
                )
                val filledSlots = outfit.slots.filter { it.value.productId.isNotBlank() }
                if (filledSlots.isEmpty()) {
                    Text(
                        text = "No items yet. Add products via the web app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EazColors.TextSecondary
                    )
                } else {
                    filledSlots.forEach { (slotKey, slot) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (slot.imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = slot.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            }
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = slot.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EazColors.TextPrimary
                                )
                                Text(
                                    text = "${slot.price} ${slot.currency}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EazColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseOutfitsFromJson(arr: org.json.JSONArray?): List<WardrobeOutfit> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        parseOutfitFromJson(obj)
    }
}

private fun parseOutfitFromJson(obj: JSONObject): WardrobeOutfit? {
    val id = obj.optString("id", "").ifBlank { return null }
    val slotsObj = obj.optJSONObject("slots")
    val slots = if (slotsObj != null) {
        parseSlotsFromJson(slotsObj)
    } else {
        try {
            parseSlotsFromJson(JSONObject(obj.optString("slots", "{}")))
        } catch (_: Exception) {
            emptyMap()
        }
    }
    return WardrobeOutfit(
        id = id,
        name = obj.optString("name", "Outfit"),
        gender = obj.optString("gender", "male"),
        ageGroup = obj.optString("age_group", "adult"),
        slots = slots,
        generatedImageUrl = obj.optString("generated_image_url").takeIf { it.isNotBlank() },
        status = obj.optString("status", "draft"),
        userImageUrl = obj.optString("user_image_url").takeIf { it.isNotBlank() }
    )
}

private fun parseSlotsFromJson(obj: JSONObject): Map<String, SlotData> {
    val map = mutableMapOf<String, SlotData>()
    obj.keys().asSequence().forEach { key ->
        val slotObj = obj.optJSONObject(key) ?: return@forEach
        val productId = slotObj.optString("product_id", "")
        if (productId.isNotBlank()) {
            map[key] = SlotData(
                productId = productId,
                variantId = slotObj.optString("variant_id", ""),
                title = slotObj.optString("title", ""),
                variantTitle = slotObj.optString("variant_title", ""),
                price = slotObj.optString("price", "0"),
                currency = slotObj.optString("currency", "EUR"),
                imageUrl = slotObj.optString("image_url", "")
            )
        }
    }
    return map
}
