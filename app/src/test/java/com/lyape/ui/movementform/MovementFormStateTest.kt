package com.lyape.ui.movementform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MovementFormStateTest {

    private val base = MovementFormValues(
        kind = MovementFormKind.PAYMENT,
        amountText = "35.50",
        dateText = "15/10/2026",
        timeText = "09:30",
        labelText = "Bodega",
    )

    @Test
    fun `un formulario igual al inicial no tiene cambios`() {
        assertFalse(base.hasChangesFrom(base.copy()))
    }

    @Test
    fun `cambiar cualquier campo cuenta como cambio`() {
        assertTrue(base.copy(amountText = "40.00").hasChangesFrom(base))
        assertTrue(base.copy(kind = MovementFormKind.ADJUSTMENT).hasChangesFrom(base))
        assertTrue(base.copy(dateText = "16/10/2026").hasChangesFrom(base))
        assertTrue(base.copy(timeText = "10:00").hasChangesFrom(base))
        assertTrue(base.copy(labelText = "").hasChangesFrom(base))
        assertTrue(base.copy(adjustmentSign = AdjustmentSign.NEGATIVE).hasChangesFrom(base))
        assertTrue(base.copy(accountId = "yape").hasChangesFrom(base))
        assertTrue(base.copy(classification = com.lyape.domain.MovementClassification.PERSONAL).hasChangesFrom(base))
    }

    @Test
    fun `volver al valor inicial deja de contar como cambio`() {
        val edited = base.copy(amountText = "99.99")
        assertTrue(edited.hasChangesFrom(base))

        val reverted = edited.copy(amountText = base.amountText)

        assertFalse(reverted.hasChangesFrom(base))
    }

    @Test
    fun `el formulario sobrevive a la serializacion`() {
        assertEquals(base, movementFormValuesFromSaved(base.toSavedValues()))
        val personal = base.copy(accountId = "bank", classification = com.lyape.domain.MovementClassification.PERSONAL)
        assertEquals(personal, movementFormValuesFromSaved(personal.toSavedValues()))
        assertEquals(base, movementFormValuesFromSaved(base.toSavedValues().take(7)))
    }

    @Test
    fun `un ajuste conserva su signo al restaurarse`() {
        val adjustment = base.copy(
            kind = MovementFormKind.ADJUSTMENT,
            adjustmentSign = AdjustmentSign.NEGATIVE,
        )

        assertEquals(adjustment, movementFormValuesFromSaved(adjustment.toSavedValues()))
    }

    @Test
    fun `un contenido incompleto no se restaura`() {
        assertNull(movementFormValuesFromSaved(listOf("PAYMENT", "1.00")))
    }

    @Test
    fun `un tipo desconocido no se restaura`() {
        val saved = base.toSavedValues().toMutableList()
        saved[0] = "DESCONOCIDO"

        assertNull(movementFormValuesFromSaved(saved))
    }

    @Test
    fun `la cuenta se conserva y admite quitarse`() {
        val selected = base.copy(accountId = "bank-1")
        assertEquals(selected, movementFormValuesFromSaved(selected.toSavedValues()))
        assertTrue(selected.copy(accountId = null).hasChangesFrom(selected))
    }

    @Test
    fun `el estado de version anterior se restaura sin inventar cuenta`() {
        assertEquals(base, movementFormValuesFromSaved(base.toSavedValues().take(6)))
    }

    @Test
    fun `editar solo importe mantiene segundos posteriores a la base de saldo`() {
        val original = Instant.parse("2026-10-15T14:30:45.123Z")
        val rounded = Instant.parse("2026-10-15T14:30:00Z")
        assertEquals(original, preserveTimestampPrecision(base.copy(amountText = "99"), base, rounded, original))
    }

    @Test
    fun `cambiar fecha u hora usa la nueva fecha elegida`() {
        val original = Instant.parse("2026-10-15T14:30:45.123Z")
        val parsed = Instant.parse("2026-10-15T15:00:00Z")
        assertEquals(parsed, preserveTimestampPrecision(base.copy(timeText = "10:00"), base, parsed, original))
    }
}
