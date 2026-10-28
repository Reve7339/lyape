package com.lyape.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryLabelsTest {

    @Test
    fun `el resumen compacto usa singular cuando hay uno`() {
        assertEquals(
            "1 registro · S/ 0.10 personal",
            compactSummaryLabel(records = 1, personalCents = 10L),
        )
    }

    @Test
    fun `el resumen compacto usa plural cuando hay varios`() {
        assertEquals(
            "8 registros · S/ 12.50 personal",
            compactSummaryLabel(records = 8, personalCents = 1250L),
        )
    }

    @Test
    fun `el conteo de negocio usa singular y plural`() {
        assertEquals("1 de negocio", businessCountLabel(1))
        assertEquals("6 de negocio", businessCountLabel(6))
    }

    @Test
    fun `los pendientes de hoy usan singular y plural`() {
        assertEquals("Sin pendientes hoy", pendingTodayLabel(0))
        assertEquals("1 pendiente hoy", pendingTodayLabel(1))
        assertEquals("4 pendientes hoy", pendingTodayLabel(4))
    }
}
