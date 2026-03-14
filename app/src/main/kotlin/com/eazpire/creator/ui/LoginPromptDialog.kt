package com.eazpire.creator.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.eazpire.creator.EazColors

@Composable
fun LoginPromptDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Login required",
                style = MaterialTheme.typography.titleMedium,
                color = EazColors.TextPrimary
            )
        },
        text = {
            Text(
                text = "Please log in to access your account.",
                style = MaterialTheme.typography.bodyMedium,
                color = EazColors.TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = EazColors.Orange)
            }
        }
    )
}
