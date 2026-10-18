package com.lyape.ui.demo

/**
 * Datos de demostración del bloque 1.
 *
 * Son fixtures de presentación: nombres ficticios y montos en céntimos. No hay Room ni
 * cálculo real todavía (bloque 2). Las sumas de aquí solo alimentan las pantallas de ejemplo.
 */

enum class DemoSource(val label: String) {
    DETECTED("Detectado"),
    MANUAL("Manual"),
    ADJUSTMENT("Ajuste"),
}

enum class DemoClassification(val label: String) {
    BUSINESS("Negocio"),
    PERSONAL("Personal"),
    EXCLUDED("Anulado"),
}

data class DemoMovement(
    val id: String,
    val senderName: String?,
    val amountCents: Long,
    val source: DemoSource,
    val classification: DemoClassification,
    val timeLabel: String,
    val detailLabel: String,
    val reviewed: Boolean = true,
    val note: String? = null,
) {
    val title: String
        get() = senderName ?: when (source) {
            DemoSource.MANUAL -> "Venta agregada"
            DemoSource.ADJUSTMENT -> "Ajuste"
            DemoSource.DETECTED -> "Sin nombre"
        }

    /** Etiqueta corta para el avatar de la lista. */
    val avatarLabel: String
        get() {
            val name = senderName?.trim().orEmpty()
            if (name.isEmpty()) return "+"
            val parts = name.split(' ').filter { it.isNotBlank() }
            return if (parts.size >= 2) {
                "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
            } else {
                name.take(2).uppercase()
            }
        }
}

object DemoData {

    val today: List<DemoMovement> = listOf(
        DemoMovement(
            id = "demo-001",
            senderName = "María Rojas",
            amountCents = 3_500,
            source = DemoSource.DETECTED,
            classification = DemoClassification.BUSINESS,
            timeLabel = "5:42 pm",
            detailLabel = "Recibido a las 5:42 pm",
        ),
        DemoMovement(
            id = "demo-002",
            senderName = "Diego Vera",
            amountCents = 1_650,
            source = DemoSource.DETECTED,
            classification = DemoClassification.BUSINESS,
            timeLabel = "4:48 pm",
            detailLabel = "Recibido a las 4:48 pm",
            reviewed = false,
        ),
        DemoMovement(
            id = "demo-003",
            senderName = "Carlos Acuña",
            amountCents = 2_000,
            source = DemoSource.DETECTED,
            classification = DemoClassification.PERSONAL,
            timeLabel = "4:16 pm",
            detailLabel = "Recibido a las 4:16 pm",
        ),
        DemoMovement(
            id = "demo-004",
            senderName = "José Torres",
            amountCents = 12_450,
            source = DemoSource.DETECTED,
            classification = DemoClassification.BUSINESS,
            timeLabel = "3:58 pm",
            detailLabel = "Recibido a las 3:58 pm",
        ),
        DemoMovement(
            id = "demo-005",
            senderName = "Ana María Ruiz",
            amountCents = 2_800,
            source = DemoSource.DETECTED,
            classification = DemoClassification.BUSINESS,
            timeLabel = "2:05 pm",
            detailLabel = "Recibido a las 2:05 pm",
            reviewed = false,
        ),
        DemoMovement(
            id = "demo-006",
            senderName = null,
            amountCents = 4_800,
            source = DemoSource.MANUAL,
            classification = DemoClassification.BUSINESS,
            timeLabel = "1:20 pm",
            detailLabel = "Agregado manualmente a la 1:20 pm",
            note = "Pago recibido con el teléfono apagado.",
        ),
        DemoMovement(
            id = "demo-007",
            senderName = "Luis Paredes",
            amountCents = -800,
            source = DemoSource.ADJUSTMENT,
            classification = DemoClassification.BUSINESS,
            timeLabel = "12:40 pm",
            detailLabel = "Ajuste registrado a las 12:40 pm",
            note = "Devolución de una venta.",
        ),
    )

    val businessTotalCents: Long
        get() = today.filter { it.classification == DemoClassification.BUSINESS }.sumOf { it.amountCents }

    val personalTotalCents: Long
        get() = today.filter { it.classification == DemoClassification.PERSONAL }.sumOf { it.amountCents }

    val pending: List<DemoMovement>
        get() = today.filter { it.source == DemoSource.DETECTED && !it.reviewed }

    fun movementById(id: String): DemoMovement? = today.firstOrNull { it.id == id }

    /** Curva de demostración para la tarjeta del resumen. El gráfico real llega en el bloque 6. */
    val dayCurve: List<Float> = listOf(
        0.08f, 0.2f, 0.14f, 0.36f, 0.3f, 0.58f, 0.52f, 0.78f, 0.9f, 1f,
    )
}
