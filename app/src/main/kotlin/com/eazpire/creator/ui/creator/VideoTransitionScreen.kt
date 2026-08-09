package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * Video Transition fullscreen shell (IDEA-067) — native parity entry next to Video Generator.
 * Encode pipeline lands in a follow-up; Web modal hosts the React lab today.
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
        "project" to t("creator.video_transition.nav_project", "Project"),
        "clips" to t("creator.video_transition.nav_clips", "Clips"),
        "globals" to t("creator.video_transition.nav_globals", "Defaults"),
        "voiceover" to t("creator.video_transition.nav_voiceover", "Voiceover"),
        "render" to t("creator.video_transition.nav_render", "Render"),
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
                IconButton(onClick = { sidebarCollapsed = !sidebarCollapsed }) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = t("creator.video_transition.toggle_sidebar", "Toggle sidebar"),
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("creator.video_transition.title", "Video Transition"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = t(
                            "creator.video_transition.subtitle",
                            "Chain clips with cinematic transitions, voiceover, and burn-in subtitles.",
                        ),
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

            Row(modifier = Modifier.fillMaxSize()) {
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
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xBF28183C), Color(0xE60C0A16))
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                            .padding(24.dp),
                    ) {
                        Column {
                            Text(
                                text = t(
                                    "creator.video_transition.hero_title",
                                    "Build a seamless clip chain",
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = t(
                                    "creator.video_transition.hero_body",
                                    "Start → transitions → optional end. Results save to Assets → Videos → Transition Videos.",
                                ),
                                color = Color.White.copy(alpha = 0.65f),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = t(
                                    "creator.video_transition.android_shell_note",
                                    "Native encode is coming next. On web, the full editor is available now.",
                                ),
                                color = EazColors.Orange.copy(alpha = 0.95f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { activeNav = "clips" }) {
                                    Text(
                                        t("creator.video_transition.start_editing", "Start editing"),
                                        color = Color.White,
                                    )
                                }
                                TextButton(onClick = { activeNav = "render" }) {
                                    Text(
                                        t("creator.video_transition.nav_render", "Render"),
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = t("creator.video_transition.panel_active", "Panel") + ": " +
                            (navItems.find { it.first == activeNav }?.second ?: activeNav),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        repeat(2) { idx ->
                            Box(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF111111))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = t("creator.video_transition.results", "Results") + " ${idx + 1}",
                                    color = Color.White.copy(alpha = 0.45f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
