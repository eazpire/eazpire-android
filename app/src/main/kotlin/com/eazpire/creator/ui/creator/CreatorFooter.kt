package com.eazpire.creator.ui.creator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CreatorFooter(
    localeStore: LocaleStore,
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val langCode by localeStore.languageCode.collectAsState(initial = "en")
    var eazBalance by remember { mutableStateOf("…") }
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }

    LaunchedEffect(ownerId) {
        if (ownerId.isNotBlank()) {
            try {
                val r = withContext(Dispatchers.IO) { api.getBalance(ownerId) }
                if (r.optBoolean("ok", false)) {
                    val bal = r.optDouble("balance", 0.0)
                    eazBalance = "%.2f".format(bal)
                } else {
                    eazBalance = "0.00"
                }
            } catch (_: Exception) {
                eazBalance = "0.00"
            }
        } else {
            eazBalance = "0.00"
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xEB080512)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "© ${java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)} ",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
        Text(
            text = "eazpire",
            style = MaterialTheme.typography.labelSmall,
            color = EazColors.Orange,
            modifier = Modifier.padding(end = 4.dp)
        )
        Text(
            text = " • ",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.72f)
        )
        Text(
            text = "Terms & Policies",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.eazpire.com/policies/terms-of-service")))
                    } catch (_: Exception) {}
                }
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = langCode.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(end = 8.dp)
            )
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("https://pub-2ffb11d4a361463498b9a842a87a870c.r2.dev/brand/coin/eaz-coin-logo.png")
                    .build(),
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp).size(14.dp)
            )
            Text(
                text = eazBalance,
                style = MaterialTheme.typography.labelMedium,
                color = EazColors.Orange
            )
            Text(
                text = " EAZ",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
    }
}
