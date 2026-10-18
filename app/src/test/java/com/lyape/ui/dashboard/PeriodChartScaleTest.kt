package com.lyape.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodChartScaleTest {

    @Test
    fun `magnitud negativa convierte antes de negar para aceptar Long MIN VALUE`() {
        val magnitude = negativeMagnitudeForChart(Long.MIN_VALUE)

        assertTrue(magnitude.isFinite())
        assertTrue(magnitude > 0.0)
        assertEquals(9.223372036854776E18, magnitude, 0.0)
    }

    @Test
    fun `magnitud para cero o positivo es cero`() {
        assertEquals(0.0, negativeMagnitudeForChart(0L), 0.0)
        assertEquals(0.0, negativeMagnitudeForChart(Long.MAX_VALUE), 0.0)
    }
}
