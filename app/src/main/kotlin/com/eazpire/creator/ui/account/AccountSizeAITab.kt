package com.eazpire.creator.ui.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.util.DebugLog
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun formatNum(obj: JSONObject, key: String, decimals: Int = 0): String {
    val v = obj.optDouble(key, 0.0)
    if (v <= 0) return ""
    return if (decimals > 0) v.toString() else v.toInt().toString()
}

private data class SizeRecommendationRow(
    val productTypeKey: String,
    val nameEn: String,
    val size: String,
    val alternativeSize: String?,
    val confidence: Double?,
    val basis: String?,
    val fitNotes: String?
)

private data class ReferenceFitUi(
    val id: Long,
    val brandName: String,
    val productTypeName: String,
    val categoryKey: String,
    val size: String,
    val fitRating: String
)

private data class BrandOption(val id: Long, val name: String)

private data class ProductTypeOption(val id: Long, val key: String, val nameEn: String, val categoryKey: String)

private fun JSONArray.toRecommendationRows(): List<SizeRecommendationRow> {
    val out = mutableListOf<SizeRecommendationRow>()
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        val conf = when {
            !o.has("confidence") || o.isNull("confidence") -> null
            else -> o.optDouble("confidence", Double.NaN).takeUnless { it.isNaN() }
        }
        val basis = o.optString("basis", "").ifBlank { null }
            ?: o.optString("calculation_basis", "").ifBlank { null }
        out.add(
            SizeRecommendationRow(
                productTypeKey = o.optString("product_type"),
                nameEn = o.optString("product_type_name_en").ifBlank { o.optString("product_type") },
                size = o.optString("size").ifBlank { "—" },
                alternativeSize = o.optString("alternative_size").takeIf { it.isNotBlank() },
                confidence = conf,
                basis = basis,
                fitNotes = o.optString("fit_notes").takeIf { it.isNotBlank() }
            )
        )
    }
    return out
}

private fun parseRecommendations(resp: JSONObject): Triple<List<SizeRecommendationRow>, List<SizeRecommendationRow>, List<SizeRecommendationRow>> {
    if (!resp.optBoolean("ok", false)) return Triple(emptyList(), emptyList(), emptyList())
    val obj = resp.optJSONObject("recommendations") ?: return Triple(emptyList(), emptyList(), emptyList())
    val tops = obj.optJSONArray("tops")?.toRecommendationRows() ?: emptyList()
    val bottoms = obj.optJSONArray("bottoms")?.toRecommendationRows() ?: emptyList()
    val footwear = obj.optJSONArray("footwear")?.toRecommendationRows() ?: emptyList()
    return Triple(tops, bottoms, footwear)
}

private fun parseReferenceFits(resp: JSONObject): List<ReferenceFitUi> {
    if (!resp.optBoolean("ok", false)) return emptyList()
    val arr = resp.optJSONArray("reference_fits") ?: return emptyList()
    val out = mutableListOf<ReferenceFitUi>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val brand = o.optJSONObject("brand")
        val pt = o.optJSONObject("product_type")
        out.add(
            ReferenceFitUi(
                id = o.optLong("id", 0L),
                brandName = brand?.optString("name") ?: "—",
                productTypeName = pt?.optString("name_en")?.ifBlank { null } ?: pt?.optString("key") ?: "—",
                categoryKey = pt?.optString("category") ?: "",
                size = o.optString("size").ifBlank { "—" },
                fitRating = o.optString("fit_rating").ifBlank { "—" }
            )
        )
    }
    return out
}

private fun parseBrands(resp: JSONObject): List<BrandOption> {
    if (!resp.optBoolean("ok", false)) return emptyList()
    val arr = resp.optJSONArray("brands") ?: return emptyList()
    val out = mutableListOf<BrandOption>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out.add(BrandOption(id = o.optLong("id", 0L), name = o.optString("name")))
    }
    return out.filter { it.id > 0 && it.name.isNotBlank() }
}

private fun parseProductTypes(resp: JSONObject): List<ProductTypeOption> {
    if (!resp.optBoolean("ok", false)) return emptyList()
    val arr = resp.optJSONArray("product_types") ?: return emptyList()
    val out = mutableListOf<ProductTypeOption>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val cat = o.optJSONObject("category")
        out.add(
            ProductTypeOption(
                id = o.optLong("id", 0L),
                key = o.optString("key"),
                nameEn = o.optString("name_en").ifBlank { o.optString("key") },
                categoryKey = cat?.optString("key") ?: ""
            )
        )
    }
    return out.filter { it.id > 0 && it.key.isNotBlank() }.sortedWith(
        compareBy({ it.categoryKey }, { it.nameEn })
    )
}

private fun fitRatingLabel(key: String): String = when (key) {
    "too_small" -> "Too small"
    "slightly_small" -> "Slightly small"
    "perfect" -> "Perfect fit"
    "slightly_large" -> "Slightly large"
    "too_large" -> "Too large"
    else -> key.replace('_', ' ')
}

private fun basisShort(basis: String?): String = when (basis) {
    "measurements" -> "measurements"
    "reference_fits" -> "reference fits"
    "photo_ai" -> "photo"
    "combined" -> "combined"
    null, "" -> ""
    else -> basis.replace('_', ' ')
}

@Composable
private fun RecommendationRowCard(row: SizeRecommendationRow, showSizeAiAttribution: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, EazColors.TopbarBorder.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = row.nameEn,
                style = MaterialTheme.typography.titleSmall,
                color = EazColors.TextPrimary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EazColors.OrangeBg,
                    border = BorderStroke(1.dp, EazColors.Orange)
                ) {
                    Text(
                        text = row.size,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = EazColors.Orange
                    )
                }
                row.alternativeSize?.let { alt ->
                    Text(
                        text = "Also: $alt",
                        style = MaterialTheme.typography.bodySmall,
                        color = EazColors.TextSecondary
                    )
                }
            }
            if (showSizeAiAttribution) {
                Text(
                    text = "Recommended by Size AI",
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.Orange
                )
            }
            val conf = row.confidence
            val basis = row.basis
            if (conf != null && conf > 0) {
                Text(
                    text = "${(conf * 100).toInt()}% confidence" +
                        if (basis != null && basis.isNotBlank()) " · ${basisShort(basis)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextSecondary
                )
            } else if (basis != null && basis.isNotBlank()) {
                Text(
                    text = basisShort(basis).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextSecondary
                )
            }
            row.fitNotes?.let { notes ->
                Text(text = notes, style = MaterialTheme.typography.bodySmall, color = EazColors.TextSecondary)
            }
        }
    }
}

@Composable
fun AccountSizeAITab(
    tokenStore: SecureTokenStore,
    onSaveActionReady: ((() -> Unit, Boolean) -> Unit)? = null,
    onSavingStateChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val jwt = remember { tokenStore.getJwt() }
    val ownerId = remember { tokenStore.getOwnerId() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }

    var selectedSubTab by remember { mutableIntStateOf(0) }
    var gender by remember { mutableStateOf("") }
    var heightCm by remember { mutableStateOf("") }
    var weightKg by remember { mutableStateOf("") }
    var chestCm by remember { mutableStateOf("") }
    var waistCm by remember { mutableStateOf("") }
    var hipCm by remember { mutableStateOf("") }
    var shoulderCm by remember { mutableStateOf("") }
    var inseamCm by remember { mutableStateOf("") }
    var footLengthCm by remember { mutableStateOf("") }
    var preferredFit by remember { mutableStateOf("regular") }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var recLoading by remember { mutableStateOf(false) }
    var recTops by remember { mutableStateOf<List<SizeRecommendationRow>>(emptyList()) }
    var recBottoms by remember { mutableStateOf<List<SizeRecommendationRow>>(emptyList()) }
    var recFootwear by remember { mutableStateOf<List<SizeRecommendationRow>>(emptyList()) }

    var refLoading by remember { mutableStateOf(false) }
    var refSaving by remember { mutableStateOf(false) }
    var references by remember { mutableStateOf<List<ReferenceFitUi>>(emptyList()) }
    var productTypes by remember { mutableStateOf<List<ProductTypeOption>>(emptyList()) }

    var allBrands by remember { mutableStateOf<List<BrandOption>>(emptyList()) }
    var brandsLoading by remember { mutableStateOf(false) }
    var brandMenuExpanded by remember { mutableStateOf(false) }
    var selectedBrand by remember { mutableStateOf<BrandOption?>(null) }
    var selectedProductType by remember { mutableStateOf<ProductTypeOption?>(null) }
    var refSize by remember { mutableStateOf("") }
    var refFitRating by remember { mutableStateOf("perfect") }
    var refNotes by remember { mutableStateOf("") }
    var refFormMessage by remember { mutableStateOf<String?>(null) }
    var refFormError by remember { mutableStateOf(false) }

    var showAddBrandDialog by remember { mutableStateOf(false) }
    var newBrandName by remember { mutableStateOf("") }
    var addBrandError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        statusMessage = null
        try {
            val resp = api.getSizeProfile(ownerId)
            if (resp.optBoolean("ok", false)) {
                val profile = resp.optJSONObject("profile")
                if (profile != null) {
                    gender = profile.optString("gender", "")
                    heightCm = formatNum(profile, "height_cm")
                    weightKg = formatNum(profile, "weight_kg", 1)
                    chestCm = formatNum(profile, "chest_cm")
                    waistCm = formatNum(profile, "waist_cm")
                    hipCm = formatNum(profile, "hip_cm")
                    shoulderCm = formatNum(profile, "shoulder_width_cm")
                    inseamCm = formatNum(profile, "inseam_cm")
                    footLengthCm = formatNum(profile, "foot_length_cm", 1)
                    preferredFit = profile.optString("preferred_fit", "regular").ifBlank { "regular" }
                }
            }
        } catch (e: Exception) {
            DebugLog.click("Size profile load error: ${e.message}")
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(ownerId, selectedSubTab) {
        if (ownerId.isBlank()) return@LaunchedEffect
        when (selectedSubTab) {
            1 -> {
                recLoading = true
                try {
                    val resp = api.getSizeRecommendations(ownerId)
                    val (t, b, f) = parseRecommendations(resp)
                    recTops = t
                    recBottoms = b
                    recFootwear = f
                } catch (e: Exception) {
                    DebugLog.click("Size recommendations load error: ${e.message}")
                    recTops = emptyList()
                    recBottoms = emptyList()
                    recFootwear = emptyList()
                } finally {
                    recLoading = false
                }
            }
            2 -> {
                refLoading = true
                try {
                    val listResp = api.listReferenceFits(ownerId)
                    references = parseReferenceFits(listResp)
                    if (productTypes.isEmpty()) {
                        val ptResp = api.listProductTypes()
                        productTypes = parseProductTypes(ptResp)
                    }
                    if (allBrands.isEmpty()) {
                        brandsLoading = true
                        try {
                            val bResp = api.listBrands(null)
                            allBrands = parseBrands(bResp).sortedBy { it.name }
                        } catch (_: Exception) {
                        } finally {
                            brandsLoading = false
                        }
                    }
                } catch (e: Exception) {
                    DebugLog.click("Reference fits load error: ${e.message}")
                } finally {
                    refLoading = false
                }
            }
        }
    }

    fun saveMeasurements() {
        if (ownerId.isBlank()) return
        val h = heightCm.toDoubleOrNull() ?: 0.0
        if (h < 50 || h > 300) {
            statusMessage = "Height must be 50–300 cm"
            statusError = true
            return
        }
        if (gender !in listOf("male", "female")) {
            statusMessage = "Please select gender"
            statusError = true
            return
        }
        scope.launch {
            isSaving = true
            statusMessage = null
            statusError = false
            try {
                val profile = mapOf<String, Any?>(
                    "gender" to gender,
                    "height_cm" to h,
                    "weight_kg" to weightKg.toDoubleOrNull(),
                    "chest_cm" to chestCm.toDoubleOrNull(),
                    "waist_cm" to waistCm.toDoubleOrNull(),
                    "hip_cm" to hipCm.toDoubleOrNull(),
                    "shoulder_width_cm" to shoulderCm.toDoubleOrNull(),
                    "inseam_cm" to inseamCm.toDoubleOrNull(),
                    "foot_length_cm" to footLengthCm.toDoubleOrNull(),
                    "preferred_fit" to preferredFit
                )
                val resp = api.saveSizeProfile(ownerId, profile)
                if (resp.optBoolean("ok", false)) {
                    statusMessage = "Saved"
                    statusError = false
                } else {
                    statusMessage = resp.optString("message", "Save failed")
                    statusError = true
                }
            } catch (e: Exception) {
                DebugLog.click("Size profile save error: ${e.message}")
                statusMessage = "Save failed"
                statusError = true
            } finally {
                isSaving = false
            }
        }
    }

    fun reloadReferences() {
        scope.launch {
            refLoading = true
            try {
                val listResp = api.listReferenceFits(ownerId)
                references = parseReferenceFits(listResp)
            } catch (_: Exception) {
            } finally {
                refLoading = false
            }
        }
    }

    fun saveReferenceFit() {
        if (ownerId.isBlank()) return
        val brand = selectedBrand
        val pt = selectedProductType
        val size = refSize.trim()
        if (brand == null) {
            refFormMessage = "Select a brand"
            refFormError = true
            return
        }
        if (pt == null) {
            refFormMessage = "Select a product type"
            refFormError = true
            return
        }
        if (size.isBlank()) {
            refFormMessage = "Enter the size label"
            refFormError = true
            return
        }
        scope.launch {
            refSaving = true
            refFormMessage = null
            refFormError = false
            try {
                val resp = api.saveReferenceFit(
                    ownerId = ownerId,
                    brandId = brand.id,
                    productTypeId = pt.id,
                    size = size,
                    fitRating = refFitRating,
                    notes = refNotes.trim().takeIf { it.isNotEmpty() }
                )
                if (resp.optBoolean("ok", false)) {
                    refSize = ""
                    refNotes = ""
                    refFormMessage = "Reference saved"
                    refFormError = false
                    reloadReferences()
                } else {
                    refFormMessage = resp.optString("message", resp.optString("error", "Save failed"))
                    refFormError = true
                }
            } catch (e: Exception) {
                refFormMessage = "Save failed"
                refFormError = true
            } finally {
                refSaving = false
            }
        }
    }

    fun deleteReference(id: Long) {
        scope.launch {
            try {
                val resp = api.deleteReferenceFit(ownerId, id)
                if (resp.optBoolean("ok", false)) reloadReferences()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(isSaving) {
        onSavingStateChange?.invoke(isSaving)
    }
    LaunchedEffect(selectedSubTab) {
        onSaveActionReady?.invoke({ saveMeasurements() }, selectedSubTab == 0)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Measurements", "Recommendations", "References").forEachIndexed { index, label ->
                val isSelected = selectedSubTab == index
                Text(
                    text = label,
                    modifier = Modifier
                        .background(
                            if (isSelected) EazColors.OrangeBg else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clickable { selectedSubTab = index },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) EazColors.Orange else EazColors.TextSecondary
                )
            }
        }

        when (selectedSubTab) {
            0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Your Measurements",
                        style = MaterialTheme.typography.titleMedium,
                        color = EazColors.TextPrimary
                    )
                    Text(
                        text = "Save your measurements to generate size recommendations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EazColors.TextSecondary
                    )
                    if (isLoading) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
                        }
                    } else {
                        Text(
                            text = "Gender",
                            style = MaterialTheme.typography.labelMedium,
                            color = EazColors.TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = gender == "male",
                                onClick = { gender = "male" },
                                colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                            )
                            Text("Male", modifier = Modifier.padding(end = 16.dp))
                            RadioButton(
                                selected = gender == "female",
                                onClick = { gender = "female" },
                                colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                            )
                            Text("Female")
                        }
                        OutlinedTextField(
                            value = heightCm,
                            onValueChange = { heightCm = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Height (cm)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EazColors.Orange,
                                unfocusedBorderColor = EazColors.TopbarBorder,
                                cursorColor = EazColors.Orange,
                                focusedLabelColor = EazColors.Orange
                            )
                        )
                        OutlinedTextField(
                            value = weightKg,
                            onValueChange = { weightKg = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Weight (kg)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EazColors.Orange,
                                unfocusedBorderColor = EazColors.TopbarBorder,
                                cursorColor = EazColors.Orange,
                                focusedLabelColor = EazColors.Orange
                            )
                        )
                        OutlinedTextField(
                            value = chestCm,
                            onValueChange = { chestCm = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Chest (cm)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EazColors.Orange,
                                unfocusedBorderColor = EazColors.TopbarBorder,
                                cursorColor = EazColors.Orange,
                                focusedLabelColor = EazColors.Orange
                            )
                        )
                        OutlinedTextField(
                            value = waistCm,
                            onValueChange = { waistCm = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Waist (cm)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EazColors.Orange,
                                unfocusedBorderColor = EazColors.TopbarBorder,
                                cursorColor = EazColors.Orange,
                                focusedLabelColor = EazColors.Orange
                            )
                        )
                        OutlinedTextField(
                            value = hipCm,
                            onValueChange = { hipCm = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Hip (cm)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EazColors.Orange,
                                unfocusedBorderColor = EazColors.TopbarBorder,
                                cursorColor = EazColors.Orange,
                                focusedLabelColor = EazColors.Orange
                            )
                        )
                        OutlinedTextField(
                            value = shoulderCm,
                            onValueChange = { shoulderCm = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Shoulder width (cm)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EazColors.Orange,
                                unfocusedBorderColor = EazColors.TopbarBorder,
                                cursorColor = EazColors.Orange,
                                focusedLabelColor = EazColors.Orange
                            )
                        )
                        OutlinedTextField(
                            value = inseamCm,
                            onValueChange = { inseamCm = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Inseam (cm)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EazColors.Orange,
                                unfocusedBorderColor = EazColors.TopbarBorder,
                                cursorColor = EazColors.Orange,
                                focusedLabelColor = EazColors.Orange
                            )
                        )
                        OutlinedTextField(
                            value = footLengthCm,
                            onValueChange = { footLengthCm = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Foot length (cm)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EazColors.Orange,
                                unfocusedBorderColor = EazColors.TopbarBorder,
                                cursorColor = EazColors.Orange,
                                focusedLabelColor = EazColors.Orange
                            )
                        )
                        Text(
                            text = "Preferred fit",
                            style = MaterialTheme.typography.labelMedium,
                            color = EazColors.TextSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferredFit == "regular",
                                onClick = { preferredFit = "regular" },
                                colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                            )
                            Text("Regular", modifier = Modifier.padding(end = 16.dp))
                            RadioButton(
                                selected = preferredFit == "tight",
                                onClick = { preferredFit = "tight" },
                                colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                            )
                            Text("Tight", modifier = Modifier.padding(end = 16.dp))
                            RadioButton(
                                selected = preferredFit == "loose",
                                onClick = { preferredFit = "loose" },
                                colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                            )
                            Text("Loose")
                        }
                        statusMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (statusError) MaterialTheme.colorScheme.error else EazColors.Orange
                            )
                        }
                    }
                }
            }
            1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Recommended sizes",
                        style = MaterialTheme.typography.titleMedium,
                        color = EazColors.TextPrimary
                    )
                    Text(
                        text = "Based on your profile and reference fits. Save measurements first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EazColors.TextSecondary
                    )
                    if (recLoading) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
                        }
                    } else {
                        val hasClothing = recTops.isNotEmpty() || recBottoms.isNotEmpty()
                        val hasAny = hasClothing || recFootwear.isNotEmpty()
                        if (!hasAny) {
                            Text(
                                text = "No recommendations yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EazColors.TextSecondary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            if (hasClothing) {
                                Text(
                                    text = "Clothing",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EazColors.TextPrimary
                                )
                                if (recTops.isNotEmpty()) {
                                    Text(
                                        text = "Tops",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = EazColors.TextSecondary,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    recTops.forEach { row ->
                                        RecommendationRowCard(row, showSizeAiAttribution = true)
                                    }
                                }
                                if (recBottoms.isNotEmpty()) {
                                    Text(
                                        text = "Bottoms",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = EazColors.TextSecondary,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                    recBottoms.forEach { row ->
                                        RecommendationRowCard(row, showSizeAiAttribution = true)
                                    }
                                }
                                if (recFootwear.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Divider(color = EazColors.TopbarBorder.copy(alpha = 0.4f))
                                }
                            }
                            if (recFootwear.isNotEmpty()) {
                                Text(
                                    text = "Footwear",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EazColors.TextPrimary,
                                    modifier = Modifier.padding(top = if (hasClothing) 8.dp else 0.dp)
                                )
                                recFootwear.forEach { row ->
                                    RecommendationRowCard(row, showSizeAiAttribution = false)
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Reference fits",
                        style = MaterialTheme.typography.titleMedium,
                        color = EazColors.TextPrimary
                    )
                    Text(
                        text = "Add pieces that fit well to improve recommendations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EazColors.TextSecondary
                    )
                    if (refLoading && references.isEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
                        }
                    } else if (references.isEmpty()) {
                        Text(
                            text = "No references yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EazColors.TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        references.forEach { ref ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, EazColors.TopbarBorder.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = "${ref.brandName} · ${ref.productTypeName}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = EazColors.TextPrimary
                                        )
                                        Text(
                                            text = "Size ${ref.size} · ${fitRatingLabel(ref.fitRating)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EazColors.TextSecondary
                                        )
                                    }
                                    IconButton(onClick = { deleteReference(ref.id) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete reference",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Add reference",
                        style = MaterialTheme.typography.titleSmall,
                        color = EazColors.TextPrimary
                    )
                    Text(
                        text = "Brand",
                        style = MaterialTheme.typography.labelMedium,
                        color = EazColors.TextSecondary
                    )
                    if (brandsLoading && allBrands.isEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            CircularProgressIndicator(
                                color = EazColors.Orange,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = selectedBrand?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select brand") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = EazColors.TextSecondary
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { brandMenuExpanded = true },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = EazColors.Orange,
                                    unfocusedBorderColor = EazColors.TopbarBorder,
                                    cursorColor = EazColors.Orange,
                                    focusedLabelColor = EazColors.Orange
                                )
                            )
                            DropdownMenu(
                                expanded = brandMenuExpanded,
                                onDismissRequest = { brandMenuExpanded = false },
                                modifier = Modifier.heightIn(max = 280.dp)
                            ) {
                                allBrands.forEach { b ->
                                    DropdownMenuItem(
                                        text = { Text(b.name) },
                                        onClick = {
                                            selectedBrand = b
                                            brandMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showAddBrandDialog = true }) {
                        Text("Add new brand", color = EazColors.Orange)
                    }

                    Text(
                        text = "Product type",
                        style = MaterialTheme.typography.labelMedium,
                        color = EazColors.TextSecondary
                    )
                    Column(modifier = Modifier.fillMaxWidth()) {
                        productTypes.forEach { pt ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProductType = pt }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedProductType?.id == pt.id,
                                    onClick = { selectedProductType = pt },
                                    colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                                )
                                Column {
                                    Text(pt.nameEn, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        pt.categoryKey,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EazColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = refSize,
                        onValueChange = { refSize = it },
                        label = { Text("Size on label (e.g. M, 42)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EazColors.Orange,
                            unfocusedBorderColor = EazColors.TopbarBorder,
                            cursorColor = EazColors.Orange,
                            focusedLabelColor = EazColors.Orange
                        )
                    )

                    Text(
                        text = "How did it fit?",
                        style = MaterialTheme.typography.labelMedium,
                        color = EazColors.TextSecondary
                    )
                    val fitKeys = listOf(
                        "too_small", "slightly_small", "perfect", "slightly_large", "too_large"
                    )
                    fitKeys.forEach { key ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = refFitRating == key,
                                onClick = { refFitRating = key },
                                colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                            )
                            Text(fitRatingLabel(key))
                        }
                    }

                    OutlinedTextField(
                        value = refNotes,
                        onValueChange = { refNotes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EazColors.Orange,
                            unfocusedBorderColor = EazColors.TopbarBorder,
                            cursorColor = EazColors.Orange,
                            focusedLabelColor = EazColors.Orange
                        )
                    )

                    refFormMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (refFormError) MaterialTheme.colorScheme.error else EazColors.Orange
                        )
                    }

                    TextButton(
                        onClick = { saveReferenceFit() },
                        enabled = !refSaving && ownerId.isNotBlank()
                    ) {
                        Text(if (refSaving) "Saving…" else "Save reference", color = EazColors.Orange)
                    }
                }
            }
        }
    }

    if (showAddBrandDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddBrandDialog = false
                newBrandName = ""
                addBrandError = null
            },
            title = { Text("Add brand") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newBrandName,
                        onValueChange = {
                            newBrandName = it
                            addBrandError = null
                        },
                        label = { Text("Brand name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EazColors.Orange,
                            unfocusedBorderColor = EazColors.TopbarBorder,
                            cursorColor = EazColors.Orange,
                            focusedLabelColor = EazColors.Orange
                        )
                    )
                    addBrandError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newBrandName.trim()
                        if (name.length < 2) {
                            addBrandError = "At least 2 characters"
                            return@TextButton
                        }
                        scope.launch {
                            try {
                                val resp = api.addBrand(name)
                                when {
                                    resp.optBoolean("ok", false) -> {
                                        val id = resp.optLong("brand_id", 0L)
                                        if (id > 0) {
                                            val opt = BrandOption(id, name)
                                            selectedBrand = opt
                                            allBrands = (allBrands + opt).distinctBy { it.id }.sortedBy { it.name }
                                            showAddBrandDialog = false
                                            newBrandName = ""
                                            addBrandError = null
                                        }
                                    }
                                    resp.optString("error") == "brand_exists" -> {
                                        val id = resp.optLong("brand_id", 0L)
                                        if (id > 0) {
                                            val opt = BrandOption(id, name)
                                            selectedBrand = opt
                                            allBrands = (allBrands + opt).distinctBy { it.id }.sortedBy { it.name }
                                            showAddBrandDialog = false
                                            newBrandName = ""
                                            addBrandError = null
                                        } else {
                                            addBrandError = "Brand already exists"
                                        }
                                    }
                                    else -> addBrandError = resp.optString("message", "Could not add brand")
                                }
                            } catch (_: Exception) {
                                addBrandError = "Request failed"
                            }
                        }
                    }
                ) {
                    Text("Add", color = EazColors.Orange)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddBrandDialog = false
                    newBrandName = ""
                    addBrandError = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
