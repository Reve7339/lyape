package com.lyape.ui.movements

import com.lyape.domain.MovementClassification
import com.lyape.ui.model.MovementUi
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementFilterCountsTest {

    @Test
    fun `cuenta movimientos por filtro`() {
        val movements = listOf(
            ui("1", MovementClassification.BUSINESS),
            ui("2", MovementClassification.BUSINESS),
            ui("3", MovementClassification.PERSONAL),
            ui("4", MovementClassification.EXCLUDED),
        )

        val counts = filterCounts(movements)

        assertEquals(4, counts[MovementFilter.ALL])
        assertEquals(2, counts[MovementFilter.BUSINESS])
        assertEquals(1, counts[MovementFilter.PERSONAL])
        assertEquals(1, counts[MovementFilter.EXCLUDED])
    }

    @Test
    fun `lista vacia devuelve ceros`() {
        val counts = filterCounts(emptyList())

        assertEquals(0, counts[MovementFilter.ALL])
        assertEquals(0, counts[MovementFilter.BUSINESS])
        assertEquals(0, counts[MovementFilter.PERSONAL])
        assertEquals(0, counts[MovementFilter.EXCLUDED])
    }

    @Test
    fun `el filtro Todos incluye a los anulados`() {
        val movements = listOf(
            ui("1", MovementClassification.EXCLUDED),
            ui("2", MovementClassification.EXCLUDED),
        )

        val counts = filterCounts(movements)

        assertEquals(2, counts[MovementFilter.ALL])
        assertEquals(0, counts[MovementFilter.BUSINESS])
        assertEquals(0, counts[MovementFilter.PERSONAL])
        assertEquals(2, counts[MovementFilter.EXCLUDED])
    }

    private fun ui(id: String, classification: MovementClassification): MovementUi = MovementUi(
        id = id,
        title = "Movimiento $id",
        amountCents = 0L,
        classification = classification,
        originLabel = "Detectado",
        supportingLabel = null,
        description = null,
        timeLabel = "",
        dateTimeLabel = "",
        avatarLabel = "",
        reviewed = false,
        previousClassification = null,
    )
}
