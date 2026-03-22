@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.eazpire.creator.ui.creator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class PromotionRow(
    val id: String,
    val name: String,
    val discountType: String,
    val discountValue: Double,
    val durationDays: Int,
    val startsAt: Long?,
    val endsAt: Long?,
    val productIds: List<String>
)

private data class PromoProductDraft(
    val id: String,
    val title: String,
    val imageUrl: String?
)

private enum class PromoSheetPage { Form, Picker }

private fun startOfDay(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun endOfDay(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

@Composable
fun MarketingPromotionsPanel(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier
) {
    val jwt = remember(tokenStore) { tokenStore.getJwt().orEmpty() }
    val api = remember(jwt) { CreatorApi(jwt = jwt) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<PromotionRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }

    var showSheet by remember { mutableStateOf(false) }
    var sheetPage by remember { mutableStateOf(PromoSheetPage.Form) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var discountType by remember { mutableStateOf("percent") }
    var discountValue by remember { mutableStateOf("15") }
    var startDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var endDateMillis by remember { mutableStateOf(System.currentTimeMillis() + 7L * 86400000L) }
    var selectedProducts by remember { mutableStateOf<List<PromoProductDraft>>(emptyList()) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    var pickerSearch by remember { mutableStateOf("") }
    var pickerProducts by remember { mutableStateOf<List<PromoProductDraft>>(emptyList()) }
    var pickerLoading by remember { mutableStateOf(false) }
    var pickerChecked by remember { mutableStateOf(setOf<String>()) }

    val dateFmt = remember {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    }

    fun parsePromotionList(resp: JSONObject): List<PromotionRow> {
        val arr = resp.optJSONArray("promotions") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val pidArr = o.optJSONArray("product_ids")
            val pids = mutableListOf<String>()
            if (pidArr != null) {
                for (j in 0 until pidArr.length()) {
                    pids.add(pidArr.optString(j, ""))
                }
            }
            PromotionRow(
                id = o.optString("id", ""),
                name = o.optString("name", ""),
                discountType = o.optString("discount_type", "percent"),
                discountValue = o.optDouble("discount_value", 0.0),
                durationDays = o.optInt("duration_days", 7),
                startsAt = if (o.has("starts_at")) o.optLong("starts_at") else null,
                endsAt = if (o.has("ends_at")) o.optLong("ends_at") else null,
                productIds = pids
            )
        }
    }

    LaunchedEffect(ownerId, refresh) {
        if (ownerId.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            val resp = withContext(Dispatchers.IO) { api.listPromotions(ownerId) }
            if (resp.optBoolean("ok", false)) {
                items = parsePromotionList(resp)
            } else {
                items = emptyList()
            }
        } catch (_: Exception) {
            items = emptyList()
        }
        loading = false
    }

    LaunchedEffect(ownerId, sheetPage, pickerSearch, showSheet) {
        if (!showSheet || sheetPage != PromoSheetPage.Picker || ownerId.isBlank()) return@LaunchedEffect
        pickerLoading = true
        try {
            val resp = withContext(Dispatchers.IO) {
                api.listProductsForPromotion(
                    ownerId,
                    promotionId = editId,
                    q = pickerSearch.takeIf { it.isNotBlank() },
                    collectionHandle = null
                )
            }
            if (resp.optBoolean("ok", false)) {
                val arr = resp.optJSONArray("products") ?: org.json.JSONArray()
                val list = mutableListOf<PromoProductDraft>()
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    list.add(
                        PromoProductDraft(
                            id = p.optString("id", ""),
                            title = p.optString("title", p.optString("id", "")),
                            imageUrl = p.optString("image", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
                pickerProducts = list.filter { pr ->
                    selectedProducts.none { it.id == pr.id }
                }
            } else {
                pickerProducts = emptyList()
            }
        } catch (_: Exception) {
            pickerProducts = emptyList()
        }
        pickerLoading = false
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Color(0x991C1F2B),
        unfocusedContainerColor = Color(0x991C1F2B),
        focusedLabelColor = EazColors.Orange,
        unfocusedLabelColor = Color.White.copy(alpha = 0.55f),
        cursorColor = EazColors.Orange,
        focusedBorderColor = EazColors.Orange,
        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
    )

    fun resetFormForNew() {
        editId = null
        name = ""
        description = ""
        discountType = "percent"
        discountValue = "15"
        startDateMillis = startOfDay(System.currentTimeMillis())
        endDateMillis = endOfDay(System.currentTimeMillis() + 7L * 86400000L)
        selectedProducts = emptyList()
        sheetPage = PromoSheetPage.Form
    }

    fun openEdit(p: PromotionRow) {
        editId = p.id
        name = p.name
        description = ""
        discountType = if (p.discountType == "fixed_usd" || p.discountType == "fixed") "fixed_usd" else "percent"
        discountValue = p.discountValue.toString()
        val s = p.startsAt ?: System.currentTimeMillis()
        val e = p.endsAt ?: (s + 7L * 86400000L)
        startDateMillis = s
        endDateMillis = e
        sheetPage = PromoSheetPage.Form
        selectedProducts = p.productIds.map { id -> PromoProductDraft(id, id, null) }
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    api.listProductsForPromotion(ownerId, promotionId = p.id, q = null, collectionHandle = null)
                }
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("products") ?: return@launch
                    val map = mutableMapOf<String, PromoProductDraft>()
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString("id", "")
                        map[id] = PromoProductDraft(
                            id = id,
                            title = o.optString("title", id),
                            imageUrl = o.optString("image", "").takeIf { it.isNotBlank() }
                        )
                    }
                    selectedProducts = p.productIds.map { id ->
                        map[id] ?: PromoProductDraft(id, id, null)
                    }
                }
            } catch (_: Exception) {
            }
        }
        showSheet = true
    }

    if (showStartPicker) {
        val startPickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startPickerState.selectedDateMillis?.let {
                            startDateMillis = startOfDay(it)
                            if (endDateMillis <= startDateMillis) {
                                endDateMillis = endOfDay(it + 7L * 86400000L)
                            }
                        }
                        showStartPicker = false
                    }
                ) {
                    Text(translationStore.t("creator.common.confirm", "OK"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) {
                    Text(translationStore.t("creator.promotions.cancel", "Cancel"))
                }
            }
        ) {
            DatePicker(state = startPickerState)
        }
    }

    if (showEndPicker) {
        val endPickerState = rememberDatePickerState(initialSelectedDateMillis = endDateMillis)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endPickerState.selectedDateMillis?.let {
                            endDateMillis = endOfDay(it)
                        }
                        showEndPicker = false
                    }
                ) {
                    Text(translationStore.t("creator.common.confirm", "OK"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) {
                    Text(translationStore.t("creator.promotions.cancel", "Cancel"))
                }
            }
        ) {
            DatePicker(state = endPickerState)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (loading) {
            Text(
                text = translationStore.t("creator.common.loading", "Loading..."),
                color = Color.White.copy(alpha = 0.7f)
            )
        } else if (items.isEmpty()) {
            Text(
                text = translationStore.t("creator.promotions.empty", "No promotions yet."),
                color = Color.White.copy(alpha = 0.6f)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, EazColors.Orange.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .background(EazColors.Orange.copy(alpha = 0.08f))
                        .clickable {
                            resetFormForNew()
                            showSheet = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "+",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = EazColors.Orange
                        )
                        Text(
                            text = translationStore.t("creator.promotions.new_promotion", "New promotion"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
            lazyGridItems(items, key = { it.id }) { p ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .background(Color(0x991C1F2B))
                        .clickable { openEdit(p) }
                        .padding(12.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Column {
                        Text(
                            text = p.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2
                        )
                        val label = if (p.discountType == "fixed_usd" || p.discountType == "fixed") {
                            "-$" + p.discountValue
                        } else {
                            "-${p.discountValue.toInt()}%"
                        }
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            color = EazColors.Orange,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                sheetPage = PromoSheetPage.Form
            },
            sheetState = sheetState,
            containerColor = Color(0xFF0B0F16),
            contentColor = Color.White,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                )
            },
            modifier = Modifier.fillMaxSize()
        ) {
            when (sheetPage) {
                PromoSheetPage.Form -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = if (editId == null) {
                                translationStore.t("creator.promotions.modal_title_new", "New promotion")
                            } else {
                                translationStore.t("creator.promotions.modal_title_edit", "Edit promotion")
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(translationStore.t("creator.promotions.name", "Name")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(translationStore.t("creator.promotions.description", "Description")) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val pctLabel = "%"
                        val usdLabel = "$"
                        Text(
                            text = translationStore.t("creator.promotions.discount_type", "Type"),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Row(
                                modifier = Modifier.weight(0.38f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { discountType = "percent" },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (discountType == "percent") EazColors.Orange else Color.White.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Text(
                                        pctLabel,
                                        color = if (discountType == "percent") EazColors.Orange else Color.White.copy(alpha = 0.75f),
                                        fontWeight = if (discountType == "percent") FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                OutlinedButton(
                                    onClick = { discountType = "fixed_usd" },
                                    modifier = Modifier.weight(1f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (discountType == "fixed_usd") EazColors.Orange else Color.White.copy(alpha = 0.2f)
                                    )
                                ) {
                                    Text(
                                        usdLabel,
                                        color = if (discountType == "fixed_usd") EazColors.Orange else Color.White.copy(alpha = 0.75f),
                                        fontWeight = if (discountType == "fixed_usd") FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = discountValue,
                                onValueChange = { discountValue = it },
                                label = { Text(translationStore.t("creator.promotions.discount_value", "Value")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(0.62f),
                                colors = fieldColors
                            )
                        }

                        if (discountType == "fixed_usd") {
                            Text(
                                text = translationStore.t(
                                    "creator.promotions.discount_value_usd_hint",
                                    ""
                                ),
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.45f),
                                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = dateFmt.format(Date(startDateMillis)),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(translationStore.t("creator.promotions.date_start", "Start")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showStartPicker = true
                                    },
                                colors = fieldColors
                            )
                            OutlinedTextField(
                                value = dateFmt.format(Date(endDateMillis)),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(translationStore.t("creator.promotions.date_end", "End")) },
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showEndPicker = true
                                    },
                                colors = fieldColors
                            )
                        }
                        Text(
                            text = translationStore.t("creator.promotions.duration_hint", ""),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.padding(top = 6.dp)
                        )

                        Divider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            color = Color.White.copy(alpha = 0.1f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = translationStore.t("creator.promotions.products_heading", "Products"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE5E7EB)
                            )
                            TextButton(
                                onClick = {
                                    pickerChecked = emptySet()
                                    pickerSearch = ""
                                    sheetPage = PromoSheetPage.Picker
                                }
                            ) {
                                Text(translationStore.t("creator.promotions.add_products", "Add products"))
                            }
                        }

                        if (selectedProducts.isEmpty()) {
                            Text(
                                text = translationStore.t("creator.promotions.products_empty", "No products yet."),
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                            ) {
                                selectedProducts.forEachIndexed { i, pr ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White.copy(alpha = 0.06f))
                                            .border(
                                                1.dp,
                                                Color.White.copy(alpha = 0.08f),
                                                RoundedCornerShape(10.dp)
                                            )
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (!pr.imageUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context).data(pr.imageUrl).build(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White.copy(alpha = 0.08f))
                                            )
                                        }
                                        Text(
                                            text = pr.title,
                                            modifier = Modifier.weight(1f),
                                            fontSize = 14.sp,
                                            color = Color.White,
                                            maxLines = 2
                                        )
                                        TextButton(
                                            onClick = {
                                                selectedProducts = selectedProducts.filterIndexed { j, _ -> j != i }
                                            }
                                        ) {
                                            Text(
                                                translationStore.t("creator.promotions.remove", "Remove"),
                                                color = Color(0xFFFCA5A5)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (editId != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        try {
                                            val resp = withContext(Dispatchers.IO) {
                                                api.deletePromotion(ownerId, editId!!)
                                            }
                                            if (resp.optBoolean("ok", false)) {
                                                showSheet = false
                                                refresh += 1
                                            }
                                        } catch (_: Exception) {
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                border = BorderStroke(
                                    1.dp,
                                    Color(0xFFF87171).copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    translationStore.t("creator.promotions.delete", "Delete"),
                                    color = Color(0xFFFECACA)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showSheet = false }) {
                                Text(translationStore.t("creator.promotions.cancel", "Cancel"))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    if (endDateMillis <= startDateMillis) {
                                        return@TextButton
                                    }
                                    val durationDays = maxOf(
                                        1,
                                        ((endDateMillis - startDateMillis) / 86400000L).toInt()
                                    )
                                    val body = JSONObject()
                                    body.put("owner_id", ownerId)
                                    body.put("name", name.trim())
                                    body.put("description", description.trim())
                                    body.put("discount_type", discountType)
                                    body.put("discount_value", discountValue.toDoubleOrNull() ?: 0.0)
                                    body.put("duration_days", durationDays)
                                    body.put("starts_at", startDateMillis)
                                    body.put("ends_at", endDateMillis)
                                    val arr = JSONArray()
                                    selectedProducts.forEach { arr.put(it.id) }
                                    body.put("product_ids", arr)
                                    editId?.let { body.put("id", it) }
                                    scope.launch {
                                        try {
                                            val resp = withContext(Dispatchers.IO) { api.savePromotion(body) }
                                            if (resp.optBoolean("ok", false)) {
                                                showSheet = false
                                                refresh += 1
                                            }
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    translationStore.t("creator.promotions.save", "Save"),
                                    color = EazColors.Orange,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                PromoSheetPage.Picker -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp)
                            .heightIn(max = 520.dp)
                    ) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            sheetPage = PromoSheetPage.Form
                                        }
                                        .padding(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                                Text(
                                    text = translationStore.t("creator.promotions.picker_title", "Add products"),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = pickerSearch,
                                onValueChange = { pickerSearch = it },
                                label = { Text(translationStore.t("creator.promotions.search_products", "Search")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        if (pickerLoading) {
                            item {
                                Text(
                                    translationStore.t("creator.common.loading", "Loading…"),
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            items(pickerProducts, key = { it.id }) { pr ->
                                val checked = pr.id in pickerChecked
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            pickerChecked = if (checked) {
                                                pickerChecked - pr.id
                                            } else {
                                                pickerChecked + pr.id
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { on ->
                                            pickerChecked = if (on) pickerChecked + pr.id else pickerChecked - pr.id
                                        }
                                    )
                                    if (!pr.imageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(pr.imageUrl).build(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.White.copy(alpha = 0.08f))
                                        )
                                    }
                                    Text(
                                        text = pr.title,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 24.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { sheetPage = PromoSheetPage.Form }) {
                                    Text(translationStore.t("creator.promotions.cancel", "Cancel"))
                                }
                                TextButton(
                                    onClick = {
                                        val add = pickerProducts.filter { it.id in pickerChecked }
                                        selectedProducts = selectedProducts + add.filter { np ->
                                            selectedProducts.none { it.id == np.id }
                                        }
                                        sheetPage = PromoSheetPage.Form
                                    }
                                ) {
                                    Text(
                                        translationStore.t("creator.promotions.add_selected", "Add selected"),
                                        color = EazColors.Orange,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
