package com.eazpire.creator.ui.creator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.account.AccountCommunityTab
import com.eazpire.creator.ui.account.AccountProfileTab
import com.eazpire.creator.ui.account.NotificationScope
import com.eazpire.creator.ui.account.NotificationSettingsContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Tab indices: 0 Profile, 1 Notifications, 2 Creator Codes, 3 Community, 4 Creator Names, 5 Level, 6 EAZ, 7 Payout, 8 Interests, 9 NFT */
@Composable
fun CreatorSettingsTabContent(
    currentTab: Int,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier
) {
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    val jwt = remember { tokenStore.getJwt() }
    val api = remember(jwt) { CreatorApi(jwt = jwt) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        when (currentTab) {
            0 -> CreatorSettingsProfileContent(tokenStore, translationStore)
            1 -> NotificationSettingsContent(
                scope = NotificationScope.Creator,
                tokenStore = tokenStore,
                modifier = Modifier.fillMaxWidth()
            )
            2 -> CreatorSettingsCreatorCodesContent(ownerId, api, translationStore)
            3 -> CreatorSettingsCommunityContent(tokenStore, translationStore)
            4 -> CreatorSettingsNamesContent(ownerId, api, translationStore)
            5 -> CreatorSettingsLevelContent(ownerId, api, translationStore)
            6 -> CreatorSettingsEazContent(translationStore)
            7 -> CreatorSettingsPayoutContent(ownerId, api, translationStore)
            8 -> CreatorSettingsInterestsContent(ownerId, api, translationStore)
            9 -> CreatorSettingsNftContent(translationStore)
        }
    }
}

@Composable
private fun CreatorSettingsProfileContent(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore
) {
    var saveProfile by remember { mutableStateOf<(() -> Unit)?>(null) }
    Column(modifier = Modifier.fillMaxWidth()) {
        AccountProfileTab(
            tokenStore = tokenStore,
            onSaveActionReady = { saveProfile = it },
            onSavingStateChange = null,
            onLogout = null,
            modifier = Modifier.fillMaxWidth(),
            translationStore = translationStore,
            useDarkPanel = true,
            embedInParentScroll = true
        )
        saveProfile?.let { save ->
            Button(
                onClick = { save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
            ) {
                Text(
                    translationStore.t("creator.common.save", "Save"),
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun CreatorSettingsCreatorCodesContent(
    ownerId: String,
    api: CreatorApi,
    translationStore: TranslationStore
) {
    var isLoading by remember { mutableStateOf(true) }
    var isCreator by remember { mutableStateOf(false) }
    var canGenerate by remember { mutableStateOf(false) }
    var activeCode by remember { mutableStateOf<String?>(null) }
    var refUrl by remember { mutableStateOf<String?>(null) }
    var redeemCode by remember { mutableStateOf("") }
    var redeemMessage by remember { mutableStateOf<String?>(null) }
    var redeemError by remember { mutableStateOf(false) }
    var statsGenerated by remember { mutableStateOf(0) }
    var statsRedeemed by remember { mutableStateOf(0) }
    var statsCommunity by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            val data = withContext(Dispatchers.IO) { api.getCreatorCode(ownerId) }
            isCreator = data.optBoolean("is_creator", false)
            canGenerate = data.optBoolean("can_generate", false)
            data.optJSONObject("active_code")?.let { ac ->
                activeCode = ac.optString("code", null).takeIf { it.isNotBlank() }
            }
            refUrl = data.optString("ref_url", null).takeIf { it.isNotBlank() }
            if (isCreator) {
                val stats = withContext(Dispatchers.IO) { api.getCreatorCodeStats(ownerId) }
                if (stats.optBoolean("ok", false)) {
                    val s = stats.optJSONObject("stats") ?: stats
                    statsGenerated = s.optInt("total_generated", 0)
                    statsRedeemed = s.optInt("total_redeemed", 0)
                    statsCommunity = s.optInt("community_size", 0)
                }
            }
        } catch (_: Exception) {}
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
        return
    }

    if (!isCreator) {
        Text(
            text = translationStore.t("creator.settings.creator_codes_redeem_title", "Redeem Creator Code"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = translationStore.t("creator.settings.creator_codes_redeem_subtitle", "Enter a code to become a Creator"),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = redeemCode,
                onValueChange = { redeemCode = it.uppercase().take(10) },
                placeholder = {
                    Text(
                        translationStore.t("creator.settings.creator_codes_redeem_placeholder", "Code"),
                        color = Color.White.copy(alpha = 0.5f)
                    )
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EazColors.Orange,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    cursorColor = EazColors.Orange,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Button(
                onClick = {
                    val code = redeemCode.trim()
                    if (code.isBlank()) return@Button
                    scope.launch {
                        try {
                            val resp = withContext(Dispatchers.IO) { api.redeemCreatorCode(ownerId, code) }
                            redeemMessage = resp.optString("message", if (resp.optBoolean("ok", false)) "Welcome!" else resp.optString("error", "Error"))
                            redeemError = !resp.optBoolean("ok", false)
                        } catch (_: Exception) {
                            redeemMessage = "Connection error"
                            redeemError = true
                        }
                    }
                },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
            ) {
                Text(
                    translationStore.t("creator.settings.creator_codes_redeem_btn", "Redeem"),
                    color = Color.Black
                )
            }
        }
        redeemMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (redeemError) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        return
    }

    if (canGenerate && activeCode == null) {
        Button(
            onClick = {
                scope.launch {
                    try {
                        val resp = withContext(Dispatchers.IO) { api.generateCreatorCode(ownerId) }
                        if (resp.optBoolean("ok", false)) {
                            activeCode = resp.optString("code", null).takeIf { it.isNotBlank() }
                            refUrl = resp.optString("ref_url", null).takeIf { it.isNotBlank() }
                        }
                    } catch (_: Exception) {}
                }
            },
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = Color.Black)
            Text(
                translationStore.t("creator.settings.creator_codes_generate_btn", "Generate Code"),
                color = Color.Black,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }

    activeCode?.let { code ->
        Column(modifier = Modifier.padding(top = 16.dp)) {
            Text(
                text = translationStore.t("creator.settings.creator_codes_generate_subtitle", "Your Code"),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = code,
                style = MaterialTheme.typography.headlineSmall,
                color = EazColors.Orange,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("code", code))
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp), tint = EazColors.Orange)
                    Text(
                        translationStore.t("creator.settings.creator_codes_copy_btn", "Copy"),
                        color = EazColors.Orange,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                refUrl?.let { url ->
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("url", url))
                        }
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp), tint = EazColors.Orange)
                        Text(
                            translationStore.t("creator.settings.creator_codes_share_link_title", "Share Link"),
                            color = EazColors.Orange,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }

    if (isCreator && (statsGenerated > 0 || statsRedeemed > 0 || statsCommunity > 0)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard(
                translationStore.t("creator.settings.creator_codes_stats_generated", "Generated"),
                statsGenerated.toString()
            )
            StatCard(
                translationStore.t("creator.settings.creator_codes_stats_redeemed", "Redeemed"),
                statsRedeemed.toString()
            )
            StatCard(
                translationStore.t("creator.settings.creator_codes_stats_community", "Community"),
                statsCommunity.toString()
            )
        }
    }
}

@Composable
private fun RowScope.StatCard(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth(1f / 3f)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = EazColors.Orange)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun CreatorSettingsCommunityContent(tokenStore: SecureTokenStore, translationStore: TranslationStore) {
    AccountCommunityTab(
        tokenStore = tokenStore,
        modifier = Modifier.fillMaxWidth(),
        scrollable = false,
        darkTheme = true,
        translationStore = translationStore
    )
}

@Composable
private fun CreatorSettingsNamesContent(
    ownerId: String,
    api: CreatorApi,
    translationStore: TranslationStore
) {
    var newName by remember { mutableStateOf("") }
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusError by remember { mutableStateOf(false) }
    var detailCreator by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            val settings = withContext(Dispatchers.IO) { api.getSettings(ownerId) }
            val settingsObj = settings.optJSONObject("settings") ?: settings
            val arr = settingsObj.optJSONArray("creator_names")
            var raw = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                arr?.optString(i, null)?.takeIf { it.isNotBlank() }
            }
            val primary = settingsObj.optString("creator_name").takeIf { it.isNotBlank() }
            if (primary != null && raw.none { it.equals(primary, ignoreCase = true) }) {
                raw = listOf(primary) + raw
            }
            val seen = mutableSetOf<String>()
            names = raw.filter { seen.add(it.lowercase()) }.take(5)
        } catch (_: Exception) {}
        isLoading = false
    }

    Text(
        text = translationStore.t("creator.settings_names.new_name", "New Creator Name"),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.9f)
    )
    OutlinedTextField(
        value = newName,
        onValueChange = { newName = it },
        placeholder = {
            Text(
                translationStore.t("creator.settings_names.placeholder", "e.g. mybrand"),
                color = Color.White.copy(alpha = 0.5f)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EazColors.Orange,
            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
            cursorColor = EazColors.Orange,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
    Button(
        onClick = {
            val name = newName.trim()
            if (name.isBlank()) return@Button
            if (names.size >= 5) return@Button
            scope.launch {
                try {
                    val resp = withContext(Dispatchers.IO) { api.addCreatorName(ownerId, name) }
                    if (resp.optBoolean("ok", false)) {
                        names = names + name
                        newName = ""
                        statusMessage = translationStore.t("creator.settings_names.added_ok", "Creator name added.")
                        statusError = false
                    } else {
                        statusMessage = resp.optString("message", resp.optString("error", "Failed"))
                        statusError = true
                    }
                } catch (_: Exception) {
                    statusMessage = translationStore.t("creator.settings_names.add_error", "Could not add name.")
                    statusError = true
                }
            }
        },
        modifier = Modifier.padding(top = 12.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
    ) {
        Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = Color.Black)
        Text(
            translationStore.t("creator.common.add", "Add"),
            color = Color.Black,
            modifier = Modifier.padding(start = 8.dp)
        )
    }

    Text(
        text = "${translationStore.t("creator.settings_names.your_names", "Your names")} (${names.size}/5)",
        style = MaterialTheme.typography.titleSmall,
        color = Color.White,
        modifier = Modifier.padding(top = 24.dp)
    )
    if (isLoading) {
        CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(16.dp))
    } else {
        names.forEach { name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .clickable { detailCreator = name }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = name, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { detailCreator = name },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = translationStore.t(
                            "creator.settings_names.edit_profile_aria",
                            "Edit creator profile"
                        ),
                        tint = EazColors.Orange,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
    detailCreator?.let { cn ->
        CreatorDetailModal(
            creatorName = cn,
            ownerId = ownerId,
            api = api,
            translationStore = translationStore,
            onDismiss = { detailCreator = null }
        )
    }
    statusMessage?.let { msg ->
        Text(
            text = msg,
            style = MaterialTheme.typography.bodySmall,
            color = if (statusError) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun CreatorSettingsLevelContent(
    ownerId: String,
    api: CreatorApi,
    translationStore: TranslationStore
) {
    var level by remember { mutableStateOf(0) }
    var xpCurrent by remember { mutableStateOf(0) }
    var xpNext by remember { mutableStateOf(0) }
    var features by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            val resp = withContext(Dispatchers.IO) { api.getLevel(ownerId) }
            if (resp.optBoolean("ok", false)) {
                val data = resp.optJSONObject("level") ?: resp
                level = data.optInt("level", 0)
                xpCurrent = data.optInt("xp_current", 0)
                xpNext = data.optInt("xp_next", 100)
                val arr = data.optJSONArray("features")
                features = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                    arr?.optString(i, null)?.takeIf { it.isNotBlank() }
                }
            }
        } catch (_: Exception) {}
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(EazColors.Orange.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Star, null, Modifier.size(32.dp), tint = EazColors.Orange)
        Column {
            Text(
                text = translationStore.t("creator.level_panel.level_label", "Level"),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            Text(
                text = level.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = EazColors.Orange
            )
        }
    }

    Text(
        text = translationStore.t("creator.level_panel.xp_label", "XP"),
        style = MaterialTheme.typography.titleSmall,
        color = Color.White,
        modifier = Modifier.padding(top = 20.dp)
    )
    Text(
        text = "$xpCurrent / $xpNext ${translationStore.t("creator.level_panel.xp_label", "XP")}",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.8f),
        modifier = Modifier.padding(top = 4.dp)
    )
    val progress = if (xpNext > 0) (xpCurrent.toFloat() / xpNext).coerceIn(0f, 1f) else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .background(EazColors.Orange, RoundedCornerShape(4.dp))
                .padding(vertical = 6.dp)
        )
    }

    if (features.isNotEmpty()) {
        Text(
            text = translationStore.t("creator.level_panel.current_features", "Current features"),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier.padding(top = 20.dp)
        )
        features.forEach { f ->
            Text(
                text = "• $f",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CreatorSettingsEazContent(translationStore: TranslationStore) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Star, null, Modifier.size(48.dp), tint = EazColors.Orange.copy(alpha = 0.6f))
        Text(
            text = translationStore.t("creator.common.coming_soon", "Coming soon"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = translationStore.t("creator.settings.eaz_coming_soon", "EAZ features are coming soon"),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun CreatorSettingsPayoutContent(
    ownerId: String,
    api: CreatorApi,
    translationStore: TranslationStore
) {
    var balanceText by remember { mutableStateOf("0.00") }
    var currencySymbol by remember { mutableStateOf("€") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            val payout = withContext(Dispatchers.IO) { api.getCreatorPayoutOverview(ownerId, 90) }
            if (payout.optBoolean("ok", false)) {
                balanceText = "%.2f".format(payout.optDouble("availableAmount", 0.0))
                currencySymbol = when (payout.optString("currency", "EUR").uppercase()) {
                    "USD" -> "$"
                    "GBP" -> "£"
                    else -> "€"
                }
            }
        } catch (_: Exception) {}
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
        return
    }

    Text(
        text = translationStore.t("creator.settings.payout_title", "Payout"),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White
    )
    Text(
        text = translationStore.t("creator.settings.payout_subtitle", "Manage your payout settings"),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 4.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .background(EazColors.Orange.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = translationStore.t("creator.sales_modal.available", "Available"),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
        Text(
            text = "$balanceText $currencySymbol",
            style = MaterialTheme.typography.headlineSmall,
            color = EazColors.Orange
        )
    }
    Text(
        text = translationStore.t("creator.settings.payout_account_title", "Payout account"),
        style = MaterialTheme.typography.titleSmall,
        color = Color.White,
        modifier = Modifier.padding(top = 24.dp)
    )
    Text(
        text = translationStore.t("creator.settings.payout_account_subtitle", "Add Wise or PayPal for payouts"),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun CreatorSettingsInterestsContent(
    ownerId: String,
    api: CreatorApi,
    translationStore: TranslationStore
) {
    var isLoading by remember { mutableStateOf(true) }
    var categories by remember { mutableStateOf<List<InterestCategory>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            val listResp = withContext(Dispatchers.IO) { api.listInterests() }
            val userResp = withContext(Dispatchers.IO) { api.getUserInterests(ownerId) }
            val cats = mutableListOf<InterestCategory>()
            val arr = listResp.optJSONArray("categories")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val cat = arr.optJSONObject(i) ?: continue
                    val key = cat.optString("key", "")
                    val label = key.replaceFirstChar { it.uppercase() }
                    val items = cat.optJSONArray("interests") ?: org.json.JSONArray()
                    val interests = (0 until items.length()).mapNotNull { j ->
                        items.optJSONObject(j)?.let { obj ->
                            InterestItem(
                                id = obj.optLong("id", 0L),
                                name = obj.optString("name", obj.optString("name_en", ""))
                            )
                        }
                    }
                    if (interests.isNotEmpty()) cats.add(InterestCategory(key, label, interests))
                }
            }
            categories = cats
            val userArr = userResp.optJSONArray("interests")
            selectedIds = (0 until (userArr?.length() ?: 0)).mapNotNull { i ->
                userArr?.optJSONObject(i)?.optLong("id", 0L)?.takeIf { it > 0 }
            }.toSet()
        } catch (_: Exception) {}
        isLoading = false
    }

    Text(
        text = translationStore.t("creator.interests.title", "Interests"),
        style = MaterialTheme.typography.titleMedium,
        color = Color.White
    )
    Text(
        text = translationStore.t("creator.interests.subtitle", "Select up to 10 interests for personalized recommendations"),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 4.dp)
    )
    Text(
        text = "${selectedIds.size} ${translationStore.t("creator.interests.of_max", "of")} 10",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 12.dp)
    )

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
        return
    }

    val filtered = if (searchQuery.isBlank()) categories
    else categories.map { cat ->
        cat.copy(interests = cat.interests.filter { it.name.contains(searchQuery, ignoreCase = true) })
    }.filter { it.interests.isNotEmpty() }

    filtered.forEach { cat ->
        Text(
            text = cat.label,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cat.interests.take(12).forEach { item ->
                val selected = item.id in selectedIds
                OutlinedButton(
                    onClick = {
                        selectedIds = if (selected) {
                            selectedIds - item.id
                        } else {
                            if (selectedIds.size < 10) selectedIds + item.id else selectedIds
                        }
                    },
                    enabled = selected || selectedIds.size < 10,
                    colors = if (selected) {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = EazColors.Orange.copy(alpha = 0.3f)
                        )
                    } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(
                        item.name,
                        color = if (selected) EazColors.Orange else Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }

    Button(
        onClick = {
            scope.launch {
                isSaving = true
                try {
                    val resp = withContext(Dispatchers.IO) {
                        api.setUserInterests(ownerId, selectedIds.toList())
                    }
                    isSaving = false
                } catch (_: Exception) {
                    isSaving = false
                }
            }
        },
        modifier = Modifier.padding(top = 24.dp),
        enabled = !isSaving,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.Black,
                strokeWidth = 2.dp
            )
        } else {
            Text(
                translationStore.t("creator.interests.save", "Save"),
                color = Color.Black
            )
        }
    }
}

private data class InterestCategory(val key: String, val label: String, val interests: List<InterestItem>)
private data class InterestItem(val id: Long, val name: String)

@Composable
private fun CreatorSettingsNftContent(translationStore: TranslationStore) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = translationStore.t("creator.common.coming_soon", "Coming soon"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = translationStore.t("creator.settings.nft_subtitle", "NFT inventory coming soon"),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
