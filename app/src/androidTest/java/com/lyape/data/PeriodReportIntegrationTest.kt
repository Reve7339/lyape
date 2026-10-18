package com.lyape.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lyape.domain.Movement
import com.lyape.domain.MovementClassification
import com.lyape.domain.MovementSource
import com.lyape.domain.MonthlyChartCalculator
import com.lyape.domain.PeriodReport
import com.lyape.domain.PeriodReportCalculator
import com.lyape.domain.ReportPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Comprueba el recorrido Room → reporte de periodo que usa el resumen. */
@RunWith(AndroidJUnit4::class)
class PeriodReportIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var movements: MovementRepository
    private val zone = ZoneId.of("America/Lima")

    @Before
    fun createDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        movements = MovementRepository(database.movementDao())
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun elMesReparteLosMovimientosEnSusDias() = runBlocking {
        movements.addAll(
            listOf(
                movement(1_000, at(5, 12)),
                movement(2_000, at(5, 13)),
                movement(3_000, at(12, 9)),
                movement(4_000, at(31, 20)),
            ),
        )

        val report = report(ReportPeriod.MONTH, LocalDate.of(2026, 10, 15))

        assertEquals(31, report.bars.size)
        assertEquals(3_000L, report.bars[4].amountCents)
        assertEquals(3_000L, report.bars[11].amountCents)
        assertEquals(4_000L, report.bars[30].amountCents)
        assertEquals(10_000L, report.totals.businessCents)
        assertEquals(report.totals.businessCents, report.bars.sumOf { it.amountCents })
    }

    @Test
    fun laSemanaAgrupaDeLunesADomingo() = runBlocking {
        movements.addAll(
            listOf(
                movement(1_000, at(12, 9)),
                movement(2_000, at(15, 15)),
                movement(3_000, at(18, 20)),
            ),
        )

        val report = report(ReportPeriod.WEEK, LocalDate.of(2026, 10, 15))

        assertEquals(LocalDate.of(2026, 10, 12), report.startDate)
        assertEquals(LocalDate.of(2026, 10, 18), report.endDateInclusive)
        assertEquals(1_000L, report.bars[0].amountCents)
        assertEquals(2_000L, report.bars[3].amountCents)
        assertEquals(3_000L, report.bars[6].amountCents)
    }

    @Test
    fun reclasificarYAnularActualizanElReporte() = runBlocking {
        val first = movement(1_000, at(5, 12))
        val second = movement(2_000, at(5, 13))
        movements.addAll(listOf(first, second))

        assertEquals(3_000L, report(ReportPeriod.MONTH, LocalDate.of(2026, 10, 15)).totals.businessCents)

        movements.classify(first.id, MovementClassification.PERSONAL)
        val afterClassify = report(ReportPeriod.MONTH, LocalDate.of(2026, 10, 15))
        assertEquals(2_000L, afterClassify.totals.businessCents)
        assertEquals(1_000L, afterClassify.totals.personalCents)

        movements.exclude(second.id)
        val afterExclude = report(ReportPeriod.MONTH, LocalDate.of(2026, 10, 15))
        assertEquals(0L, afterExclude.totals.businessCents)
    }

    @Test
    fun elLimiteDelPeriodoExcluyeElInstanteSiguiente() = runBlocking {
        movements.addAll(
            listOf(
                movement(1_000, Instant.parse("2026-10-15T05:00:00Z")),
                movement(2_000, Instant.parse("2026-10-16T05:00:00Z")),
                movement(4_000, Instant.parse("2026-11-01T05:00:00Z")),
            ),
        )

        val day = report(ReportPeriod.DAY, LocalDate.of(2026, 10, 15))
        assertEquals(1_000L, day.totals.businessCents)
        assertEquals(1, day.totals.movementCount)

        val month = report(ReportPeriod.MONTH, LocalDate.of(2026, 10, 15))
        assertEquals(3_000L, month.totals.businessCents)
        assertEquals(2, month.totals.movementCount)
    }

    @Test
    fun cerrarYReabrirElDiaNoAlteraElReporte() = runBlocking {
        val date = LocalDate.of(2026, 10, 15)
        movements.addAll(listOf(movement(1_500, at(15, 10)), movement(2_500, at(15, 11))))

        assertEquals(2, report(ReportPeriod.DAY, date).pendingActiveCount)

        assertEquals(2, movements.closeDay(date))
        assertEquals(0, report(ReportPeriod.DAY, date).pendingActiveCount)
        assertEquals(4_000L, report(ReportPeriod.DAY, date).totals.businessCents)

        assertEquals(2, movements.reopenDay(date))
        assertEquals(2, report(ReportPeriod.DAY, date).pendingActiveCount)

        val observed = movements
            .observeInRange(PeriodReportCalculator.rangeFor(ReportPeriod.DAY, date, zone))
            .first()
        assertTrue(observed.all { it.classification == MovementClassification.BUSINESS })
    }

    @Test
    fun elGraficoMensualAcumulaLosDiasDesdeRoom() = runBlocking {
        movements.addAll(
            listOf(
                movement(1_000, at(5, 12)),
                movement(2_000, at(12, 9)),
                movement(-500, at(12, 10), source = MovementSource.ADJUSTMENT),
                movement(3_000, at(31, 20)),
            ),
        )

        val report = report(ReportPeriod.MONTH, LocalDate.of(2026, 10, 15))
        val series = MonthlyChartCalculator.calculate(
            bars = report.bars,
            expensesCents = 4_000L,
            goalCents = 6_000L,
        )

        assertEquals(31, series.points.size)
        // Día 4 = 0; día 5 = 1_000; día 12 = 2_000 - 500 = 2_500; día 31 = 5_500.
        assertEquals(0L, series.points[3].cumulativeCents)
        assertEquals(1_000L, series.points[4].cumulativeCents)
        assertEquals(2_500L, series.points[11].cumulativeCents)
        assertEquals(report.totals.businessCents, series.points.last().cumulativeCents)
        assertEquals(4_000L, series.thresholds.first().amountCents)
        assertEquals(6_000L, series.thresholds.last().amountCents)
    }

    private suspend fun report(period: ReportPeriod, reference: LocalDate): PeriodReport {
        val range = PeriodReportCalculator.rangeFor(period, reference, zone)
        return PeriodReportCalculator.calculate(movements.movementsInRange(range), period, reference, zone)
    }

    private fun at(day: Int, hour: Int): Instant =
        LocalDateTime.of(2026, 10, day, hour, 0).atZone(zone).toInstant()

    private fun movement(
        amountCents: Long,
        occurredAt: Instant,
        source: MovementSource = MovementSource.NOTIFICATION,
    ): Movement = Movement(
        id = UUID.randomUUID(),
        amountCents = amountCents,
        occurredAt = occurredAt,
        capturedAt = Instant.now(),
        source = source,
        classification = MovementClassification.BUSINESS,
        reviewed = false,
        createdAt = occurredAt,
        updatedAt = occurredAt,
    )
}
