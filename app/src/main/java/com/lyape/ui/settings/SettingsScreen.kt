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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyape.preferences.CapturePreferenceState
import com.lyape.ui.capture.captureStatus
import com.lyape.ui.capture.color
import com.lyape.ui.capture.hasNotificationAccess
import com.lyape.ui.capture.label
import com.lyape.ui.components.InfoRow
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.rememberAppPreferences
import com.lyape.ui.theme.PillShape
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appVersion = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val preferences = rememberAppPreferences()
    val scope = rememberCoroutineScope()

    var retryKey by remember { mutableIntStateOf(0) }
    val preferenceFlow = remember(preferences, retryKey) {
        preferences.captureState
            .map<CapturePreferenceState, CapturePreferenceState?> { it }
            .onStart { emit(null) }
    }
    val preference by preferenceFlow.collectAsStateWithLifecycle(initialValue = null)
    val paused = preference == CapturePreferenceState.PAUSED
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    var hasAccess by remember { mutableStateOf(hasNotificationAccess(context)) }
    val capture = captureStatus(hasAccess, preference)

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
                if (preference == CapturePreferenceState.UNAVAILABLE) {
                    Text(
                        text = "No se pudo leer la configuración. Captura detenida.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            retryKey++
                            saveFailed = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = PillShape,
                    ) {
                        Text("Reintentar", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .toggleable(
                                value = paused,
                                enabled = preference != null && !saving,
                                role = Role.Switch,
                                onValueChange = { value ->
                                    if (!saving) {
                                        saving = true
                                        saveFailed = false
                                        scope.launch {
                                            try {
                                                preferences.setCapturePaused(value)
                                            } catch (error: Exception) {
                                                if (error is CancellationException) throw error
                                                saveFailed = true
                                            } finally {
                                                saving = false
                                            }
                                        }
                                    }
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
                        // La fila completa es un único control con nombre y estado para TalkBack.
                        Switch(
                            checked = paused,
                            onCheckedChange = null,
                            enabled = preference != null && !saving,
                        )
                    }
                    if (saveFailed) {
                        Text(
                            text = "No se guardó el cambio. Inténtalo otra vez.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
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
                text = "Guarda monto, remitente y fecha; no la notificación completa. " +
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
