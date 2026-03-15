package com.eazpire.creator.ui.header

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors

@Composable
fun HeaderSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String = "Search...",
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().height(44.dp).padding(horizontal = 8.dp, vertical = 2.dp),
        textStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
        placeholder = { Text(placeholder, color = EazColors.TextSecondary, fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EazColors.Orange,
            unfocusedBorderColor = EazColors.TopbarBorder,
            focusedContainerColor = androidx.compose.ui.graphics.Color.White,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.White,
            cursorColor = EazColors.Orange,
            focusedTextColor = EazColors.TextPrimary,
            unfocusedTextColor = EazColors.TextPrimary
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); onSearch() }),
        trailingIcon = {
            IconButton(
                onClick = { focusManager.clearFocus(); onSearch() }
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = EazColors.Orange
                )
            }
        }
    )
}
