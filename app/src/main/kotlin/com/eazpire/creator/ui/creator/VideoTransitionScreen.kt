package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.modal.EazFullScreenDialog

/**
 * Video Transition fullscreen shell (IDEA-067 UX redesign) — PARTIAL Android parity.
 * Page sidebar + global footer placeholders; encode/STT/templates follow on Web first.
 */
@Composable
fun VideoTransitionScreen(
    visible: Boolean,
    onDismiss: () -> Unit,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)

    var sidebarCollapsed by remember { mutableStateOf(false) }
    var activeNav by remember { mutableStateOf("project") }
    val navItems = listOf(
        "project" to t("creator.video_transition.nav_project_details", "Project Details"),
        "templates" to t("creator.video_transition.nav_templates", "Templates"),
        "video_settings" to t("creator.video_transition.nav_video_settings", "Video Settings"),
        "global_settings" to t("creator.video_transition.nav_global_settings", "Global Settings"),
        "clips" to t("creator.video_transition.nav_clips", "Clips"),
        "voiceover" to t("creator.video_transition.nav_voiceover", "Voiceover"),
        "results" to t("creator.video_transition.nav_transition_videos", "Transition Videos"),
        "assets" to t("creator.video_transition.nav_assets", "Assets"),
    )

    EazFullScreenDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1A1028), Color(0xFF0B0714))
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("creator.video_transition.title", "Video Transition"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = t("creator.video_transition.ready", "Ready"),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = t("creator.video_transition.close", "Close"),
                        tint = Color.White,
                    )
                }
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box {
                    if (!sidebarCollapsed) {
                        Column(
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight()
                                .background(Color.Black.copy(alpha = 0.28f))
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            navItems.forEach { (id, label) ->
                                val active = activeNav == id
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (active) EazColors.Orange.copy(alpha = 0.22f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (active) EazColors.Orange.copy(alpha = 0.45f)
                                            else Color.Transparent,
                                            shape = RoundedCornerShape(10.dp),
                                        )
                                        .clickable { activeNav = id }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.width(28.dp).fillMaxHeight())
                    }
                    IconButton(
                        onClick = { sidebarCollapsed = !sidebarCollapsed },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = if (sidebarCollapsed) 0.dp else 0.dp),
                    ) {
                        Icon(
                            imageVector = if (sidebarCollapsed) {
                                Icons.Default.KeyboardArrowRight
                            } else {
                                Icons.Default.KeyboardArrowLeft
                            },
                            contentDescription = t("creator.video_transition.toggle_sidebar", "Toggle sidebar"),
                            tint = Color.White,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Text(
                        text = navItems.find { it.first == activeNav }?.second ?: activeNav,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = t(
                            "creator.video_transition.android_shell_note",
                            "Native encode, templates, and STT are coming next. On web, the full page editor is available now.",
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF111111))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = t("creator.video_transition.android_page_placeholder", "Page content placeholder"),
                            color = Color.White.copy(alpha = 0.45f),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { /* template save follow-up */ }) {
                    Text(t("creator.video_transition.save_template", "Save Template"))
                }
                Button(
                    onClick = { /* render follow-up */ },
                    colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                ) {
                    Text(t("creator.video_transition.render", "Render chain"))
                }
            }
        }
    }
}
