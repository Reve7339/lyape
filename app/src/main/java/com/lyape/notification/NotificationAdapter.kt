package com.lyape.notification

import android.app.Notification
import android.service.notification.StatusBarNotification
import java.time.Instant

/**
 * Adaptador entre Android y el parser.
 *
 * Es el único punto que lee `Notification.extras`. Comprueba primero el paquete contra la única
 * constante de [YapePackage] y retorna antes de tocar el contenido de cualquier otra aplicación.
 * Los campos ausentes se devuelven vacíos o nulos sin lanzar excepciones.
 */
object NotificationAdapter {

    /** `true` solo si la notificación proviene del paquete configurado para Yape. */
    fun isFromYape(packageName: String): Boolean =
        packageName == YapePackage.PROVISIONAL_PACKAGE_ID

    /**
     * Construye la entrada del parser.
     *
     * Devuelve `null` si el paquete no corresponde a Yape o si la notificación no tiene contenido
     * legible. No copia el texto a ninguna parte: solo se queda en memoria mientras se analiza.
     */
    fun toInput(sbn: StatusBarNotification): NotificationInput? {
        if (!isFromYape(sbn.packageName)) return null

        val notification = sbn.notification ?: return null
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
