package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.LocalTranslationStore
import kotlinx.coroutines.launch

private data class PublishAssistPendingItem(
    val id: String,
    val side: String,
    val direction: String,
    val partnerName: String,
    val designTitle: String,
    val variantLabel: String,
    val productKey: String,
)

/**
 * Thin Publish Assist Pending sheet (IDEA-050 Android notification parity).
 * Full Offer/Request UI can follow; this unblocks accept/decline/cancel from push/Eazy.
 */
@Composable
fun PublishAssistPendingModal(
    visible: Boolean,
    ownerId: String,
    creatorApi: CreatorApi,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val store = LocalTranslationStore.current
    fun t(key: String, fallback: String) = store?.t(key, fallback) ?: fallback

    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<PublishAssistPendingItem>>(emptyList()) }
    var busyId by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (ownerId.isBlank()) {
            loading = false
            error = t("creator.publish_assist.login_required", "Please log in.")
            return
        }
        scope.launch {
            loading = true
            error = null
            try {
                val res = creatorApi.listPublishAssistPending(ownerId)
                if (!res.optBoolean("ok", false)) {
                    error = res.optString("error").ifBlank { "Error" }
                    items = emptyList()
                } else {
                    val arr = res.optJSONArray("pending")
                    val list = mutableListOf<PublishAssistPendingItem>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            list.add(
                                PublishAssistPendingItem(
                                    id = o.optString("id"),
                                    side = o.optString("side"),
                                    direction = o.optString("direction"),
                                    partnerName = o.optString("partner_display_name")
                                        .ifBlank { o.optString("partner_username") },
                                    designTitle = o.optString("design_title").ifBlank { "Design" },
                                    variantLabel = o.optString("variant_label").ifBlank {
                                        listOfNotNull(
                                            o.optString("color_slug").takeIf { it.isNotBlank() },
                                            o.optString("size").takeIf { it.isNotBlank() },
                                        ).joinToString(" / ")
                                    },
                                    productKey = o.optString("product_key"),
                                ),
                            )
                        }
                    }
                    items = list
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(visible, ownerId) {
        if (visible) refresh()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = t("creator.publish_assist.pending_title", "Publish Assist — Pending"),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = t("creator.common.close", "Close"))
                }
            }
            HorizontalDivider(color = EazColors.TopbarBorder)
            Text(
                text = t(
                    "creator.publish_assist.pending_hint",
                    "Accept starts publish for the requested design × variant.",
                ),
                modifier = Modifier.padding(16.dp),
                fontSize = 13.sp,
                color = EazColors.TextSecondary,
            )
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EazColors.Orange)
                }
                error != null -> Text(
                    error!!,
                    modifier = Modifier.padding(16.dp),
                    color = EazColors.TextSecondary,
                )
                items.isEmpty() -> Text(
                    t("creator.publish_assist.pending_empty", "No pending requests."),
                    modifier = Modifier.padding(16.dp),
                    color = EazColors.TextSecondary,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, EazColors.TopbarBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Text(
                                "${item.direction.replaceFirstChar { it.uppercase() }} · ${item.side}",
                                fontSize = 11.sp,
                                color = EazColors.TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(item.designTitle, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(
                                listOfNotNull(
                                    item.partnerName.takeIf { it.isNotBlank() }?.let { "@$it" },
                                    item.productKey.takeIf { it.isNotBlank() },
                                    item.variantLabel.takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                                fontSize = 12.sp,
                                color = EazColors.TextSecondary,
                            )
                            Spacer(Modifier.height(10.dp))
                            if (busyId == item.id) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = EazColors.Orange,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (item.side == "incoming") {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    busyId = item.id
                                                    try {
                                                        creatorApi.resolvePublishAssistRequest(ownerId, item.id, "accept")
                                                        refresh()
                                                    } catch (_: Exception) {
                                                    } finally {
                                                        busyId = null
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                                        ) {
                                            Text(t("creator.publish_assist.accept", "Accept"))
                                        }
                                        TextButton(onClick = {
                                            scope.launch {
                                                busyId = item.id
                                                try {
                                                    creatorApi.resolvePublishAssistRequest(ownerId, item.id, "decline")
                                                    refresh()
                                                } catch (_: Exception) {
                                                } finally {
                                                    busyId = null
                                                }
                                            }
                                        }) {
                                            Text(t("creator.publish_assist.decline", "Decline"))
                                        }
                                    } else {
                                        TextButton(onClick = {
                                            scope.launch {
                                                busyId = item.id
                                                try {
                                                    creatorApi.resolvePublishAssistRequest(ownerId, item.id, "cancel")
                                                    refresh()
                                                } catch (_: Exception) {
                                                } finally {
                                                    busyId = null
                                                }
                                            }
                                        }) {
                                            Text(t("creator.publish_assist.cancel", "Cancel"), color = Color(0xFFB91C1C))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}
