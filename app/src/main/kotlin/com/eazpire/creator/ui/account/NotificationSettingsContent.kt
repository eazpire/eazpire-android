package com.eazpire.creator.ui.account

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

private data class NotifPrefRowModel(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val infoRes: Int,
    val testOpenTarget: String,
    val checked: Boolean,
    val enabled: Boolean,
    val onChecked: (Boolean) -> Unit,
)

@Composable
fun NotificationSettingsContent(
    scope: NotificationScope,
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier,
    embedInParentScroll: Boolean = false,
) {
    val context = LocalContext.current
    val repo = remember { NotificationPreferencesRepository(context) }
    val jwt = tokenStore.getJwt()
    val api = remember(jwt) { CreatorApi(jwt = jwt) }
    val state by repo.prefsFlow.collectAsState(initial = NotificationPrefs())
    var loading by remember { mutableStateOf(true) }
    var expandedInfoId by remember { mutableStateOf<String?>(null) }
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
    val infoColor = if (scope == NotificationScope.Creator) Color(0xFFB0B0B0) else MaterialTheme.colorScheme.onSurfaceVariant
    val infoBtnTint = if (scope == NotificationScope.Creator) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onSurfaceVariant

    if (jwt.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.notif_prefs_login_required),
            style = MaterialTheme.typography.bodyMedium,
            color = infoColor,
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

    val rows = when (scope) {
        NotificationScope.Shop -> buildShopRows(state, api, repo, scopeIo)
        NotificationScope.Creator -> buildCreatorRows(state, api, repo, scopeIo)
    }

    val contentModifier = if (embedInParentScroll) {
        modifier.fillMaxWidth()
    } else {
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    }

    Column(
        modifier = contentModifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.notif_push_section_title),
            style = MaterialTheme.typography.titleSmall,
            color = labelColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Text(
            text = stringResource(R.string.notif_push_section_hint),
            style = MaterialTheme.typography.bodySmall,
            color = infoColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 0.dp)
        )
        rows.forEach { row ->
            notifRow(
                context = context,
                row = row,
                labelColor = labelColor,
                infoColor = infoColor,
                infoBtnTint = infoBtnTint,
                expanded = expandedInfoId == row.id,
                onToggleInfo = {
                    expandedInfoId = if (expandedInfoId == row.id) null else row.id
                }
            )
        }
    }
}

private fun buildShopRows(
    state: NotificationPrefs,
    api: CreatorApi,
    repo: NotificationPreferencesRepository,
    scopeIo: kotlinx.coroutines.CoroutineScope,
): List<NotifPrefRowModel> {
    val subEnabled = state.shopMaster
    return listOf(
        NotifPrefRowModel(
            id = "shop_master",
            labelRes = R.string.notif_shop_master,
            infoRes = R.string.notif_info_shop_master,
            testOpenTarget = "eazy_notifications",
            checked = state.shopMaster,
            enabled = true,
            onChecked = { v -> scopeIo.launch { repo.saveShopMaster(api, v) } }
        ),
        NotifPrefRowModel(
            id = "cart_reminder",
            labelRes = R.string.notif_shop_cart,
            infoRes = R.string.notif_info_shop_cart,
            testOpenTarget = "cart",
            checked = state.shop["cart_reminder"] != false,
            enabled = subEnabled,
            onChecked = { v -> scopeIo.launch { repo.saveShopKey(api, "cart_reminder", v) } }
        ),
        NotifPrefRowModel(
            id = "orders",
            labelRes = R.string.notif_shop_orders,
            infoRes = R.string.notif_info_shop_orders,
            testOpenTarget = "eazy_notifications",
            checked = state.shop["orders"] != false,
            enabled = subEnabled,
            onChecked = { v -> scopeIo.launch { repo.saveShopKey(api, "orders", v) } }
        ),
        NotifPrefRowModel(
            id = "promotions_new",
            labelRes = R.string.notif_shop_promotions_new,
            infoRes = R.string.notif_info_shop_promotions_new,
            testOpenTarget = "shop",
            checked = state.shop["promotions_new"] != false,
            enabled = subEnabled,
            onChecked = { v -> scopeIo.launch { repo.saveShopKey(api, "promotions_new", v) } }
        ),
        NotifPrefRowModel(
            id = "promotions_ending_soon",
            labelRes = R.string.notif_shop_promotions_ending,
            infoRes = R.string.notif_info_shop_promotions_ending,
            testOpenTarget = "shop",
            checked = state.shop["promotions_ending_soon"] != false,
            enabled = subEnabled,
            onChecked = { v -> scopeIo.launch { repo.saveShopKey(api, "promotions_ending_soon", v) } }
        ),
        NotifPrefRowModel(
            id = "app_promotions",
            labelRes = R.string.notif_shop_app_promotions,
            infoRes = R.string.notif_info_shop_app_promotions,
            testOpenTarget = "gift_cards_won",
            checked = state.shop["app_promotions"] != false,
            enabled = subEnabled,
            onChecked = { v -> scopeIo.launch { repo.saveShopKey(api, "app_promotions", v) } }
        ),
        NotifPrefRowModel(
            id = "daily_game",
            labelRes = R.string.notif_shop_daily_game,
            infoRes = R.string.notif_info_shop_daily_game,
            testOpenTarget = "eazy_notifications",
            checked = state.shop["daily_game"] != false,
            enabled = subEnabled,
            onChecked = { v -> scopeIo.launch { repo.saveShopKey(api, "daily_game", v) } }
        ),
    )
}

private fun buildCreatorRows(
    state: NotificationPrefs,
    api: CreatorApi,
    repo: NotificationPreferencesRepository,
    scopeIo: kotlinx.coroutines.CoroutineScope,
): List<NotifPrefRowModel> {
    val subEnabled = state.creatorMaster
    return listOf(
        NotifPrefRowModel(
            id = "creator_master",
            labelRes = R.string.notif_creator_master,
            infoRes = R.string.notif_info_creator_master,
            testOpenTarget = "eazy_notifications",
            checked = state.creatorMaster,
            enabled = true,
            onChecked = { v -> scopeIo.launch { repo.saveCreatorMaster(api, v) } }
        ),
        NotifPrefRowModel(
            id = "generations",
            labelRes = R.string.notif_creator_generations,
            infoRes = R.string.notif_info_creator_generations,
            testOpenTarget = "eazy_jobs",
            checked = state.creator["generations"] != false,
            enabled = subEnabled,
            onChecked = { v -> scopeIo.launch { repo.saveCreatorKey(api, "generations", v) } }
        ),
        NotifPrefRowModel(
            id = "design_saved",
            labelRes = R.string.notif_creator_designs,
            infoRes = R.string.notif_info_creator_designs,
            testOpenTarget = "eazy_notifications",
            checked = state.creator["design_saved"] != false,
            enabled = subEnabled,
            onChecked = { v -> scopeIo.launch { repo.saveCreatorKey(api, "design_saved", v) } }
        ),
        NotifPrefRowModel(
            id = "product_published",
            labelRes = R.string.notif_creator_publish,
            infoRes = R.string.notif_info_creator_publish,
            testOpenTarget = "eazy_notifications",
            checked = state.creator["product_published"] != false,
            enabled = subEnabled,
            onChecked = { v -> scopeIo.launch { repo.saveCreatorKey(api, "product_published", v) } }
        ),
        NotifPrefRowModel(
            id = "community",
            labelRes = R.string.notif_creator_community,
            infoRes = R.string.notif_info_creator_community,
            testOpenTarget = "eazy_notifications",
            checked = state.creator["community"] != false,
            enabled = subEnabled,
            onChecked = { v -> scopeIo.launch { repo.saveCreatorKey(api, "community", v) } }
        ),
        NotifPrefRowModel(
            id = "other",
            labelRes = R.string.notif_creator_other,
            infoRes = R.string.notif_info_creator_other,
            testOpenTarget = "eazy_notifications",
            checked = state.creator["other"] != false,
            enabled = subEnabled,
            onChecked = { v -> scopeIo.launch { repo.saveCreatorKey(api, "other", v) } }
        ),
    )
}

@Composable
private fun notifRow(
    context: Context,
    row: NotifPrefRowModel,
    labelColor: Color,
    infoColor: Color,
    infoBtnTint: Color,
    expanded: Boolean,
    onToggleInfo: () -> Unit,
) {
    val label = stringResource(row.labelRes)
    val infoText = stringResource(row.infoRes)
    val infoBtnLabel = stringResource(R.string.notif_info_btn)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = labelColor,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 4.dp)
                        .clickable {
                            EazNotificationDisplay.showTestPushForOpenTarget(
                                context,
                                label,
                                row.testOpenTarget
                            )
                        }
                )
                IconButton(
                    onClick = onToggleInfo,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = infoBtnLabel,
                        tint = if (expanded) Color(0xFFF97316) else infoBtnTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Switch(
                checked = row.checked,
                onCheckedChange = row.onChecked,
                enabled = row.enabled
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                text = infoText,
                style = MaterialTheme.typography.bodySmall,
                color = infoColor,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            )
        }
    }
}
