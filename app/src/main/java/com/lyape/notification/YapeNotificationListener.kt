package com.lyape.notification

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.lyape.LyapeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Fuente de captura administrada por Android.
 *
 * No depende de `MainActivity`: al quitar la interfaz de recientes, este servicio sigue enlazado.
 * No se promete funcionamiento tras "Forzar detención", con el teléfono apagado o si el acceso a
 * notificaciones se revoca.
 */
class YapeNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        val app = application as? LyapeApplication ?: return

        // Registro cerrado: se resuelve el proveedor por paquete antes de leer extras.
        val source = app.captureProviders.forPackage(notification.packageName) ?: return
        val input = NotificationAdapter.toInput(notification, source) ?: return

        scope.launch {
            val result = try {
                app.notificationIngestor.ingest(input, source)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                IngestResult.Failed(IngestFailure.UNEXPECTED)
            }
            app.captureHealth.record(result, source.provider)
            if (result is IngestResult.Stored) {
                try {
                    app.voiceSession.onCaptured(result.payment)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Un fallo de audio nunca revierte ni interrumpe la captura ya guardada.
                }
            }
        }
    }

    /**
     * Android desenlaza el servicio en algunos casos (por ejemplo, al actualizar la app). Se pide
     * reconexión con la API permitida; no se intenta eludir las restricciones del sistema.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        (application as? LyapeApplication)?.setCaptureListenerConnected(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        val app = application as? LyapeApplication
        app?.setCaptureListenerConnected(false)
        if (app != null && !hasNotificationAccess()) {
            app.voiceSession.stopSessionKeepingPreference()
        }
        requestRebind(ComponentName(this, YapeNotificationListener::class.java))
    }

    override fun onDestroy() {
        val app = application as? LyapeApplication
        app?.setCaptureListenerConnected(false)
        if (app != null && !hasNotificationAccess()) {
            app.voiceSession.stopSessionKeepingPreference()
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
}
