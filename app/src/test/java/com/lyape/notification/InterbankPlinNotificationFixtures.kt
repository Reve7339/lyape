package com.lyape.notification

import java.time.Instant

/**
 * Fixture basada en la primera muestra real de Interbank-Plin.
 *
 * Procedencia: existe una captura real aportada por el usuario y una transcripción visual de una
 * notificación expandida proporcionada el 2026-10-25. El remitente original se sustituyó por
 * «Persona Ejemplo»; el importe y la frase son los de la muestra. No se conserva la captura
 * original.
 *
 * LÍMITE: el paquete se confirmó en el dispositivo, pero no se conocen la clave, el grupo, el
 * postTime, la categoría, el canal ni los extras reales. La colocación del contenido en
 * `title`/`text` es una hipótesis de prueba, no una observación. El salto visual antes del importe
 * puede ser un ajuste de línea. Esta fixture no habilita la captura en producción ni acredita
 * todos los formatos, actualizaciones o agrupaciones.
 */
object InterbankPlinNotificationFixtures {

    /**
     * Paquete confirmado. Las variantes inventadas para las pruebas son sintéticas: no acreditan
     * todos los formatos reales ni demuestran por sí solas la compatibilidad del proveedor.
     */
    const val INTERBANK_PACKAGE: String = InterbankPackage.PACKAGE_NAME

    private val POSTED_AT: Instant = Instant.parse("2026-10-25T12:00:00Z")

    /**
     * Transcripción visual base: remitente y frase en una línea; importe en la siguiente.
     *
     * `title = "Interbank"` reproduce el título visible, pero su ubicación exacta en los extras
     * no está confirmada. La asignación a `text` también es una hipótesis de prueba.
     */
    fun visualTranscription(
        sender: String = "Persona Ejemplo",
        amount: String = "1.00",
        separator: String = "\n",
        title: String = "Interbank",
        expandedText: String? = null,
        postedAt: Instant = POSTED_AT,
    ): NotificationInput = NotificationInput(
        packageName = INTERBANK_PACKAGE,
        key = null,
        title = title,
        text = "$sender te ha plineado$separator" + "S/ $amount",
        expandedText = expandedText,
        postedAt = postedAt,
    )

    /** Variante sintética para probar colocación y textos alternativos. */
    fun syntheticText(
        text: String,
        title: String = "Interbank",
        expandedText: String? = null,
        postedAt: Instant = POSTED_AT,
    ): NotificationInput = NotificationInput(
        packageName = INTERBANK_PACKAGE,
        key = null,
        title = title,
        text = text,
        expandedText = expandedText,
        postedAt = postedAt,
    )
}
