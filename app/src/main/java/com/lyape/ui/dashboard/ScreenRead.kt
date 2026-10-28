package com.lyape.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Una lectura fallida o todavía pendiente nunca se presenta como un saldo de cero. */
sealed interface ScreenRead<out T> {
    data object Loading : ScreenRead<Nothing>
    data class Ready<T>(val value: T) : ScreenRead<T>
    data object Failed : ScreenRead<Nothing>
}

fun <T> Flow<T>.asScreenRead(): Flow<ScreenRead<T>> =
    map<T, ScreenRead<T>> { ScreenRead.Ready(it) }
        .onStart { emit(ScreenRead.Loading) }
        .catch { error ->
            if (error is CancellationException) throw error
            emit(ScreenRead.Failed)
        }

@Composable
fun ReadFeedback(
    failed: Boolean,
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (failed) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Reintentar")
            }
        } else {
            CircularProgressIndicator()
            Text("Cargando…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
