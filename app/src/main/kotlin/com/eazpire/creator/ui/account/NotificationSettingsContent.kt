package com.eazpire.creator.ui.account

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eazpire.creator.R
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.notifications.EazNotificationDisplay
import com.eazpire.creator.notifications.NotificationPrefs
import com.eazpire.creator.notifications.NotificationPreferencesRepository
import kotlinx.coroutines.launch

enum class NotificationScope {
    Shop,
    Creator
}

@Composable
fun NotificationSettingsContent(
    scope: NotificationScope,
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repo = remember { NotificationPreferencesRepository(context) }
    val jwt = tokenStore.getJwt()
    val api = remember(jwt) { CreatorApi(jwt = jwt) }
    val state by repo.prefsFlow.collectAsState(initial = NotificationPrefs())
    var loading by remember { mutableStateOf(true) }
    val scopeIo = rememberCoroutineScope()

    LaunchedEffect(jwt) {
        if (jwt.isNullOrBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            repo.syncFromServer(api)
        } finally {
            loading = false
        }
    }

    val labelColor = if (scope == NotificationScope.Creator) Color(0xFFE8E8E8) else MaterialTheme.colorScheme.onSurface

    if (jwt.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.notif_prefs_login_required),
            style = MaterialTheme.typography.bodyMedium,
            color = if (scope == NotificationScope.Creator) Color(0xFFB0B0B0) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(16.dp)
        )
        return
    }

    if (loading) {
        Column(modifier = modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        when (scope) {
            NotificationScope.Shop -> {
                notifRow(
                    context = context,
                    label = stringResource(R.string.notif_shop_master),
                    labelColor = labelColor,
                    testOpenTarget = "eazy_notifications",
                    checked = state.shopMaster,
                    enabled = true,
                    onChecked = { v ->
                        scopeIo.launch { repo.saveShopMaster(api, v) }
                    }
                )
                val subEnabled = state.shopMaster
                notifRow(
                    context = context,
                    label = stringResource(R.string.notif_shop_cart),
                    labelColor = labelColor,
                    testOpenTarget = "cart",
                    checked = state.shop["cart_reminder"] != false,
                    enabled = subEnabled,
                    onChecked = { v ->
                        scopeIo.launch { repo.saveShopKey(api, "cart_reminder", v) }
                    }
                )
                notifRow(
                    context = context,
                    label = stringResource(R.string.notif_shop_orders),
                    labelColor = labelColor,
                    testOpenTarget = "eazy_notifications",
                    checked = state.shop["orders"] != false,
                    enabled = subEnabled,
                    onChecked = { v ->
                        scopeIo.launch { repo.saveShopKey(api, "orders", v) }
                    }
                )
                notifRow(
                    context = context,
                    label = stringResource(R.string.notif_shop_promotions_new),
                    labelColor = labelColor,
                    testOpenTarget = "shop",
                    checked = state.shop["promotions_new"] != false,
                    enabled = subEnabled,
                    onChecked = { v ->
                        scopeIo.launch { repo.saveShopKey(api, "promotions_new", v) }
                    }
                )
                notifRow(
                    context = context,
                    label = stringResource(R.string.notif_shop_promotions_ending),
                    labelColor = labelColor,
                    testOpenTarget = "shop",
                    checked = state.shop["promotions_ending_soon"] != false,
                    enabled = subEnabled,
                    onChecked = { v ->
                        scopeIo.launch { repo.saveShopKey(api, "promotions_ending_soon", v) }
                    }
                )
            }
            NotificationScope.Creator -> {
                notifRow(
                    context = context,
                    label = stringResource(R.string.notif_creator_master),
                    labelColor = labelColor,
                    testOpenTarget = "eazy_notifications",
                    checked = state.creatorMaster,
                    enabled = true,
                    onChecked = { v ->
                        scopeIo.launch { repo.saveCreatorMaster(api, v) }
                    }
                )
                val subEnabled = state.creatorMaster
                notifRow(
                    context = context,
                    label = stringResource(R.string.notif_creator_generations),
                    labelColor = labelColor,
                    testOpenTarget = "eazy_jobs",
                    checked = state.creator["generations"] != false,
                    enabled = subEnabled,
                    onChecked = { v ->
                        scopeIo.launch { repo.saveCreatorKey(api, "generations", v) }
                    }
                )
                notifRow(
                    context = context,
                    label = stringResource(R.string.notif_creator_designs),
                    labelColor = labelColor,
                    testOpenTarget = "eazy_notifications",
                    checked = state.creator["design_saved"] != false,
                    enabled = subEnabled,
                    onChecked = { v ->
                        scopeIo.launch { repo.saveCreatorKey(api, "design_saved", v) }
                    }
                )
                notifRow(
                    context = context,
                    label = stringResource(R.string.notif_creator_publish),
                    labelColor = labelColor,
                    testOpenTarget = "eazy_notifications",
                    checked = state.creator["product_published"] != false,
                    enabled = subEnabled,
                    onChecked = { v ->
                        scopeIo.launch { repo.saveCreatorKey(api, "product_published", v) }
                    }
                )
                notifRow(
                    context = context,
                    label = stringResource(R.string.notif_creator_community),
                    labelColor = labelColor,
                    testOpenTarget = "eazy_notifications",
                    checked = state.creator["community"] != false,
                    enabled = subEnabled,
                    onChecked = { v ->
                        scopeIo.launch { repo.saveCreatorKey(api, "community", v) }
                    }
                )
                notifRow(
                    context = context,
                    label = stringResource(R.string.notif_creator_other),
                    labelColor = labelColor,
                    testOpenTarget = "eazy_notifications",
                    checked = state.creator["other"] != false,
                    enabled = subEnabled,
                    onChecked = { v ->
                        scopeIo.launch { repo.saveCreatorKey(api, "other", v) }
                    }
                )
            }
        }
    }
}

@Composable
private fun notifRow(
    context: Context,
    label: String,
    labelColor: Color,
    testOpenTarget: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
                .clickable {
                    EazNotificationDisplay.showTestPushForOpenTarget(context, label, testOpenTarget)
                }
        )
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            enabled = enabled
        )
    }
}
