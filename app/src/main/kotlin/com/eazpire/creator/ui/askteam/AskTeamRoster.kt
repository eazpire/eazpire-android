package com.eazpire.creator.ui.askteam

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import org.json.JSONArray
import org.json.JSONObject

data class AskTeamProductOption(
    val id: String,
    val label: String,
    val variantId: String,
    val sizes: List<String>,
)

data class AskTeamRosterRow(
    val name: String = "",
    val number: String = "",
    val productId: String = "",
    val size: String = "",
    val quantity: String = "1",
)

data class AskTeamRosterState(
    val memberCount: String = "",
    val indProduct: Boolean = false,
    val indName: Boolean = false,
    val indNumber: Boolean = false,
    val indSize: Boolean = false,
    val indQuantity: Boolean = false,
    val globalProductId: String = "",
    val globalName: String = "",
    val globalNumber: String = "",
    val globalSize: String = "",
    val globalQuantity: String = "1",
    val rows: List<AskTeamRosterRow> = emptyList(),
)

fun parseAskTeamProducts(raw: JSONArray?): List<AskTeamProductOption> {
    val arr = raw ?: JSONArray()
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val sizesArr = o.optJSONArray("sizes") ?: JSONArray()
        AskTeamProductOption(
            id = o.optString("id").ifBlank { "p${i + 1}" },
            label = listOf(o.optString("title"), o.optString("version_label")).filter { it.isNotBlank() }.joinToString(" · "),
            variantId = o.optString("shopify_variant_id"),
            sizes = (0 until sizesArr.length()).map { sizesArr.optString(it) }.filter { it.isNotBlank() },
        )
    }
}

fun rosterStateFromCampaign(camp: JSONObject): AskTeamRosterState {
    val individual = camp.optJSONObject("individual") ?: JSONObject()
    val global = camp.optJSONObject("global") ?: JSONObject()
    val rowsArr = camp.optJSONArray("roster_rows") ?: JSONArray()
    val rows = (0 until rowsArr.length()).map { i ->
        val r = rowsArr.getJSONObject(i)
        AskTeamRosterRow(
            name = r.optString("jersey_name").ifBlank { r.optString("display_name") },
            number = r.optString("jersey_number"),
            productId = r.optString("product_id"),
            size = r.optString("size"),
            quantity = r.optInt("quantity", 1).toString(),
        )
    }
    val count = camp.optInt("roster_count", rows.size)
    return AskTeamRosterState(
        memberCount = if (count > 0) count.toString() else "",
        indProduct = individual.optBoolean("product"),
        indName = individual.optBoolean("name"),
        indNumber = individual.optBoolean("number"),
        indSize = individual.optBoolean("size"),
        indQuantity = individual.optBoolean("quantity"),
        globalProductId = global.optString("product_id"),
        globalName = global.optString("jersey_name"),
        globalNumber = global.optString("jersey_number"),
        globalSize = global.optString("size"),
        globalQuantity = global.optInt("quantity", 1).toString(),
        rows = rows,
    )
}

fun AskTeamRosterState.resized(): AskTeamRosterState {
    val count = memberCount.toIntOrNull()?.coerceIn(0, 50) ?: 0
    val next = rows.toMutableList()
    while (next.size < count) next.add(AskTeamRosterRow())
    return copy(rows = next.take(count))
}

fun AskTeamRosterState.toPayload(): JSONObject {
    val ready = resized()
    val individual = JSONObject()
        .put("product", ready.indProduct)
        .put("name", ready.indName)
        .put("number", ready.indNumber)
        .put("size", ready.indSize)
        .put("quantity", ready.indQuantity)
    val global = JSONObject()
        .put("product_id", ready.globalProductId)
        .put("jersey_name", ready.globalName)
        .put("jersey_number", ready.globalNumber)
        .put("size", ready.globalSize)
        .put("quantity", ready.globalQuantity.toIntOrNull() ?: 1)
    val rows = JSONArray()
    ready.rows.forEach { row ->
        rows.put(
            JSONObject()
                .put("display_name", row.name)
                .put("jersey_name", row.name)
                .put("jersey_number", row.number)
                .put("product_id", row.productId)
                .put("size", row.size)
                .put("quantity", row.quantity.toIntOrNull() ?: 1),
        )
    }
    return JSONObject()
        .put("member_count", ready.memberCount.toIntOrNull() ?: 0)
        .put("individual", individual)
        .put("global", global)
        .put("rows", rows)
}

@Composable
fun AskTeamRosterEditor(
    products: List<AskTeamProductOption>,
    state: AskTeamRosterState,
    onState: (AskTeamRosterState) -> Unit,
    t: (String, String) -> String,
    showSave: Boolean = false,
    onSave: (() -> Unit)? = null,
) {
    val ready = state.resized()
    val sizes = products.flatMap { it.sizes }.distinct()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(t("eaz.ask_team.roster_title", "Enter team yourself"), style = MaterialTheme.typography.titleMedium)
        Text(
            t("eaz.ask_team.roster_lead", "Set how many people, then choose what is individual. Everything else is the same for everyone."),
            color = EazColors.TextSecondary,
        )
        OutlinedTextField(
            ready.memberCount,
            {
                onState(state.copy(memberCount = it.filter { ch -> ch.isDigit() }.take(2)).resized())
            },
            label = { Text(t("eaz.ask_team.member_count", "Number of people")) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(t("eaz.ask_team.individual_flags", "Individual per person"), color = EazColors.TextSecondary)
        AskTeamCheckRow(t("eaz.ask_team.ind_product", "Individual variant"), ready.indProduct) { onState(state.copy(indProduct = it)) }
        AskTeamCheckRow(t("eaz.ask_team.ind_name", "Individual name"), ready.indName) { onState(state.copy(indName = it)) }
        AskTeamCheckRow(t("eaz.ask_team.ind_number", "Individual number"), ready.indNumber) { onState(state.copy(indNumber = it)) }
        AskTeamCheckRow(t("eaz.ask_team.ind_size", "Individual size"), ready.indSize) { onState(state.copy(indSize = it)) }
        AskTeamCheckRow(t("eaz.ask_team.ind_quantity", "Individual quantity"), ready.indQuantity) { onState(state.copy(indQuantity = it)) }
        val count = ready.rows.size
        if (count > 0) {
            Text(t("eaz.ask_team.global_values", "Same for everyone"), style = MaterialTheme.typography.titleSmall)
            if (!ready.indProduct && products.size > 1) {
                ChoiceChips(products.map { it.id to it.label }, ready.globalProductId) { onState(state.copy(globalProductId = it)) }
            }
            if (!ready.indName) {
                OutlinedTextField(ready.globalName, { onState(state.copy(globalName = it)) }, label = { Text(t("eaz.ask_team.name", "Name")) }, modifier = Modifier.fillMaxWidth())
            }
            if (!ready.indNumber) {
                OutlinedTextField(ready.globalNumber, { onState(state.copy(globalNumber = it)) }, label = { Text(t("eaz.ask_team.number", "Number")) }, modifier = Modifier.fillMaxWidth())
            }
            if (!ready.indSize && sizes.isNotEmpty()) {
                ChoiceChips(sizes.map { it to it }, ready.globalSize) { onState(state.copy(globalSize = it)) }
            } else if (!ready.indSize) {
                OutlinedTextField(ready.globalSize, { onState(state.copy(globalSize = it)) }, label = { Text(t("eaz.ask_team.size", "Size")) }, modifier = Modifier.fillMaxWidth())
            }
            if (!ready.indQuantity) {
                OutlinedTextField(ready.globalQuantity, { onState(state.copy(globalQuantity = it.filter { ch -> ch.isDigit() })) }, label = { Text(t("eaz.ask_team.quantity", "Quantity")) }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (count > 0 && (ready.indProduct || ready.indName || ready.indNumber || ready.indSize || ready.indQuantity)) {
            Text(t("eaz.ask_team.per_person", "Per person"), style = MaterialTheme.typography.titleSmall)
            ready.rows.forEachIndexed { index, row ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("${index + 1}", color = EazColors.TextSecondary)
                    if (ready.indName) {
                        OutlinedTextField(row.name, { next -> onState(state.copy(rows = ready.rows.toMutableList().also { it[index] = row.copy(name = next) })) }, label = { Text(t("eaz.ask_team.name", "Name")) }, modifier = Modifier.fillMaxWidth())
                    }
                    if (ready.indNumber) {
                        OutlinedTextField(row.number, { next -> onState(state.copy(rows = ready.rows.toMutableList().also { it[index] = row.copy(number = next) })) }, label = { Text(t("eaz.ask_team.number", "Number")) }, modifier = Modifier.fillMaxWidth())
                    }
                    if (ready.indProduct && products.size > 1) {
                        ChoiceChips(products.map { it.id to it.label }, row.productId) { next ->
                            onState(state.copy(rows = ready.rows.toMutableList().also { it[index] = row.copy(productId = next) }))
                        }
                    }
                    if (ready.indSize) {
                        if (sizes.isNotEmpty()) {
                            ChoiceChips(sizes.map { it to it }, row.size) { next ->
                                onState(state.copy(rows = ready.rows.toMutableList().also { it[index] = row.copy(size = next) }))
                            }
                        } else {
                            OutlinedTextField(row.size, { next -> onState(state.copy(rows = ready.rows.toMutableList().also { it[index] = row.copy(size = next) })) }, label = { Text(t("eaz.ask_team.size", "Size")) }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (ready.indQuantity) {
                        OutlinedTextField(row.quantity, { next -> onState(state.copy(rows = ready.rows.toMutableList().also { it[index] = row.copy(quantity = next.filter { ch -> ch.isDigit() }) })) }, label = { Text(t("eaz.ask_team.quantity", "Quantity")) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (showSave && onSave != null) {
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(t("eaz.ask_team.save_roster", "Save team data"))
            }
        }
    }
}

@Composable
fun AskTeamCheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (id, label) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(id) },
                label = { Text(label) },
            )
        }
    }
}
