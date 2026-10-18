package com.lyape.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Lima no aplica horario de verano, así que la medianoche local siempre es 05:00Z. Eso permite
 * escribir los límites esperados como instantes absolutos conocidos.
 */
class DateRangeTest {

    private val lima = ZoneId.of("America/Lima")

    @Test
    fun `el dia empieza en la medianoche local`() {
        val range = DateRange.day(LocalDate.of(2026, 10, 14), lima)

        assertEquals(Instant.parse("2026-10-14T05:00:00Z"), range.start)
        assertEquals(Instant.parse("2026-10-15T05:00:00Z"), range.endExclusive)
    }

    @Test
    fun `el rango incluye el inicio y excluye el fin`() {
        val range = DateRange.day(LocalDate.of(2026, 10, 14), lima)

        assertTrue(range.contains(Instant.parse("2026-10-14T05:00:00Z")))
        assertTrue(range.contains(Instant.parse("2026-10-15T04:59:59.999Z")))
        assertFalse(range.contains(Instant.parse("2026-10-15T05:00:00Z")))
        assertFalse(range.contains(Instant.parse("2026-10-14T04:59:59.999Z")))
    }

    @Test
    fun `la semana va de lunes a lunes`() {
        // 2026-10-14 es miércoles; la semana empieza el lunes 12.
        val range = DateRange.week(LocalDate.of(2026, 10, 14), lima)

        assertEquals(Instant.parse("2026-10-12T05:00:00Z"), range.start)
        assertEquals(Instant.parse("2026-10-19T05:00:00Z"), range.endExclusive)
    }

    @Test
    fun `el domingo pertenece a la semana que empieza el lunes anterior`() {
        // 2026-10-18 es domingo.
        val range = DateRange.week(LocalDate.of(2026, 10, 18), lima)

        assertEquals(Instant.parse("2026-10-12T05:00:00Z"), range.start)
        assertEquals(Instant.parse("2026-10-19T05:00:00Z"), range.endExclusive)
    }

    @Test
    fun `el lunes es el primer dia de su propia semana`() {
        val range = DateRange.week(LocalDate.of(2026, 10, 12), lima)

        assertEquals(Instant.parse("2026-10-12T05:00:00Z"), range.start)
        assertEquals(Instant.parse("2026-10-19T05:00:00Z"), range.endExclusive)
    }

    @Test
    fun `el mes cubre del primero al primero del siguiente`() {
        val range = DateRange.month(LocalDate.of(2026, 10, 15), lima)

        assertEquals(Instant.parse("2026-10-01T05:00:00Z"), range.start)
        assertEquals(Instant.parse("2026-11-01T05:00:00Z"), range.endExclusive)
    }

    @Test
    fun `el mes de diciembre termina en enero del anio siguiente`() {
        val range = DateRange.month(LocalDate.of(2026, 12, 31), lima)

        assertEquals(Instant.parse("2026-12-01T05:00:00Z"), range.start)
        assertEquals(Instant.parse("2027-01-01T05:00:00Z"), range.endExclusive)
    }

    @Test
    fun `el anio cubre del 1 de enero al 1 de enero siguiente`() {
        val range = DateRange.year(LocalDate.of(2026, 6, 15), lima)

        assertEquals(Instant.parse("2026-01-01T05:00:00Z"), range.start)
        assertEquals(Instant.parse("2027-01-01T05:00:00Z"), range.endExclusive)
    }

    @Test
    fun `of selecciona el periodo pedido`() {
        val date = LocalDate.of(2026, 10, 14)

        assertEquals(DateRange.day(date, lima), DateRange.of(PeriodType.DAY, date, lima))
        assertEquals(DateRange.week(date, lima), DateRange.of(PeriodType.WEEK, date, lima))
        assertEquals(DateRange.month(date, lima), DateRange.of(PeriodType.MONTH, date, lima))
        assertEquals(DateRange.year(date, lima), DateRange.of(PeriodType.YEAR, date, lima))
    }

    @Test
    fun `of acepta un instante de referencia en la zona local`() {
        // 2026-10-14T18:00Z son las 13:00 en Lima del día 14.
        val reference = Instant.parse("2026-10-14T18:00:00Z")

        assertEquals(
            DateRange.day(LocalDate.of(2026, 10, 14), lima),
            DateRange.of(PeriodType.DAY, reference, lima),
        )
    }

    @Test
    fun `un rango vacio es valido`() {
        val instant = Instant.parse("2026-10-14T05:00:00Z")
        val range = DateRange(instant, instant)

        assertFalse(range.contains(instant))
    }
}
