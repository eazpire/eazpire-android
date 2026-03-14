package com.eazpire.creator.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.util.DebugLog
import kotlinx.coroutines.launch

@Composable
fun AccountBalanceTab(
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier
) {
    val jwt = remember { tokenStore.getJwt() }
    val ownerId = remember { tokenStore.getOwnerId() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }

    var balanceEaz by remember { mutableStateOf<Double?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        errorMessage = null
        try {
            val resp = api.getBalance(ownerId)
            if (resp.optBoolean("ok", false)) {
                balanceEaz = resp.optDouble("balance_eaz", 0.0)
            } else {
                errorMessage = resp.optString("error", "Failed to load balance")
            }
        } catch (e: Exception) {
            DebugLog.click("Balance load error: ${e.message}")
            errorMessage = "Failed to load balance"
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Balance & Payouts",
            style = MaterialTheme.typography.titleMedium,
            color = EazColors.TextPrimary
        )
        Text(
            text = "Your EAZ balance for creator earnings.",
            style = MaterialTheme.typography.bodySmall,
            color = EazColors.TextSecondary
        )

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
            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            balanceEaz?.let { balance ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EazColors.OrangeBg),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "Available Balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = EazColors.TextSecondary
                        )
                        Text(
                            text = "%.2f EAZ".format(balance),
                            style = MaterialTheme.typography.headlineMedium,
                            color = EazColors.Orange
                        )
                    }
                }
            }
        }

        Text(
            text = "Payout settings and history coming soon.",
            style = MaterialTheme.typography.bodySmall,
            color = EazColors.TextSecondary
        )
    }
}
