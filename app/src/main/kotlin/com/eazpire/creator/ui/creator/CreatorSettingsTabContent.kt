package com.eazpire.creator.ui.creator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.billing.EazBalanceRefreshBus
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.creatorcodes.CreatorCodeAvailableHintStore
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

/** Tab indices: 0 Profile … 8 NFT, 9 Creator Wear (Level moved to Creator Journey modal) */
@Composable
fun CreatorSettingsTabContent(
    currentTab: Int,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onLogout: (() -> Unit)? = null,
    pendingWearPairToken: String? = null,
    initialRedeemCode: String? = null,
    onInitialRedeemCodeConsumed: () -> Unit = {},
    onRequestSettingsTab: (Int) -> Unit = {},
    initialEazSub: String? = null,
    modifier: Modifier = Modifier
) {
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    val jwt = remember { tokenStore.getJwt() }
    val api = remember(jwt) { CreatorApi(jwt = jwt) }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
        when (currentTab) {
            0 -> CreatorSettingsProfileContent(tokenStore, translationStore, onLogout = onLogout)
            1 -> NotificationSettingsContent(
                scope = NotificationScope.Creator,
                tokenStore = tokenStore,
                embedInParentScroll = true,
                modifier = Modifier.fillMaxWidth()
            )
            2 -> CreatorSettingsCreatorCodesContent(
                ownerId = ownerId,
                api = api,
                translationStore = translationStore,
                initialRedeemCode = initialRedeemCode,
                onInitialRedeemCodeConsumed = onInitialRedeemCodeConsumed,
                onRequestSettingsTab = onRequestSettingsTab,
            )
            3 -> CreatorSettingsCommunityContent(tokenStore, translationStore)
            4 -> CreatorSettingsNamesContent(ownerId, api, translationStore)
            5 -> CreatorSettingsEazPanel(
                tokenStore = tokenStore,
                translationStore = translationStore,
                onRequestSettingsTab = onRequestSettingsTab,
                initialEazSub = initialEazSub,
            )
            6 -> CreatorSettingsPayoutContent(ownerId, api, translationStore)
            7 -> CreatorSettingsInterestsContent(ownerId, api, translationStore)
            8 -> CreatorSettingsNftContent(translationStore)
            9 -> CreatorSettingsWearContent(
                tokenStore = tokenStore,
                translationStore = translationStore,
                pendingPairToken = pendingWearPairToken,
                onRequestSettingsTab = onRequestSettingsTab,
            )
        }
        }
    }
}

@Composable
private fun CreatorSettingsProfileContent(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onLogout: (() -> Unit)? = null,
) {
    var saveProfile by remember { mutableStateOf<(() -> Unit)?>(null) }
    var profileDirty by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        AccountProfileTab(
            tokenStore = tokenStore,
            onSaveActionReady = { saveProfile = it },
            onSavingStateChange = null,
            onDirtyChange = { profileDirty = it },
            onLogout = onLogout,
            modifier = Modifier.fillMaxWidth(),
            translationStore = translationStore,
            useDarkPanel = true,
            embedInParentScroll = true
        )
        saveProfile?.let { save ->
            Button(
                onClick = { save() },
                enabled = profileDirty,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = EazColors.Orange,
                    disabledContainerColor = EazColors.Orange.copy(alpha = 0.35f),
                )
            ) {
                Text(
                    translationStore.t("creator.settings.profile_save_button", "Save profile settings"),
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
    translationStore: TranslationStore,
    initialRedeemCode: String? = null,
    onInitialRedeemCodeConsumed: () -> Unit = {},
    onRequestSettingsTab: (Int) -> Unit = {},
) {
    var isLoading by remember { mutableStateOf(true) }
    var isCreator by remember { mutableStateOf(false) }
    var canGenerate by remember { mutableStateOf(false) }
    var activeCode by remember { mutableStateOf<String?>(null) }
    var activeCodeId by remember { mutableStateOf<Long?>(null) }
    var activeCodeCanShare by remember { mutableStateOf(false) }
    var activeCodeIsPermanentGift by remember { mutableStateOf(false) }
    var refUrl by remember { mutableStateOf<String?>(null) }
    var pendingSaleId by remember { mutableStateOf<Long?>(null) }
    var pendingPurchaseId by remember { mutableStateOf<Long?>(null) }
    var purchaseNeedsQr by remember { mutableStateOf(false) }
    var purchaseQrVerified by remember { mutableStateOf(false) }
    var redeemCode by remember { mutableStateOf("") }
    var redeemMessage by remember { mutableStateOf<String?>(null) }
    var redeemError by remember { mutableStateOf(false) }
    var statsGenerated by remember { mutableStateOf(0) }
    var statsRedeemed by remember { mutableStateOf(0) }
    var statsCommunity by remember { mutableStateOf(0) }
    var statsCommunityActive by remember { mutableStateOf(0) }
    var statsPendingDesigns by remember { mutableStateOf(0) }
    var statsCommunityRevenueCents by remember { mutableStateOf(0) }
    var recruiterOptIn by remember { mutableStateOf(false) }
    var memberRelationships by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var communityMembers by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var pendingCommunityDesigns by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var redeemedHistory by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showUserPickerDialog by remember { mutableStateOf(false) }
    var showPoolDialog by remember { mutableStateOf(false) }
    var showRecruiterOptInConfirm by remember { mutableStateOf(false) }
    var showMemberOptInConfirmFor by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun reload() {
        scope.launch {
            if (ownerId.isBlank()) {
                isLoading = false
                return@launch
            }
            isLoading = true
            try {
                val data = withContext(Dispatchers.IO) { api.getCreatorCode(ownerId) }
                CreatorCodeAvailableHintStore.refreshFromResponse(data)
                isCreator = data.optBoolean("is_creator", false)
                canGenerate = data.optBoolean("can_generate", false)
                data.optJSONObject("active_code")?.let { ac ->
                    activeCode = ac.optString("code", null).takeIf { it.isNotBlank() }
                    activeCodeId = ac.optLong("id").takeIf { it > 0L }
                    activeCodeCanShare = ac.optBoolean("can_share", true)
                    activeCodeIsPermanentGift = ac.optBoolean("is_permanent_gift", false)
                } ?: run {
                    activeCode = null
                    activeCodeId = null
                    activeCodeCanShare = false
                    activeCodeIsPermanentGift = false
                }
                refUrl = data.optString("ref_url", null).takeIf { it.isNotBlank() }
                pendingSaleId = data.optJSONObject("pending_sale")?.optLong("id")?.takeIf { it > 0L }
                data.optJSONObject("pending_purchase")?.let { pp ->
                    pendingPurchaseId = pp.optLong("id").takeIf { it > 0L }
                    purchaseNeedsQr = pp.optBoolean("requires_qr", false)
                    purchaseQrVerified = pp.optBoolean("qr_verified", false)
                } ?: run {
                    pendingPurchaseId = null
                    purchaseNeedsQr = false
                    purchaseQrVerified = false
                }
                if (isCreator) {
                    val stats = withContext(Dispatchers.IO) { api.getCreatorCodeStats(ownerId) }
                    if (stats.optBoolean("ok", false)) {
                        val s = stats.optJSONObject("stats") ?: stats
                        statsGenerated = s.optInt("total_generated", 0)
                        statsRedeemed = s.optInt("total_redeemed", 0)
                        statsCommunity = s.optInt("community_size", 0)
                        statsCommunityActive = s.optInt("community_active", 0)
                        statsPendingDesigns = s.optInt("pending_designs", 0)
                        statsCommunityRevenueCents = s.optInt("community_revenue_cents", 0)
                    }
                    val communitySettings = withContext(Dispatchers.IO) {
                        api.getCreatorCommunitySettings(ownerId)
                    }
                    if (communitySettings.optBoolean("ok", false)) {
                        val settings = communitySettings.optJSONObject("settings")
                        recruiterOptIn = settings?.optBoolean("recruiter_opt_in", false) == true
                        val relArr = settings?.optJSONArray("member_relationships") ?: org.json.JSONArray()
                        memberRelationships = buildList(relArr.length()) {
                            for (i in 0 until relArr.length()) {
                                relArr.optJSONObject(i)?.let { add(it) }
                            }
                        }
                    }
                    val membersResp = withContext(Dispatchers.IO) {
                        api.listCreatorCommunityMembers(ownerId)
                    }
                    if (membersResp.optBoolean("ok", false)) {
                        val arr = membersResp.optJSONArray("members") ?: org.json.JSONArray()
                        communityMembers = buildList(arr.length()) {
                            for (i in 0 until arr.length()) {
                                arr.optJSONObject(i)?.let { add(it) }
                            }
                        }
                    }
                    val designsResp = withContext(Dispatchers.IO) {
                        api.getCommunityDesigns(ownerId)
                    }
                    if (designsResp.optBoolean("ok", false)) {
                        val arr = designsResp.optJSONArray("designs") ?: org.json.JSONArray()
                        pendingCommunityDesigns = buildList(arr.length()) {
                            for (i in 0 until arr.length()) {
                                arr.optJSONObject(i)?.let { add(it) }
                            }
                        }
                    }
                    val hist = withContext(Dispatchers.IO) { api.listRedeemedCreatorCodes(ownerId) }
                    if (hist.optBoolean("ok", false)) {
                        val arr = hist.optJSONArray("codes") ?: org.json.JSONArray()
                        redeemedHistory = buildList(arr.length()) {
                            for (i in 0 until arr.length()) {
                                val row = arr.optJSONObject(i) ?: continue
                                val code = row.optString("code", "").trim()
                                if (code.isBlank()) continue
                                val whenAt = row.optString("redeemed_at", row.optString("created_at", ""))
                                add(code to whenAt)
                            }
                        }
                    } else {
                        redeemedHistory = emptyList()
                    }
                }
            } catch (_: Exception) {}
            isLoading = false
        }
    }

    LaunchedEffect(ownerId) { reload() }

    LaunchedEffect(initialRedeemCode) {
        val code = initialRedeemCode?.trim()?.uppercase().orEmpty()
        if (code.isNotBlank()) {
            redeemCode = code
            onInitialRedeemCodeConsumed()
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
        return
    }

    pendingSaleId?.let { entId ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .background(EazColors.Orange.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = translationStore.t("creator.settings.creator_codes_pending_sale_title", "First sale unlocked!"),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = translationStore.t("creator.settings.creator_codes_pending_sale_subtitle", "Reveal your Creator Code here."),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 4.dp)
            )
            Button(
                onClick = {
                    scope.launch {
                        val resp = withContext(Dispatchers.IO) { api.revealCreatorCode(ownerId, entId) }
                        if (resp.optBoolean("ok", false)) reload()
                    }
                },
                modifier = Modifier.padding(top = 12.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
            ) {
                Text(
                    translationStore.t("creator.settings.creator_codes_reveal_btn", "Show Creator Code"),
                    color = Color.Black
                )
            }
        }
    }

    pendingPurchaseId?.let { entId ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .background(EazColors.Orange.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Text(
                text = translationStore.t("creator.settings.creator_codes_pending_purchase_title", "Purchase unlocked!"),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            if (purchaseNeedsQr && !purchaseQrVerified) {
                Text(
                    text = translationStore.t("creator.settings.creator_codes_pending_purchase_qr_hint", "Scan the product QR code."),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                OutlinedButton(
                    onClick = {
                        val token = android.widget.EditText(context).text.toString()
                        android.widget.Toast.makeText(context, "Paste QR token from scan", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(translationStore.t("creator.settings.creator_codes_scan_qr_btn", "Scan QR code"), color = EazColors.Orange)
                }
            } else {
                Button(
                    onClick = {
                        scope.launch {
                            val resp = withContext(Dispatchers.IO) { api.revealCreatorCode(ownerId, entId) }
                            if (resp.optBoolean("ok", false)) reload()
                        }
                    },
                    modifier = Modifier.padding(top = 12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
                ) {
                    Text(
                        translationStore.t("creator.settings.creator_codes_reveal_btn", "Show Creator Code"),
                        color = Color.Black
                    )
                }
            }
        }
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
                            if (resp.optBoolean("ok", false)) {
                                EazBalanceRefreshBus.requestRefresh()
                                reload()
                                onRequestSettingsTab(4)
                            }
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
                            reload()
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            EazColors.Orange.copy(alpha = 0.14f),
                            EazColors.Orange.copy(alpha = 0.05f),
                        ),
                    ),
                )
                .border(1.dp, EazColors.Orange.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = translationStore.t("creator.settings.creator_codes_generate_subtitle", "Share this code to invite others to become Creators."),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = code,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 2.sp,
                ),
                color = EazColors.Orange,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                androidx.compose.material3.Button(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cm?.setPrimaryClip(ClipData.newPlainText("code", code))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp), tint = Color.Black)
                    Text(
                        translationStore.t("creator.settings.creator_codes_copy_btn", "Copy"),
                        color = Color.Black,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (activeCodeCanShare && activeCodeId != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { showUserPickerDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Person, null, Modifier.size(18.dp), tint = EazColors.Orange)
                        Text(
                            translationStore.t("creator.settings.creator_codes_send_user_btn", "Send to user"),
                            color = EazColors.Orange,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = { showPoolDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Public, null, Modifier.size(18.dp), tint = EazColors.Orange)
                        Text(
                            translationStore.t("creator.settings.creator_codes_eazy_pool_btn", "Eazy pool"),
                            color = EazColors.Orange,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }

    activeCodeId?.let { codeId ->
        CreatorCodeUserPickerDialog(
            visible = showUserPickerDialog,
            ownerId = ownerId,
            api = api,
            codeId = codeId,
            isPermanentGift = activeCodeIsPermanentGift,
            translationStore = translationStore,
            onDismiss = { showUserPickerDialog = false },
            onSent = { reload() },
        )
        CreatorCodePoolConfirmDialog(
            visible = showPoolDialog,
            ownerId = ownerId,
            api = api,
            codeId = codeId,
            isPermanentGift = activeCodeIsPermanentGift,
            translationStore = translationStore,
            onDismiss = { showPoolDialog = false },
            onSent = { reload() },
        )
    }

    if (showRecruiterOptInConfirm) {
        AlertDialog(
            onDismissRequest = { showRecruiterOptInConfirm = false },
            title = {
                Text(
                    translationStore.t(
                        "creator.settings.creator_community_recruiter_opt_in_confirm_title",
                        "Enable community for recruits",
                    ),
                )
            },
            text = {
                Text(
                    translationStore.t(
                        "creator.settings.creator_community_recruiter_opt_in_confirm_body",
                        "When you and a recruit both opt in: you receive AI bonus designs when they create, and you earn 30% of their net creator profit on new sales (they keep 70%). You can turn this off anytime — new sales stop sharing revenue; already published products keep their split.",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRecruiterOptInConfirm = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                api.setCreatorCommunityOptIn(ownerId, "recruiter", true)
                            }
                            reload()
                        }
                    },
                ) {
                    Text(
                        translationStore.t(
                            "creator.settings.creator_community_opt_in_confirm_btn",
                            "Activate",
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecruiterOptInConfirm = false }) {
                    Text(translationStore.t("creator.common.cancel", "Cancel"))
                }
            },
        )
    }

    showMemberOptInConfirmFor?.let { communityOwnerId ->
        AlertDialog(
            onDismissRequest = { showMemberOptInConfirmFor = null },
            title = {
                Text(
                    translationStore.t(
                        "creator.settings.creator_community_member_opt_in_confirm_title",
                        "Join community program",
                    ),
                )
            },
            text = {
                Text(
                    translationStore.t(
                        "creator.settings.creator_community_member_opt_in_confirm_body",
                        "When you and the creator who invited you both opt in: they receive bonus designs when you create, and you keep 70% of your net creator profit on new sales (they receive 30%). You can turn this off anytime — new sales stop sharing revenue; your existing published products keep their split.",
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val owner = communityOwnerId
                        showMemberOptInConfirmFor = null
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                api.setCreatorCommunityOptIn(ownerId, "member", true, owner)
                            }
                            reload()
                        }
                    },
                ) {
                    Text(
                        translationStore.t(
                            "creator.settings.creator_community_opt_in_confirm_btn",
                            "Activate",
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showMemberOptInConfirmFor = null }) {
                    Text(translationStore.t("creator.common.cancel", "Cancel"))
                }
            },
        )
    }

    if (isCreator) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                translationStore.t("creator.settings.creator_codes_stats_generated", "Generated"),
                statsGenerated.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                translationStore.t("creator.settings.creator_codes_stats_redeemed", "Redeemed"),
                statsRedeemed.toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                translationStore.t("creator.settings.creator_codes_stats_community", "Community"),
                statsCommunity.toString(),
                modifier = Modifier.weight(1f),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(
                text = translationStore.t("creator.settings.creator_community_title", "Creator Community"),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Text(
                text = translationStore.t(
                    "creator.settings.creator_community_subtitle",
                    "Bonus designs and 70/30 revenue split when both sides opt in.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = translationStore.t(
                        "creator.settings.creator_community_recruiter_opt_in",
                        "Enable community features for my recruits",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                Switch(
                    checked = recruiterOptIn,
                    onCheckedChange = { checked ->
                        if (checked) {
                            showRecruiterOptInConfirm = true
                        } else {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    api.setCreatorCommunityOptIn(ownerId, "recruiter", false)
                                }
                                reload()
                            }
                        }
                    },
                )
            }
            memberRelationships.forEach { rel ->
                val owner = rel.optString("community_owner_id", "")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = translationStore.t(
                            "creator.settings.creator_community_member_opt_in",
                            "Join community program",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Switch(
                        checked = rel.optBoolean("member_opt_in", false),
                        onCheckedChange = { checked ->
                            if (checked) {
                                showMemberOptInConfirmFor = owner
                            } else {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        api.setCreatorCommunityOptIn(ownerId, "member", false, owner)
                                    }
                                    reload()
                                }
                            }
                        },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatCard(
                    translationStore.t("creator.settings.creator_community_stats_active", "Active"),
                    statsCommunityActive.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    translationStore.t("creator.settings.creator_community_stats_pending", "Pending designs"),
                    statsPendingDesigns.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    translationStore.t("creator.settings.creator_community_stats_revenue", "Revenue"),
                    String.format("%.2f", statsCommunityRevenueCents / 100.0),
                    modifier = Modifier.weight(1f),
                )
            }
            if (communityMembers.isNotEmpty()) {
                Text(
                    text = translationStore.t(
                        "creator.settings.creator_community_members_title",
                        "Community members",
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                communityMembers.forEach { m ->
                    val active = m.optBoolean("active", false)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = m.optString("member_id", ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                        )
                        Text(
                            text = if (active) {
                                translationStore.t("creator.settings.creator_community_member_active", "Active")
                            } else {
                                translationStore.t("creator.settings.creator_community_member_pending", "Waiting for opt-in")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) EazColors.Orange else Color.White.copy(alpha = 0.55f),
                        )
                    }
                }
            }
            Text(
                text = translationStore.t("creator.settings.creator_community_designs_title", "Bonus designs"),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            if (pendingCommunityDesigns.isEmpty()) {
                Text(
                    text = translationStore.t(
                        "creator.settings.creator_community_designs_empty",
                        "No pending bonus designs.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                )
            } else {
                pendingCommunityDesigns.forEach { d ->
                    val designId = d.optLong("id")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        api.claimCommunityDesign(ownerId, designId)
                                    }
                                    reload()
                                }
                            },
                        ) {
                            Text(
                                translationStore.t("creator.settings.creator_community_claim_btn", "Claim"),
                                color = EazColors.Orange,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        api.dismissCommunityDesign(ownerId, designId)
                                    }
                                    reload()
                                }
                            },
                        ) {
                            Text(
                                translationStore.t("creator.settings.creator_community_dismiss_btn", "Dismiss"),
                                color = Color.White.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = translationStore.t("creator.settings.creator_codes_redeemed_title", "Redeemed Codes"),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        )
        if (redeemedHistory.isEmpty()) {
            Text(
                text = translationStore.t("creator.settings.creator_codes_redeemed_empty", "No codes have been redeemed yet."),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        } else {
            redeemedHistory.forEach { (code, whenAt) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        ),
                        color = Color.White,
                    )
                    if (whenAt.isNotBlank()) {
                        Text(
                            text = whenAt.take(10),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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

private val CREATOR_NAME_CHARS = Regex("^[\\p{L}\\p{N}\\s-]+$")

private fun normalizeCreatorNameInput(name: String): String =
    name.trim().replace(Regex("\\s+"), " ")

private fun validateCreatorNameInput(name: String): String? {
    val trimmed = normalizeCreatorNameInput(name)
    if (trimmed.isBlank()) return "missing_name"
    if (trimmed.length < 3) return "too_short"
    if (!CREATOR_NAME_CHARS.matches(trimmed)) return "invalid_chars"
    if (trimmed.startsWith("-") || trimmed.endsWith("-")) return "invalid_chars"
    return null
}

private fun creatorNameErrorMessage(error: String, translationStore: TranslationStore): String =
    when (error) {
        "invalid_chars" -> translationStore.t(
            "creator.settings_names.invalid_chars",
            "Only letters, numbers, spaces, and hyphens (-) are allowed."
        )
        "name_taken" -> translationStore.t(
            "creator.settings_names.name_taken",
            "This name is already taken. Try another one!"
        )
        "too_short" -> translationStore.t(
            "creator.settings_names.too_short",
            "Name must be at least 3 characters."
        )
        "limit_reached" -> translationStore.t(
            "creator.settings_names.limit_reached",
            "You can register up to 5 creator names."
        )
        "already_added" -> translationStore.t(
            "creator.settings_names.already_added",
            "You already added this name."
        )
        "blocked_phrase" -> translationStore.t(
            "creator.settings_names.blocked_phrase",
            "This name contains a blocked phrase and cannot be used."
        )
        else -> translationStore.t("creator.settings_names.add_error", "Could not add name.")
    }

@Composable
private fun CreatorSettingsNamesContent(
    ownerId: String,
    api: CreatorApi,
    translationStore: TranslationStore
) {
    var newName by remember { mutableStateOf("") }
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var nameLimit by remember { mutableStateOf(5) }
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
            val limitRaw = settingsObj.optInt("creator_name_limit", -1)
            nameLimit = if (limitRaw > 0) limitRaw else 5
            names = raw.filter { seen.add(it.lowercase()) }.take(nameLimit.coerceAtMost(5))
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
            val name = normalizeCreatorNameInput(newName)
            if (name.isBlank()) return@Button
            if (names.size >= nameLimit) return@Button
            validateCreatorNameInput(name)?.let { code ->
                statusMessage = creatorNameErrorMessage(code, translationStore)
                statusError = true
                return@Button
            }
            scope.launch {
                try {
                    val resp = withContext(Dispatchers.IO) { api.addCreatorName(ownerId, name) }
                    if (resp.optBoolean("ok", false)) {
                        names = names + name
                        newName = ""
                        statusMessage = translationStore.t("creator.settings_names.added_ok", "Creator name added.")
                        statusError = false
                    } else {
                        statusMessage = creatorNameErrorMessage(
                            resp.optString("error", "add_error"),
                            translationStore
                        )
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
        text = "${translationStore.t("creator.settings_names.your_names", "Your names")} (${names.size}/$nameLimit)",
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
