package com.eazpire.creator.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors

@Composable
fun CollectionBreadcrumb(
    categoryTitle: String,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Home",
            style = MaterialTheme.typography.bodySmall,
            color = EazColors.Orange,
            modifier = Modifier.clickable(onClick = onHomeClick)
        )
        Text(
            text = " > ",
            style = MaterialTheme.typography.bodySmall,
            color = EazColors.TextSecondary
        )
        Text(
            text = categoryTitle,
            style = MaterialTheme.typography.bodySmall,
            color = EazColors.TextPrimary
        )
    }
}
