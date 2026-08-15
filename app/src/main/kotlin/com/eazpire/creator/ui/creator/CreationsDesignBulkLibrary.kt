package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.ui.modal.EazInsetDialog
import coil.compose.SubcomposeAsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class CreationsBulkCohort {
    Empty, ActiveSaved, InactiveSaved, InactiveUnsaved,
}

fun CreationDesign.bulkSelectionKey(): String {
    val id = id?.trim().orEmpty()
    if (id.isNotBlank()) return "id:$id"
    val jid = jobId?.trim().orEmpty()
    if (jid.isNotBlank()) return "job:$jid"
    return ""
}

fun CreationDesign.effectiveLibraryStatus(): String {
    val ls = libraryStatus.trim().lowercase()
    if (ls == "active" || ls == "inactive") return ls
    return if (!id.isNullOrBlank()) "active" else "inactive"
}

fun isBulkSelectableDesign(design: CreationDesign, activityFilter: String): Boolean {
    if (design.publishActive) return false
    val act = activityFilter.trim().lowercase()
    val id = design.id?.trim().orEmpty()
    val jid = design.jobId?.trim().orEmpty()
    return when (act) {
        "active" -> id.isNotBlank()
        "inactive" -> {
            if (id.isNotBlank()) design.effectiveLibraryStatus() == "inactive"
            else jid.isNotBlank()
        }
        else -> false
    }
}

fun resolveBulkCohort(selectedKeys: Set<String>, activityFilter: String): CreationsBulkCohort {
    if (selectedKeys.isEmpty()) return CreationsBulkCohort.Empty
    var sawJob = false
    var sawId = false
    selectedKeys.forEach { k ->
        if (k.startsWith("job:")) sawJob = true
        if (k.startsWith("id:")) sawId = true
    }
    if (activityFilter == "active") return CreationsBulkCohort.ActiveSaved
    if (sawJob) return CreationsBulkCohort.InactiveUnsaved
    if (sawId) return CreationsBulkCohort.InactiveSaved
    return CreationsBulkCohort.Empty
}

/** Per-design product counts from op=get-creations-product-badges (matches web). */
data class CreationProductBadge(
    val eligible: Int = 0,
    val published: Int = 0,
)

fun parseCreationProductBadges(resp: org.json.JSONObject): Map<String, CreationProductBadge> {
    if (!resp.optBoolean("ok", false)) return emptyMap()
    val designs = resp.optJSONObject("designs") ?: return emptyMap()
    val out = mutableMapOf<String, CreationProductBadge>()
    val keys = designs.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val row = designs.optJSONObject(key) ?: continue
        out[key] = CreationProductBadge(
            eligible = row.optInt("eligible_product_count", 0),
            published = row.optInt("published_product_count", 0),
        )
    }
    return out
}

/** Web formatDesignProductBadgeText: inactive = eligible only; active = published / eligible. */
fun formatDesignProductBadgeText(design: CreationDesign, badges: Map<String, CreationProductBadge>): String {
    val id = design.id?.trim().orEmpty()
    if (id.isBlank()) return ""
    val row = badges[id]
    val eligible = row?.eligible ?: 0
    val pub = row?.published ?: 0
    return if (design.effectiveLibraryStatus() == "inactive") {
        eligible.toString()
    } else {
        "$pub / $eligible"
    }
}

fun selectAllPoolDesigns(
    designs: List<CreationDesign>,
    activityFilter: String,
    selectedKeys: Set<String>,
): List<CreationDesign> {
    val cohort = resolveBulkCohort(selectedKeys, activityFilter)
    val selectable = designs.filter { isBulkSelectableDesign(it, activityFilter) }
    return when (cohort) {
        CreationsBulkCohort.ActiveSaved -> selectable.filter { !it.id.isNullOrBlank() }
        CreationsBulkCohort.InactiveSaved -> selectable.filter {
            !it.id.isNullOrBlank() && it.effectiveLibraryStatus() == "inactive"
        }
        CreationsBulkCohort.InactiveUnsaved -> selectable.filter { it.id.isNullOrBlank() && !it.jobId.isNullOrBlank() }
        CreationsBulkCohort.Empty -> when (activityFilter) {
            "active" -> selectable.filter { !it.id.isNullOrBlank() }
            else -> {
                val saved = selectable.filter { !it.id.isNullOrBlank() && it.effectiveLibraryStatus() == "inactive" }
                if (saved.isNotEmpty()) saved
                else selectable.filter { it.id.isNullOrBlank() && !it.jobId.isNullOrBlank() }
            }
        }
    }
}

fun designsFromSelectedKeys(designs: List<CreationDesign>, selectedKeys: Set<String>): List<CreationDesign> {
    if (selectedKeys.isEmpty()) return emptyList()
    val map = designs.associateBy { it.bulkSelectionKey() }
    return selectedKeys.mapNotNull { map[it] }
}

fun setBulkSelectedKey(
    design: CreationDesign,
    on: Boolean,
    current: Set<String>,
): Set<String> {
    val key = design.bulkSelectionKey()
    if (key.isBlank()) return current
    if (!on) return current - key
    val isSaved = !design.id.isNullOrBlank()
    val next = current.filterTo(mutableSetOf()) { k ->
        !(isSaved && k.startsWith("job:")) && !(!isSaved && k.startsWith("id:"))
    }
    next.add(key)
    return next
}

fun parseSavedCreationDesign(obj: JSONObject): CreationDesign? {
    val preview = obj.optString("preview_url", "").ifBlank { obj.optString("original_url", "") }
    if (preview.isBlank()) return null
    val meta = try {
        when (val m = obj.opt("metadata")) {
            is String -> JSONObject(m.ifBlank { "{}" })
            is JSONObject -> m
            else -> JSONObject()
        }
    } catch (_: Exception) {
        JSONObject()
    }
    val userImg = meta.optString("user_image_url", "").ifBlank { obj.optString("user_image_url", "") }
    val designPrompt = meta.optString("design_prompt", "").ifBlank { obj.optString("design_prompt", "") }
    val isUploaded = userImg.isNotBlank() && designPrompt.isBlank()
    val src = when {
        isUploaded -> "uploaded"
        else -> (obj.optString("design_source", obj.optString("source", "saved"))).lowercase()
    }
    val ct = meta.optString("content_type", "").let { c ->
        when (c) {
            "Design + Text" -> "design_text"
            "Text Only" -> "text_only"
            "Design Only" -> "design_only"
            else -> c.ifBlank { null }
        }
    }
    val ls = obj.optString("library_status", "").trim().lowercase().let {
        if (it == "active" || it == "inactive") it else if (obj.optString("id", "").isNotBlank()) "active" else "inactive"
    }
    return CreationDesign(
        id = obj.optString("id", "").takeIf { it.isNotBlank() },
        designId = obj.optString("id", "").takeIf { it.isNotBlank() },
        jobId = obj.optString("job_id", "").takeIf { it.isNotBlank() },
        imageUrl = preview,
        previewUrl = obj.optString("preview_url", "").ifBlank { preview },
        originalUrl = obj.optString("original_url", "").ifBlank { preview },
        title = obj.optString("title", obj.optString("prompt", "Design")).take(80),
        prompt = obj.optString("prompt").takeIf { it.isNotBlank() },
        designPrompt = obj.optString("design_prompt").takeIf { it.isNotBlank() },
        createdAt = (obj.opt("updated_at") as? Number)?.toLong() ?: (obj.opt("created_at") as? Number)?.toLong() ?: 0L,
        source = src,
        designSource = when (src) {
            "generated" -> "Generated"
            "uploaded" -> "Uploaded"
            else -> "Saved"
        },
        creatorName = meta.optString("creator_name", "").takeIf { it.isNotBlank() }
            ?: obj.optString("creator_name", "").takeIf { it.isNotBlank() },
        productsCount = 0,
        ratio = meta.optString("ratio", "").takeIf { it.isNotBlank() }?.lowercase(),
        designType = meta.optString("design_type", "").takeIf { it.isNotBlank() }?.lowercase(),
        contentType = ct,
        libraryStatus = ls,
        savingToLibrary = obj.optBoolean("saving_to_library", false),
        publishActive = obj.optBoolean("publish_active", false) ||
            obj.optString("publish_session_id", "").isNotBlank(),
        publishSessionId = obj.optString("publish_session_id", "").takeIf { it.isNotBlank() },
        reviewStatus = obj.optString("review_status", "").takeIf { it.isNotBlank() },
    )
}

fun parseKvSavingCreationDesign(obj: JSONObject): CreationDesign? {
    val preview = obj.optJSONObject("result")?.optString("preview_url").orEmpty()
        .ifBlank { obj.optJSONObject("result")?.optString("image_url").orEmpty() }
        .ifBlank { obj.optString("preview_url", "") }
        .ifBlank { obj.optString("image_url", "") }
    if (preview.isBlank()) return null
    return CreationDesign(
        id = null,
        designId = null,
        jobId = obj.optString("job_id", "").takeIf { it.isNotBlank() },
        imageUrl = preview,
        previewUrl = preview,
        originalUrl = preview,
        title = obj.optString("prompt", obj.optString("title", "Design")).take(80),
        prompt = obj.optString("prompt").takeIf { it.isNotBlank() },
        designPrompt = obj.optString("design_prompt").takeIf { it.isNotBlank() },
        createdAt = (obj.opt("finished") as? Number)?.toLong()
            ?: (obj.opt("started") as? Number)?.toLong() ?: System.currentTimeMillis(),
        source = "generated",
        designSource = "Generated",
        creatorName = obj.optString("creator_name", "").takeIf { it.isNotBlank() },
        productsCount = 0,
        libraryStatus = "inactive",
        savingToLibrary = true,
        reviewStatus = null,
    )
}

fun parseGeneratedCreationDesign(obj: JSONObject, savingToLibrary: Boolean = false): CreationDesign? {
    val preview = obj.optString("preview_url", "")
        .ifBlank { obj.optJSONObject("result")?.optString("preview_url").orEmpty() }
        .ifBlank { obj.optJSONObject("result")?.optString("image_url").orEmpty() }
        .ifBlank { obj.optString("image_url", "") }
    if (preview.isBlank()) return null
    return CreationDesign(
        id = obj.optString("design_id", "").takeIf { it.isNotBlank() }
            ?: obj.optString("id", "").takeIf { it.isNotBlank() },
        designId = obj.optString("design_id", "").takeIf { it.isNotBlank() }
            ?: obj.optString("id", "").takeIf { it.isNotBlank() },
        jobId = obj.optString("job_id", "").takeIf { it.isNotBlank() },
        imageUrl = preview,
        previewUrl = preview,
        originalUrl = preview,
        title = obj.optString("title", obj.optString("prompt", "Design")).take(80),
        prompt = obj.optString("prompt").takeIf { it.isNotBlank() },
        designPrompt = obj.optString("design_prompt").takeIf { it.isNotBlank() },
        createdAt = (obj.opt("started") as? Number)?.toLong()
            ?: (obj.opt("updated_at") as? Number)?.toLong() ?: 0L,
        source = "generated",
        designSource = "Generated",
        creatorName = obj.optString("creator_name", "").takeIf { it.isNotBlank() },
        productsCount = 0,
        libraryStatus = "inactive",
        savingToLibrary = savingToLibrary,
        reviewStatus = obj.optString("review_status", "").takeIf { it.isNotBlank() },
    )
}

data class ActivateCatalogProduct(
    val productKey: String,
    val title: String,
    val previewImageUrl: String?,
    val mockUrls: List<String>,
    val unlocked: Boolean = true,
)

data class ActivateCatalogBundle(
    val products: List<ActivateCatalogProduct> = emptyList(),
    val eligibleKeys: List<String> = emptyList(),
    val lockedKeys: List<String> = emptyList(),
    val checked: Map<String, Boolean> = emptyMap(),
    val metaExcludedSnapshot: List<String> = emptyList(),
    val publishedKeys: Set<String> = emptySet(),
    val loadError: Boolean = false,
)

fun parsePublishExcludedFromMetadata(metadata: Any?): List<String> {
    val meta = try {
        when (metadata) {
            is String -> JSONObject(metadata.ifBlank { "{}" })
            is JSONObject -> metadata
            else -> JSONObject()
        }
    } catch (_: Exception) {
        JSONObject()
    }
    val arr = meta.optJSONArray("publish_excluded_product_keys") ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            arr.optString(i)?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }
}

fun normalizeActivateCatalogMockUrls(product: ActivateCatalogProduct): String? {
    val urls = product.mockUrls.map { it.trim() }.filter { it.isNotBlank() }
    val creator = urls.filter { it.contains("/mockup/", ignoreCase = true) }
    val picked = when {
        creator.isNotEmpty() -> creator
        else -> urls.filter { !it.contains("images.printify.com", ignoreCase = true) }
    }
    return picked.firstOrNull() ?: product.previewImageUrl?.trim()?.takeIf { it.isNotBlank() }
}

fun computeActivateExcludedKeys(
    eligibleKeys: List<String>,
    checked: Map<String, Boolean>,
    metaExcludedSnapshot: List<String>,
    lockedKeys: List<String> = emptyList(),
): List<String> {
    val eligible = eligibleKeys.toSet()
    val out = linkedSetOf<String>()
    metaExcludedSnapshot.forEach { pk ->
        val key = pk.trim()
        if (key.isNotBlank() && !eligible.contains(key)) out.add(key)
    }
    eligibleKeys.forEach { key ->
        if (checked[key] != true) out.add(key)
    }
    lockedKeys.forEach { pk ->
        val key = pk.trim()
        if (key.isNotBlank()) out.add(key)
    }
    return out.sorted()
}

suspend fun loadActivateCatalogBundle(
    api: CreatorApi,
    ownerId: String,
    shop: String?,
    designId: String,
    metadataExcluded: List<String>,
    region: String = "EU",
): ActivateCatalogBundle = withContext(Dispatchers.IO) {
    try {
        val catResp = api.getCatalogProducts(region = region, designId = designId, ownerId = ownerId)
        val productsArr = catResp.optJSONArray("products") ?: JSONArray()
        val allProducts = buildList {
            for (i in 0 until productsArr.length()) {
                val o = productsArr.optJSONObject(i) ?: continue
                val pk = o.optString("product_key", "").trim()
                if (pk.isBlank()) continue
                val mockUrls = buildList {
                    val mocks = o.optJSONArray("mock_urls")
                    if (mocks != null) {
                        for (j in 0 until mocks.length()) {
                            mocks.optString(j)?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                }
                add(
                    ActivateCatalogProduct(
                        productKey = pk,
                        title = o.optString("title", pk).ifBlank { pk },
                        previewImageUrl = o.optString("preview_image_url", "").trim().takeIf { it.isNotBlank() },
                        mockUrls = mockUrls,
                        unlocked = o.optBoolean("unlocked", true),
                    )
                )
            }
        }
        val products = allProducts.filter { it.unlocked }
        val lockedKeys = allProducts.filter { !it.unlocked }.map { it.productKey }
        val eligibleKeys = products.map { it.productKey }
        val metaSnap = metadataExcluded.distinct()
        val checked = eligibleKeys.associateWith { pk -> !metaSnap.contains(pk) }
        val pubResp = api.getDesignPublishedRows(ownerId, designId, shop)
        val pubRows = pubResp.optJSONArray("rows") ?: JSONArray()
        val publishedKeys = buildSet {
            for (i in 0 until pubRows.length()) {
                pubRows.optJSONObject(i)?.optString("product_key", "")?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(it) }
            }
        }
        ActivateCatalogBundle(
            products = products,
            eligibleKeys = eligibleKeys,
            lockedKeys = lockedKeys,
            checked = checked,
            metaExcludedSnapshot = metaSnap,
            publishedKeys = publishedKeys,
            loadError = false,
        )
    } catch (_: Exception) {
        ActivateCatalogBundle(loadError = true)
    }
}

object CreationsDesignLibraryActions {
    suspend fun deactivateDesign(api: CreatorApi, ownerId: String, shop: String, designId: String): Boolean {
        val rowsResp = api.getDesignPublishedRows(ownerId, designId, shop)
        val rows = rowsResp.optJSONArray("rows") ?: JSONArray()
        val pubIds = buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val pid = row.opt("id")
                val n = when (pid) {
                    is Number -> pid.toLong()
                    is String -> pid.toLongOrNull()
                    else -> null
                }
                if (n != null) add(n)
            }
        }
        if (pubIds.isNotEmpty()) {
            val batch = api.batchUnpublishPublished(ownerId, pubIds, shop)
            if (!batch.optBoolean("ok", false) && batch.optJSONArray("enqueued_ids") == null) return false
        }
        val upd = api.updateDesign(
            JSONObject()
                .put("design_id", designId)
                .put("library_status", "inactive")
        )
        return upd.optBoolean("ok", false)
    }

    suspend fun activateDesign(
        api: CreatorApi,
        designId: String,
        creatorName: String,
        visibility: String,
        activateWithoutCreator: Boolean,
        publishExcludedProductKeys: List<String>? = null,
    ): Boolean {
        val body = JSONObject()
            .put("design_id", designId)
            .put("library_status", "active")
            .put("visibility", if (visibility == "private") "private" else "public")
        if (activateWithoutCreator) {
            body.put("activate_without_creator_name", true)
            body.put("creator_name", "")
        } else {
            body.put("creator_name", creatorName.trim())
        }
        if (!publishExcludedProductKeys.isNullOrEmpty()) {
            body.put(
                "metadata",
                JSONObject().put(
                    "publish_excluded_product_keys",
                    JSONArray().apply { publishExcludedProductKeys.forEach { put(it) } },
                ),
            )
        }
        return api.updateDesign(body).optBoolean("ok", false)
    }

    suspend fun deleteSavedDesign(api: CreatorApi, ownerId: String, designId: String): Boolean =
        api.deleteDesign(ownerId, designId).optBoolean("ok", false)

    suspend fun deleteGeneratedJob(api: CreatorApi, ownerId: String, jobId: String): Boolean =
        api.deleteJob(ownerId, jobId).optBoolean("ok", false)

    suspend fun saveGeneratedDesign(
        api: CreatorApi,
        ownerId: String,
        design: CreationDesign,
        creatorName: String,
        visibility: String,
    ): Boolean {
        val jid = design.jobId?.trim().orEmpty()
        if (jid.isBlank()) return false
        val url = design.previewUrl.ifBlank { design.originalUrl.ifBlank { design.imageUrl } }
        val body = JSONObject()
            .put("job_id", jid)
            .put("owner_id", ownerId)
            .put("prompt", design.prompt ?: design.title)
            .put("image_url", url)
            .put("design_prompt", design.designPrompt ?: design.prompt ?: "")
            .put("visibility", if (visibility == "private") "private" else "public")
        if (creatorName.isNotBlank()) body.put("creator_name", creatorName.trim())
        return api.saveDesign(body).optBoolean("ok", false)
    }

    suspend fun loadCreatorNames(api: CreatorApi, ownerId: String): List<String> {
        val settings = runCatching { api.getSettings(ownerId) }.getOrNull() ?: return emptyList()
        val settingsObj = settings.optJSONObject("settings") ?: settings
        val arr = settingsObj.optJSONArray("creator_names") ?: JSONArray()
        val out = buildList {
            for (i in 0 until arr.length()) {
                arr.optString(i)?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }.toMutableList()
        val primary = settingsObj.optString("creator_name").takeIf { it.isNotBlank() }
        if (primary != null && out.none { it.equals(primary, ignoreCase = true) }) {
            out.add(0, primary)
        }
        return out.distinct()
    }
}

@Composable
fun CreationsDesignBulkDock(
    selectedCount: Int,
    cohort: CreationsBulkCohort,
    translationStore: TranslationStore,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selectedCount <= 0) return
    val countTpl = translationStore.t("creator.creations.bulk_selected_count_tpl", "%n% selected")
    val countLabel = countTpl.replace("%n%", selectedCount.toString())
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF0F172A).copy(alpha = 0.96f),
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(countLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSelectAll) {
                        Text(
                            translationStore.t("creator.creations.bulk_select_all", "Select all"),
                            fontSize = 12.sp,
                        )
                    }
                    TextButton(onClick = onDeselectAll) {
                        Text(
                            translationStore.t("creator.creations.bulk_deselect_all", "Deselect all"),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (cohort == CreationsBulkCohort.InactiveSaved) {
                    BulkDockActionBtn(
                        label = translationStore.t("creator.creations.bulk_activate", "Activate"),
                        onClick = onActivate,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (cohort == CreationsBulkCohort.ActiveSaved) {
                    BulkDockActionBtn(
                        label = translationStore.t("creator.creations.bulk_deactivate", "Deactivate"),
                        onClick = onDeactivate,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (cohort == CreationsBulkCohort.InactiveUnsaved) {
                    BulkDockActionBtn(
                        label = if (selectedCount > 1) {
                            translationStore.t("creator.creations.bulk_save_all", "Save all")
                        } else {
                            translationStore.t("creator.creations.bulk_save", "Save")
                        },
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (cohort != CreationsBulkCohort.Empty) {
                    BulkDockActionBtn(
                        label = translationStore.t("creator.creations.bulk_delete", "Delete"),
                        onClick = onDelete,
                        danger = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BulkDockActionBtn(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) Color(0xFF7F1D1D) else Color(0xFF92400E),
            contentColor = Color.White,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun ActivateCatalogProductCard(
    product: ActivateCatalogProduct,
    checked: Boolean,
    published: Boolean,
    translationStore: TranslationStore,
    onCheckedChange: (Boolean) -> Unit,
) {
    val imageUrl = normalizeActivateCatalogMockUrls(product)
    Column(
        modifier = Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF353D4C))
            .clickable { onCheckedChange(!checked) }
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E293B)),
        ) {
            if (!imageUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(28.dp),
                colors = CheckboxDefaults.colors(checkedColor = EazColors.Orange),
            )
            if (published) {
                Text(
                    translationStore.t("creator.design_products.badge_online", "Online"),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color(0xFF166534), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            } else if (checked) {
                Text(
                    translationStore.t("creator.design_products.badge_queue", "Queue"),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color(0xFF92400E), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            product.title,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 10.sp,
            maxLines = 2,
            lineHeight = 12.sp,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreationsActivateDesignDialog(
    targets: List<CreationDesign>,
    creatorNames: List<String>,
    translationStore: TranslationStore,
    api: CreatorApi,
    ownerId: String,
    shop: String?,
    catalogRegion: String = "EU",
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        creatorName: String,
        visibilityPublic: Boolean,
        activateWithoutCreator: Boolean,
        publishExcludedProductKeys: List<String>?,
    ) -> Unit,
) {
    if (targets.isEmpty()) return
    val singleTarget = targets.singleOrNull()
    var creatorName by remember(targets, creatorNames) {
        mutableStateOf(creatorNames.firstOrNull().orEmpty())
    }
    var visibilityPublic by remember { mutableStateOf(true) }
    var activateWithout by remember { mutableStateOf(creatorNames.isEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    var catalogLoading by remember(targets) { mutableStateOf(singleTarget != null) }
    var catalogBundle by remember(targets) { mutableStateOf<ActivateCatalogBundle?>(null) }
    val checkedMap = remember(targets) { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(singleTarget?.id, ownerId) {
        val design = singleTarget ?: return@LaunchedEffect
        val designId = design.id?.trim().orEmpty()
        if (designId.isBlank()) {
            catalogLoading = false
            return@LaunchedEffect
        }
        catalogLoading = true
        val designResp = runCatching { api.getDesign(ownerId, designId) }.getOrNull()
        val metaObj = designResp?.optJSONObject("design")?.opt("metadata")
            ?: designResp?.opt("metadata")
        val excluded = parsePublishExcludedFromMetadata(metaObj)
        val bundle = loadActivateCatalogBundle(api, ownerId, shop, designId, excluded, catalogRegion)
        catalogBundle = bundle
        checkedMap.clear()
        bundle.checked.forEach { (k, v) -> checkedMap[k] = v }
        catalogLoading = false
    }

    EazInsetDialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1E293B),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (targets.size > 1) {
                        translationStore.t("creator.creations.bulk_activate_title", "Activate designs")
                    } else {
                        translationStore.t("creator.creations.library_activate_title", "Activate design")
                    },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                if (creatorNames.isEmpty()) {
                    Text(
                        translationStore.t(
                            "creator.creations.library_activate_no_creator_body",
                            "No creator name configured. Activate without publishing under a creator name?"
                        ),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                } else if (creatorNames.size == 1) {
                    Text(
                        translationStore.t(
                            "creator.creations.library_activate_scope_named",
                            "Publish as %name%"
                        ).replace("%name%", creatorNames.first()),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                } else {
                    Text(
                        translationStore.t(
                            "creator.creations.library_activate_choose_intro",
                            "Choose creator name"
                        ),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = creatorName,
                            onValueChange = {},
                            readOnly = true,
                            label = {
                                Text(translationStore.t("creator.creations.library_activate_creator_label", "Creator"))
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            creatorNames.forEach { name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        creatorName = name
                                        expanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                if (singleTarget != null) {
                    Text(
                        translationStore.t("creator.design_products.eligible_hint", "Eligible for auto-publish"),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                    )
                    if (catalogLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = EazColors.Orange,
                            )
                        }
                    } else if (catalogBundle?.loadError == true) {
                        Text(
                            translationStore.t("creator.design_products.load_error", "Could not load products."),
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                        )
                    } else {
                        val bundle = catalogBundle
                        val products = bundle?.products.orEmpty()
                        if (products.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        products.forEach { checkedMap[it.productKey] = true }
                                    },
                                    enabled = !busy,
                                ) {
                                    Text(
                                        translationStore.t("creator.design_products.select_all", "Select all"),
                                        color = EazColors.Orange,
                                        fontSize = 12.sp,
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        products.forEach { checkedMap[it.productKey] = false }
                                    },
                                    enabled = !busy,
                                ) {
                                    Text(
                                        translationStore.t("creator.design_products.deselect_all", "Deselect all"),
                                        color = EazColors.Orange,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                products.forEach { product ->
                                    ActivateCatalogProductCard(
                                        product = product,
                                        checked = checkedMap[product.productKey] == true,
                                        published = bundle?.publishedKeys?.contains(product.productKey) == true,
                                        translationStore = translationStore,
                                        onCheckedChange = { on ->
                                            checkedMap[product.productKey] = on
                                        },
                                    )
                                }
                            }
                        } else {
                            Text(
                                translationStore.t("creator.design_products.empty", "No products available for your region."),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (visibilityPublic) {
                            translationStore.t("creator.common.visibility_public", "Public")
                        } else {
                            translationStore.t("creator.common.visibility_private", "Private")
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                    Switch(
                        checked = visibilityPublic,
                        onCheckedChange = { visibilityPublic = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = EazColors.Orange),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, enabled = !busy) {
                        Text(
                            translationStore.t("creator.common.cancel", "Cancel"),
                            color = Color.White.copy(alpha = 0.8f),
                        )
                    }
                    Button(
                        onClick = {
                            val excluded = catalogBundle?.let { b ->
                                computeActivateExcludedKeys(
                                    b.eligibleKeys,
                                    checkedMap.toMap(),
                                    b.metaExcludedSnapshot,
                                    b.lockedKeys,
                                )
                            }
                            onConfirm(
                                creatorName,
                                visibilityPublic,
                                activateWithout || creatorNames.isEmpty(),
                                excluded,
                            )
                        },
                        enabled = !busy && (activateWithout || creatorNames.isEmpty() || creatorName.isNotBlank()),
                        colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            Text(translationStore.t("creator.creations.library_confirm_activate", "Activate"))
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
fun CreationsConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    busy: Boolean,
    danger: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color(0xFF1E293B),
        titleContentColor = Color.White,
        textContentColor = Color.White.copy(alpha = 0.85f),
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (danger) Color(0xFF7F1D1D) else Color(0xFF92400E),
                ),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(confirmLabel)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun CreationDesignGridCard(
    design: CreationDesign,
    productBadgeText: String,
    bulkSelectable: Boolean,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onLibraryAction: (() -> Unit)?,
    onClick: () -> Unit,
    activateLabel: String,
    deactivateLabel: String,
    modifier: Modifier = Modifier,
    shimmer: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF353D4C)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (design.publishActive) Modifier
                    else Modifier.clickable(onClick = onClick)
                ),
        ) {
            SubcomposeAsyncImage(
                model = design.imageUrl,
                contentDescription = design.title,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                loading = { shimmer() },
            )
        }
        if (design.id?.isNotBlank() == true && productBadgeText.isNotBlank() && !design.publishActive) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(
                    productBadgeText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (bulkSelectable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp)),
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onSelectedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = EazColors.Orange,
                        uncheckedColor = Color.White.copy(alpha = 0.7f),
                        checkmarkColor = Color.White,
                    ),
                )
            }
        }
        if (onLibraryAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF92400E).copy(alpha = 0.88f))
                    .clickable { onLibraryAction() }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (design.effectiveLibraryStatus() == "inactive") activateLabel else deactivateLabel,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        design.reviewStatus?.takeIf { it.isNotBlank() && it != "approved" }?.let { rs ->
            val label = when (rs) {
                "rejected" -> "Needs changes"
                else -> "In review"
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color(0xFF0F172A).copy(alpha = 0.82f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(label, color = if (rs == "rejected") Color(0xFFFCA5A5) else Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (design.savingToLibrary || design.publishActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF0F172A).copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = EazColors.Orange,
                        strokeWidth = 3.dp,
                    )
                    Text(
                        text = if (design.publishActive) "Publishing…" else "Saving…",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

suspend fun runBulkSaveWithThrottle(
    designs: List<CreationDesign>,
    ownerId: String,
    api: CreatorApi,
    creatorName: String,
    visibilityPublic: Boolean,
): Int {
    var ok = 0
    designs.forEachIndexed { idx, design ->
        if (CreationsDesignLibraryActions.saveGeneratedDesign(
                api, ownerId, design, creatorName, if (visibilityPublic) "public" else "private"
            )
        ) ok++
        if (idx < designs.lastIndex) delay(1300)
    }
    return ok
}
