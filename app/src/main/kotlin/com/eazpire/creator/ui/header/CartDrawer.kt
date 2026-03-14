package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.util.DebugLog

@Composable
fun CartDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    Dialog(
        onDismissRequest = {
            DebugLog.click("CartDrawer dismiss (backdrop)")
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        DebugLog.click("CartDrawer backdrop")
                        onDismiss()
                    }
            )
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.85f)
                    .align(Alignment.CenterEnd)
                    .background(Color.White)
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Warenkorb",
                    style = MaterialTheme.typography.titleLarge,
                    color = EazColors.TextPrimary
                )
                IconButton(onClick = {
                    DebugLog.click("CartDrawer close")
                    onDismiss()
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Schließen",
                        tint = EazColors.TextPrimary
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Dein Warenkorb ist leer",
                    style = MaterialTheme.typography.bodyLarge,
                    color = EazColors.TextSecondary
                )
                Text(
                    text = "Platzhalter – echte Cart-Daten folgen",
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            }
        }
    }
}
