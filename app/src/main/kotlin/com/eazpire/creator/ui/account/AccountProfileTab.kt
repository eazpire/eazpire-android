package com.eazpire.creator.ui.account

import android.app.DatePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Male
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.account.wardrobe.WardrobeColors
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.ui.header.AVAILABLE_COUNTRIES
import com.eazpire.creator.ui.header.LocaleModal
import com.eazpire.creator.util.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Date format for display: DD.MM.YYYY (German/EU) or MM/DD/YYYY (US) */
private fun getDisplayDateFormat(locale: Locale): Pair<String, String> {
    val lang = locale.language
    return when {
        lang == "de" -> "dd.MM.yyyy" to "."
        lang == "en" && locale.country.uppercase() == "US" -> "MM/dd/yyyy" to "/"
        else -> "dd.MM.yyyy" to "."
    }
}

/** Convert YYYY-MM-DD (API) to display format */
private fun apiDateToDisplay(apiDate: String, locale: Locale): String {
    if (apiDate.isBlank()) return ""
    val (pattern, _) = getDisplayDateFormat(locale)
    return try {
        val from = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val to = SimpleDateFormat(pattern, locale)
        val date = from.parse(apiDate)
        if (date != null) to.format(date) else apiDate
    } catch (_: Exception) {
        apiDate
    }
}

/** Convert display format to YYYY-MM-DD (API) */
private fun displayDateToApi(display: String, locale: Locale): String {
    if (display.isBlank()) return ""
    val (pattern, _) = getDisplayDateFormat(locale)
    return try {
        val from = SimpleDateFormat(pattern, locale)
        val to = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = from.parse(display)
        if (date != null) to.format(date) else ""
    } catch (_: Exception) {
        display.replace(Regex("[^0-9]"), "").let { digits ->
            when (digits.length) {
                8 -> "${digits.takeLast(4)}-${digits.drop(2).take(2)}-${digits.take(2)}"
                else -> ""
            }
        }
    }
}

/** Format digits-only input to display format (DD.MM.YYYY or MM/DD/YYYY) */
private fun formatDateInput(digits: String, locale: Locale): String {
    val (_, sep) = getDisplayDateFormat(locale)
    val isUS = locale.language == "en" && locale.country.uppercase() == "US"
    val clean = digits.filter { it.isDigit() }.take(8)
    return when {
        clean.length <= 2 -> clean
        clean.length <= 4 -> {
            val p1 = clean.take(2)
            val p2 = clean.drop(2)
            if (isUS) "$p1$sep$p2" else "$p1$sep$p2"
        }
        clean.length <= 6 -> {
            val p1 = clean.take(2)
            val p2 = clean.drop(2).take(2)
            val p3 = clean.drop(4)
            if (isUS) "$p1$sep$p2$sep$p3" else "$p1$sep$p2$sep$p3"
        }
        else -> {
            val p1 = clean.take(2)
            val p2 = clean.drop(2).take(2)
            val p3 = clean.drop(4).take(4)
            "$p1$sep$p2$sep$p3"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountProfileTab(
    tokenStore: SecureTokenStore,
    onSaveActionReady: ((() -> Unit) -> Unit)? = null,
    onSavingStateChange: ((Boolean) -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    translationStore: TranslationStore? = null,
    /** Creator Settings modal dark sheet (matches web creator-settings-v2) */
    useDarkPanel: Boolean = false,
    /** Parent already scrolls (e.g. Creator Settings); avoids nested scroll / broken weight. */
    embedInParentScroll: Boolean = false
) {
    val context = LocalContext.current
    val dm = EazColors.CreatorModal
    fun tr(key: String, def: String) = translationStore?.t(key, def) ?: def
    val textPrimary = if (useDarkPanel) dm.TextPrimary else EazColors.TextPrimary
    val textSecondary = if (useDarkPanel) dm.TextSecondary else EazColors.TextSecondary
    val outlineFieldColors = if (useDarkPanel) {
        OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = EazColors.Orange,
            unfocusedBorderColor = dm.Border,
            cursorColor = EazColors.Orange,
            focusedLabelColor = EazColors.Orange,
            unfocusedLabelColor = textSecondary,
            focusedContainerColor = dm.Elevated,
            unfocusedContainerColor = dm.Elevated
        )
    } else {
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EazColors.Orange,
            unfocusedBorderColor = EazColors.TopbarBorder,
            cursorColor = EazColors.Orange,
            focusedLabelColor = EazColors.Orange
        )
    }
    val locale = Locale.getDefault()
    val (datePattern, _) = getDisplayDateFormat(locale)
    val jwt = remember { tokenStore.getJwt() }
    val ownerId = remember { tokenStore.getOwnerId() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }

    var email by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var addressLine by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var birthDateValue by remember { mutableStateOf(TextFieldValue("")) }
    var birthDateApi by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusError by remember { mutableStateOf(false) }
    var showCountryModal by remember { mutableStateOf(false) }
    var showSuccessOverlay by remember { mutableStateOf(false) }
    var addressSuggestions by remember { mutableStateOf<List<AddressSuggestion>>(emptyList()) }
    var showAddressSuggestions by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        statusMessage = null
        try {
            val profileResp = api.getCustomerProfile(ownerId)
            if (profileResp.optBoolean("ok", false)) {
                val profile = profileResp.optJSONObject("profile")
                if (profile != null) {
                    firstName = profile.optString("first_name", "")
                    lastName = profile.optString("last_name", "")
                    addressLine = profile.optString("address_line", "")
                    city = profile.optString("city", "")
                    postalCode = profile.optString("postal_code", "")
                    country = profile.optString("country", "")
                    birthDateApi = profile.optString("birth_date", "")
                    val disp = apiDateToDisplay(birthDateApi, locale)
                    birthDateValue = TextFieldValue(disp, TextRange(disp.length))
                    gender = profile.optString("gender", "")
                }
            }
            val emailResp = api.getCustomerEmail(ownerId, AuthConfig.SHOP_DOMAIN)
            if (emailResp.optBoolean("ok", false)) {
                val raw = when (val e = emailResp.opt("email")) {
                    null, org.json.JSONObject.NULL -> ""
                    else -> e.toString().trim()
                }
                email = raw.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: ""
            }
        } catch (e: Exception) {
            DebugLog.click("Profile load error: ${e.message}")
            statusMessage = tr("creator.settings.profile_load_failed", "Profile settings could not be loaded.")
            statusError = true
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(addressLine) {
        if (addressLine.length < 3) {
            addressSuggestions = emptyList()
            showAddressSuggestions = false
            return@LaunchedEffect
        }
        delay(400)
        try {
            val query = addressLine
            val list = withContext(Dispatchers.IO) {
                val client = OkHttpClient.Builder().build()
                val url = "https://photon.komoot.io/api/?q=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=6&lang=de"
                val req = Request.Builder().url(url).build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: "{}"
                val root = org.json.JSONObject(body)
                val features = root.optJSONArray("features") ?: org.json.JSONArray()
                (0 until features.length()).mapNotNull { i ->
                    val feat = features.optJSONObject(i) ?: return@mapNotNull null
                    val props = feat.optJSONObject("properties") ?: org.json.JSONObject()
                    val street = props.optString("street", "")
                    val housenumber = props.optString("housenumber", "")
                    val addrLine = listOf(street, housenumber).filter { it.isNotBlank() }.joinToString(" ")
                        .ifBlank { props.optString("name", "") }
                    val city = props.optString("city", "").ifBlank { props.optString("locality", "") }.ifBlank { props.optString("district", "") }
                    val postalCode = props.optString("postcode", "")
                    val countryCode = props.optString("countrycode", "").uppercase()
                    val display = listOfNotNull(
                        addrLine.ifBlank { null },
                        postalCode.takeIf { it.isNotBlank() },
                        city.takeIf { it.isNotBlank() },
                        props.optString("country", "").takeIf { it.isNotBlank() }
                    ).joinToString(", ").ifBlank { props.optString("name", query) }
                    AddressSuggestion(
                        display = display,
                        addressLine = addrLine,
                        city = city,
                        postalCode = postalCode,
                        countryCode = countryCode
                    )
                }
            }
            if (query == addressLine) {
                addressSuggestions = list
                showAddressSuggestions = list.isNotEmpty()
            }
        } catch (e: Exception) {
            addressSuggestions = emptyList()
            showAddressSuggestions = false
        }
    }

    fun saveProfile() {
        if (ownerId.isBlank()) return
        scope.launch {
            isSaving = true
            statusMessage = null
            statusError = false
            try {
                val bdApi = if (birthDateValue.text.isNotBlank()) displayDateToApi(birthDateValue.text, locale) else birthDateApi
                val resp = api.saveCustomerProfile(
                    ownerId,
                    mapOf(
                        "first_name" to firstName,
                        "last_name" to lastName,
                        "address_line" to addressLine,
                        "city" to city,
                        "postal_code" to postalCode,
                        "country" to country,
                        "birth_date" to bdApi,
                        "gender" to gender
                    )
                )
                if (resp.optBoolean("ok", false)) {
                    statusMessage = tr("creator.settings.profile_saved", "Profile settings saved.")
                    statusError = false
                    showSuccessOverlay = true
                } else {
                    statusMessage = resp.optString("message", tr("creator.settings.profile_save_failed", "Profile settings could not be saved."))
                    statusError = true
                }
            } catch (e: Exception) {
                DebugLog.click("Profile save error: ${e.message}")
                statusMessage = tr("creator.settings.profile_save_failed", "Profile settings could not be saved.")
                statusError = true
            } finally {
                isSaving = false
            }
        }
    }

    LaunchedEffect(isSaving) {
        onSavingStateChange?.invoke(isSaving)
    }
    LaunchedEffect(Unit) {
        onSaveActionReady?.invoke { saveProfile() }
    }

    LaunchedEffect(showSuccessOverlay) {
        if (showSuccessOverlay) {
            delay(2000)
            showSuccessOverlay = false
            statusMessage = null
        }
    }

    val genderCardSurface = if (useDarkPanel) dm.Elevated else MaterialTheme.colorScheme.surface
    val countryBorder = if (useDarkPanel) dm.Border else EazColors.TopbarBorder
    val suggestionCardBg = if (useDarkPanel) dm.Elevated else MaterialTheme.colorScheme.surface
    val emailHeaderBg = if (useDarkPanel) dm.Elevated else EazColors.TopbarBorder.copy(alpha = 0.08f)
    val rootModifier = if (embedInParentScroll) modifier.fillMaxWidth() else modifier.fillMaxSize()

    Box(modifier = rootModifier) {
        Column(modifier = if (embedInParentScroll) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(emailHeaderBg, RoundedCornerShape(if (useDarkPanel) 12.dp else 0.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (embedInParentScroll) {
                    Text(
                        text = tr("creator.settings.profile_subtitle", "Manage your personal Creator settings"),
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Text(
                    text = tr("creator.settings.profile_email_label", "Email"),
                    style = MaterialTheme.typography.labelMedium,
                    color = textSecondary
                )
                Text(
                    text = email.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = textPrimary
                )
                Text(
                    text = tr("creator.settings.profile_email_readonly", "Your account email (from Shopify)"),
                    style = MaterialTheme.typography.bodySmall,
                    color = textSecondary
                )
                if (onLogout != null) {
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, if (useDarkPanel) dm.Border else EazColors.TopbarBorder),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = textSecondary
                        )
                    ) {
                        Icon(
                            Icons.Default.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = textSecondary
                        )
                        Text(
                            text = tr("creator.common.logout", "Log out"),
                            style = MaterialTheme.typography.labelLarge,
                            color = textSecondary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            Column(
                modifier = if (embedInParentScroll) {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.dp, vertical = 4.dp)
                } else {
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                },
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            label = { Text(tr("creator.settings.profile_first_name_label", "First name")) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = outlineFieldColors
                        )
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it },
                            label = { Text(tr("creator.settings.profile_last_name_label", "Last name")) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = outlineFieldColors
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = addressLine,
                            onValueChange = { addressLine = it },
                            label = { Text(tr("creator.settings.profile_address_label", "Address")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = outlineFieldColors
                        )
                        if (showAddressSuggestions && addressSuggestions.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 52.dp),
                                colors = CardDefaults.cardColors(containerColor = suggestionCardBg),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(4.dp)) {
                                    addressSuggestions.take(5).forEach { s ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    addressLine = s.addressLine.ifBlank { s.display }
                                                    city = s.city
                                                    postalCode = s.postalCode
                                                    if (s.countryCode.isNotBlank()) {
                                                        val match = AVAILABLE_COUNTRIES.find { it.code.equals(s.countryCode, ignoreCase = true) }
                                                        if (match != null) country = match.code else country = s.countryCode
                                                    }
                                                    showAddressSuggestions = false
                                                }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = s.display,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = textPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        text = tr("creator.settings.profile_address_hint", "Start typing to get address suggestions"),
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text(tr("creator.settings.profile_city_label", "City")) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = outlineFieldColors
                        )
                        OutlinedTextField(
                            value = postalCode,
                            onValueChange = { postalCode = it },
                            label = { Text(tr("creator.settings.profile_postal_code_label", "Postal code")) },
                            modifier = Modifier.weight(0.65f),
                            singleLine = true,
                            colors = outlineFieldColors
                        )
                    }
                    Card(
                        onClick = { showCountryModal = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, countryBorder),
                        colors = CardDefaults.cardColors(containerColor = genderCardSurface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 56.dp)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr("creator.settings.profile_country_label", "Country"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textSecondary
                                )
                                Text(
                                    text = country.let { cc ->
                                        AVAILABLE_COUNTRIES.find { it.code.equals(cc, ignoreCase = true) }?.label
                                            ?: cc.ifBlank { tr("creator.settings.profile_country_select", "Select country") }
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (country.isBlank()) textSecondary else textPrimary
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.KeyboardArrowDown,
                                contentDescription = null,
                                tint = textSecondary
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = birthDateValue,
                            onValueChange = { new ->
                                val digits = new.text.filter { it.isDigit() }.take(8)
                                val formatted = formatDateInput(digits, locale)
                                birthDateValue = TextFieldValue(formatted, TextRange(formatted.length))
                                if (formatted.length >= 10) {
                                    birthDateApi = displayDateToApi(formatted, locale)
                                }
                            },
                            label = {
                                Text(
                                    "${tr("creator.settings.profile_birth_date_label", "Birth date")} ($datePattern)"
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = outlineFieldColors
                        )
                        IconButton(
                            onClick = {
                                val cal = Calendar.getInstance()
                                if (birthDateApi.isNotBlank()) {
                                    try {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                        sdf.parse(birthDateApi)?.let { cal.time = it }
                                    } catch (_: Exception) {}
                                }
                                DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        cal.set(y, m, d)
                                        birthDateApi = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                                        val disp = apiDateToDisplay(birthDateApi, locale)
                                        birthDateValue = TextFieldValue(disp, TextRange(disp.length))
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = tr("creator.settings.profile_birth_date_label", "Birth date"),
                                tint = EazColors.Orange
                            )
                        }
                    }
                    Text(
                        text = tr(
                            "creator.settings.profile_birth_date_hint",
                            "Share your birth date to unlock a birthday surprise."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary
                    )
                    Text(
                        text = tr("creator.settings.profile_gender_label", "Gender"),
                        style = MaterialTheme.typography.labelMedium,
                        color = textSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            onClick = { gender = "female" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (gender == "female") WardrobeColors.FemalePink.copy(alpha = 0.2f)
                                else genderCardSurface
                            ),
                            border = if (gender == "female") BorderStroke(2.dp, WardrobeColors.FemalePink) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Female,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = WardrobeColors.FemalePink
                                )
                                Text(
                                    text = tr("creator.settings.profile_gender_female", "Female"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = textPrimary
                                )
                            }
                        }
                        Card(
                            onClick = { gender = "male" },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (gender == "male") WardrobeColors.MaleBlue.copy(alpha = 0.2f)
                                else genderCardSurface
                            ),
                            border = if (gender == "male") BorderStroke(2.dp, WardrobeColors.MaleBlue) else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Male,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = WardrobeColors.MaleBlue
                                )
                                Text(
                                    text = tr("creator.settings.profile_gender_male", "Male"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = textPrimary
                                )
                            }
                        }
                    }
                    statusMessage?.let { msg ->
                        if (!showSuccessOverlay) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (statusError) MaterialTheme.colorScheme.error else EazColors.Orange
                            )
                        }
                    }
                }
            }
        }

        if (embedInParentScroll) {
            AnimatedVisibility(
                visible = showSuccessOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(dm.Elevated, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = EazColors.Orange
                    )
                    Text(
                        text = tr("creator.settings.profile_saved", "Profile settings saved."),
                        style = MaterialTheme.typography.titleSmall,
                        color = textPrimary
                    )
                }
            }
        } else {
            AnimatedVisibility(
                visible = showSuccessOverlay,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.95f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = EazColors.Orange
                        )
                        Text(
                            text = tr("creator.settings.profile_saved", "Profile settings saved."),
                            style = MaterialTheme.typography.titleLarge,
                            color = EazColors.TextPrimary
                        )
                    }
                }
            }
        }
    }

    if (showCountryModal) {
        LocaleModal(
            title = tr("creator.settings.profile_country_label", "Country"),
            items = AVAILABLE_COUNTRIES,
            selectedCode = country.ifBlank { "DE" },
            onDismiss = { showCountryModal = false },
            onSelect = { code ->
                country = code
                showCountryModal = false
            },
            searchPlaceholder = tr("creator.common.search", "Search") + "…",
            sheetContainerColor = if (useDarkPanel) dm.SheetBg else Color.White,
            contentColorPrimary = textPrimary,
            contentColorSecondary = textSecondary,
            fieldContainerColor = if (useDarkPanel) dm.Elevated else Color.White,
            outlineUnfocused = if (useDarkPanel) dm.Border else EazColors.TopbarBorder
        )
    }
}

private data class AddressSuggestion(
    val display: String,
    val addressLine: String,
    val city: String,
    val postalCode: String,
    val countryCode: String
)
