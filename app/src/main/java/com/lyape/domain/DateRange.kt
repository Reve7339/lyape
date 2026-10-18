package com.lyape.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Periodos que la interfaz puede consultar. */
enum class PeriodType {
    DAY,
    WEEK,
    MONTH,
    YEAR,
}

/**
 * Rango temporal semiabierto `[start, endExclusive)` en instantes absolutos.
 *
 * Los límites se calculan con la zona horaria del teléfono. La semana va de lunes a domingo.
 */
data class DateRange(
    val start: Instant,
    val endExclusive: Instant,
) {
    init {
        require(!endExclusive.isBefore(start)) {
            "El fin del rango no puede ser anterior al inicio."
        }
    }

    fun contains(instant: Instant): Boolean =
        !instant.isBefore(start) && instant.isBefore(endExclusive)

    companion object {
        fun day(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DateRange =
            DateRange(startOfDay(date, zone), startOfDay(date.plusDays(1), zone))

        fun week(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DateRange {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return DateRange(startOfDay(monday, zone), startOfDay(monday.plusWeeks(1), zone))
        }

        fun month(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DateRange {
            val first = date.withDayOfMonth(1)
            return DateRange(startOfDay(first, zone), startOfDay(first.plusMonths(1), zone))
        }

        fun year(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DateRange {
            val first = date.withDayOfYear(1)
            return DateRange(startOfDay(first, zone), startOfDay(first.plusYears(1), zone))
        }

        fun of(
            period: PeriodType,
            date: LocalDate,
            zone: ZoneId = ZoneId.systemDefault(),
        ): DateRange = when (period) {
            PeriodType.DAY -> day(date, zone)
            PeriodType.WEEK -> week(date, zone)
            PeriodType.MONTH -> month(date, zone)
            PeriodType.YEAR -> year(date, zone)
        }

        fun of(
            period: PeriodType,
            reference: Instant,
            zone: ZoneId = ZoneId.systemDefault(),
        ): DateRange = of(period, reference.atZone(zone).toLocalDate(), zone)
    }
}

private fun startOfDay(date: LocalDate, zone: ZoneId): Instant =
    date.atStartOfDay(zone).toInstant()
