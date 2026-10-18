package com.lyape.ui.format

import com.lyape.domain.ReportPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PeriodLabelsTest {

    @Test
    fun `el dia se escribe completo`() {
        assertEquals(
            "15 octubre 2026",
            formatReportRangeLabel(
                ReportPeriod.DAY,
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 15),
            ),
        )
    }

    @Test
    fun `la semana dentro del mes usa un guion`() {
        assertEquals(
            "12–18 octubre 2026",
            formatReportRangeLabel(
                ReportPeriod.WEEK,
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 18),
            ),
        )
    }

    @Test
    fun `la semana que cambia de mes nombra los dos meses`() {
        // Semana del lunes 26 de octubre al domingo 1 de noviembre de 2026.
        assertEquals(
            "26 octubre–1 noviembre 2026",
            formatWeekRangeLabel(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)),
        )
    }

    @Test
    fun `la semana que cambia de anio incluye ambos anios`() {
        assertEquals(
            "28 diciembre 2026–3 enero 2027",
            formatWeekRangeLabel(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3)),
        )
    }

    @Test
    fun `el mes se escribe con su anio`() {
        assertEquals(
            "Octubre 2026",
            formatReportRangeLabel(
                ReportPeriod.MONTH,
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
            ),
        )
    }
}
