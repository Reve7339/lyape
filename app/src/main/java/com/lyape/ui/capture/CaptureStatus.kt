package com.lyape.ui.capture

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.app.NotificationManagerCompat
import com.lyape.preferences.CapturePreferenceState

/**
 * Estado de captura de pagos.
 *
 * Ajustes lo deriva del acceso a notificaciones del sistema y la preferencia local de pausa.
 * Al volver de los ajustes del sistema se vuelve a leer el acceso.
 */
enum class CaptureStatus { ACTIVE, PAUSED, NO_PERMISSION, LOADING, UNAVAILABLE }

/** Sin acceso, la captura no puede funcionar aunque la preferencia no esté pausada. */
internal fun captureStatus(hasAccess: Boolean, preference: CapturePreferenceState?): CaptureStatus = when {
    !hasAccess -> CaptureStatus.NO_PERMISSION
    preference == null -> CaptureStatus.LOADING
    preference == CapturePreferenceState.UNAVAILABLE -> CaptureStatus.UNAVAILABLE
    preference == CapturePreferenceState.PAUSED -> CaptureStatus.PAUSED
    else -> CaptureStatus.ACTIVE
}

/** Texto que se muestra y se anuncia; el color solo lo acompaña. */
internal val CaptureStatus.label: String
    get() = when (this) {
        CaptureStatus.ACTIVE -> "Activa"
        CaptureStatus.PAUSED -> "Pausada"
        CaptureStatus.NO_PERMISSION -> "Sin permiso"
        CaptureStatus.LOADING -> "Comprobando"
        CaptureStatus.UNAVAILABLE -> "No disponible"
    }

@Composable
internal fun CaptureStatus.color(): Color = when (this) {
    CaptureStatus.ACTIVE -> MaterialTheme.colorScheme.primary
    CaptureStatus.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
    CaptureStatus.NO_PERMISSION -> MaterialTheme.colorScheme.error
    CaptureStatus.LOADING -> MaterialTheme.colorScheme.onSurfaceVariant
    CaptureStatus.UNAVAILABLE -> MaterialTheme.colorScheme.error
}

/** Mismo criterio que usa el listener: el paquete figura entre los listeners habilitados. */
internal fun hasNotificationAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
