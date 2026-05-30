package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore

@Composable
fun CreatorQuickActionsSection(
    translationStore: TranslationStore,
    isLoggedIn: Boolean,
    onGeneratorClick: () -> Unit = {},
    onDesignsClick: () -> Unit = {},
    onContentClick: () -> Unit = {},
    onProductsClick: () -> Unit = {},
    onAutomationsClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onRegisterClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Text(
        text = translationStore.t("creator.overview.quick_actions", "Quick Actions"),
        style = MaterialTheme.typography.titleSmall,
        color = Color.White,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        CreatorQuickActionItem(
            icon = "🎨",
            title = translationStore.t("creator.overview.action_generator_title", "Design Generator"),
            desc = translationStore.t("creator.overview.action_generator_desc", "Create designs with AI"),
            onClick = onGeneratorClick,
        )
        CreatorQuickActionItem(
            icon = "📤",
            title = translationStore.t("creator.overview.action_designs_title", "My Designs"),
            desc = translationStore.t("creator.overview.action_designs_desc", "View and manage designs"),
            onClick = onDesignsClick,
        )
        CreatorQuickActionItem(
            icon = "🖼️",
            title = translationStore.t("creator.overview.action_content_title", "Content Creation"),
            desc = translationStore.t("creator.overview.action_content_desc", "Hero images & more"),
            onClick = onContentClick,
        )
        CreatorQuickActionItem(
            icon = "📊",
            title = translationStore.t("creator.overview.action_products_title", "My Products"),
            desc = translationStore.t("creator.overview.action_products_desc", "Manage your products"),
            onClick = onProductsClick,
        )
        CreatorQuickActionItem(
            icon = "⚙️",
            title = translationStore.t("creator.overview.action_automations_title", "Automations"),
            desc = translationStore.t("creator.overview.action_automations_desc", "Automate your workflow"),
            onClick = onAutomationsClick,
        )
        if (!isLoggedIn) {
            CreatorQuickActionItem(
                icon = "🔐",
                title = translationStore.t("creator.overview.action_login_title", "Log in"),
                desc = translationStore.t("creator.overview.action_login_desc", "Sign in to unlock full features"),
                highlight = true,
                onClick = onLoginClick,
            )
            CreatorQuickActionItem(
                icon = "🎉",
                title = translationStore.t("creator.overview.action_register_title", "Register"),
                desc = translationStore.t("creator.overview.action_register_desc", "Create an account"),
                highlight = true,
                onClick = onRegisterClick,
            )
        }
    }
}

@Composable
private fun CreatorQuickActionItem(
    icon: String,
    title: String,
    desc: String,
    highlight: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                color = if (highlight) EazColors.Orange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        Text(
            text = "→",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}
