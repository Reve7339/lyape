package com.lyape.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyape.domain.MonthlyChartCalculator
import com.lyape.domain.MonthlyChartSeries
import com.lyape.domain.MonthlyChartThresholdKind
import com.lyape.domain.MonthlyProgress
import com.lyape.domain.PeriodReport
import com.lyape.domain.PeriodReportCalculator
import com.lyape.domain.ReportPeriod
import com.lyape.ui.format.formatCents

/**
 * Gráfico del periodo, sin librerías externas.
 *
 * En día y semana se conserva el gráfico de barras (positivas en morado uva, ajustes negativos
 * bajo la línea de cero con el color de error). En mes se dibuja el ingreso acumulado con su
 * referencia de gastos y meta.
 */
@Composable
fun PeriodChart(
    report: PeriodReport,
    progress: MonthlyProgress?,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = surface,
        border = BorderStroke(1.dp, outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (report.period == ReportPeriod.MONTH) "Ingreso acumulado" else "Ingresos por tramo",
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
            )
            Spacer(Modifier.height(10.dp))
            if (report.period == ReportPeriod.MONTH) {
                MonthlyProgressChart(report = report, progress = progress)
            } else {
                BarsChart(report)
            }
        }
    }
}

@Composable
private fun BarsChart(report: PeriodReport) {
    val bars = report.bars
    val positiveColor = MaterialTheme.colorScheme.primary
    val negativeColor = MaterialTheme.colorScheme.error
    val zeroColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val maxPositive = remember(bars) { bars.maxOfOrNull { it.amountCents }?.coerceAtLeast(0L) ?: 0L }
    val minNegative = remember(bars) { bars.minOfOrNull { it.amountCents }?.coerceAtMost(0L) ?: 0L }
    val negativeMagnitude = remember(minNegative) { negativeMagnitudeForChart(minNegative) }
    val visibleLabels = remember(report.period, bars.size) {
        PeriodReportCalculator.visibleLabelIndices(report.period, bars.size)
    }
    val description = remember(report) { barsChartDescription(report) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .semantics { contentDescription = description },
    ) {
        if (bars.isEmpty()) return@Canvas

        val hasValues = maxPositive > 0L || minNegative < 0L
        val zeroY = if (!hasValues) {
            size.height
        } else {
            val positive = maxPositive.toDouble()
            // Convierte antes de negar: `-Long.MIN_VALUE` desborda como Long.
            val negative = negativeMagnitude
            val total = positive + negative
            val fraction = if (total <= 0.0) 1.0 else positive / total
            (size.height.toDouble() * fraction).toFloat()
        }

        drawLine(
            color = zeroColor,
            start = Offset(0f, zeroY),
            end = Offset(size.width, zeroY),
            strokeWidth = 2f,
        )

        val slot = size.width / bars.size
        val barWidth = slot * 0.56f
        bars.forEachIndexed { index, bar ->
            val left = index * slot + (slot - barWidth) / 2f
            when {
                bar.amountCents > 0L && maxPositive > 0L -> {
                    val height = bar.amountCents.toDouble() / maxPositive.toDouble() * zeroY
                    drawRect(
                        color = positiveColor,
                        topLeft = Offset(left, zeroY - height.toFloat()),
                        size = Size(barWidth, height.toFloat()),
                    )
                }
                bar.amountCents < 0L && minNegative < 0L -> {
                    val height = negativeMagnitudeForChart(bar.amountCents) /
                        negativeMagnitude *
                        (size.height - zeroY)
                    drawRect(
                        color = negativeColor,
                        topLeft = Offset(left, zeroY),
                        size = Size(barWidth, height.toFloat()),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        report.bars.forEachIndexed { index, bar ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (index in visibleLabels) {
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}

/**
 * Acumulado del mes: línea morado uva con área lavanda desde cero, más las referencias de gastos y
 * meta. Las etiquetas de cada referencia viven en un margen derecho para no tapar la trayectoria.
 */
@Composable
private fun MonthlyProgressChart(report: PeriodReport, progress: MonthlyProgress?) {
    val positiveColor = MaterialTheme.colorScheme.primary
    val areaColor = MaterialTheme.colorScheme.primaryContainer
    val zeroColor = MaterialTheme.colorScheme.outlineVariant
    val inkColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val expenses = progress?.takeIf { it.hasExpenses }?.totalExpensesCents
    val goal = progress?.takeIf { it.hasGoal }?.goalCents
    val series = remember(report.bars, expenses, goal) {
        MonthlyChartCalculator.calculate(report.bars, expenses, goal)
    }
    val visibleLabels = remember(series.points.size) {
        PeriodReportCalculator.visibleLabelIndices(ReportPeriod.MONTH, series.points.size)
    }
    val description = remember(report, progress) { monthlyChartDescription(report, progress) }
    val textMeasurer = rememberTextMeasurer()

    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val thresholdNameStyle = MaterialTheme.typography.labelSmall.copy(
        color = inkColor,
        fontWeight = FontWeight.ExtraBold,
    )
    val thresholdValueStyle = TextStyle(
        color = labelColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .semantics { contentDescription = description },
    ) {
        if (series.points.isEmpty()) return@Canvas

        // Mide primero las etiquetas de referencia para reservar un margen que nunca las recorte,
        // incluso con el texto del sistema aumentado o con importes largos.
        val thresholdLabels = series.thresholds.map { threshold ->
            val name = if (threshold.kind == MonthlyChartThresholdKind.EXPENSES) "Gastos" else "Meta"
            val color = if (threshold.kind == MonthlyChartThresholdKind.EXPENSES) inkColor else positiveColor
            ThresholdLabel(
                nameLayout = textMeasurer.measure(name, thresholdNameStyle.copy(color = color)),
                valueLayout = textMeasurer.measure(formatCents(threshold.amountCents), thresholdValueStyle),
            )
        }
        val widestLabel = thresholdLabels
            .maxOfOrNull { maxOf(it.nameLayout.size.width.toFloat(), it.valueLayout.size.width.toFloat()) }
            ?: 0f
        val gutter = (widestLabel + 10.dp.toPx()).coerceIn(0f, size.width * 0.5f)

        val plotLeft = 4.dp.toPx()
        val plotRight = (size.width - gutter).coerceAtLeast(plotLeft + 1f)
        val plotTop = 6.dp.toPx()
        val axisBand = 20.dp.toPx()
        val plotBottom = (size.height - axisBand).coerceAtLeast(plotTop + 1f)
        val height = plotBottom - plotTop

        fun xOf(index: Int): Float {
            val count = series.points.size
            if (count <= 1) return plotLeft
            return plotLeft + (plotRight - plotLeft) * index / (count - 1)
        }

        fun yOf(fraction: Float): Float = plotBottom - fraction * height

        // Línea de cero.
        val zeroY = yOf(series.fractionOf(0L))
        drawLine(
            color = zeroColor,
            start = Offset(plotLeft, zeroY),
            end = Offset(plotRight, zeroY),
            strokeWidth = 2f,
        )

        // Referencias horizontales bajo el área, con trazo discontinuo para no confundirse con la línea.
        val labelFractions = MonthlyChartCalculator.stackedLabelFractions(series, THRESHOLD_LABEL_GAP)
        series.thresholds.forEachIndexed { index, threshold ->
            val y = yOf(series.fractionOf(threshold.amountCents))
            val color = if (threshold.kind == MonthlyChartThresholdKind.EXPENSES) inkColor else positiveColor
            drawLine(
                color = color,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = if (threshold.kind == MonthlyChartThresholdKind.EXPENSES) {
                    dashedEffect
                } else {
                    dottedEffect
                },
            )
            drawThresholdLabel(
                label = thresholdLabels[index],
                x = plotRight + 6.dp.toPx(),
                y = yOf(labelFractions[index]),
                canvasHeight = size.height,
            )
        }

        // Área desde cero hasta el acumulado, incluso cuando el acumulado baja de cero.
        val area = Path().apply {
            moveTo(xOf(0), zeroY)
            series.points.forEachIndexed { index, point ->
                lineTo(xOf(index), yOf(series.fractionOf(point.cumulativeCents)))
            }
            lineTo(xOf(series.points.lastIndex), zeroY)
            close()
        }
        drawPath(area, color = areaColor, alpha = 0.75f)

        // Línea de acumulado.
        val line = Path().apply {
            series.points.forEachIndexed { index, point ->
                val x = xOf(index)
                val y = yOf(series.fractionOf(point.cumulativeCents))
                if (index == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(
            path = line,
            color = positiveColor,
            style = Stroke(
                width = 3.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // Punto final del acumulado.
        val last = series.points.last()
        drawCircle(
            color = positiveColor,
            radius = 4.dp.toPx(),
            center = Offset(xOf(series.points.lastIndex), yOf(series.fractionOf(last.cumulativeCents))),
        )

        // Etiquetas de días visibles.
        series.points.forEachIndexed { index, point ->
            if (index !in visibleLabels) return@forEachIndexed
            val layout = textMeasurer.measure(point.label, axisStyle)
            val x = (xOf(index) - layout.size.width / 2f)
                .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(layout, topLeft = Offset(x, plotBottom + 2.dp.toPx()))
        }
    }
}

private const val THRESHOLD_LABEL_GAP = 0.16f

private val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f), 0f)
private val dottedEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 9f), 0f)

private class ThresholdLabel(
    val nameLayout: TextLayoutResult,
    val valueLayout: TextLayoutResult,
)

private fun DrawScope.drawThresholdLabel(
    label: ThresholdLabel,
    x: Float,
    y: Float,
    canvasHeight: Float,
) {
    val blockHeight = label.nameLayout.size.height + label.valueLayout.size.height

    var nameTop = y - blockHeight / 2f
    var valueTop = nameTop + label.nameLayout.size.height
    if (nameTop < 0f) {
        valueTop -= nameTop
        nameTop = 0f
    }
    val overflow = (valueTop + label.valueLayout.size.height) - canvasHeight
    if (overflow > 0f) {
        nameTop = (nameTop - overflow).coerceAtLeast(0f)
        valueTop = nameTop + label.nameLayout.size.height
    }

    drawText(label.nameLayout, topLeft = Offset(x, nameTop))
    drawText(label.valueLayout, topLeft = Offset(x, valueTop))
}

/** Magnitud para escalar el dibujo; la conversión previa evita desbordar con `Long.MIN_VALUE`. */
internal fun negativeMagnitudeForChart(value: Long): Double =
    if (value < 0L) -value.toDouble() else 0.0

private fun barsChartDescription(report: PeriodReport): String {
    if (!report.hasBusinessMovements) {
        return "Sin movimientos de negocio en este periodo."
    }
    val detail = report.bars
        .filter { it.amountCents != 0L }
        .joinToString(separator = "; ") { "${it.label}: ${formatCents(it.amountCents)}" }
    return "Gráfico de barras del periodo. Total del negocio " +
        "${formatCents(report.totals.businessCents)}. $detail"
}

private fun monthlyChartDescription(report: PeriodReport, progress: MonthlyProgress?): String {
    if (!report.hasBusinessMovements) {
        return "Sin movimientos de negocio en este mes."
    }
    val total = report.totals.businessCents
    val parts = mutableListOf("Ingreso acumulado del mes: ${formatCents(total)}.")
    if (progress != null) {
        if (progress.hasExpenses) {
            parts += if (progress.expensesCovered) {
                "Gastos previstos ${formatCents(progress.totalExpensesCents)} ya cubiertos."
            } else {
                "Gastos previstos ${formatCents(progress.totalExpensesCents)}; " +
                    "faltan ${formatCents(progress.expensesRemainingCents ?: 0L)}."
            }
        }
        if (progress.hasGoal) {
            val goal = progress.goalCents ?: 0L
            parts += if (progress.goalReached) {
                "Meta ${formatCents(goal)} alcanzada."
            } else {
                "Meta ${formatCents(goal)}; faltan ${formatCents(progress.goalRemainingCents ?: 0L)}."
            }
        }
        if (progress.hasExpenses && progress.expensesCovered) {
            parts += "Ganancia estimada: ${formatCents(progress.estimatedProfitCents ?: 0L)}."
        }
    }
    return parts.joinToString(separator = " ")
}
