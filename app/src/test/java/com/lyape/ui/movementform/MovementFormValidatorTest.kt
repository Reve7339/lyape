package com.lyape.ui.movementform

import com.lyape.ui.format.instantOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class MovementFormValidatorTest {

    private val lima = ZoneId.of("America/Lima")

    private fun values(
        kind: MovementFormKind = MovementFormKind.PAYMENT,
        amountText: String = "35.00",
        dateText: String = "15/10/2026",
        timeText: String = "17:42",
        labelText: String = "",
        adjustmentSign: AdjustmentSign = AdjustmentSign.POSITIVE,
    ): MovementFormValues = MovementFormValues(
        kind = kind,
        amountText = amountText,
        dateText = dateText,
        timeText = timeText,
        labelText = labelText,
        adjustmentSign = adjustmentSign,
    )

    @Test
    fun `un pago valido resuelve monto y fecha`() {
        val result = MovementFormValidator.validate(values(labelText = "  Bodega Luna "), lima)

        assertFalse(result.hasErrors)
        assertEquals(3_500L, result.amountCents)
        assertEquals("Bodega Luna", result.label)
        assertEquals(
            instantOf(LocalDate.of(2026, 10, 15), LocalTime.of(17, 42), lima),
            result.occurredAt,
        )
    }

    @Test
    fun `un pago sin etiqueta es valido`() {
        val result = MovementFormValidator.validate(values(), lima)

        assertFalse(result.hasErrors)
        assertNull(result.label)
    }

    @Test
    fun `un ajuste positivo mantiene el signo`() {
        val result = MovementFormValidator.validate(
            values(kind = MovementFormKind.ADJUSTMENT, labelText = "Propina"),
            lima,
        )
        assertEquals(3_500L, result.amountCents)
    }

    @Test
    fun `un ajuste negativo invierte el signo`() {
        val result = MovementFormValidator.validate(
            values(
                kind = MovementFormKind.ADJUSTMENT,
                labelText = "Devolución",
                adjustmentSign = AdjustmentSign.NEGATIVE,
            ),
            lima,
        )
        assertEquals(-3_500L, result.amountCents)
    }

    @Test
    fun `un ajuste exige motivo`() {
        val result = MovementFormValidator.validate(
            values(kind = MovementFormKind.ADJUSTMENT, labelText = "   "),
            lima,
        )

        assertTrue(result.hasErrors)
        assertNotNull(result.labelError)
    }

    @Test
    fun `un pago no exige etiqueta`() {
        val result = MovementFormValidator.validate(values(kind = MovementFormKind.PAYMENT), lima)
        assertNull(result.labelError)
    }

    @Test
    fun `un monto vacio, mal escrito o cero no resuelve`() {
        val empty = MovementFormValidator.validate(values(amountText = "  "), lima)
        assertNotNull(empty.amountError)
        assertNull(empty.amountCents)

        val malformed = MovementFormValidator.validate(values(amountText = "1,284.50"), lima)
        assertNotNull(malformed.amountError)

        val zero = MovementFormValidator.validate(values(amountText = "0"), lima)
        assertNotNull(zero.amountError)
        assertNull(zero.amountCents)
    }

    @Test
    fun `una fecha o una hora invalida impide resolver el instante`() {
        val badDate = MovementFormValidator.validate(values(dateText = "2026-10-15"), lima)
        assertNotNull(badDate.dateError)
        assertNull(badDate.occurredAt)

        val badTime = MovementFormValidator.validate(values(timeText = "5pm"), lima)
        assertNotNull(badTime.timeError)
        assertNull(badTime.occurredAt)
    }
}
