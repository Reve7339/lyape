package com.lyape.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyape.ui.capture.captureStatus
import com.lyape.ui.capture.color
import com.lyape.ui.capture.hasNotificationAccess
import com.lyape.ui.capture.label
import com.lyape.ui.components.InfoRow
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.rememberAppPreferences
import com.lyape.ui.theme.PillShape
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appVersion = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val preferences = rememberAppPreferences()
    val scope = rememberCoroutineScope()

    val paused by preferences.capturePaused.collectAsStateWithLifecycle(initialValue = false)
    var hasAccess by remember { mutableStateOf(hasNotificationAccess(context)) }
    val capture = captureStatus(hasAccess, paused)

    // El permiso se concede en los ajustes del sistema: al volver, se vuelve a comprobar.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = hasNotificationAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 17.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ajustes",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(16.dp))
        LyapeCard {
            InfoRow(
                label = "Captura de pagos",
                value = capture.label,
                valueColor = capture.color(),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (hasAccess) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .toggleable(
                            value = paused,
                            role = Role.Switch,
                            onValueChange = { value ->
                                scope.launch { preferences.setCapturePaused(value) }
                            },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Pausar captura",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    // El interruptor no lleva su propio control táctil ni su propio anuncio: la fila
                    // completa lo activa y TalkBack lee la etiqueta junto con el estado.
                    Switch(
                        checked = paused,
                        onCheckedChange = null,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { openNotificationAccessSettings(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = PillShape,
                ) {
                    Text("Administrar acceso", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Permite leer las notificaciones de Yape para registrar pagos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { openNotificationAccessSettings(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Text("Dar acceso a notificaciones", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        LyapeCard {
            Text(
                text = "Privacidad",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Solo en este dispositivo. Sin respaldo automático.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "No guarda el texto de las notificaciones. " +
                    "Sin cuentas, servidores ni publicidad.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = "Sobre LYape",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Versión $appVersion · Código abierto",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Proyecto independiente, no afiliado a Yape ni al BCP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

private fun openNotificationAccessSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}
