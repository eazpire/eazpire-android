package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Woman
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.launch
private val ONE_PIECE_CONFLICTS = setOf("upper_body", "layer", "pants")

private fun artifactSlotLabel(key: String, t: (String, String) -> String): String = when (key) {
    "head" -> t("eazy_chat.artifacts_slot_head", "Head")
    "upper_body" -> t("eazy_chat.artifacts_slot_upper_body", "Upper Body")
    "layer" -> t("eazy_chat.artifacts_slot_layer", "Layer")
    "pants" -> t("eazy_chat.artifacts_slot_pants", "Pants")
    "feet" -> t("eazy_chat.artifacts_slot_feet", "Feet")
    "socks" -> t("eazy_chat.artifacts_slot_socks", "Socks")
    "accessory_1" -> t("eazy_chat.artifacts_slot_accessory_1", "Accessory 1")
    "accessory_2" -> t("eazy_chat.artifacts_slot_accessory_2", "Accessory 2")
    "one_piece" -> t("eazy_chat.artifacts_slot_one_piece", "One Piece")
    else -> key.replace('_', ' ').split(' ').joinToString(" ") { part ->
        part.replaceFirstChar { c -> c.uppercase() }
    }
}

private fun artifactSlotIcon(key: String): ImageVector = when (key) {
    "all" -> Icons.Default.Apps
    "head" -> Icons.Default.Face
    "upper_body" -> Icons.Default.Checkroom
    "layer" -> Icons.Default.Layers
    "pants" -> Icons.Default.Style
    "feet" -> Icons.Default.DirectionsWalk
    "socks" -> Icons.Default.LocalLaundryService
    "accessory_1" -> Icons.Default.Watch
    "accessory_2" -> Icons.Default.Diamond
    "one_piece" -> Icons.Default.Woman
    else -> Icons.Default.Apps
}

@Composable
fun EazyArtifactsSlotFilterRow(
    slotFilter: String,
    onFilter: (String) -> Unit,
    t: (String, String) -> String,
) {
    val palette = LocalEazyModalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.12f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        EazyArtifactsSlotFilterItem(
            label = t("eazy_chat.artifacts_filter_all", "All"),
            icon = artifactSlotIcon("all"),
            active = slotFilter == "all",
            palette = palette,
            onClick = { onFilter("all") },
        )
        ArtifactsJson.slotKeys.forEach { key ->
            EazyArtifactsSlotFilterItem(
                label = artifactSlotLabel(key, t),
                icon = artifactSlotIcon(key),
                active = slotFilter == key,
                palette = palette,
                onClick = { onFilter(key) },
            )
        }
    }
}

@Composable
private fun EazyArtifactsSlotFilterItem(
    label: String,
    icon: ImageVector,
    active: Boolean,
    palette: EazyModalPalette,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) palette.accent.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                1.dp,
                if (active) palette.accent.copy(alpha = 0.35f) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) palette.accent else palette.accent.copy(alpha = 0.9f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            color = if (active) palette.text else palette.muted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EazyArtifactsNftsPanel(
    slots: List<ArtifactSlot>,
    t: (String, String) -> String,
) {
    val palette = LocalEazyModalPalette.current
    if (slots.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                t("eazy_chat.artifacts_collection_empty", "No slot NFTs yet. Scan a product QR to claim."),
                color = palette.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(100.dp),
        modifier = Modifier.fillMaxSize().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(slots, key = { it.id }) { slot ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                    .background(palette.muted.copy(alpha = 0.04f)),
            ) {
                if (!slot.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = slot.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Brush.linearGradient(listOf(Color(0xFF2A2A35), Color(0xFF1A1A22)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (slot.generationStatus == "generating") {
                            CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(28.dp))
                        }
                    }
                }
                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text(slot.slotType.let { artifactSlotLabel(it, t) }, style = MaterialTheme.typography.labelSmall, color = palette.text, maxLines = 1)
                    Text(slot.serial, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun EazyArtifactsOutfitPanel(
    api: CreatorApi,
    ownerId: String,
    shop: String,
    inventory: List<ArtifactSlot>,
    loadout: ArtifactLoadoutState,
    onLoadoutChanged: () -> Unit,
    t: (String, String) -> String,
) {
    val palette = LocalEazyModalPalette.current
    val scope = rememberCoroutineScope()
    var selectedSlot by remember { mutableStateOf<String?>(null) }
    var equipSlotKey by remember { mutableStateOf<String?>(null) }
    var mintDialog by remember { mutableStateOf(false) }
    var mintPhrase by remember { mutableStateOf("") }
    var mintBusy by remember { mutableStateOf(false) }
    var mintError by remember { mutableStateOf<String?>(null) }
    var mintReferenceUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        mintReferenceUri = uri
    }

    val onePieceActive = loadout.slots.containsKey("one_piece")
    val filledSlots = loadout.slots.keys.toSet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                .padding(vertical = 8.dp),
        ) {
            EazyArtifactsFigure(
                filledSlots = filledSlots,
                selectedSlot = selectedSlot,
                onSlotClick = { selectedSlot = it },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        ArtifactsJson.slotKeys.chunked(3).forEach { rowKeys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowKeys.forEach { key ->
                    val instanceId = loadout.slots[key]
                    val inst = instanceId?.let { id -> inventory.find { it.id == id } }
                    val covered = onePieceActive && key in ONE_PIECE_CONFLICTS && inst == null
                    val vis = loadout.visibility[key] != false
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                1.dp,
                                if (selectedSlot == key) palette.accent else palette.border,
                                RoundedCornerShape(10.dp),
                            )
                            .background(palette.muted.copy(alpha = if (covered) 0.02f else 0.06f))
                            .clickable(enabled = !covered) { selectedSlot = key }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            artifactSlotLabel(key, t),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (!inst?.artworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = inst!!.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Text("+", color = palette.muted.copy(alpha = 0.4f), style = MaterialTheme.typography.titleLarge)
                        }
                        if (inst != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = vis,
                                    onCheckedChange = { checked ->
                                        val next = loadout.visibility.toMutableMap().apply { put(key, checked) }
                                        scope.launch {
                                            api.postArtifactsLoadoutVisibility(
                                                ownerId,
                                                shop,
                                                ArtifactsJson.visibilityToJson(next),
                                            )
                                            onLoadoutChanged()
                                        }
                                    },
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    t("eazy_chat.artifacts_visible_in_mint", "Visible"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.muted,
                                )
                            }
                        }
                        TextButton(
                            onClick = { equipSlotKey = key },
                            enabled = !covered,
                        ) {
                            Text(t("eazy_chat.artifacts_equip", "Equip"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                repeat(3 - rowKeys.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.accent.copy(alpha = 0.08f))
                .border(1.dp, palette.accent.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            loadout.setTheme?.let { theme ->
                Text(
                    "${t("eazy_chat.artifacts_set_theme", "Set theme")}: $theme",
                    color = palette.text,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
            }
            TextButton(
                onClick = { mintDialog = true },
                enabled = loadout.setComplete && !mintBusy,
            ) {
                Text(t("eazy_chat.artifacts_mint_character", "Mint Character"))
            }
        }
    }

    equipSlotKey?.let { slotKey ->
        val options = inventory.filter { it.slotType == slotKey && it.status == "owned" }
        AlertDialog(
            onDismissRequest = { equipSlotKey = null },
            title = { Text(t("eazy_chat.artifacts_equip", "Equip")) },
            text = {
                if (options.isEmpty()) {
                    Text(t("eazy_chat.artifacts_no_slot_items", "No items for this slot."))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { opt ->
                            TextButton(
                                onClick = {
                                    val next = loadout.slots.toMutableMap().apply { put(slotKey, opt.id) }
                                    scope.launch {
                                        api.postArtifactsLoadoutSet(ownerId, shop, ArtifactsJson.slotsToJson(next))
                                        equipSlotKey = null
                                        onLoadoutChanged()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("${opt.serial} (#${opt.id})", modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { equipSlotKey = null }) {
                    Text(t("eazy_chat.close", "Close"))
                }
            },
        )
    }

    if (mintDialog) {
        AlertDialog(
            onDismissRequest = { if (!mintBusy) mintDialog = false },
            title = { Text(t("eazy_chat.artifacts_mint_character", "Mint Character")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        t(
                            "eazy_chat.artifacts_mint_reference_optional",
                            "Optional: upload a photo of yourself to personalize your character.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.muted,
                    )
                    TextButton(onClick = { pickPhoto.launch("image/*") }, enabled = !mintBusy) {
                        Text(
                            if (mintReferenceUri != null) {
                                t("eazy_chat.artifacts_mint_reference_upload", "Upload photo (optional)") + " ✓"
                            } else {
                                t("eazy_chat.artifacts_mint_reference_upload", "Upload photo (optional)")
                            },
                        )
                    }
                    Text(
                        t(
                            "eazy_chat.artifacts_mint_warning",
                            "This is permanent. Slot NFTs will be destroyed. Type exactly:",
                        ) + " MINT CHARACTER",
                    )
                    OutlinedTextField(
                        value = mintPhrase,
                        onValueChange = { mintPhrase = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    mintError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !mintBusy,
                    onClick = {
                        scope.launch {
                            mintBusy = true
                            mintError = null
                            try {
                                var referenceUrl: String? = null
                                mintReferenceUri?.let { uri ->
                                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                        ?: throw IllegalStateException("upload_failed")
                                    val type = context.contentResolver.getType(uri) ?: "image/jpeg"
                                    val upload = api.uploadArtifactsMintReference(ownerId, shop, bytes, type)
                                    if (!upload.optBoolean("ok", false)) {
                                        mintError = upload.optString("error", "upload_failed")
                                        return@launch
                                    }
                                    referenceUrl = upload.optString("image_url", "").takeIf { it.isNotBlank() }
                                }
                                val prep = api.postArtifactsMintPrepare(ownerId, shop, referenceUrl)
                                if (!prep.optBoolean("ok", false)) {
                                    mintError = prep.optString("error", "Cannot mint")
                                    return@launch
                                }
                                val res = api.postArtifactsMintCharacter(
                                    ownerId,
                                    shop,
                                    prep.optString("mint_intent_id"),
                                    mintPhrase.trim(),
                                )
                                if (res.optBoolean("ok", false)) {
                                    mintDialog = false
                                    mintPhrase = ""
                                    mintReferenceUri = null
                                    onLoadoutChanged()
                                } else {
                                    mintError = res.optString("error", "Mint failed")
                                }
                            } catch (e: Exception) {
                                mintError = e.message ?: "Mint failed"
                            } finally {
                                mintBusy = false
                            }
                        }
                    },
                ) { Text(t("eazy_chat.artifacts_mint_character", "Mint Character")) }
            },
            dismissButton = {
                TextButton(enabled = !mintBusy, onClick = { mintDialog = false }) {
                    Text(t("eazy_chat.cancel", "Cancel"))
                }
            },
        )
    }
}

@Composable
private fun EazyArtifactsActionButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val palette = LocalEazyModalPalette.current
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled) palette.accent else palette.muted.copy(alpha = 0.1f))
            .border(1.dp, if (filled) palette.accent else palette.border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = if (filled) Color.White else palette.text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun EazyArtifactsExchangePanel(
    api: CreatorApi,
    ownerId: String,
    shop: String,
    listings: List<ArtifactTradeListing>,
    tradeTokens: Int,
    inventory: List<ArtifactSlot>,
    slotFilter: String,
    isMyListings: Boolean = false,
    onRefresh: () -> Unit,
    t: (String, String) -> String,
) {
    val palette = LocalEazyModalPalette.current
    val scope = rememberCoroutineScope()
    var offerListingId by remember { mutableIntStateOf(0) }

    val filtered = remember(listings, slotFilter) {
        if (slotFilter == "all") listings
        else listings.filter { it.slot.slotType == slotFilter }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (!isMyListings) {
            Text(
                "${t("eazy_chat.artifacts_trade_tokens", "Trade tokens")}: $tradeTokens",
                style = MaterialTheme.typography.titleSmall,
                color = palette.text,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (isMyListings) {
                        t("eazy_chat.artifacts_my_listings_empty", "You have no active listings.")
                    } else {
                        t("eazy_chat.artifacts_exchange_empty", "No listings yet.")
                    },
                    color = palette.muted,
                )
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filtered.forEach { listing ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (!listing.slot.artworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = listing.slot.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(artifactSlotLabel(listing.slot.slotType, t), fontWeight = FontWeight.SemiBold, color = palette.text)
                            Text(listing.slot.serial, style = MaterialTheme.typography.bodySmall, color = palette.muted)
                        }
                        if (isMyListings) {
                            EazyArtifactsActionButton(t("eazy_chat.artifacts_cancel_listing", "Cancel"), filled = false) {
                                scope.launch {
                                    api.deleteArtifactsTradeListing(ownerId, shop, listing.id)
                                    onRefresh()
                                }
                            }
                        } else {
                            EazyArtifactsActionButton(t("eazy_chat.artifacts_offer_trade", "Offer trade"), filled = true) {
                                offerListingId = listing.id
                            }
                        }
                    }
                }
            }
        }
    }

    if (offerListingId > 0) {
        val owned = inventory.filter { it.status == "owned" }
        AlertDialog(
            onDismissRequest = { offerListingId = 0 },
            title = { Text(t("eazy_chat.artifacts_offer_trade", "Offer trade")) },
            text = {
                if (owned.isEmpty()) {
                    Text(t("eazy_chat.artifacts_no_slot_items", "No items for this slot."))
                } else {
                    Column {
                        owned.forEach { slot ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        api.postArtifactsTradeOffer(ownerId, shop, offerListingId, slot.id)
                                        offerListingId = 0
                                        onRefresh()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("${slot.slotType} — ${slot.serial} (#${slot.id})")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { offerListingId = 0 }) {
                    Text(t("eazy_chat.close", "Close"))
                }
            },
        )
    }
}

@Composable
fun EazyArtifactsMarketplacePanel(
    api: CreatorApi,
    ownerId: String,
    shop: String,
    listings: List<ArtifactMarketListing>,
    sellableCharacters: List<ArtifactCharacter> = emptyList(),
    isSellMode: Boolean = false,
    onRefresh: () -> Unit,
    t: (String, String) -> String,
) {
    val palette = LocalEazyModalPalette.current
    val scope = rememberCoroutineScope()
    var confirmListingId by remember { mutableIntStateOf(0) }

    if (isSellMode) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (listings.isEmpty() && sellableCharacters.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(t("eazy_chat.artifacts_sell_empty", "No characters to sell yet."), color = palette.muted)
                }
            }
            if (listings.isNotEmpty()) {
                Text(t("eazy_chat.artifacts_my_market_listings", "My listings"), style = MaterialTheme.typography.titleSmall, color = palette.text)
                listings.forEach { listing ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${listing.character.rarity} · ${listing.priceEaz} EAZ", color = palette.text)
                        EazyArtifactsActionButton(t("eazy_chat.artifacts_cancel_listing", "Cancel"), filled = false) {
                            scope.launch {
                                api.postArtifactsMarketCancel(ownerId, shop, listing.listingId)
                                onRefresh()
                            }
                        }
                    }
                }
            }
            if (sellableCharacters.isNotEmpty()) {
                Text(t("eazy_chat.artifacts_sellable_characters", "Sell a character"), style = MaterialTheme.typography.titleSmall, color = palette.text)
                sellableCharacters.forEach { ch ->
                    Text("${ch.rarity} · ${ch.serial}", color = palette.muted, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
        return
    }

    if (listings.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(t("eazy_chat.artifacts_market_empty", "No characters for sale."), color = palette.muted)
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listings.forEach { listing ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!listing.character.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = listing.character.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF2A2A35), Color(0xFF1A1A22)))),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "${listing.character.rarity} · ${listing.character.archetype}",
                        fontWeight = FontWeight.SemiBold,
                        color = palette.text,
                    )
                    Text("${listing.priceEaz} EAZ", color = palette.accent, style = MaterialTheme.typography.bodyMedium)
                }
                EazyArtifactsActionButton(t("eazy_chat.artifacts_buy", "Buy"), filled = true) {
                    confirmListingId = listing.listingId
                }
            }
        }
    }

    if (confirmListingId > 0) {
        AlertDialog(
            onDismissRequest = { confirmListingId = 0 },
            title = { Text(t("eazy_chat.artifacts_buy", "Buy")) },
            text = { Text(t("eazy_chat.artifacts_buy_confirm", "Buy this character with earned EAZ?")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            api.postArtifactsMarketBuy(ownerId, shop, confirmListingId)
                            confirmListingId = 0
                            onRefresh()
                        }
                    },
                ) { Text(t("eazy_chat.artifacts_buy", "Buy")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmListingId = 0 }) {
                    Text(t("eazy_chat.cancel", "Cancel"))
                }
            },
        )
    }
}
