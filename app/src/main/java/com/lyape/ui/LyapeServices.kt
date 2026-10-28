package com.lyape.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.lyape.LyapeApplication
import com.lyape.data.MovementRepository
import com.lyape.data.AccountsRepository
import com.lyape.data.MonthlyPlanRepository
import com.lyape.notification.CaptureHealthStore
import com.lyape.preferences.AppPreferences
import com.lyape.voice.VoiceSessionCoordinator
import kotlinx.coroutines.flow.StateFlow

/**
 * Acceso a las dependencias de [LyapeApplication] desde Compose, sin librería de inyección.
 */
@Composable
private fun rememberLyapeApplication(): LyapeApplication =
    LocalContext.current.applicationContext as LyapeApplication

@Composable
fun rememberMovementRepository(): MovementRepository {
    val application = rememberLyapeApplication()
    return remember(application) { application.movementRepository }
}

@Composable
fun rememberMonthlyPlanRepository(): MonthlyPlanRepository {
    val application = rememberLyapeApplication()
    return remember(application) { application.monthlyPlanRepository }
}

@Composable
fun rememberAccountsRepository(): AccountsRepository {
    val application = rememberLyapeApplication()
    return remember(application) { application.accountsRepository }
}

@Composable
fun rememberAppPreferences(): AppPreferences {
    val application = rememberLyapeApplication()
    return remember(application) { application.preferences }
}

/** Coordinador de voz del proceso; el servicio foreground mantiene la sesión. */
@Composable
fun rememberVoiceSessionCoordinator(): VoiceSessionCoordinator {
    val application = rememberLyapeApplication()
    return remember(application) { application.voiceSession }
}

/** Estado real de conexión del listener, no solo el permiso concedido. */
@Composable
fun rememberCaptureListenerConnected(): StateFlow<Boolean> {
    val application = rememberLyapeApplication()
    return remember(application) { application.captureListenerConnected }
}

/** Incidencias de captura del proceso; un éxito posterior no las borra. */
@Composable
fun rememberCaptureHealth(): CaptureHealthStore {
    val application = rememberLyapeApplication()
    return remember(application) { application.captureHealth }
}
