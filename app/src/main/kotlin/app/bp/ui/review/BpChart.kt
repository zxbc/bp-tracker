package app.bp.ui.review

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorUtils
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import app.bp.data.Reading
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.hypot

/** One day's worth of readings, ascending by time. */
data class DayBucket(
    val date: LocalDate,
    val readings: List<Reading>,
)

/** The days present in [readings], oldest first — the chart's X axis. */
fun lastSevenDayBuckets(readings: List<Reading>, zone: ZoneId = ZoneId.systemDefault()): List<DayBucket> {
    return readings.groupBy { Instant.ofEpochMilli(it.ts).atZone(zone).toLocalDate() }
        .entries
        .sortedBy { it.key }
        .map { DayBucket(it.key, it.value.sortedBy { r -> r.ts }) }
}

/** "Mon" — short weekday for the chart's X labels. */
fun LocalDate.shortWeekday(): String =
    dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

/**
 * The 7-day chart.
 *
 * X axis: one band per day (today plus the previous six); a day's readings
 * become side-by-side bars (first = left = morning, second = night).
 * Y axis: mmHg. Each BP reading is a bar spanning diastolic → systolic.
 * Heart rate is a thin dotted line on the same axis, deliberately muted.
 */
@Composable
fun BpChart(readings: List<Reading>) {
    val buckets = remember(readings) { lastSevenDayBuckets(readings) }
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "chart-in",
    )
    val colors = MaterialTheme.colorScheme
    val today = remember { LocalDate.now() }
    val capColor = remember(colors) { ColorUtils.blendARGB(colors.primary, Color.Black, 0.35f) }
    val hrColor = colors.onSurface.copy(alpha = 0.35f)
    val hrDotColor = colors.onSurface.copy(alpha = 0.55f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .aspectRatio(1.4f),
    ) {
        drawChart(
            buckets = buckets,
            progress = progress,
            today = today,
            barColor = colors.primary,
            capColor = capColor,
            hrColor = hrColor,
            hrDotColor = hrDotColor,
            gridColor = colors.onSurface.copy(alpha = 0.10f),
            labelColor = colors.onSurfaceVariant,
            labelActiveColor = colors.primary,
        )
    }
}

private fun DrawScope.drawChart(
    buckets: List<DayBucket>,
    progress: Float,
    today: LocalDate,
    barColor: Color,
    capColor: Color,
    hrColor: Color,
    hrDotColor: Color,
    gridColor: Color,
    labelColor: Color,
    labelActiveColor: Color,
) {
    val density = this.density
    val padLeft = 30f * density
    val padTop = 8f * density
    val padBottom = 40f * density
    val padRight = 6f * density
    val plotLeft = padLeft
    val plotRight = size.width - padRight
    val plotTop = padTop
    val plotBottom = size.height - padBottom

    // Y scale from the data, with a sensible baseline when it is empty.
    val all = buckets.flatMap { it.readings }
    val dataMin = all.minOfOrNull { minOf(it.dia, it.hr) }?.toFloat() ?: 40f
    val dataMax = all.maxOfOrNull { maxOf(it.sys, it.hr) }?.toFloat() ?: 140f
    // Tight-ish dynamic bounds: the axis adapts to the data, but with a
    // floor/ceiling so a single odd reading doesn't squash the whole chart.
    val yMin = minOf(50f, dataMin - 10f)
    val yMax = maxOf(150f, dataMax + 15f)
    fun y(v: Float): Float =
        plotBottom - (v - yMin) / (yMax - yMin) * (plotBottom - plotTop)

    // Horizontal gridlines every 20 mmHg with labels.
    val gridPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = gridColor.toArgb()
        textSize = 11f * density
        textAlign = android.graphics.Paint.Align.RIGHT
    }
    var grid = ceil(yMin / 20f) * 20f
    while (grid <= yMax) {
        val gy = y(grid)
        drawLine(gridColor, Offset(plotLeft, gy), Offset(plotRight, gy), strokeWidth = 1f)
        drawContext.canvas.nativeCanvas.drawText(
            grid.toInt().toString(),
            plotLeft - 6f * density,
            gy + 4f * density,
            gridPaint,
        )
        grid += 20f
    }

    // X axis: 7 day bands, oldest left, newest (today) right.
    val bandW = (plotRight - plotLeft) / 7f
    val labelPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 11f * density
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val hrPoints = mutableListOf<Offset>()

    buckets.forEachIndexed { i, bucket ->
        val bandLeft = plotLeft + bandW * i
        val centerX = bandLeft + bandW / 2f
        val isToday = bucket.date == today
        labelPaint.color = (if (isToday) labelActiveColor else labelColor).toArgb()
        drawContext.canvas.nativeCanvas.drawText(
            bucket.date.shortWeekday(),
            centerX,
            size.height - 22f * density,
            labelPaint,
        )
        drawContext.canvas.nativeCanvas.drawText(
            bucket.date.dayOfMonth.toString(),
            centerX,
            size.height - 8f * density,
            labelPaint,
        )

        if (bucket.readings.isEmpty()) return@forEachIndexed

        val n = bucket.readings.size
        val slot = bandW / n
        val barWidth = minOf(14f * density, slot * 0.34f)
        // Staggered entrance: band i starts at i/8 of the animation, each takes 2/8.
        val p = ((progress - i / 8f) * 8f / 2f).coerceIn(0f, 1f)

        bucket.readings.forEachIndexed { j, r ->
            val x = bandLeft + slot * (j + 0.5f)
            val ySys = y(r.sys.toFloat())
            val yDiaFull = y(r.dia.toFloat())
            val yDia = ySys + (yDiaFull - ySys) * p
            drawLine(
                barColor,
                Offset(x, ySys),
                Offset(x, yDia),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
            if (p > 0.999f) {
                drawCircle(capColor, Offset(x, ySys), barWidth * 0.62f)
            }
            hrPoints.add(Offset(x, y(r.hr.toFloat())))
        }
    }

    // Heart rate: thin dotted line, clearly subordinate to the BP bars.
    if (progress > 0.35f) {
        drawDottedLine(hrColor, hrPoints, dotRadius = 1.3f * density, step = 5f * density)
        hrPoints.forEach { drawCircle(hrDotColor, it, 2.3f * density) }
    }
}

/** A dotted polyline: evenly spaced dots along each segment. */
private fun DrawScope.drawDottedLine(
    color: Color,
    points: List<Offset>,
    dotRadius: Float,
    step: Float,
) {
    if (points.size < 2) return
    for (k in 0 until points.size - 1) {
        val a = points[k]
        val b = points[k + 1]
        val dist = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
        if (dist < 1f) continue
        var d = 0f
        while (d < dist) {
            val t = d / dist
            drawCircle(
                color,
                Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t),
                dotRadius,
            )
            d += step
        }
    }
}
