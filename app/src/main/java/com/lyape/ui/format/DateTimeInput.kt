package com.lyape.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

// `uuuu` + STRICT: rechaza fechas imposibles como 31/02/2026 en lugar de ajustarlas.
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)
private val DAY_LABEL_FORMATTER =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es-PE"))

fun formatDateInput(date: LocalDate): String = DATE_FORMATTER.format(date)

fun formatTimeInput(time: LocalTime): String = TIME_FORMATTER.format(time)

/** Etiqueta corta de fecha local, por ejemplo `"mié 14 oct"`. */
fun formatDayLabel(date: LocalDate): String = DAY_LABEL_FORMATTER.format(date)

fun parseDateInput(text: String): LocalDate? =
    runCatching { LocalDate.parse(text.trim(), DATE_FORMATTER) }.getOrNull()

fun parseTimeInput(text: String): LocalTime? =
    runCatching { LocalTime.parse(text.trim(), TIME_FORMATTER) }.getOrNull()

fun instantOf(date: LocalDate, time: LocalTime, zone: ZoneId = ZoneId.systemDefault()): Instant =
    LocalDateTime.of(date, time).atZone(zone).toInstant()
