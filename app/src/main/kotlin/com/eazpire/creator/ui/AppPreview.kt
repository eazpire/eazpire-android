package com.eazpire.creator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazpireCreatorTheme
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.ui.header.MainHeader

/**
 * Vollständige App-Preview für Android Studio.
 * Öffne diese Datei und wechsle zur Design-Ansicht (Split/Design),
 * um die komplette App visuell anzuzeigen.
 */
@Preview(
    name = "Vollständige App",
    showBackground = true,
    showSystemUi = true
)
@Composable
private fun FullAppPreview() {
    val context = LocalContext.current
    val localeStore = remember { LocaleStore(context) }

    EazpireCreatorTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                MainHeader(localeStore = localeStore)
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Willkommen bei eazpire",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Shop – direkt ohne Login",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Region/Sprache werden automatisch erkannt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
