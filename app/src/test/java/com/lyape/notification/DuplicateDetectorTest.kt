package com.lyape.notification

import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

class DuplicateDetectorTest {

    private val detector = DuplicateDetector()
    private val base: Instant = Instant.parse("2026-10-14T22:00:00Z")

    private fun movement(
        notificationKey: String? = null,
        deduplicationHash: String? = null,
        occurredAt: Instant = base,
    ): Movement = Movement(
        id = UUID.randomUUID(),
        amountCents = 3_500,
        occurredAt = occurredAt,
        capturedAt = occurredAt,
        source = MovementSource.NOTIFICATION,
        classification = MovementClassification.BUSINESS,
        reviewed = false,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        notificationKey = notificationKey,
        deduplicationHash = deduplicationHash,
    )

    // --- Clave de Android: misma entrega, no identificador eterno ---

    @Test
    fun `la misma clave y el mismo instante es la misma entrega`() {
        val candidate = movement(notificationKey = "clave-1", deduplicationHash = "huella")
        val existing = movement(notificationKey = "clave-1", deduplicationHash = "huella")

        assertTrue(detector.isDuplicate(candidate, listOf(existing)))
    }

    @Test
    fun `la misma entrega se reconoce aunque cambie la huella`() {
        val candidate = movement(notificationKey = "clave-1", deduplicationHash = "huella-nueva")
        val existing = movement(notificationKey = "clave-1", deduplicationHash = "huella-vieja")

        assertTrue(detector.isDuplicate(candidate, listOf(existing)))
    }

    @Test
    fun `la misma clave con horas distintas son pagos distintos`() {
        val candidate = movement(notificationKey = "clave-1", deduplicationHash = "huella")

        // Ambas diferencias caen en el mismo escenario de la regla: dentro de la ventana la huella
        // podría coincidir, pero con clave en ambos lados no debe descartarse el movimiento.
        val dentroDeLaVentana = movement(
            notificationKey = "clave-1",
            deduplicationHash = "huella",
            occurredAt = base.plusSeconds(30),
        )
        val fueraDeLaVentana = movement(
            notificationKey = "clave-1",
            deduplicationHash = "huella",
            occurredAt = base.plusSeconds(600),
        )

        assertFalse(detector.isDuplicate(candidate, listOf(dentroDeLaVentana)))
        assertFalse(detector.isDuplicate(candidate, listOf(fueraDeLaVentana)))
    }

    @Test
    fun `claves distintas con la misma huella dentro de la ventana no se descartan`() {
        val candidate = movement(notificationKey = "clave-1", deduplicationHash = "huella")
        val existing = movement(
            notificationKey = "clave-2",
            deduplicationHash = "huella",
            occurredAt = base.plusSeconds(30),
        )

        assertFalse(detector.isDuplicate(candidate, listOf(existing)))
    }

    @Test
    fun `una clave distinta no es duplicado`() {
        val candidate = movement(notificationKey = "clave-1", deduplicationHash = "huella-a")
        val existing = movement(notificationKey = "clave-2", deduplicationHash = "huella-b")

        assertFalse(detector.isDuplicate(candidate, listOf(existing)))
    }

    // --- Huella: respaldo solo cuando falta una clave ---

    @Test
    fun `la huella es respaldo cuando el candidato no tiene clave`() {
        val candidate = movement(notificationKey = null, deduplicationHash = "huella")
        val existing = movement(
            notificationKey = "clave-1",
            deduplicationHash = "huella",
            occurredAt = base.plusSeconds(30),
        )

        assertTrue(detector.isDuplicate(candidate, listOf(existing)))
    }

    @Test
    fun `la huella es respaldo cuando el guardado no tiene clave`() {
        val candidate = movement(notificationKey = "clave-1", deduplicationHash = "huella")
        val existing = movement(
            notificationKey = null,
            deduplicationHash = "huella",
            occurredAt = base.plusSeconds(30),
        )

        assertTrue(detector.isDuplicate(candidate, listOf(existing)))
    }

    @Test
    fun `una huella repetida dentro de la ventana es duplicado`() {
        val candidate = movement(deduplicationHash = "huella")
        val existing = movement(deduplicationHash = "huella", occurredAt = base.plusSeconds(60))

        assertTrue(detector.isDuplicate(candidate, listOf(existing)))
    }

    @Test
    fun `una huella repetida justo en el límite de la ventana es duplicado`() {
        val candidate = movement(deduplicationHash = "huella")
        val existing = movement(
            deduplicationHash = "huella",
            occurredAt = base.plusMillis(DuplicateDetector.DEFAULT_WINDOW.toMillis()),
        )

        assertTrue(detector.isDuplicate(candidate, listOf(existing)))
    }

    @Test
    fun `la misma huella fuera de la ventana no es duplicado`() {
        val candidate = movement(deduplicationHash = "huella")
        val existing = movement(
            deduplicationHash = "huella",
            occurredAt = base.plus(DuplicateDetector.DEFAULT_WINDOW).plusMillis(1),
        )

        assertFalse(detector.isDuplicate(candidate, listOf(existing)))
    }

    @Test
    fun `huellas distintas dentro de la ventana no son duplicado`() {
        val candidate = movement(deduplicationHash = "huella-a", notificationKey = null)
        val existing = movement(deduplicationHash = "huella-b", occurredAt = base.plusSeconds(30))

        assertFalse(detector.isDuplicate(candidate, listOf(existing)))
    }

    @Test
    fun `sin clave ni huella no es duplicado`() {
        assertFalse(detector.isDuplicate(movement(), listOf(movement())))
    }

    @Test
    fun `una ventana configurable amplía el respaldo por huella`() {
        val wide = DuplicateDetector(window = Duration.ofHours(2))
        val candidate = movement(deduplicationHash = "huella")
        val existing = movement(deduplicationHash = "huella", occurredAt = base.plusSeconds(3_600))

        assertFalse(detector.isDuplicate(candidate, listOf(existing)))
        assertTrue(wide.isDuplicate(candidate, listOf(existing)))
    }

    @Test
    fun `instantes extremos no desbordan la comparación temporal`() {
        val candidate = movement(
            deduplicationHash = "huella",
            occurredAt = Instant.ofEpochMilli(Long.MIN_VALUE),
        )
        val existing = movement(
            deduplicationHash = "huella",
            occurredAt = Instant.ofEpochMilli(Long.MAX_VALUE),
        )

        assertFalse(detector.isDuplicate(candidate, listOf(existing)))
    }

    @Test
    fun `rechaza una ventana negativa`() {
        assertThrows(IllegalArgumentException::class.java) {
            DuplicateDetector(window = Duration.ofMillis(-1))
        }
    }
}
