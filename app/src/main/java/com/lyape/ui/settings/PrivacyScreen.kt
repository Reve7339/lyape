package com.lyape.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lyape.R

/** Política completa disponible sin conexión; usa la navegación normal de la aplicación. */
@Composable
fun PrivacyScreen(modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val paragraphs = remember(resources) {
        resources.openRawResource(R.raw.privacy_policy).bufferedReader().use { it.readText() }
            .trim().split("\n\n").drop(1) // El título ya está en la barra superior.
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(paragraphs) { paragraph ->
            val lines = paragraph.split('\n', limit = 2)
            val section = lines.size == 2 && !paragraph.startsWith("Responsable:")
            Column {
                if (section) {
                    Text(
                        text = lines[0],
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    text = if (section) lines[1] else paragraph,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
