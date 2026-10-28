package com.lyape.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyape.BuildConfig
import com.lyape.R
import com.lyape.domain.CaptureProvider
import com.lyape.notification.CaptureIssue
import com.lyape.notification.CaptureHealthState
import com.lyape.preferences.CapturePreferenceState
import com.lyape.ui.capture.captureStatus
import com.lyape.ui.capture.color
import com.lyape.ui.capture.hasNotificationAccess
import com.lyape.ui.capture.label
import com.lyape.ui.components.InfoRow
import com.lyape.ui.components.LyapeCard
import com.lyape.ui.rememberAppPreferences
import com.lyape.ui.rememberCaptureHealth
import com.lyape.ui.rememberCaptureListenerConnected
import com.lyape.ui.rememberVoiceSessionCoordinator
import com.lyape.ui.theme.PillShape
import com.lyape.voice.VoiceError
import com.lyape.voice.VoiceNotificationChannel
import com.lyape.voice.VoiceSessionAvailability
import com.lyape.voice.VoiceState
import com.lyape.voice.availability
import com.lyape.voice.canRetrySave
import com.lyape.voice.canToggle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenMovements: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
) {
    val context = LocalContext.current
    val appVersion = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val preferences = rememberAppPreferences()
    val scope = rememberCoroutineScope()
    var showCaptureDisclosure by remember { mutableStateOf(false) }
    val captureHealthStore = rememberCaptureHealth()
    val captureHealth by captureHealthStore.state.collectAsStateWithLifecycle()

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
    val captureConnected by rememberCaptureListenerConnected().collectAsStateWithLifecycle()
    val capture = captureStatus(hasAccess, preference, captureConnected)

    val voiceSession = rememberVoiceSessionCoordinator()
    val voiceSessionState by voiceSession.state.collectAsStateWithLifecycle()
    val voiceState = voiceSessionState.voice
    val voiceNoticeShown by remember(preferences) { preferences.voiceNoticeShown }
        .collectAsStateWithLifecycle(initialValue = false)
    var showVoiceNotice by remember { mutableStateOf(false) }
    var pendingVoiceEnable by remember { mutableStateOf(false) }
    var voiceStartMessage by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val shouldStart = pendingVoiceEnable
        pendingVoiceEnable = false
        if (granted && shouldStart) {
            voiceStartMessage = null
            voiceSession.requestEnabled(true)
        } else if (!granted) {
            voiceStartMessage =
                "Permite las notificaciones para activar los avisos de voz."
        }
    }
    val startVoiceSession: () -> Unit = {
        if (paused) {
            voiceStartMessage = "Reanuda la captura para activar los avisos de voz."
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingVoiceEnable = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            voiceStartMessage = null
            voiceSession.requestEnabled(true)
        }
    }
    val requestVoiceStart: () -> Unit = {
        voiceStartMessage = null
        if (voiceNoticeShown) {
            startVoiceSession()
        } else {
            showVoiceNotice = true
        }
    }
    DisposableEffect(voiceSession) {
        onDispose { voiceSession.cancelPreview() }
    }
    LaunchedEffect(paused) {
        if (paused) voiceSession.stopSessionKeepingPreference()
    }
    LaunchedEffect(hasAccess) {
        if (!hasAccess) voiceSession.stopSessionKeepingPreference()
    }

    // El permiso se concede en los ajustes del sistema: al volver, se vuelve a comprobar.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = hasNotificationAccess(context)
                voiceSession.revalidateAvailability()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showCaptureDisclosure) {
        AlertDialog(
            onDismissRequest = { showCaptureDisclosure = false },
            title = { Text(stringResource(R.string.capture_disclosure_title)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.capture_disclosure_body))
                    TextButton(
                        onClick = {
                            showCaptureDisclosure = false
                            onOpenPrivacy()
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.privacy_read_policy)) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCaptureDisclosure = false
                        openNotificationAccessSettings(context)
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.capture_disclosure_continue)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCaptureDisclosure = false },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.capture_disclosure_cancel)) }
            },
        )
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
                    text = "Permite leer notificaciones de Yape e Interbank para registrar cobros.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showCaptureDisclosure = true },
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

        if (captureHealth.hasIssues) {
            Spacer(Modifier.height(12.dp))
            CaptureHealthNotice(
                state = captureHealth,
                onReviewMovements = onOpenMovements,
                onReviewAccounts = onOpenAccounts,
                onDismiss = captureHealthStore::acknowledge,
            )
        }

        Spacer(Modifier.height(12.dp))
        LyapeCard {
            Text(
                text = stringResource(R.string.voice_channel_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Anuncia cobros de Yape y Plin, sin nombres.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (voiceSessionState.voice.preferenceAvailable) {
                val availability = voiceSessionState.availability()
                val canTurnOn = voiceSessionState.canToggle && !paused
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .toggleable(
                            value = voiceState.enabled,
                            enabled = voiceSessionState.canToggle && (voiceState.enabled || canTurnOn),
                            role = Role.Switch,
                            onValueChange = { value ->
                                if (voiceSessionState.canToggle) {
                                    if (value) {
                                        if (!voiceState.enabled) requestVoiceStart()
                                    } else {
                                        voiceSession.requestEnabled(false)
                                    }
                                }
                            },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Anunciar pagos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = voiceState.enabled,
                        onCheckedChange = null,
                        enabled = voiceSessionState.canToggle && (voiceState.enabled || canTurnOn),
                    )
                }

                voiceStartMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }

                if (voiceState.error == VoiceError.SAVE_FAILED) {
                    Text(
                        text = "No se guardó el cambio. Inténtalo otra vez.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (voiceSessionState.canRetrySave) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                voiceSession.requestEnabled(voiceState.saveRetryTarget ?: false)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = PillShape,
                        ) {
                            Text("Reintentar guardado", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                if (!hasAccess) {
                    Text(
                        text = "La captura no tiene acceso a notificaciones; los cobros no llegarán.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when (availability) {
                    VoiceSessionAvailability.DISABLED -> Unit

                    VoiceSessionAvailability.PREPARING -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Preparando voz…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }

                    VoiceSessionAvailability.ACTIVE -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Avisos activos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Spacer(Modifier.height(8.dp))
                        VoiceSelector(
                            state = voiceState,
                            onSelect = { id -> voiceSession.selectVoice(id) },
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { voiceSession.preview() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = PillShape,
                            enabled = !voiceState.speaking && !voiceState.initializing,
                        ) {
                            Text("Probar voz", style = MaterialTheme.typography.labelLarge)
                        }
                        voiceErrorMessage(voiceState.error)?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }

                    VoiceSessionAvailability.STOPPED -> {
                        Spacer(Modifier.height(4.dp))
                        if (paused) {
                            Text(
                                text = "Captura pausada.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Text(
                                text = "Avisos detenidos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = requestVoiceStart,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp),
                                shape = PillShape,
                            ) {
                                Text("Reactivar", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    VoiceSessionAvailability.NO_VOICE -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Falta una voz en español.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { openTtsSettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = PillShape,
                        ) {
                            Text("Configurar voz", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    VoiceSessionAvailability.ENGINE_UNAVAILABLE,
                    VoiceSessionAvailability.SERVICE_UNAVAILABLE -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "No se pudieron activar los avisos.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = requestVoiceStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = PillShape,
                        ) {
                            Text("Reintentar", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    VoiceSessionAvailability.NOTIFICATION_BLOCKED -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Las notificaciones de LYape o el canal de avisos están bloqueados.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { openAppNotificationSettings(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = PillShape,
                        ) {
                            Text("Abrir ajustes de notificaciones", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = requestVoiceStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = PillShape,
                        ) {
                            Text("Reintentar", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    VoiceSessionAvailability.CAPTURE_UNAVAILABLE -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "La captura no está disponible; los avisos no se pueden activar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (hasAccess) openNotificationAccessSettings(context)
                                else showCaptureDisclosure = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = PillShape,
                        ) {
                            Text("Revisar acceso a notificaciones", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = requestVoiceStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = PillShape,
                        ) {
                            Text("Reintentar", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    VoiceSessionAvailability.VOICE_UNAVAILABLE -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "No se puede usar la voz elegida.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (voiceState.voices.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            VoiceSelector(
                                state = voiceState,
                                onSelect = { id -> voiceSession.selectVoice(id) },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = requestVoiceStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = PillShape,
                        ) {
                            Text("Reintentar", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    VoiceSessionAvailability.PREFERENCE_UNAVAILABLE -> {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "No se pudo leer la configuración de voz.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { voiceSession.reloadPreferences() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = PillShape,
                        ) {
                            Text("Reintentar", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    VoiceSessionAvailability.SAVE_FAILED -> Unit
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No se pudo leer la configuración de voz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { voiceSession.reloadPreferences() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = PillShape,
                ) {
                    Text("Reintentar", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Probar voz no crea movimientos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showVoiceNotice) {
            AlertDialog(
                onDismissRequest = { showVoiceNotice = false },
                title = { Text("Avisos de voz") },
                text = {
                    Text(
                        "Mientras estén activos, LYape muestra un aviso persistente con la " +
                            "acción Desactivar. El monto se dirá en voz alta, también con la " +
                            "pantalla bloqueada cuando el sistema lo permita. La captura sigue " +
                            "funcionando aunque apagues la voz.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showVoiceNotice = false
                            scope.launch { preferences.setVoiceNoticeShown() }
                            startVoiceSession()
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Activar")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showVoiceNotice = false },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("Cancelar")
                    }
                },
            )
        }

        Spacer(Modifier.height(12.dp))
        LyapeCard {
            Text(
                text = stringResource(R.string.privacy_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.privacy_local),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.privacy_details),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.privacy_voice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onOpenPrivacy,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.privacy_read_policy)) }
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
                text = "Proyecto independiente, no afiliado a Yape, Interbank ni al BCP.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun CaptureHealthNotice(
    state: CaptureHealthState,
    onReviewMovements: () -> Unit,
    onReviewAccounts: () -> Unit,
    onDismiss: (CaptureProvider, Long) -> Unit,
) {
    LyapeCard {
        Text(
            text = "Captura",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        state.issues.forEach { (provider, notice) ->
            val issue = notice.issue
            Spacer(Modifier.height(8.dp))
            val providerName = when (provider) {
                CaptureProvider.YAPE -> "Yape"
                CaptureProvider.INTERBANK_PLIN -> "Plin"
            }
            Text(
                text = "$providerName: ${stringResource(issue.messageResource())}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        when (issue) {
                            CaptureIssue.SAVE_FAILED,
                            CaptureIssue.CAPTURE_FAILED,
                            -> onReviewMovements()

                            CaptureIssue.ACCOUNT_UNAVAILABLE -> onReviewAccounts()
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        stringResource(
                            if (issue == CaptureIssue.ACCOUNT_UNAVAILABLE) {
                                R.string.capture_health_review_accounts
                            } else {
                                R.string.capture_health_review_movements
                            },
                        ),
                    )
                }
                TextButton(
                    onClick = { onDismiss(provider, notice.version) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.capture_health_dismiss))
                }
            }
        }
        Text(
            text = "El aviso se conserva mientras LYape siga abierto.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun CaptureIssue.messageResource(): Int = when (this) {
    CaptureIssue.SAVE_FAILED -> R.string.capture_issue_save_failed
    CaptureIssue.CAPTURE_FAILED -> R.string.capture_issue_capture_failed
    CaptureIssue.ACCOUNT_UNAVAILABLE -> R.string.capture_issue_account_unavailable
}

private fun openAppNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, VoiceNotificationChannel.ID),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }
}

private fun openNotificationAccessSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }
}

private fun openTtsSettings(context: Context) {
    runCatching {
        context.startActivity(Intent("com.android.settings.TTS_SETTINGS"))
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

@Composable
private fun VoiceSelector(state: VoiceState, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Voz",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = PillShape,
            ) {
                Text(
                    text = voiceLabel(state),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                state.voices.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            onSelect(option.id)
                        },
                    )
                }
            }
        }
    }
}

private fun voiceLabel(state: VoiceState): String {
    return state.selectedVoiceId?.let { selected ->
        state.voices.find { it.id == selected }?.label ?: "Voz elegida"
    } ?: "Elegir voz"
}

private fun voiceErrorMessage(error: VoiceError?): String? = when (error) {
    VoiceError.AUDIO_BLOCKED -> "No se reproduce con el teléfono en silencio o en No molestar."
    VoiceError.FOCUS_DENIED -> "Otra app está usando el audio. Prueba de nuevo."
    VoiceError.FOCUS_LOST -> "Otra app usó el audio. Prueba de nuevo."
    VoiceError.PLAYBACK_FAILED -> "No se pudo reproducir. Prueba de nuevo."
    else -> null
}
