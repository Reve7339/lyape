package com.lyape.ui.format

import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag("es-PE"))

fun formatMonthLabel(month: YearMonth): String = MONTH_FORMATTER.format(month).replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("es-PE")) else it.toString()
}
