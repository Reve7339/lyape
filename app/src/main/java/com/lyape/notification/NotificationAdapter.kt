package com.lyape.notification

import android.app.Notification
import android.os.Process
import android.service.notification.StatusBarNotification
import java.time.Instant

/**
 * Adaptador entre Android y el parser.
 *
 * Es el único punto que lee `Notification.extras`. Recibe el proveedor ya resuelto por el
 * registro cerrado y comprueba otra vez su paquete antes de tocar cualquier contenido.
 * Los campos ausentes se devuelven vacíos o nulos sin lanzar excepciones.
 */
object NotificationAdapter {

    /**
     * Construye la entrada del parser.
     *
     * Devuelve `null` si el paquete no corresponde al proveedor habilitado o si la notificación
     * no tiene contenido legible. El texto se queda en memoria mientras se analiza.
     */
    fun toInput(sbn: StatusBarNotification, source: CaptureProviderSpec): NotificationInput? {
        if (!source.acceptsPackage(sbn.packageName)) return null
        val notification = sbn.notification ?: return null
        if (!acceptsCaptureEnvelope(source, sbn.packageName, sbn.userId,
                sbn.user == Process.myUserHandle(), notification.flags and Notification.FLAG_GROUP_SUMMARY != 0)) return null
        val extras = notification.extras ?: return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val expanded = bigText?.takeIf { it.isNotBlank() && it != text }

        return NotificationInput(
            packageName = sbn.packageName,
            key = sbn.key,
            title = title.orEmpty(),
            text = text.orEmpty(),
            expandedText = expanded,
            postedAt = Instant.ofEpochMilli(sbn.postTime),
        )
    }
}
