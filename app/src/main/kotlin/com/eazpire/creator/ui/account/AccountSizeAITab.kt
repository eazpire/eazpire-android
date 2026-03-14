package com.eazpire.creator.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.util.DebugLog
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun formatNum(obj: JSONObject, key: String, decimals: Int = 0): String {
    val v = obj.optDouble(key, 0.0)
    if (v <= 0) return ""
    return if (decimals > 0) v.toString() else v.toInt().toString()
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
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Recommended Sizes",
                        style = MaterialTheme.typography.titleMedium,
                        color = EazColors.TextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Based on your profile and reference fits. Save measurements first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EazColors.TextSecondary
                    )
                    Text(
                        text = "No recommendations yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EazColors.TextSecondary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
            2 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Reference Fits",
                        style = MaterialTheme.typography.titleMedium,
                        color = EazColors.TextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Add pieces that fit well to improve recommendations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EazColors.TextSecondary
                    )
                    Text(
                        text = "No references yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EazColors.TextSecondary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        }
    }
}
