package com.eazpire.creator.ui.footer

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eazpire.creator.i18n.TranslationStore

private val TabBg = Color(0xF7FFFFFF)
private val HandleIdle = Color(0xFFC4C4C4)

/**
 * Collapsible shop footer (GlobalFooter) with a narrow centered tab.
 * Matches web mobile `.eaz-footer-stack__tab` behaviour; persists via SharedPreferences.
 * Locale / wallet widgets live in the main header.
 */
@Composable
fun CollapsibleShopFooter(
    translationStore: TranslationStore? = null,
    onTermsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var collapsed by remember {
        mutableStateOf(prefs.getBoolean(KEY_COLLAPSED, false))
    }

    fun setCollapsed(next: Boolean) {
        collapsed = next
        prefs.edit().putBoolean(KEY_COLLAPSED, next).apply()
    }

    val collapseLabel = translationStore?.t("eaz.footer.collapse_stack", "Hide footer") ?: "Hide footer"
    val expandLabel = translationStore?.t("eaz.footer.expand_stack", "Show footer") ?: "Show footer"
    val tabLabel = if (collapsed) expandLabel else collapseLabel

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(TabBg)
                    .clickable { setCollapsed(!collapsed) }
                    .semantics {
                        role = Role.Button
                        contentDescription = tabLabel
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(HandleIdle)
                )
            }
        }

        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                GlobalFooter(onTermsClick = onTermsClick)
            }
        }
    }
}

private const val PREFS_NAME = "eaz_footer_stack"
private const val KEY_COLLAPSED = "collapsed"
