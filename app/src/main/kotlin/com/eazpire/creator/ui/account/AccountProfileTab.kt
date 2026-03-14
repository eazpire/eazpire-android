package com.eazpire.creator.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.util.DebugLog
import kotlinx.coroutines.launch

@Composable
fun AccountProfileTab(
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier
) {
    val jwt = remember { tokenStore.getJwt() }
    val ownerId = remember { tokenStore.getOwnerId() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var addressLine by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
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
            val resp = api.getCustomerProfile(ownerId)
            if (resp.optBoolean("ok", false)) {
                val profile = resp.optJSONObject("profile")
                if (profile != null) {
                    firstName = profile.optString("first_name", "")
                    lastName = profile.optString("last_name", "")
                    addressLine = profile.optString("address_line", "")
                    city = profile.optString("city", "")
                    country = profile.optString("country", "")
                    birthDate = profile.optString("birth_date", "")
                    gender = profile.optString("gender", "")
                }
            }
        } catch (e: Exception) {
            DebugLog.click("Profile load error: ${e.message}")
            statusMessage = "Failed to load profile"
            statusError = true
        } finally {
            isLoading = false
        }
    }

    fun saveProfile() {
        if (ownerId.isBlank()) return
        scope.launch {
            isSaving = true
            statusMessage = null
            statusError = false
            try {
                val resp = api.saveCustomerProfile(
                    ownerId,
                    mapOf(
                        "first_name" to firstName,
                        "last_name" to lastName,
                        "address_line" to addressLine,
                        "city" to city,
                        "country" to country,
                        "birth_date" to birthDate,
                        "gender" to gender
                    )
                )
                if (resp.optBoolean("ok", false)) {
                    statusMessage = "Saved"
                    statusError = false
                } else {
                    statusMessage = resp.optString("message", "Save failed")
                    statusError = true
                }
            } catch (e: Exception) {
                DebugLog.click("Profile save error: ${e.message}")
                statusMessage = "Save failed"
                statusError = true
            } finally {
                isSaving = false
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
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(24.dp),
                    color = EazColors.Orange
                )
            }
        } else {
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First name") },
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
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last name") },
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
                value = addressLine,
                onValueChange = { addressLine = it },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EazColors.Orange,
                    unfocusedBorderColor = EazColors.TopbarBorder,
                    cursorColor = EazColors.Orange,
                    focusedLabelColor = EazColors.Orange
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EazColors.Orange,
                        unfocusedBorderColor = EazColors.TopbarBorder,
                        cursorColor = EazColors.Orange,
                        focusedLabelColor = EazColors.Orange
                    )
                )
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("Country") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EazColors.Orange,
                        unfocusedBorderColor = EazColors.TopbarBorder,
                        cursorColor = EazColors.Orange,
                        focusedLabelColor = EazColors.Orange
                    )
                )
            }
            OutlinedTextField(
                value = birthDate,
                onValueChange = { birthDate = it },
                label = { Text("Birth date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EazColors.Orange,
                    unfocusedBorderColor = EazColors.TopbarBorder,
                    cursorColor = EazColors.Orange,
                    focusedLabelColor = EazColors.Orange
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gender",
                    style = MaterialTheme.typography.labelMedium,
                    color = EazColors.TextSecondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = gender == "female",
                        onClick = { gender = "female" },
                        colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                    )
                    Text("Female", modifier = Modifier.padding(end = 16.dp))
                    RadioButton(
                        selected = gender == "male",
                        onClick = { gender = "male" },
                        colors = RadioButtonDefaults.colors(selectedColor = EazColors.Orange)
                    )
                    Text("Male")
                }
            }
            Button(
                onClick = { saveProfile() },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSaving) "Saving..." else "Save")
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
