package com.lyape.ui.movements

import com.lyape.domain.MovementCounts
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementFilterCountsTest {

    @Test
    fun `cuenta movimientos por filtro`() {
        val counts = MovementCounts(total = 4, business = 2, personal = 1, excluded = 1).filterCounts()

        assertEquals(4, counts[MovementFilter.ALL])
        assertEquals(2, counts[MovementFilter.BUSINESS])
        assertEquals(1, counts[MovementFilter.PERSONAL])
        assertEquals(1, counts[MovementFilter.EXCLUDED])
    }

    @Test
    fun `lista vacia devuelve ceros`() {
        val counts = MovementCounts().filterCounts()

        assertEquals(0, counts[MovementFilter.ALL])
        assertEquals(0, counts[MovementFilter.BUSINESS])
        assertEquals(0, counts[MovementFilter.PERSONAL])
        assertEquals(0, counts[MovementFilter.EXCLUDED])
    }

    @Test
    fun `el filtro Todos incluye a los anulados`() {
        val counts = MovementCounts(total = 2, excluded = 2).filterCounts()

        assertEquals(2, counts[MovementFilter.ALL])
        assertEquals(0, counts[MovementFilter.BUSINESS])
        assertEquals(0, counts[MovementFilter.PERSONAL])
        assertEquals(2, counts[MovementFilter.EXCLUDED])
    }

}
