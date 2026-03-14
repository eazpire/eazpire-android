package com.eazpire.creator.ui.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.ui.account.wardrobe.ONE_PIECE_CONFLICTS
import com.eazpire.creator.ui.account.wardrobe.WARDROBE_SLOTS
import com.eazpire.creator.ui.account.wardrobe.WardrobeColors
import com.eazpire.creator.ui.account.wardrobe.WardrobeFilter
import com.eazpire.creator.ui.account.wardrobe.WardrobeFigureSvg
import com.eazpire.creator.ui.account.wardrobe.WardrobeSlot
import com.eazpire.creator.util.DebugLog
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Wardrobe Tab – 1:1 native clone of web theme/sections/wardrobe-panel.liquid
 * Layout: Sidebar (outfit list + user upload) | Main (Figure + 3x3 Slot grid)
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

data class WardrobeProduct(
    val id: String,
    val title: String,
    val handle: String,
    val image: String?,
    val price: String,
    val currency: String,
    val variantId: String?,
    val productType: String,
    val tags: List<String>,
    val variants: List<WardrobeVariant>
)

data class WardrobeVariant(
    val id: String,
    val title: String,
    val price: String,
    val available: Boolean,
    val image: String?,
    val option1: String?,
    val option2: String?
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountWardrobeTab(
    tokenStore: SecureTokenStore,
    onTotalPriceChange: (String) -> Unit = {},
    onGenerateActionReady: ((() -> Unit)?, Boolean) -> Unit = { _, _ -> },
    onSaveActionReady: ((() -> Unit)?, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val jwt = remember { tokenStore.getJwt() }
    val ownerId = remember { tokenStore.getOwnerId() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }
    val scope = rememberCoroutineScope()

    var outfits by remember { mutableStateOf<List<WardrobeOutfit>>(emptyList()) }
    var activeOutfitId by remember { mutableStateOf<String>("unsaved") }
    var unsavedSlots by remember { mutableStateOf<Map<String, SlotData>>(emptyMap()) }
    var unsavedGender by remember { mutableStateOf("male") }
    var unsavedAgeGroup by remember { mutableStateOf("adult") }
    var userImageUrl by remember { mutableStateOf<String?>(null) }
    var figureView by remember { mutableStateOf("figure") }
    var currentGeneratedImageUrl by remember { mutableStateOf<String?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var productModalSlot by remember { mutableStateOf<String?>(null) }
    var variantModalProduct by remember { mutableStateOf<WardrobeProduct?>(null) }
    var lightboxUrl by remember { mutableStateOf<String?>(null) }
    var userImageDialogOpen by remember { mutableStateOf(false) }
    var sidebarOpen by remember { mutableStateOf(false) }
    var saveOutfitDialogOpen by remember { mutableStateOf(false) }
    var saveOutfitName by remember { mutableStateOf("Outfit ${System.currentTimeMillis().toString().takeLast(4)}") }

    val currentSlots = when (activeOutfitId) {
        "unsaved" -> unsavedSlots
        else -> outfits.find { it.id == activeOutfitId }?.slots ?: emptyMap()
    }
    val currentGender = when (activeOutfitId) {
        "unsaved" -> unsavedGender
        else -> outfits.find { it.id == activeOutfitId }?.gender ?: "male"
    }
    val currentAgeGroup = when (activeOutfitId) {
        "unsaved" -> unsavedAgeGroup
        else -> outfits.find { it.id == activeOutfitId }?.ageGroup ?: "adult"
    }
    val activeOutfit = outfits.find { it.id == activeOutfitId }

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
                    if (activeOutfitId != "unsaved" && outfits.none { it.id == activeOutfitId }) {
                        activeOutfitId = "unsaved"
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

    fun updateSlots(newSlots: Map<String, SlotData>) {
        if (activeOutfitId == "unsaved") {
            unsavedSlots = newSlots
        } else {
            scope.launch {
                val outfit = outfits.find { it.id == activeOutfitId } ?: return@launch
                val slotsMap = newSlots.mapValues { (_, s) ->
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
                api.wardrobeSave(ownerId, mapOf(
                    "outfit_id" to activeOutfitId,
                    "name" to outfit.name,
                    "gender" to currentGender,
                    "age_group" to currentAgeGroup,
                    "slots" to slotsMap
                ))
                loadOutfits()
            }
        }
    }

    fun setSlot(slotKey: String, data: SlotData?) {
        val newSlots = currentSlots.toMutableMap()
        if (data == null) {
            newSlots.remove(slotKey)
        } else {
            newSlots[slotKey] = data
        }
        updateSlots(newSlots)
    }

    fun clearSlot(slotKey: String) = setSlot(slotKey, null)

    LaunchedEffect(ownerId) { loadOutfits() }

    val canGenerate = currentSlots.values.any { it.productId.isNotBlank() }
    LaunchedEffect(activeOutfitId, currentSlots, canGenerate) {
        if (canGenerate) {
            val slotsMap = currentSlots.mapValues { (_, s) ->
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
            onGenerateActionReady({
                scope.launch {
                    try {
                        api.wardrobeGenerate(ownerId, mapOf(
                            "outfit_id" to (if (activeOutfitId == "unsaved") "" else activeOutfitId),
                            "slots" to slotsMap,
                            "gender" to currentGender,
                            "age_group" to currentAgeGroup,
                            "name" to (activeOutfit?.name ?: "Outfit")
                        ))
                        loadOutfits()
                        if (activeOutfitId != "unsaved") {
                            currentGeneratedImageUrl = outfits.find { it.id == activeOutfitId }?.generatedImageUrl
                        }
                    } catch (e: Exception) {
                        DebugLog.click("Wardrobe generate error: ${e.message}")
                    }
                }
            }, true)
        } else {
            onGenerateActionReady(null, false)
        }
    }

    LaunchedEffect(currentSlots) {
        val total = currentSlots.values.sumOf { it.price.replace(",", ".").toDoubleOrNull() ?: 0.0 }
        onTotalPriceChange("%.2f €".format(total))
    }

    fun deleteOutfit(id: String) {
        scope.launch {
            try {
                api.wardrobeDelete(ownerId, id)
                loadOutfits()
                if (activeOutfitId == id) activeOutfitId = "unsaved"
            } catch (e: Exception) {
                DebugLog.click("Wardrobe delete error: ${e.message}")
            }
        }
    }

    val canSaveUnsaved = activeOutfitId == "unsaved" && unsavedSlots.values.any { it.productId.isNotBlank() }
    fun openSaveOutfitDialog() { saveOutfitDialogOpen = true }
    fun saveUnsavedOutfit(name: String) {
        scope.launch {
            try {
                val slotsMap = unsavedSlots.mapValues { (_, s) ->
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
                val resp = api.wardrobeSave(ownerId, mapOf(
                    "name" to name,
                    "gender" to unsavedGender,
                    "age_group" to unsavedAgeGroup,
                    "slots" to slotsMap
                ))
                if (resp.optBoolean("ok", false)) {
                    val newId = resp.optString("outfit_id", "")
                    loadOutfits()
                    activeOutfitId = newId
                    outfits.find { it.id == newId }?.let { currentGeneratedImageUrl = it.generatedImageUrl }
                    unsavedSlots = emptyMap()
                    saveOutfitDialogOpen = false
                }
            } catch (e: Exception) {
                DebugLog.click("Wardrobe save error: ${e.message}")
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
                        "gender" to unsavedGender,
                        "age_group" to unsavedAgeGroup,
                        "slots" to emptyMap<String, Any>()
                    )
                )
                if (resp.optBoolean("ok", false)) {
                    loadOutfits()
                    activeOutfitId = resp.optString("outfit_id", "")
                }
            } catch (e: Exception) {
                DebugLog.click("Wardrobe create error: ${e.message}")
            }
        }
    }
    LaunchedEffect(canSaveUnsaved) {
        if (canSaveUnsaved) {
            onSaveActionReady({ openSaveOutfitDialog() }, true)
        } else {
            onSaveActionReady(null, false)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            userImageUrl = it.toString()
            userImageDialogOpen = false
        }
    }

    val sidebarWidth = 260.dp
    val sidebarOffset by animateDpAsState(
        targetValue = if (sidebarOpen) 0.dp else (-sidebarWidth),
        animationSpec = tween(250),
        label = "sidebar"
    )

    fun closeSidebar() { sidebarOpen = false }
    fun selectOutfitAndClose(id: String) {
        activeOutfitId = id
        outfits.find { it.id == id }?.let { currentGeneratedImageUrl = it.generatedImageUrl }
        closeSidebar()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ── Main content (full width) ──
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
                // Header: hamburger + label (drawer always)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .background(WardrobeColors.Gray100),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { sidebarOpen = !sidebarOpen }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = WardrobeColors.Gray700)
                    }
                    Text(
                        text = if (activeOutfitId == "unsaved") "Unsaved Outfit" else (activeOutfit?.name ?: "Outfit"),
                        style = MaterialTheme.typography.titleMedium,
                        color = WardrobeColors.Gray900,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Audience selectors: icons only (Gender + Age)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WardrobeIconButton(
                        selected = currentGender == "male",
                        icon = Icons.Default.Male,
                        onClick = {
                            if (activeOutfitId == "unsaved") unsavedGender = "male"
                            else scope.launch {
                                activeOutfit?.let { api.wardrobeSave(ownerId, mapOf("outfit_id" to it.id, "gender" to "male", "name" to it.name, "age_group" to it.ageGroup, "slots" to it.slots)) }
                                loadOutfits()
                            }
                        },
                        isMale = true
                    )
                    WardrobeIconButton(
                        selected = currentGender == "female",
                        icon = Icons.Default.Female,
                        onClick = {
                            if (activeOutfitId == "unsaved") unsavedGender = "female"
                            else scope.launch {
                                activeOutfit?.let { api.wardrobeSave(ownerId, mapOf("outfit_id" to it.id, "gender" to "female", "name" to it.name, "age_group" to it.ageGroup, "slots" to it.slots)) }
                                loadOutfits()
                            }
                        },
                        isMale = false
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(WardrobeColors.Gray300)
                    )
                    WardrobeIconButton(
                        selected = currentAgeGroup == "adult",
                        icon = Icons.Default.Person,
                        onClick = {
                            if (activeOutfitId == "unsaved") unsavedAgeGroup = "adult"
                            else scope.launch {
                                activeOutfit?.let { api.wardrobeSave(ownerId, mapOf("outfit_id" to it.id, "age_group" to "adult", "name" to it.name, "gender" to it.gender, "slots" to it.slots)) }
                                loadOutfits()
                            }
                        }
                    )
                    WardrobeIconButton(
                        selected = currentAgeGroup == "child",
                        icon = Icons.Default.Person,
                        onClick = {
                            if (activeOutfitId == "unsaved") unsavedAgeGroup = "child"
                            else scope.launch {
                                activeOutfit?.let { api.wardrobeSave(ownerId, mapOf("outfit_id" to it.id, "age_group" to "child", "name" to it.name, "gender" to it.gender, "slots" to it.slots)) }
                                loadOutfits()
                            }
                        }
                    )
                    WardrobeIconButton(
                        selected = currentAgeGroup == "baby",
                        icon = Icons.Default.ChildCare,
                        onClick = {
                            if (activeOutfitId == "unsaved") unsavedAgeGroup = "baby"
                            else scope.launch {
                                activeOutfit?.let { api.wardrobeSave(ownerId, mapOf("outfit_id" to it.id, "age_group" to "baby", "name" to it.name, "gender" to it.gender, "slots" to it.slots)) }
                                loadOutfits()
                            }
                        }
                    )
                }

                // Content: Figure on top, Produkte-Grid below
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Figure
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (currentGeneratedImageUrl != null) {
                            Row(
                                modifier = Modifier.padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WardrobeIconButton(
                                    selected = figureView == "figure",
                                    icon = Icons.Default.Person,
                                    onClick = { figureView = "figure" }
                                )
                                WardrobeIconButton(
                                    selected = figureView == "outfit",
                                    icon = Icons.Default.Image,
                                    onClick = { figureView = "outfit" }
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .background(WardrobeColors.FigureDefault.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        ) {
                            if (figureView == "outfit" && currentGeneratedImageUrl != null) {
                                AsyncImage(
                                    model = currentGeneratedImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { lightboxUrl = currentGeneratedImageUrl }
                                )
                            } else {
                                WardrobeFigureSvg(
                                    gender = currentGender,
                                    ageGroup = currentAgeGroup,
                                    slots = currentSlots,
                                    onSlotClick = { slotKey -> productModalSlot = slotKey },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    // Produkte-Grid (3x3) unter der Figur
                    val onePieceFilled = currentSlots["one_piece"]?.productId?.isNotBlank() == true
                    WARDROBE_SLOTS.chunked(3).forEach { rowSlots ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowSlots.forEach { slot ->
                                val data = currentSlots[slot.key]
                                val isFilled = data != null && data.productId.isNotBlank()
                                val isCovered = onePieceFilled && slot.key in ONE_PIECE_CONFLICTS && !isFilled
                                Box(modifier = Modifier.weight(1f)) {
                                    WardrobeSlotCell(
                                        slot = slot,
                                        data = data,
                                        isFilled = isFilled,
                                        isCovered = isCovered,
                                        onSlotClick = { productModalSlot = slot.key }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

        // Drawer: outfit list (hamburger menu)
        if (sidebarOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { closeSidebar() }
                    .zIndex(1f)
            )
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(260.dp)
                    .offset(x = sidebarOffset)
                    .zIndex(2f),
                color = WardrobeColors.Gray100,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    WardrobeSidebarUnsaved(
                        isActive = activeOutfitId == "unsaved",
                        userImageUrl = userImageUrl,
                        onClick = { activeOutfitId = "unsaved"; closeSidebar() },
                        onUploadClick = { userImageDialogOpen = true },
                        onClearImage = { userImageUrl = null }
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .height(1.dp)
                            .fillMaxWidth()
                            .background(WardrobeColors.Gray200)
                    )
                    outfits.forEach { outfit ->
                        WardrobeSidebarEntry(
                            outfit = outfit,
                            isActive = activeOutfitId == outfit.id,
                            onSelect = { selectOutfitAndClose(outfit.id) },
                            onDelete = { deleteOutfit(outfit.id) }
                        )
                    }
                }
            }
        }

        // Product selection modal
        productModalSlot?.let { slotKey ->
            WardrobeProductModal(
                slotKey = slotKey,
                slotLabel = WARDROBE_SLOTS.find { it.key == slotKey }?.label ?: slotKey,
                gender = currentGender,
                ageGroup = currentAgeGroup,
                usedProductIds = (outfits.flatMap { it.slots.values.map { it.productId } } + unsavedSlots.values.map { it.productId }).toSet(),
                api = api,
                onSelectVariant = { product, variant ->
                    setSlot(slotKey, SlotData(
                        productId = product.id,
                        variantId = variant.id,
                        title = product.title,
                        variantTitle = variant.title,
                        price = variant.price,
                        currency = product.currency,
                        imageUrl = variant.image ?: product.image ?: ""
                    ))
                    productModalSlot = null
                    variantModalProduct = null
                },
                onSelectProduct = { product ->
                    if (product.variants.size <= 1) {
                        val v = product.variants.firstOrNull()
                        if (v != null) {
                            setSlot(slotKey, SlotData(
                                productId = product.id,
                                variantId = v.id,
                                title = product.title,
                                variantTitle = v.title,
                                price = v.price,
                                currency = product.currency,
                                imageUrl = v.image ?: product.image ?: ""
                            ))
                        }
                        productModalSlot = null
                    } else {
                        variantModalProduct = product
                    }
                },
                onDismiss = { productModalSlot = null; variantModalProduct = null }
            )
        }

        // Variant modal
        variantModalProduct?.let { product ->
            WardrobeVariantModal(
                product = product,
                slotKey = productModalSlot ?: "",
                onConfirm = { variant ->
                    productModalSlot?.let { sk ->
                        setSlot(sk, SlotData(
                            productId = product.id,
                            variantId = variant.id,
                            title = product.title,
                            variantTitle = variant.title,
                            price = variant.price,
                            currency = product.currency,
                            imageUrl = variant.image ?: product.image ?: ""
                        ))
                    }
                    productModalSlot = null
                    variantModalProduct = null
                },
                onDismiss = { variantModalProduct = null }
            )
        }

        // Lightbox
        lightboxUrl?.let { url ->
            WardrobeLightbox(url = url, onDismiss = { lightboxUrl = null })
        }

        // User image dialog
        if (userImageDialogOpen) {
            WardrobeUserImageDialog(
                onUpload = { filePicker.launch("image/*") },
                onMockup = { userImageDialogOpen = false },
                onDismiss = { userImageDialogOpen = false }
            )
        }

        // Save outfit dialog
        if (saveOutfitDialogOpen) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { saveOutfitDialogOpen = false }) {
                Surface(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Outfit speichern", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        BasicTextField(
                            value = saveOutfitName,
                            onValueChange = { saveOutfitName = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .background(WardrobeColors.Gray100, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            singleLine = true,
                            decorationBox = { inner ->
                                Box { inner() }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { saveOutfitDialogOpen = false }) { Text("Abbrechen") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    saveUnsavedOutfit(saveOutfitName.ifBlank { "Outfit" })
                                }
                            ) { Text("Speichern") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WardrobeSidebarUnsaved(
    isActive: Boolean,
    userImageUrl: String?,
    onClick: () -> Unit,
    onUploadClick: () -> Unit,
    onClearImage: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp)
            .then(if (isActive) Modifier.background(WardrobeColors.IndigoBg) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(2.dp, WardrobeColors.Gray300, RoundedCornerShape(10.dp))
                .clickable(onClick = onUploadClick),
            contentAlignment = Alignment.Center
        ) {
            if (userImageUrl != null) {
                AsyncImage(
                    model = userImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = null, tint = WardrobeColors.Gray400)
                Text("Upload", style = MaterialTheme.typography.labelSmall, color = WardrobeColors.Gray400)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text("Unsaved Outfit", style = MaterialTheme.typography.bodyMedium, color = WardrobeColors.Gray700)
    }
}

@Composable
private fun WardrobeSidebarEntry(
    outfit: WardrobeOutfit,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(12.dp)
            .then(if (isActive) Modifier.background(WardrobeColors.IndigoBg) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (outfit.generatedImageUrl != null) 72.dp else if (outfit.userImageUrl != null) 48.dp else 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(WardrobeColors.Gray200)
        ) {
            when {
                outfit.status == "generating" -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = WardrobeColors.Indigo)
                outfit.generatedImageUrl != null -> AsyncImage(model = outfit.generatedImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                outfit.userImageUrl != null -> AsyncImage(model = outfit.userImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> Icon(Icons.Default.Checkroom, contentDescription = null, modifier = Modifier.align(Alignment.Center), tint = WardrobeColors.Gray400)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = outfit.name,
            style = MaterialTheme.typography.bodyMedium,
            color = WardrobeColors.Gray700,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = WardrobeColors.Gray400)
        }
    }
}

@Composable
private fun WardrobeIconButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isMale: Boolean? = null
) {
    val color = when {
        isMale == true && selected -> WardrobeColors.MaleBlue
        isMale == false && selected -> WardrobeColors.FemalePink
        selected -> WardrobeColors.Indigo
        else -> WardrobeColors.Gray500
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .then(
                if (selected) Modifier.background(WardrobeColors.IndigoBg, RoundedCornerShape(8.dp))
                    .border(2.dp, color, RoundedCornerShape(8.dp))
                else Modifier.background(WardrobeColors.Gray100, RoundedCornerShape(8.dp))
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun WardrobeSelectorButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    isMale: Boolean? = null
) {
    val color = when {
        isMale == true && selected -> WardrobeColors.MaleBlue
        isMale == false && selected -> WardrobeColors.FemalePink
        selected -> WardrobeColors.Indigo
        else -> WardrobeColors.Gray500
    }
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) WardrobeColors.IndigoBg else WardrobeColors.Gray100,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, color) else null
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun WardrobeSlotCell(
    slot: WardrobeSlot,
    data: SlotData?,
    isFilled: Boolean,
    isCovered: Boolean,
    onSlotClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .border(
                2.dp,
                if (isCovered) WardrobeColors.Gray300 else WardrobeColors.Gray300,
                RoundedCornerShape(10.dp)
            )
            .background(if (isCovered) WardrobeColors.Gray100 else WardrobeColors.Gray100)
            .clickable(onClick = onSlotClick)
            .padding(6.dp)
    ) {
        if (isCovered) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Checkroom, contentDescription = null, tint = WardrobeColors.Gray400.copy(alpha = 0.6f))
                Text(slot.label, style = MaterialTheme.typography.labelSmall, color = WardrobeColors.Gray400)
                Text("Verdeckt", style = MaterialTheme.typography.labelSmall, color = WardrobeColors.Gray400)
            }
        } else if (isFilled && data != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (data.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = data.imageUrl,
                        contentDescription = data.title,
                        modifier = Modifier.weight(0.7f).fillMaxWidth(),
                        contentScale = ContentScale.Crop
                    )
                }
                Text(
                    text = "${data.price} ${data.currency}",
                    style = MaterialTheme.typography.labelSmall,
                    color = WardrobeColors.Gray700,
                    modifier = Modifier.padding(4.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = WardrobeColors.Gray400)
                Text(slot.label, style = MaterialTheme.typography.labelSmall, color = WardrobeColors.Gray400)
            }
        }
    }
}

@Composable
private fun WardrobeProductModal(
    slotKey: String,
    slotLabel: String,
    gender: String,
    ageGroup: String,
    usedProductIds: Set<String>,
    api: com.eazpire.creator.api.CreatorApi,
    onSelectProduct: (WardrobeProduct) -> Unit,
    onSelectVariant: (WardrobeProduct, WardrobeVariant) -> Unit,
    onDismiss: () -> Unit
) {
    var products by remember { mutableStateOf<List<WardrobeProduct>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var usageFilter by remember { mutableStateOf("unused") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(slotKey) {
        loading = true
        try {
            val resp = api.getShopifyProducts(com.eazpire.creator.auth.AuthConfig.SHOP_DOMAIN)
            if (resp.optBoolean("ok", false)) {
                val arr = resp.optJSONArray("products")
                products = parseProductsFromJson(arr)
            }
        } catch (e: Exception) {
            DebugLog.click("Products load error: ${e.message}")
        } finally {
            loading = false
        }
    }

    val filtered = remember(products, searchQuery, usageFilter, usedProductIds, slotKey, gender, ageGroup) {
        var list = products
        // Category filter (slot)
        list = list.filter { WardrobeFilter.matchesCategory(it.productType, it.title, it.tags, slotKey) }
        // Gender filter
        list = list.filter { WardrobeFilter.matchesGender(it.tags, gender) }
        // Age filter
        list = list.filter { WardrobeFilter.matchesAge(it.tags, ageGroup) }
        // Usage filter
        if (usageFilter == "used") {
            list = list.filter { it.id in usedProductIds }
        } else {
            list = list.filter { it.id !in usedProductIds }
        }
        // Search
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            list = list.filter { it.title.lowercase().contains(q) || it.productType.lowercase().contains(q) }
        }
        list
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$slotLabel auswählen", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp)
                            .background(WardrobeColors.Gray100, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        singleLine = true,
                        decorationBox = { inner ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Search, contentDescription = null, tint = WardrobeColors.Gray500)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) { inner() }
                            }
                        }
                    )
                    Row {
                        WardrobeSelectorButton(selected = usageFilter == "unused", label = "Unused", onClick = { usageFilter = "unused" })
                        WardrobeSelectorButton(selected = usageFilter == "used", label = "Used", onClick = { usageFilter = "used" })
                    }
                }
                if (loading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = WardrobeColors.Indigo)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filtered) { product ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.75f)
                                    .clickable { onSelectProduct(product) },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = WardrobeColors.Gray100)
                            ) {
                                Column {
                                    if (product.image != null) {
                                        AsyncImage(
                                            model = product.image,
                                            contentDescription = product.title,
                                            modifier = Modifier.height(160.dp).fillMaxWidth(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Text(product.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp), maxLines = 2)
                                    Text("${product.price} ${product.currency}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WardrobeVariantModal(
    product: WardrobeProduct,
    slotKey: String,
    onConfirm: (WardrobeVariant) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedVariant by remember { mutableStateOf<WardrobeVariant?>(null) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Variante wählen", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                    if (product.image != null) {
                        AsyncImage(model = product.image, contentDescription = null, modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)))
                    }
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(product.title, style = MaterialTheme.typography.bodyLarge)
                        Text("${product.price} ${product.currency}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                product.variants.forEach { v ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedVariant = v }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedVariant == v, onClick = { selectedVariant = v })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${v.title} - ${v.price} ${product.currency}")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { selectedVariant?.let { onConfirm(it) } },
                        enabled = selectedVariant != null
                    ) { Text("Confirm") }
                }
            }
        }
    }
}

@Composable
private fun WardrobeLightbox(url: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f))
                .clickable(onClick = onDismiss)
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

@Composable
private fun WardrobeUserImageDialog(
    onUpload: () -> Unit,
    onMockup: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Userbild auswählen", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onUpload, modifier = Modifier.fillMaxWidth()) { Text("Bild hochladen") }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onMockup, modifier = Modifier.fillMaxWidth()) { Text("Mockup auswählen") }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) { Text("Abbrechen") }
            }
        }
    }
}

private fun parseProductsFromJson(arr: org.json.JSONArray?): List<WardrobeProduct> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        val variantsArr = obj.optJSONArray("variants")
        val variants = (0 until (variantsArr?.length() ?: 0)).mapNotNull { j ->
            val v = variantsArr?.optJSONObject(j) ?: return@mapNotNull null
            WardrobeVariant(
                id = v.optString("id", ""),
                title = v.optString("title", "Default"),
                price = v.optString("price", "0"),
                available = v.optBoolean("available", true),
                image = v.optString("image").takeIf { it.isNotBlank() },
                option1 = v.optString("option1").takeIf { it.isNotBlank() },
                option2 = v.optString("option2").takeIf { it.isNotBlank() }
            )
        }
        val finalVariants = if (variants.isEmpty()) {
            listOf(WardrobeVariant(
                id = obj.optString("variantId", ""),
                title = "Default",
                price = obj.optString("price", "0"),
                available = true,
                image = null,
                option1 = null,
                option2 = null
            ))
        } else variants
        WardrobeProduct(
            id = obj.optString("id", ""),
            title = obj.optString("title", "Product"),
            handle = obj.optString("handle", ""),
            image = obj.optString("image").takeIf { it.isNotBlank() },
            price = obj.optString("price", "0"),
            currency = obj.optString("currency", "EUR"),
            variantId = obj.optString("variantId").takeIf { it.isNotBlank() },
            productType = obj.optString("product_type", obj.optString("productType", "")),
            tags = (obj.optJSONArray("tags")?.let { t -> (0 until t.length()).mapNotNull { idx -> t.optString(idx).takeIf { s -> s.isNotBlank() } } } ?: emptyList()),
            variants = finalVariants
        )
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
    val slots = if (slotsObj != null) parseSlotsFromJson(slotsObj) else emptyMap()
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
