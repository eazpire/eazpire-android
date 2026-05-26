package com.eazpire.creator.ui.creator

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.WearPairApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.wear.WearPairPrefs
import com.eazpire.creator.wear.sync.WearAuthSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private const val WEAR_PLAY_STORE_URL =
    "https://play.google.com/store/apps/details?id=com.eazpire.creator.wear"
private const val SETTINGS_TAB_CREATOR_CODES = 2
private const val SETTINGS_TAB_CREATOR_NAMES = 4

@Composable
fun CreatorSettingsWearContent(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    pendingPairToken: String? = null,
    onRequestSettingsTab: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val jwt = remember { tokenStore.getJwt() }
    val loggedIn = remember { tokenStore.getJwt()?.isNotBlank() == true }
    var showScanner by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var claiming by remember { mutableStateOf(false) }
    var loadingStatus by remember { mutableStateOf(false) }
    var wearConnected by remember { mutableStateOf(false) }
    var wearDeviceName by remember { mutableStateOf<String?>(null) }
    var wearConnectedAt by remember { mutableStateOf<Long?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var connectReady by remember { mutableStateOf(false) }
    var missingPrereq by remember { mutableStateOf<List<String>>(emptyList()) }

    fun formatConnectedAt(ts: Long?): String? {
        if (ts == null || ts <= 0L) return null
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(ts))
    }

    fun loadServerStatus() {
        val j = jwt?.trim().orEmpty()
        if (j.isBlank()) {
            wearConnected = false
            wearDeviceName = null
            wearConnectedAt = null
            connectReady = false
            missingPrereq = emptyList()
            return
        }
        loadingStatus = true
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) { WearPairApi(jwt = j).getStatus(j) }
                if (res.optBoolean("connected", false)) {
                    wearConnected = true
                    wearDeviceName = res.optString("device_name", "").trim().ifBlank {
                        res.optString("device_id", "").trim()
                    }.ifBlank { null }
                    val at = res.optLong("connected_at", 0L)
                    wearConnectedAt = at.takeIf { it > 0L }
                    wearDeviceName?.let { name ->
                        WearPairPrefs.save(
                            context,
                            res.optString("device_id", "").ifBlank { "watch" },
                            name,
                            wearConnectedAt ?: System.currentTimeMillis(),
                        )
                    }
                } else {
                    wearConnected = false
                    wearDeviceName = WearPairPrefs.getDeviceName(context)
                    wearConnectedAt = WearPairPrefs.getPairedAt(context).takeIf { it > 0L }
                }
            } catch (_: Exception) {
                wearConnected = WearPairPrefs.getDeviceId(context) != null
                wearDeviceName = WearPairPrefs.getDeviceName(context)
                wearConnectedAt = WearPairPrefs.getPairedAt(context).takeIf { it > 0L }
            } finally {
                loadingStatus = false
            }
        }
    }

    fun claimToken(token: String) {
        val j = jwt?.trim().orEmpty()
        if (j.isBlank()) {
            statusMessage = translationStore.t(
                "creator.settings.wear_login_required",
                "Log in on this device first.",
            )
            return
        }
        claiming = true
        statusMessage = null
        scope.launch {
            try {
                val phoneName = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
                    .joinToString(" ")
                    .trim()
                    .ifBlank { "Phone" }
                val res = withContext(Dispatchers.IO) {
                    WearPairApi(jwt = j).claim(token, phoneName)
                }
                if (res.optBoolean("ok", false)) {
                    val deviceId = res.optString("device_id", "").trim()
                    val deviceName = res.optString("device_name", "").trim()
                    val connectedAt = res.optLong("connected_at", System.currentTimeMillis())
                    if (deviceId.isNotBlank()) {
                        WearPairPrefs.save(context, deviceId, deviceName.ifBlank { null }, connectedAt)
                    }
                    WearAuthSync.push(context, tokenStore)
                    statusMessage = translationStore.t(
                        "creator.settings.wear_connected_ok",
                        "Watch connected. Open the Wear app on your watch.",
                    )
                    refreshKey++
                    loadServerStatus()
                } else if (res.optString("error") == "wear_prerequisites_not_met") {
                    val pre = res.optJSONObject("prerequisites")
                    connectReady = false
                    missingPrereq = pre?.optJSONArray("missing")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i ->
                            arr.optString(i, "").trim().takeIf { it.isNotEmpty() }
                        }
                    } ?: listOf("creator_code", "creator_name")
                    statusMessage = translationStore.t(
                        "creator.settings.wear_prereq_title",
                        "Before you can connect",
                    )
                } else {
                    statusMessage = res.optString("error", "claim_failed")
                }
            } catch (e: Exception) {
                statusMessage = e.message ?: "error"
            } finally {
                claiming = false
                showScanner = false
            }
        }
    }

    fun disconnectWear() {
        val j = jwt?.trim().orEmpty()
        if (j.isBlank()) return
        scope.launch {
            claiming = true
            try {
                withContext(Dispatchers.IO) { WearPairApi(jwt = j).disconnect(j) }
                WearAuthSync.clear(context)
                WearPairPrefs.clear(context)
                wearConnected = false
                wearDeviceName = null
                wearConnectedAt = null
                statusMessage = translationStore.t(
                    "creator.settings.wear_disconnected",
                    "Watch disconnected.",
                )
            } catch (e: Exception) {
                statusMessage = e.message
            } finally {
                claiming = false
            }
        }
    }

    LaunchedEffect(refreshKey, loggedIn) {
        loadServerStatus()
    }

    LaunchedEffect(pendingPairToken) {
        val t = pendingPairToken?.trim().orEmpty()
        if (t.isNotBlank()) claimToken(t)
    }

    if (showScanner) {
        WearPairQrScannerOverlay(
            hint = translationStore.t(
                "creator.settings.wear_scan_hint",
                "Scan the QR code on your watch",
            ),
            onScanned = { claimToken(it) },
            onDismiss = { showScanner = false },
        )
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = translationStore.t(
                "creator.settings.wear_intro",
                "Install Eazpire Creator on your Wear OS watch, then connect your account by scanning the QR on the watch.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
        )

        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(WEAR_PLAY_STORE_URL)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                translationStore.t(
                    "creator.settings.wear_play_store",
                    "Get app on Google Play",
                )
            )
        }

        Text(
            text = translationStore.t("creator.settings.wear_status_title", "Status"),
            style = MaterialTheme.typography.titleSmall,
            color = EazColors.Orange,
        )
        Text(
            text = if (loggedIn) {
                translationStore.t("creator.settings.wear_phone_logged_in", "Phone: logged in")
            } else {
                translationStore.t("creator.settings.wear_phone_logged_out", "Phone: not logged in")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
        if (loadingStatus) {
            CircularProgressIndicator(color = EazColors.Orange)
        } else if (wearConnected && !wearDeviceName.isNullOrBlank()) {
            Text(
                text = translationStore.t(
                    "creator.settings.wear_connected_watch",
                    "Connected watch: {{name}}",
                ).replace("{{name}}", wearDeviceName!!),
                style = MaterialTheme.typography.bodyMedium,
                color = EazColors.Orange,
            )
            formatConnectedAt(wearConnectedAt)?.let { whenStr ->
                Text(
                    text = translationStore.t(
                        "creator.settings.wear_connected_at",
                        "Connected: {{when}}",
                    ).replace("{{when}}", whenStr),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        } else if (loggedIn) {
            Text(
                text = translationStore.t(
                    "creator.settings.wear_not_connected",
                    "No watch connected to your account.",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }

        if (claiming) {
            CircularProgressIndicator(color = EazColors.Orange)
        }

        if (loggedIn && !wearConnected && missingPrereq.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        EazColors.Orange.copy(alpha = 0.08f),
                        RoundedCornerShape(12.dp),
                    )
                    .border(1.dp, EazColors.Orange.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = translationStore.t(
                        "creator.settings.wear_prereq_title",
                        "Before you can connect",
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                Text(
                    text = translationStore.t(
                        "creator.settings.wear_prereq_intro",
                        "Complete these steps in Creator Settings:",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
                if (missingPrereq.contains("creator_code")) {
                    WearPrereqItem(
                        title = translationStore.t(
                            "creator.settings.wear_prereq_creator_code",
                            "Redeem a Creator Code",
                        ),
                        how = translationStore.t(
                            "creator.settings.wear_prereq_creator_code_how",
                            "Open Creator Settings → Creator Codes and enter your code.",
                        ),
                        cta = translationStore.t(
                            "creator.settings.wear_prereq_go_creator_code",
                            "Go to Creator Codes",
                        ),
                        onCta = { onRequestSettingsTab(SETTINGS_TAB_CREATOR_CODES) },
                    )
                }
                if (missingPrereq.contains("creator_name")) {
                    WearPrereqItem(
                        title = translationStore.t(
                            "creator.settings.wear_prereq_creator_name",
                            "Add at least one Creator Name",
                        ),
                        how = translationStore.t(
                            "creator.settings.wear_prereq_creator_name_how",
                            "Open Creator Settings → Creator Names and add your public display name.",
                        ),
                        cta = translationStore.t(
                            "creator.settings.wear_prereq_go_creator_names",
                            "Go to Creator Names",
                        ),
                        onCta = { onRequestSettingsTab(SETTINGS_TAB_CREATOR_NAMES) },
                    )
                }
            }
        }

        statusMessage?.let { msg ->
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (msg.contains("connected", ignoreCase = true)) EazColors.Orange else Color(0xFFFCA5A5),
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (wearConnected) {
            OutlinedButton(
                onClick = { disconnectWear() },
                enabled = !claiming,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(translationStore.t("creator.settings.wear_disconnect", "Disconnect watch"))
            }
        } else if (loggedIn && (connectReady || missingPrereq.isEmpty())) {
            Button(
                onClick = {
                    if (!loggedIn) {
                        statusMessage = translationStore.t(
                            "creator.settings.wear_login_required",
                            "Log in on this device first.",
                        )
                    } else if (!connectReady && missingPrereq.isNotEmpty()) {
                        statusMessage = translationStore.t(
                            "creator.settings.wear_prereq_title",
                            "Before you can connect",
                        )
                    } else {
                        showScanner = true
                    }
                },
                enabled = !claiming && connectReady,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(translationStore.t("creator.settings.wear_connect", "Connect"))
            }
        }
    }
}

@Composable
private fun WearPrereqItem(
    title: String,
    how: String,
    cta: String,
    onCta: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, color = Color.White)
        Text(text = how, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        TextButton(onClick = onCta) {
            Text(cta, color = EazColors.Orange)
        }
    }
}
