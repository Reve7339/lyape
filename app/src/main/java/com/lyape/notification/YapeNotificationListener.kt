package com.lyape.notification

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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

        // Filtro por paquete ANTES de leer extras, título o texto de cualquier otra aplicación.
        if (!NotificationAdapter.isFromYape(notification.packageName)) return

        val input = NotificationAdapter.toInput(notification) ?: return
        val app = application as? LyapeApplication ?: return

        scope.launch { app.notificationIngestor.ingest(input) }
    }

    /**
     * Android desenlaza el servicio en algunos casos (por ejemplo, al actualizar la app). Se pide
     * reconexión con la API permitida; no se intenta eludir las restricciones del sistema.
     */
    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        requestRebind(ComponentName(this, YapeNotificationListener::class.java))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
