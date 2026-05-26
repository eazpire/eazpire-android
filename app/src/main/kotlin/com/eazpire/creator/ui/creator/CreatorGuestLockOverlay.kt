package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore

/** Full-screen login gate — matches web mobile `creator-generator-lock`. */
@Composable
fun CreatorGuestLockOverlay(
    translationStore: TranslationStore,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xAD080512)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(
                    Color(0xEB14141E),
                    RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = translationStore.t(
                    "eazy_chat.login_required_title",
                    "Log in to continue",
                ),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Text(
                text = translationStore.t(
                    "eazy_chat.login_required_text",
                    "Sign in to use this feature.",
                ),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Button(
                onClick = onLoginClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EazColors.Orange,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(
                    text = translationStore.t(
                        "eazy_chat.login_required_btn",
                        "Sign in",
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
