package com.lyape.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class TotalsCalculatorTest {

    private val lima = ZoneId.of("America/Lima")

    private fun movement(
        amountCents: Long,
        classification: MovementClassification = MovementClassification.BUSINESS,
        source: MovementSource = MovementSource.NOTIFICATION,
        reviewed: Boolean = true,
        occurredAt: Instant = Instant.parse("2026-10-14T18:00:00Z"),
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

    @Test
    fun `una lista vacia da totales en cero`() {
        assertEquals(Totals.Empty, TotalsCalculator.totalFor(emptyList()))
    }

    @Test
    fun `suma solo los movimientos de negocio`() {
        val totals = TotalsCalculator.totalFor(
            listOf(
                movement(1_000, MovementClassification.BUSINESS),
                movement(500, MovementClassification.PERSONAL),
                movement(2_000, MovementClassification.BUSINESS),
                movement(300, MovementClassification.EXCLUDED),
            ),
        )

        assertEquals(3_000, totals.businessCents)
        assertEquals(500, totals.personalCents)
        assertEquals(4, totals.movementCount)
    }

    @Test
    fun `los ajustes negativos de negocio restan del total`() {
        val totals = TotalsCalculator.totalFor(
            listOf(
                movement(10_000, MovementClassification.BUSINESS),
                movement(
                    amountCents = -2_500,
                    classification = MovementClassification.BUSINESS,
                    source = MovementSource.ADJUSTMENT,
                ),
            ),
        )

        assertEquals(7_500, totals.businessCents)
    }

    @Test
    fun `un ajuste personal o anulado no participa del total`() {
        val totals = TotalsCalculator.totalFor(
            listOf(
                movement(
                    amountCents = -3_000,
                    classification = MovementClassification.PERSONAL,
                    source = MovementSource.ADJUSTMENT,
                ),
                movement(
                    amountCents = -1_000,
                    classification = MovementClassification.EXCLUDED,
                    source = MovementSource.ADJUSTMENT,
                ),
                movement(4_000, MovementClassification.BUSINESS),
            ),
        )

        assertEquals(4_000, totals.businessCents)
        assertEquals(-3_000, totals.personalCents)
    }

    @Test
    fun `cuenta los movimientos pendientes de revision`() {
        val totals = TotalsCalculator.totalFor(
            listOf(
                movement(100, reviewed = false),
                movement(100, reviewed = true),
                movement(100, classification = MovementClassification.PERSONAL, reviewed = false),
            ),
        )

        assertEquals(2, totals.pendingCount)
    }

    @Test
    fun `businessTotalCents coincide con el total de negocio`() {
        val movements = listOf(
            movement(1_234, MovementClassification.BUSINESS),
            movement(-234, MovementClassification.BUSINESS, source = MovementSource.ADJUSTMENT),
            movement(999, MovementClassification.EXCLUDED),
        )

        assertEquals(1_000, TotalsCalculator.businessTotalCents(movements))
    }

    @Test
    fun `una suma final representable no depende del orden`() {
        val movements = listOf(
            movement(Long.MAX_VALUE),
            movement(1),
            movement(-1),
        )

        assertEquals(Long.MAX_VALUE, TotalsCalculator.totalFor(movements).businessCents)
        assertEquals(
            Long.MAX_VALUE,
            TotalsCalculator.totalFor(movements.reversed()).businessCents,
        )
    }

    @Test
    fun `un total de negocio fuera de rango se marca como error y no se satura`() {
        val totals = TotalsCalculator.totalFor(
            listOf(
                movement(Long.MAX_VALUE),
                movement(1),
            ),
        )

        assertEquals(0L, totals.businessCents)
        assertTrue(totals.calculationError)
        assertEquals(2, totals.movementCount)
    }

    @Test
    fun `un total personal fuera de rango se marca como error`() {
        val totals = TotalsCalculator.totalFor(
            listOf(
                movement(Long.MIN_VALUE, MovementClassification.PERSONAL),
                movement(-1, MovementClassification.PERSONAL),
            ),
        )

        assertEquals(0L, totals.personalCents)
        assertTrue(totals.calculationError)
    }

    @Test(expected = ArithmeticException::class)
    fun `businessTotalCents falla de forma explícita si no cabe`() {
        TotalsCalculator.businessTotalCents(
            listOf(movement(Long.MAX_VALUE), movement(1)),
        )
    }

    @Test
    fun `los limites de dia mes y anio separan los movimientos con totales conocidos`() {
        // Lima = UTC-5 todo el año.
        val movements = listOf(
            movement(1_000, occurredAt = Instant.parse("2025-12-31T23:30:00Z")), // 31 dic 2025
            movement(2_000, occurredAt = Instant.parse("2026-01-01T05:00:00Z")), // 1 ene 2026 00:00
            movement(3_000, occurredAt = Instant.parse("2026-10-14T05:00:00Z")), // 14 oct 00:00
            movement(4_000, occurredAt = Instant.parse("2026-10-15T04:59:59Z")), // 14 oct 23:59:59
            movement(5_000, occurredAt = Instant.parse("2026-10-15T05:00:00Z")), // 15 oct 00:00
            movement(
                amountCents = -500,
                source = MovementSource.ADJUSTMENT,
                occurredAt = Instant.parse("2026-10-14T18:00:00Z"), // 14 oct
            ),
        )

        fun totalFor(period: PeriodType, date: LocalDate): Long {
            val range = DateRange.of(period, date, lima)
            return TotalsCalculator.totalFor(movements.filter { range.contains(it.occurredAt) })
                .businessCents
        }

        assertEquals(6_500, totalFor(PeriodType.DAY, LocalDate.of(2026, 10, 14))) // 3000+4000-500
        assertEquals(5_000, totalFor(PeriodType.DAY, LocalDate.of(2026, 10, 15)))
        assertEquals(11_500, totalFor(PeriodType.MONTH, LocalDate.of(2026, 10, 15)))
        assertEquals(2_000, totalFor(PeriodType.MONTH, LocalDate.of(2026, 1, 15)))
        assertEquals(13_500, totalFor(PeriodType.YEAR, LocalDate.of(2026, 1, 1)))
        assertEquals(1_000, totalFor(PeriodType.YEAR, LocalDate.of(2025, 1, 1)))
        assertEquals(11_500, totalFor(PeriodType.WEEK, LocalDate.of(2026, 10, 14)))
    }
}
