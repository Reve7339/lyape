package com.lyape.domain

import java.time.Instant
import java.util.UUID

/** Evento mínimo de una captura persistida; no contiene nombre ni texto de la notificación. */
data class CapturedPayment(
    val id: UUID,
    val medium: PaymentMedium,
    val amountCents: Long,
    val occurredAt: Instant,
)

/**
 * Medio de pago de un movimiento, independiente del origen (`MovementSource`) y de la cuenta.
 *
 * Yape y Plin son medios; [UNKNOWN] conserva los movimientos cuyo medio no se puede inferir.
 */
enum class PaymentMedium {
    YAPE,
    PLIN,
    UNKNOWN,
}

/**
 * Proveedor de captura reconocido.
 *
 * Plin se registra en LYape como dinero recibido; no crea, consulta ni mueve una cuenta
 * bancaria. [requiredAccountKind] mantiene la compatibilidad de datos y la validación de la
 * cuenta local asociada.
 */
enum class CaptureProvider(
    val medium: PaymentMedium,
    val requiredAccountKind: AccountKind,
) {
    YAPE(PaymentMedium.YAPE, AccountKind.YAPE),
    INTERBANK_PLIN(PaymentMedium.PLIN, AccountKind.BANK),
}

/**
 * Identidad mínima de una cuenta receptora, sin saldos ni cálculos monetarios.
 *
 * La resolución y la selección de captura solo necesitan saber que la cuenta existe y que su
 * tipo es compatible. Un fallo al calcular el saldo no debe ocultar una cuenta válida.
 */
data class CaptureAccount(
    val id: String,
    val name: String,
    val kind: AccountKind,
)

/**
 * Asociación persistida entre un proveedor y su cuenta receptora.
 *
 * [account] es `null` si la cuenta ya no existe o dejó de tener el tipo requerido. En ese caso
 * la asociación no se borra ni se redirige: las nuevas capturas quedan bloqueadas hasta que el
 * usuario restablezca explícitamente el vínculo local desde Cuentas.
 */
data class CaptureAccountLink(
    val provider: CaptureProvider,
    val accountId: String,
    val account: CaptureAccount?,
)

/** Configuración observada de un proveedor: asociación actual y cuentas compatibles. */
data class CaptureConfiguration(
    val provider: CaptureProvider,
    val link: CaptureAccountLink?,
    val eligibleAccounts: List<CaptureAccount>,
)
