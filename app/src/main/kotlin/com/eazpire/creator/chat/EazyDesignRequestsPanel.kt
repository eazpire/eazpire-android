package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.ui.designrequest.DesignActionMenu
import com.eazpire.creator.ui.designrequest.DesignRequestOpen
import com.eazpire.creator.ui.designrequest.DesignRequestUiTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class DesignRequestRow(
    val id: String,
    val status: String,
    val queryText: String,
    val uploadUrl: String?,
    val drafts: List<DesignRequestDraft>,
    val children: List<DesignRequestRow>,
)

private data class DesignRequestDraft(
    val id: String,
    val requestId: String,
    val previewUrl: String?,
    val status: String,
)

private fun parseDraft(obj: JSONObject, requestId: String) = DesignRequestDraft(
    id = obj.optString("id"),
    requestId = requestId,
    previewUrl = obj.optString("preview_url").takeIf { it.isNotBlank() },
    status = obj.optString("status"),
)

private fun parseRequest(obj: JSONObject): DesignRequestRow {
    val id = obj.optString("id")
    val draftsArr = obj.optJSONArray("drafts")
    val drafts = mutableListOf<DesignRequestDraft>()
    if (draftsArr != null) {
        for (i in 0 until draftsArr.length()) {
            drafts.add(parseDraft(draftsArr.optJSONObject(i) ?: JSONObject(), id))
        }
    }
    val kidsArr = obj.optJSONArray("children")
    val kids = mutableListOf<DesignRequestRow>()
    if (kidsArr != null) {
        for (i in 0 until kidsArr.length()) {
            kids.add(parseRequest(kidsArr.optJSONObject(i) ?: JSONObject()))
        }
    }
    return DesignRequestRow(
        id = id,
        status = obj.optString("status"),
        queryText = obj.optString("query_text"),
        uploadUrl = obj.optString("upload_url").takeIf { it.isNotBlank() },
        drafts = drafts,
        children = kids,
    )
}

@Composable
fun EazyDesignRequestsPanel(
    api: CreatorApi,
    ownerId: String?,
    t: (String, String) -> String,
    modifier: Modifier = Modifier,
    onUseOnProduct: (previewUrl: String, designId: String?) -> Unit = { _, _ -> },
) {
    val palette = LocalEazyModalPalette.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<DesignRequestRow>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuDraft by remember { mutableStateOf<DesignRequestDraft?>(null) }
    var deleteTarget by remember { mutableStateOf<Pair<String, String?>?>(null) }

    suspend fun reload() {
        if (ownerId.isNullOrBlank()) {
            rows = emptyList()
            error = t("eaz.design_request.login", "Sign in to see your design requests.")
            return
        }
        loading = true
        error = null
        val res = withContext(Dispatchers.IO) { api.listDesignRequests(ownerId) }
        loading = false
        if (!res.optBoolean("ok", false)) {
            error = t("eazy_chat.qi_error", "Could not load requests.")
            return
        }
        val arr = res.optJSONArray("requests")
        val out = mutableListOf<DesignRequestRow>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                out.add(parseRequest(arr.optJSONObject(i) ?: JSONObject()))
            }
        }
        rows = out
        if (out.isEmpty()) error = t("eaz.design_request.empty", "No design requests yet.")
    }

    LaunchedEffect(ownerId) { reload() }
    LaunchedEffect(ownerId, rows.any { it.status == "generating" || it.children.any { c -> c.status == "generating" } }) {
        if (ownerId.isNullOrBlank()) return@LaunchedEffect
        if (rows.any { it.status == "generating" || it.children.any { c -> c.status == "generating" } }) {
            delay(4000)
            reload()
        }
    }

    fun statusLabel(st: String) = when (st) {
        "received" -> t("eaz.design_request.status_received", "Received")
        "generating" -> t("eaz.design_request.status_generating", "Creating")
        "ready" -> t("eaz.design_request.status_ready", "Ready")
        "failed" -> t("eaz.design_request.status_failed", "Failed")
        else -> st
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(t("eazy_chat.design_requests_title", "Design Requests"), color = palette.text, fontSize = 16.sp)
            Button(
                onClick = { DesignRequestUiTrigger.openSheet(DesignRequestOpen(source = "eazy")) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
            ) {
                Text(t("eazy_chat.design_requests_new", "New request"))
            }
        }
        when {
            loading && rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            rows.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: t("eaz.design_request.empty", "No design requests yet."), color = palette.muted)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rows, key = { it.id }) { req ->
                    DesignRequestCard(
                        req = req,
                        statusLabel = { statusLabel(it) },
                        t = t,
                        onFollowUp = {
                            DesignRequestUiTrigger.openSheet(
                                DesignRequestOpen(parentId = req.id, source = "followup")
                            )
                        },
                        onDeleteRequest = { deleteTarget = req.id to null },
                        onDraftMenu = { menuDraft = it },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    DesignActionMenu(
        visible = menuDraft != null,
        imageUrl = menuDraft?.previewUrl.orEmpty(),
        designId = menuDraft?.id,
        canOpenStudio = false,
        canDelete = true,
        onDismiss = { menuDraft = null },
        onDelete = {
            val d = menuDraft ?: return@DesignActionMenu
            deleteTarget = d.requestId to d.id
        },
        onUseOnProduct = { url, id -> onUseOnProduct(url, id) },
    )

    val del = deleteTarget
    if (del != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(t("eaz.design_request.delete", "Delete")) },
            text = {
                Text(
                    if (del.second == null) t("eaz.design_request.delete_thread", "Delete this request and its drafts?")
                    else t("eaz.design_request.delete_confirm", "Delete this design?")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleteTarget
                    deleteTarget = null
                    if (target == null || ownerId.isNullOrBlank()) return@TextButton
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            api.deleteDesignRequest(ownerId, target.first, target.second)
                        }
                        reload()
                    }
                }) { Text(t("eaz.design_request.delete", "Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(t("eaz.reference_search.cancel", "Cancel"))
                }
            }
        )
    }
}

@Composable
private fun DesignRequestCard(
    req: DesignRequestRow,
    statusLabel: (String) -> String,
    t: (String, String) -> String,
    onFollowUp: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDraftMenu: (DesignRequestDraft) -> Unit,
) {
    val palette = LocalEazyModalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.header)
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(req.queryText.ifBlank { "—" }, color = palette.text)
                Text(statusLabel(req.status), color = palette.muted, fontSize = 12.sp)
            }
            TextButton(onClick = onDeleteRequest) {
                Text(t("eaz.design_request.delete", "Delete"), color = palette.muted)
            }
        }
        if (!req.uploadUrl.isNullOrBlank()) {
            AsyncImage(
                model = req.uploadUrl,
                contentDescription = null,
                modifier = Modifier.padding(top = 8.dp).height(48.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
        DraftRow(req.drafts, onDraftMenu)
        TextButton(onClick = onFollowUp) {
            Text(t("eaz.design_request.followup", "Follow up"))
        }
        req.children.forEach { child ->
            Column(
                modifier = Modifier
                    .padding(start = 10.dp, top = 8.dp)
                    .fillMaxWidth()
            ) {
                Text("${statusLabel(child.status)} · ${child.queryText}", color = palette.muted, fontSize = 12.sp)
                DraftRow(child.drafts, onDraftMenu)
            }
        }
    }
}

@Composable
private fun DraftRow(drafts: List<DesignRequestDraft>, onMenu: (DesignRequestDraft) -> Unit) {
    if (drafts.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        drafts.take(4).forEach { d ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x22000000))
                    .clickable { onMenu(d) }
            ) {
                if (!d.previewUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = d.previewUrl,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Text("⋯", modifier = Modifier.align(Alignment.TopEnd).padding(4.dp), color = Color.White)
            }
        }
    }
}
