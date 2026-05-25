package com.eazpire.creator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.pricing.QuantityDiscount

@Composable
fun QuantityDiscountModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    unitPrice: Double,
    quantity: Int,
    currencyLabel: String,
    t: (String, String) -> String,
) {
    if (!visible) return
    val estimate = QuantityDiscount.estimateLineTotals(unitPrice, quantity)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t("creator.quantity_discount.modal_title", "Quantity discounts"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = t("common.close", "Close"))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    t("creator.quantity_discount.modal_no_combine", "Not combinable with other discount codes or promotions."),
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextSecondary,
                )
                Divider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        t("creator.quantity_discount.modal_tier_header_qty", "Quantity (same variant)"),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        t("creator.quantity_discount.modal_tier_header_pct", "Discount"),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                QuantityDiscount.tierTable().forEach { tier ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(QuantityDiscount.formatTierRange(tier), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (tier.percent == 0) "—" else "${tier.percent}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (tier.percent == estimate.percent) FontWeight.Bold else FontWeight.Normal,
                            color = if (tier.percent == estimate.percent) EazColors.Orange else EazColors.TextPrimary,
                        )
                    }
                }
                Divider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t("creator.quantity_discount.modal_subtotal_list", "List price (this line)"))
                    Text("$currencyLabel %.2f".format(estimate.listSubtotal))
                }
                if (estimate.discountAmount > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(t("creator.quantity_discount.modal_you_save", "You save"))
                        Text(
                            "−$currencyLabel %.2f".format(estimate.discountAmount),
                            color = EazColors.Orange,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        t("creator.quantity_discount.modal_subtotal_after", "Estimated line total"),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "$currencyLabel %.2f".format(estimate.afterDiscount),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    t("creator.quantity_discount.modal_estimate_note", "Final price at checkout. Taxes may apply."),
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(t("common.close", "Close"))
            }
        },
    )
}
