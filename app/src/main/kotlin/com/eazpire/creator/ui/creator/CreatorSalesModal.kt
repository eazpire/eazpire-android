package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CreatorSalesModal(
    tokenStore: SecureTokenStore,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var balanceText by remember { mutableStateOf("0.00") }
    var currencySymbol by remember { mutableStateOf("€") }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }

    LaunchedEffect(ownerId) {
        if (ownerId.isNotBlank()) {
            try {
                val sales = withContext(Dispatchers.IO) { api.getCreatorSales(ownerId) }
                if (sales.optBoolean("ok", false)) {
                    val bal = sales.optDouble("available_balance", 0.0)
                    balanceText = "%.2f".format(bal)
                    currencySymbol = sales.optString("currency_symbol", "€")
                }
            } catch (_: Exception) {}
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color(0xFF0A0514))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sales & Balance",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Text(
                    text = "Available Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 24.dp)
                )
                Text(
                    text = "$balanceText $currencySymbol",
                    style = MaterialTheme.typography.headlineMedium,
                    color = EazColors.Orange,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Text(
                    text = "Earnings, network activity, and payout requests can be managed in the full web experience.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
    }
}
