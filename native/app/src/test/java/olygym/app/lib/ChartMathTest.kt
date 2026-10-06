package olygym.app.lib

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The chart maths, which frontend/src/components/LineChart.jsx keeps inline. The cases are the ones
 * its own rendering depends on: a flat series still draws, the axis prefers round numbers, and a
 * short span gets three ticks rather than a month label that would sit at the frame's edge.
 */
class ChartMathTest {

    @Test
    fun `a flat series gets a unit of room on either side, then the padding`() {
        val (lo, hi) = chartYRange(listOf(60.0))
        assertEquals(60.0 - 1.0 - 2.0 * 0.12, lo, 1e-9)
        assertEquals(60.0 + 1.0 + 2.0 * 0.12, hi, 1e-9)
    }

    @Test
    fun `the goal is inside the range even when every point is above it`() {
        val (lo, hi) = chartYRange(listOf(90.0, 92.0), goal = 80.0)
        assertEquals(80.0, lo + (92.0 - 80.0) * 0.12, 1e-9)
        assertEquals(92.0, hi - (92.0 - 80.0) * 0.12, 1e-9)
    }

    @Test
    fun `the step is one, two, two and a half, five or ten of a power of ten`() {
        assertEquals(10.0, chartGridStep(30.0), 1e-9)
        assertEquals(100.0, chartGridStep(300.0), 1e-9)
        assertEquals(0.5, chartGridStep(1.0), 1e-9)
        assertEquals(1.0, chartGridStep(0.0), 1e-9)
        assertEquals(1.0, chartGridStep(Double.NaN), 1e-9)
    }

    @Test
    fun `the gridlines start at the first multiple above the floor`() {
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0), chartGridValues(0.0, 30.0, 10.0))
        assertEquals(listOf(10.0, 20.0, 30.0), chartGridValues(3.2, 30.0, 10.0))
        assertEquals(emptyList<Double>(), chartGridValues(0.0, 30.0, 0.0))
    }

    @Test
    fun `one tick per month boundary inside the span`() {
        val t0 = java.time.LocalDate.parse("2026-01-15").atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val t1 = java.time.LocalDate.parse("2026-03-10").atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val months = chartMonthTicks(t0, t1).map {
            java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        }
        assertEquals(listOf("2026-02-01", "2026-03-01"), months)
        assertEquals(emptyList<Long>(), chartMonthTicks(t0, t0))
    }

    @Test
    fun `a span with no boundary in it gets three ticks, and the ends hang inwards`() {
        val t0 = 1_000_000L
        val t1 = t0 + 4L * 86_400_000L
        val ticks = chartSpanTicks(t0, t1)
        assertEquals(listOf(t0, t0 + 2L * 86_400_000L, t1), ticks.map { it.first })
        assertEquals(listOf("start", "middle", "end"), ticks.map { it.second })
    }
}
