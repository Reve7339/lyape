package com.lyape.notification

import java.time.Instant

/**
 * Fixtures **ARTIFICIALES y PROVISIONALES** del parser.
 *
 * NO son formatos reales confirmados de Yape. Sirven únicamente para probar el comportamiento del
 * parser y de la deduplicación. Antes de declarar compatibilidad real deben reemplazarse por entre
 * 3 y 5 muestras anonimizadas de notificaciones reales. Los nombres de personas son inventados.
 */
object ProvisionalNotificationFixtures {

    private val POSTED_AT: Instant = Instant.parse("2026-10-14T22:00:00Z")

    fun input(
        text: String,
        title: String = "Yape",
        expandedText: String? = null,
        key: String? = "0|${YapePackage.PROVISIONAL_PACKAGE_ID}|1|null|10001",
        postedAt: Instant = POSTED_AT,
    ): NotificationInput = NotificationInput(
        packageName = YapePackage.PROVISIONAL_PACKAGE_ID,
        key = key,
        title = title,
        text = text,
        expandedText = expandedText,
        postedAt = postedAt,
    )

    /** 1. Monto y remitente en una sola línea. */
    val withSender: NotificationInput = input(text = "María Rojas te pagó S/ 35.00")

    /** 2. Monto sin remitente reconocible (el remitente es opcional). */
    val withoutSender: NotificationInput = input(text = "Recibiste un pago de S/ 20.00")

    /** 3. Espacios irregulares y espacio duro alrededor del monto. */
    val irregularSpacing: NotificationInput =
        input(text = "  Recibiste   un\u00A0pago  de  S/   1,284.50 ")

    /** 4. Separador decimal alternativo (coma). */
    val commaDecimal: NotificationInput = input(text = "Te llegó un pago por S/ 16,50")

    /** 5. El monto y el remitente aparecen solo en el texto expandido. */
    val expandedTextOnly: NotificationInput = input(
        text = "Nuevo pago recibido",
        expandedText = "Carlos Acuña te pagó S/ 124.50",
    )
}
