package com.lyape.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Periodos que ofrece el resumen.
 *
 * Es un enum propio para no exponer [PeriodType.YEAR] en la interfaz: el resumen solo consulta
 * día, semana y mes.
 */
enum class ReportPeriod {
    DAY,
    WEEK,
    MONTH,
}

/** Una barra del gráfico: etiqueta corta e importe exacto en céntimos (`Long`). */
data class PeriodBar(
    val label: String,
    val amountCents: Long,
)

/**
 * Resultado del resumen de un periodo.
 *
 * Los importes siguen en céntimos: la conversión a `Float` ocurre únicamente al dibujar. Las
 * barras están ordenadas y su suma coincide exactamente con [Totals.businessCents] porque cada
 * movimiento cae en un único grupo.
 */
data class PeriodReport(
    val period: ReportPeriod,
    val range: DateRange,
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    val bars: List<PeriodBar>,
    val totals: Totals,
    /** Movimientos activos del negocio (los que participan en el total). */
    val businessMovementCount: Int,
    /** Pendientes activos: sin revisar y no anulados. */
    val pendingActiveCount: Int,
) {
    val hasBusinessMovements: Boolean get() = businessMovementCount > 0

    /** Mes del plan que corresponde al periodo consultado. */
    val month: YearMonth get() = YearMonth.from(startDate)
}

/**
 * Calcula el resumen de un periodo de forma pura y comprobable.
 *
 * No duplica la regla monetaria: cada grupo se totaliza con [TotalsCalculator]. El rango es
 * semiabierto `[start, endExclusive)` y la semana va de lunes a domingo en la zona indicada.
 */
object PeriodReportCalculator {

    private val WEEKDAY_LABELS = listOf("L", "M", "X", "J", "V", "S", "D")
    private const val DAY_BUCKET_HOURS = 4
    private const val DAY_BUCKETS = 24 / DAY_BUCKET_HOURS

    fun rangeFor(
        period: ReportPeriod,
        reference: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DateRange = when (period) {
        ReportPeriod.DAY -> DateRange.day(reference, zone)
        ReportPeriod.WEEK -> DateRange.week(reference, zone)
        ReportPeriod.MONTH -> DateRange.month(reference, zone)
    }

    fun calculate(
        movements: Iterable<Movement>,
        period: ReportPeriod,
        reference: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): PeriodReport {
        val range = rangeFor(period, reference, zone)
        // Solo los movimientos dentro del rango semiabierto participan, aunque el repositorio ya
        // los filtre: así el cálculo es correcto por sí mismo.
        val inRange = movements.filter { range.contains(it.occurredAt) }

        val startDate = range.start.atZone(zone).toLocalDate()
        val endDateInclusive = range.endExclusive.atZone(zone).toLocalDate().minusDays(1)

        val labels = labelsFor(period, startDate)
        val groups = List(labels.size) { mutableListOf<Movement>() }
        for (movement in inRange) {
            groups[indexFor(period, movement.occurredAt, zone)].add(movement)
        }

        val bars = groups.mapIndexed { index, group ->
            PeriodBar(
                label = labels[index],
                amountCents = TotalsCalculator.totalFor(group).businessCents,
            )
        }

        return PeriodReport(
            period = period,
            range = range,
            startDate = startDate,
            endDateInclusive = endDateInclusive,
            bars = bars,
            totals = TotalsCalculator.totalFor(inRange),
            businessMovementCount = inRange.count { it.participatesInTotals },
            pendingActiveCount = inRange.count {
                it.classification != MovementClassification.EXCLUDED && !it.reviewed
            },
        )
    }

    /**
     * Índices de barras cuya etiqueta conviene mostrar.
     *
     * En día y semana se muestran todas; en mes solo cada siete días y la última para evitar
     * saturación visual.
     */
    fun visibleLabelIndices(period: ReportPeriod, barCount: Int): Set<Int> {
        if (barCount <= 0) return emptySet()
        return when (period) {
            ReportPeriod.DAY, ReportPeriod.WEEK -> (0 until barCount).toSet()
            ReportPeriod.MONTH -> {
                // Etiquetas cada siete días (1, 8, 15, 22) más la del último día del mes.
                val anchors = listOf(0, 7, 14, 21).filter { it in 0 until barCount }
                (anchors + (barCount - 1)).toSet()
            }
        }
    }

    private fun labelsFor(period: ReportPeriod, startDate: LocalDate): List<String> = when (period) {
        ReportPeriod.DAY -> List(DAY_BUCKETS) { bucket ->
            (bucket * DAY_BUCKET_HOURS).toString().padStart(2, '0')
        }
        ReportPeriod.WEEK -> WEEKDAY_LABELS
        ReportPeriod.MONTH -> (1..startDate.lengthOfMonth()).map(Int::toString)
    }

    private fun indexFor(period: ReportPeriod, instant: Instant, zone: ZoneId): Int {
        val local = instant.atZone(zone)
        return when (period) {
            ReportPeriod.DAY -> local.hour / DAY_BUCKET_HOURS
            ReportPeriod.WEEK -> {
                val date = local.toLocalDate()
                val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                ChronoUnit.DAYS.between(monday, date).toInt()
            }
            ReportPeriod.MONTH -> local.dayOfMonth - 1
        }
    }
}
