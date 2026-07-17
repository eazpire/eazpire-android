package com.eazpire.creator.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.formatCountLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CreatorFollowPrefs(
    val focusProducts: Boolean = true,
    val notifyEmail: Boolean = true,
    val notifyEazy: Boolean = true,
    val notifyPush: Boolean = true,
)

fun parseCreatorFollowPrefs(obj: JSONObject?): CreatorFollowPrefs {
    if (obj == null) return CreatorFollowPrefs()
    return CreatorFollowPrefs(
        focusProducts = obj.optBoolean("focus_products", true),
        notifyEmail = obj.optBoolean("notify_email", true),
        notifyEazy = obj.optBoolean("notify_eazy", true),
        notifyPush = obj.optBoolean("notify_push", true),
    )
}

fun formatFollowersLabel(t: (String, String) -> String, count: Int): String =
    formatCountLabel(t("eaz.creator_follow.followers_count", "{{ count }} followers"), count)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorFollowSheet(
    visible: Boolean,
    creatorName: String,
    creatorOwnerId: String?,
    customerId: String,
    following: Boolean,
    initialPrefs: CreatorFollowPrefs,
    followerCount: Int,
    api: CreatorApi,
    t: (String, String) -> String,
    onDismiss: () -> Unit,
    onChanged: (following: Boolean, prefs: CreatorFollowPrefs?, followerCount: Int) -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var focusProducts by remember(creatorName, following) { mutableStateOf(initialPrefs.focusProducts) }
    var notifyEmail by remember(creatorName, following) { mutableStateOf(initialPrefs.notifyEmail) }
    var notifyEazy by remember(creatorName, following) { mutableStateOf(initialPrefs.notifyEazy) }
    var notifyPush by remember(creatorName, following) { mutableStateOf(initialPrefs.notifyPush) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun currentPrefs() = CreatorFollowPrefs(focusProducts, notifyEmail, notifyEazy, notifyPush)

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = t(
                    if (following) "eaz.creator_follow.modal_title_following" else "eaz.creator_follow.modal_title_follow",
                    if (following) "Following settings" else "Follow creator",
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = EazColors.TextPrimary,
            )
            Text(
                text = creatorName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = EazColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            FollowSwitchRow(
                label = t("eaz.creator_follow.focus_products", "Focus Products"),
                hint = t(
                    "eaz.creator_follow.focus_products_hint",
                    "Products and designs from this creator appear a bit higher in shop listings.",
                ),
                checked = focusProducts,
                enabled = !busy,
                onCheckedChange = { focusProducts = it },
            )

            Text(
                text = t("eaz.creator_follow.notifications", "Notifications"),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = EazColors.TextPrimary,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
            )
            Text(
                text = t(
                    "eaz.creator_follow.notify_hint",
                    "Get notified about new products or discount promotions from this creator.",
                ),
                fontSize = 12.sp,
                color = EazColors.TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FollowSwitchRow(
                label = t("eaz.creator_follow.notify_email", "E-Mail"),
                checked = notifyEmail,
                enabled = !busy,
                onCheckedChange = { notifyEmail = it },
            )
            FollowSwitchRow(
                label = t("eaz.creator_follow.notify_eazy", "eazy Notifications"),
                hint = t("eaz.creator_follow.notify_eazy_hint", "Under eazy system notifications"),
                checked = notifyEazy,
                enabled = !busy,
                onCheckedChange = { notifyEazy = it },
            )
            FollowSwitchRow(
                label = t("eaz.creator_follow.notify_push", "Push Notifications"),
                checked = notifyPush,
                enabled = !busy,
                onCheckedChange = { notifyPush = it },
            )

            if (!error.isNullOrBlank()) {
                Text(
                    text = error ?: "",
                    color = Color(0xFFB42318),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (following) {
                Button(
                    onClick = {
                        busy = true
                        error = null
                        val prefs = currentPrefs()
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching {
                                    api.updateCreatorFollow(
                                        customerId = customerId,
                                        creatorName = creatorName,
                                        focusProducts = prefs.focusProducts,
                                        notifyEmail = prefs.notifyEmail,
                                        notifyEazy = prefs.notifyEazy,
                                        notifyPush = prefs.notifyPush,
                                    ).optBoolean("ok", false)
                                }.getOrDefault(false)
                            }
                            busy = false
                            if (ok) {
                                onChanged(true, prefs, followerCount)
                                onDismiss()
                            } else {
                                error = t("eaz.creator_follow.error", "Something went wrong. Please try again.")
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(t("eaz.creator_follow.save", "Save"), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                runCatching {
                                    api.unfollowCreator(customerId, creatorName)
                                }.getOrNull()
                            }
                            busy = false
                            if (res?.optBoolean("ok", false) == true) {
                                onChanged(false, null, res.optInt("follower_count", followerCount))
                                onDismiss()
                            } else {
                                error = t("eaz.creator_follow.error", "Something went wrong. Please try again.")
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB42318)),
                ) {
                    Text(t("eaz.creator_follow.unfollow", "Unfollow"), fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        busy = true
                        error = null
                        val prefs = currentPrefs()
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                runCatching {
                                    api.followCreator(
                                        customerId = customerId,
                                        creatorName = creatorName,
                                        creatorOwnerId = creatorOwnerId,
                                        focusProducts = prefs.focusProducts,
                                        notifyEmail = prefs.notifyEmail,
                                        notifyEazy = prefs.notifyEazy,
                                        notifyPush = prefs.notifyPush,
                                    )
                                }.getOrNull()
                            }
                            busy = false
                            if (res?.optBoolean("ok", false) == true) {
                                val followObj = res.optJSONObject("follow")
                                onChanged(
                                    true,
                                    parseCreatorFollowPrefs(followObj),
                                    res.optInt("follower_count", followerCount + 1),
                                )
                                onDismiss()
                            } else {
                                error = t("eaz.creator_follow.error", "Something went wrong. Please try again.")
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(t("eaz.creator_follow.follow", "Follow"), fontWeight = FontWeight.Bold)
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(t("eaz.creator_follow.cancel", "Cancel"))
                }
            }
        }
    }
}

@Composable
private fun FollowSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold, color = EazColors.TextPrimary, fontSize = 14.sp)
            if (!hint.isNullOrBlank()) {
                Text(hint, color = EazColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = EazColors.Orange,
                checkedThumbColor = Color.White,
            ),
        )
    }
}
