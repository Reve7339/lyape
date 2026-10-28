package com.lyape.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Lima no aplica horario de verano, así que los instantes locales tienen un desfase fijo de
 * −05:00. Eso permite escribir los límites esperados como instantes absolutos conocidos.
 */
class PeriodReportCalculatorTest {

    private val lima = ZoneId.of("America/Lima")

    @Test
    fun `el dia arma seis tramos de cuatro horas`() {
        val report = PeriodReportCalculator.calculate(
            movements = listOf(
                movement(1_000, at(2026, 10, 15, 0, 30)),
                movement(2_000, at(2026, 10, 15, 8, 0)),
                movement(3_000, at(2026, 10, 15, 13, 59)),
                movement(4_000, at(2026, 10, 15, 20, 1)),
            ),
            period = ReportPeriod.DAY,
            reference = LocalDate.of(2026, 10, 15),
            zone = lima,
        )

        assertEquals(listOf("00", "04", "08", "12", "16", "20"), report.bars.map { it.label })
        assertEquals(
            listOf(1_000L, 0L, 2_000L, 3_000L, 0L, 4_000L),
            report.bars.map { it.amountCents },
        )
        assertEquals(10_000L, report.totals.businessCents)
        assertEquals(report.totals.businessCents, report.bars.sumOf { it.amountCents })
    }

    @Test
    fun `el dia usa los limites semiabiertos de la zona local`() {
        // 23:59:59 del día anterior y 00:00:00 del día siguiente quedan fuera; el inicio entra.
        val report = PeriodReportCalculator.calculate(
            movements = listOf(
                movement(1L, Instant.parse("2026-10-15T04:59:59Z")),
                movement(2L, Instant.parse("2026-10-15T05:00:00Z")),
                movement(4L, Instant.parse("2026-10-16T05:00:00Z")),
            ),
            period = ReportPeriod.DAY,
            reference = LocalDate.of(2026, 10, 15),
            zone = lima,
        )

        assertEquals(2L, report.totals.businessCents)
        assertEquals(1, report.totals.movementCount)
    }

    @Test
    fun `la semana va de lunes a domingo`() {
        // El 15 de octubre de 2026 es jueves; la semana es 12–18.
        val report = PeriodReportCalculator.calculate(
            movements = listOf(
                movement(1_000, at(2026, 10, 12, 9, 0)),
                movement(2_000, at(2026, 10, 18, 23, 0)),
            ),
            period = ReportPeriod.WEEK,
            reference = LocalDate.of(2026, 10, 15),
            zone = lima,
        )

        assertEquals(LocalDate.of(2026, 10, 12), report.startDate)
        assertEquals(LocalDate.of(2026, 10, 18), report.endDateInclusive)
        assertEquals(listOf("L", "M", "X", "J", "V", "S", "D"), report.bars.map { it.label })
        assertEquals(1_000L, report.bars[0].amountCents)
        assertEquals(2_000L, report.bars[6].amountCents)
        assertEquals(3_000L, report.bars.sumOf { it.amountCents })
    }

    @Test
    fun `la semana resuelve el cambio de mes`() {
        // El 1 de octubre de 2026 es jueves: la semana empieza el lunes 28 de septiembre.
        val report = PeriodReportCalculator.calculate(
            movements = emptyList(),
            period = ReportPeriod.WEEK,
            reference = LocalDate.of(2026, 10, 1),
            zone = lima,
        )

        assertEquals(LocalDate.of(2026, 9, 28), report.startDate)
        assertEquals(LocalDate.of(2026, 10, 4), report.endDateInclusive)
        assertEquals(7, report.bars.size)
    }

    @Test
    fun `el mes tiene una barra por dia`() {
        val report = PeriodReportCalculator.calculate(
            movements = listOf(
                movement(1_000, at(2026, 10, 1, 12, 0)),
                movement(2_000, at(2026, 10, 31, 12, 0)),
            ),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 10, 15),
            zone = lima,
        )

        assertEquals(31, report.bars.size)
        assertEquals("1", report.bars.first().label)
        assertEquals("31", report.bars.last().label)
        assertEquals(1_000L, report.bars[0].amountCents)
        assertEquals(2_000L, report.bars[30].amountCents)
    }

    @Test
    fun `el mes excluye el primer instante del mes siguiente`() {
        val report = PeriodReportCalculator.calculate(
            movements = listOf(
                movement(1L, Instant.parse("2026-11-01T05:00:00Z")),
                movement(2L, Instant.parse("2026-10-01T05:00:00Z")),
                movement(4L, Instant.parse("2026-10-31T23:59:00Z")),
            ),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 10, 15),
            zone = lima,
        )

        assertEquals(6L, report.totals.businessCents)
        assertEquals(2, report.totals.movementCount)
    }

    @Test
    fun `el mes admite 28 29 30 y 31 dias`() {
        fun barCount(year: Int, month: Int): Int = PeriodReportCalculator.calculate(
            movements = emptyList(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(year, month, 10),
            zone = lima,
        ).bars.size

        assertEquals(28, barCount(2026, 2))
        assertEquals(29, barCount(2028, 2))
        assertEquals(30, barCount(2026, 4))
        assertEquals(31, barCount(2026, 10))
    }

    @Test
    fun `un ajuste negativo resta en su tramo y en el total`() {
        val report = PeriodReportCalculator.calculate(
            movements = listOf(
                movement(5_000, at(2026, 10, 15, 12, 0)),
                movement(-1_500, at(2026, 10, 15, 12, 30), source = MovementSource.ADJUSTMENT),
            ),
            period = ReportPeriod.DAY,
            reference = LocalDate.of(2026, 10, 15),
            zone = lima,
        )

        assertEquals(3_500L, report.bars[3].amountCents)
        assertEquals(3_500L, report.totals.businessCents)
        assertEquals(3_500L, report.bars.sumOf { it.amountCents })
    }

    @Test
    fun `personal y anulado quedan fuera del total de negocio`() {
        val report = PeriodReportCalculator.calculate(
            movements = listOf(
                movement(4_000, at(2026, 10, 15, 12, 0)),
                movement(9_000, at(2026, 10, 15, 12, 10), MovementClassification.PERSONAL),
                movement(7_000, at(2026, 10, 15, 12, 20), MovementClassification.EXCLUDED),
            ),
            period = ReportPeriod.DAY,
            reference = LocalDate.of(2026, 10, 15),
            zone = lima,
        )

        assertEquals(4_000L, report.totals.businessCents)
        assertEquals(4_000L, report.bars.sumOf { it.amountCents })
        assertEquals(9_000L, report.totals.personalCents)
        assertEquals(3, report.totals.movementCount)
        assertEquals(1, report.businessMovementCount)
    }

    @Test
    fun `cuenta los pendientes activos sin anulados`() {
        val report = PeriodReportCalculator.calculate(
            movements = listOf(
                movement(1L, at(2026, 10, 15, 9, 0), reviewed = false),
                movement(2L, at(2026, 10, 15, 10, 0), MovementClassification.PERSONAL, reviewed = false),
                movement(3L, at(2026, 10, 15, 11, 0), MovementClassification.EXCLUDED, reviewed = false),
                movement(4L, at(2026, 10, 15, 12, 0), reviewed = true),
            ),
            period = ReportPeriod.DAY,
            reference = LocalDate.of(2026, 10, 15),
            zone = lima,
        )

        assertEquals(2, report.pendingActiveCount)
    }

    @Test
    fun `un periodo vacio no tiene movimientos de negocio`() {
        val report = PeriodReportCalculator.calculate(
            movements = emptyList(),
            period = ReportPeriod.MONTH,
            reference = LocalDate.of(2026, 10, 15),
            zone = lima,
        )

        assertTrue(report.bars.all { it.amountCents == 0L })
        assertEquals(0L, report.totals.businessCents)
        assertEquals(0L, report.bars.sumOf { it.amountCents })
        assertFalse(report.hasBusinessMovements)
        assertEquals(0, report.pendingActiveCount)
    }

    @Test
    fun `solo se etiquetan tramos espaciados del mes`() {
        assertEquals(
            (0 until 6).toSet(),
            PeriodReportCalculator.visibleLabelIndices(ReportPeriod.DAY, 6),
        )
        assertEquals(
            (0 until 7).toSet(),
            PeriodReportCalculator.visibleLabelIndices(ReportPeriod.WEEK, 7),
        )
        assertEquals(
            setOf(0, 7, 14, 21, 30),
            PeriodReportCalculator.visibleLabelIndices(ReportPeriod.MONTH, 31),
        )
        assertEquals(
            setOf(0, 7, 14, 21, 27),
            PeriodReportCalculator.visibleLabelIndices(ReportPeriod.MONTH, 28),
        )
        assertTrue(PeriodReportCalculator.visibleLabelIndices(ReportPeriod.MONTH, 0).isEmpty())
    }

    @Test
    fun `un total fuera de rango se marca en el reporte`() {
        val report = PeriodReportCalculator.calculate(
            movements = listOf(
                movement(Long.MAX_VALUE, at(2026, 10, 15, 9, 0)),
                movement(1L, at(2026, 10, 15, 10, 0)),
            ),
            period = ReportPeriod.DAY,
            reference = LocalDate.of(2026, 10, 15),
            zone = lima,
        )

        assertTrue(report.calculationError)
        assertTrue(report.totals.calculationError)
        assertTrue(report.bars.any { it.calculationError })
    }

    @Test
    fun `rangeFor no expone el periodo anual`() {
        val day = PeriodReportCalculator.rangeFor(ReportPeriod.DAY, LocalDate.of(2026, 10, 15), lima)
        val week = PeriodReportCalculator.rangeFor(ReportPeriod.WEEK, LocalDate.of(2026, 10, 15), lima)
        val month = PeriodReportCalculator.rangeFor(ReportPeriod.MONTH, LocalDate.of(2026, 10, 15), lima)

        assertEquals(DateRange.day(LocalDate.of(2026, 10, 15), lima), day)
        assertEquals(DateRange.week(LocalDate.of(2026, 10, 15), lima), week)
        assertEquals(DateRange.month(LocalDate.of(2026, 10, 15), lima), month)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(lima).toInstant()

    private fun movement(
        amountCents: Long,
        occurredAt: Instant,
        classification: MovementClassification = MovementClassification.BUSINESS,
        reviewed: Boolean = true,
        source: MovementSource = MovementSource.NOTIFICATION,
    ): Movement = Movement(
        id = UUID.randomUUID(),
        amountCents = amountCents,
        occurredAt = occurredAt,
        capturedAt = occurredAt,
        source = source,
        classification = classification,
        reviewed = reviewed,
        createdAt = occurredAt,
        updatedAt = occurredAt,
    )
}
