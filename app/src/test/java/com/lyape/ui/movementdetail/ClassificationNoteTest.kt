package com.lyape.ui.movementdetail

import com.lyape.domain.MovementClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassificationNoteTest {

    @Test
    fun `negocio no muestra nota`() {
        assertNull(classificationNote(MovementClassification.BUSINESS, excluded = false))
    }

    @Test
    fun `personal indica que no suma a los totales`() {
        assertEquals(
            "No se suma a los totales.",
            classificationNote(MovementClassification.PERSONAL, excluded = false),
        )
    }

    @Test
    fun `anulado indica que no suma y permanece en el historial`() {
        assertEquals(
            "No se suma y permanece en el historial.",
            classificationNote(MovementClassification.EXCLUDED, excluded = true),
        )
    }
}
