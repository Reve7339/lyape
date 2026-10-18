package com.lyape.notification

import java.time.Instant

/**
 * Estructura confirmada con una notificación real y anonimizada de Yape.
 *
 * El nombre y el código son valores artificiales: no se conserva ningún dato de la captura
 * original. Hace falta reunir más variantes anonimizadas antes de declarar el parser completo.
 */
object AnonymizedRealNotificationFixtures {

    fun paymentConfirmation(securityCode: String = "000"): NotificationInput = NotificationInput(
        packageName = YapePackage.PROVISIONAL_PACKAGE_ID,
        key = "0|${YapePackage.PROVISIONAL_PACKAGE_ID}|real-format|null|10001",
        title = "Confirmación de Pago",
        text = "Persona Ejemplo* te envió un pago por S/ 0.1. " +
            "El cód. de seguridad es: $securityCode",
        postedAt = Instant.parse("2026-10-15T21:43:00Z"),
    )
}
