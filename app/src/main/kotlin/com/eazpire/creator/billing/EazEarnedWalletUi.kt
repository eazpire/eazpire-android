package com.eazpire.creator.billing

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EazEarnedConvertBlock(
    ownerId: String,
    api: CreatorApi,
    earnedAvailable: Double,
    eazCentsPerEaz: Double,
    minConvertEaz: Double = 50.0,
    translate: (String, String) -> String,
    onConverted: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var amountText by remember { mutableStateOf("") }
    var converting by remember { mutableStateOf(false) }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val previewCents = if (amount > 0 && eazCentsPerEaz > 0) (amount * eazCentsPerEaz).toLong() else 0L

    fun runConvert(op: String) {
        if (ownerId.isBlank() || amount <= 0 || amount > earnedAvailable) {
            Toast.makeText(
                context,
                translate("creator.settings.eaz_convert_fail", "Conversion failed."),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (amount < minConvertEaz) {
            Toast.makeText(
                context,
                translate("creator.settings.eaz_convert_fail", "Conversion failed."),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        scope.launch {
            converting = true
            try {
                val resp = withContext(Dispatchers.IO) {
                    when (op) {
                        "fiat" -> api.convertEazToFiat(ownerId, amount)
                        else -> api.convertEazToGiftCard(ownerId, amount)
                    }
                }
                if (resp.optBoolean("ok", false)) {
                    amountText = ""
                    EazBalanceRefreshBus.requestRefresh()
                    onConverted()
                    Toast.makeText(
                        context,
                        translate("creator.settings.eaz_convert_success", "Conversion complete."),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        translate("creator.settings.eaz_convert_fail", "Conversion failed."),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    translate("creator.settings.eaz_convert_fail", "Conversion failed."),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                converting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            translate(
                "creator.settings.eaz_convert_hint",
                "Convert available earned EAZ to fiat balance or a shop gift card."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f)
        )
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
            label = {
                Text(translate("creator.settings.eaz_convert_amount_label", "Amount (EAZ)"))
            },
            singleLine = true,
            enabled = !converting,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = EazColors.Orange,
                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
            )
        )
        if (previewCents > 0) {
            Text(
                translate("creator.settings.eaz_convert_preview", "≈ {{amount}} fiat")
                    .replace("{{amount}}", String.format("%.2f", previewCents / 100.0)),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { runConvert("fiat") },
                enabled = !converting,
                colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
            ) {
                Text(
                    translate("creator.settings.eaz_convert_fiat", "Convert to fiat"),
                    color = Color.White
                )
            }
            OutlinedButton(
                onClick = { runConvert("gc") },
                enabled = !converting,
            ) {
                Text(translate("creator.settings.eaz_convert_gift_card", "Convert to gift card"))
            }
        }
    }
}
