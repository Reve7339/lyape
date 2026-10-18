package com.lyape.ui.format

import com.lyape.domain.ReportPeriod
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPANISH = Locale.forLanguageTag("es-PE")
private val FULL_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", SPANISH)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", SPANISH)
private val MONTH_NAME = DateTimeFormatter.ofPattern("MMMM", SPANISH)

/**
 * Etiqueta del rango consultado en el resumen.
 *
 * - día: `15 octubre 2026`
 * - semana: `12–18 octubre 2026`, resolviendo cambios de mes o año
 * - mes: `Octubre 2026`
 */
fun formatReportRangeLabel(
    period: ReportPeriod,
    startDate: LocalDate,
    endDateInclusive: LocalDate,
): String = when (period) {
    ReportPeriod.DAY -> FULL_DATE.format(startDate)
    ReportPeriod.WEEK -> formatWeekRangeLabel(startDate, endDateInclusive)
    ReportPeriod.MONTH -> formatMonthLabel(YearMonth.from(startDate))
}

/** Rango de una semana de lunes a domingo, con el mes o el año solo cuando hacen falta. */
fun formatWeekRangeLabel(startDate: LocalDate, endDateInclusive: LocalDate): String {
    val sameYear = startDate.year == endDateInclusive.year
    val sameMonth = sameYear && startDate.month == endDateInclusive.month
    return when {
        sameMonth ->
            "${startDate.dayOfMonth}–${endDateInclusive.dayOfMonth} " +
                "${MONTH_NAME.format(endDateInclusive)} ${endDateInclusive.year}"
        sameYear ->
            "${DAY_MONTH.format(startDate)}–${DAY_MONTH.format(endDateInclusive)} " +
                "${endDateInclusive.year}"
        else ->
            "${FULL_DATE.format(startDate)}–${FULL_DATE.format(endDateInclusive)}"
    }
}
