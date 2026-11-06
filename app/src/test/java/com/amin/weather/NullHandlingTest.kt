package com.amin.weather

import com.amin.weather.core.ForecastRow
import com.amin.weather.core.ModelGrid
import com.amin.weather.core.Tuning
import org.junit.Assert.*
import org.junit.Test

class NullHandlingTest {

    private fun invokeWetFraction(grid: ModelGrid?, gIdx: Int): Double? {
        val method = com.amin.weather.core.ForecastEngine::class.java.getDeclaredMethod(
            "wetFraction",
            ModelGrid::class.java,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(com.amin.weather.core.ForecastEngine, grid, gIdx) as Double?
    }

    @Test
    fun testWetFractionFewerThanMinValidPointsReturnsNull() {
        val nHours = 1
        val nPoints = 17
        val precipGrid = Array(nPoints) { DoubleArray(nHours) }

        // 11 valid points, 6 NaN points
        for (i in 0 until 11) {
            precipGrid[i][0] = 0.5
        }
        for (i in 11 until 17) {
            precipGrid[i][0] = Double.NaN
        }

        val grid = ModelGrid(LongArray(1) { 1000L }, precipGrid, null, null, null)
        val fraction = invokeWetFraction(grid, 0)
        assertNull(fraction)
    }

    @Test
    fun testWetFractionValid17PointsWith3Wet() {
        val nHours = 1
        val nPoints = 17
        val precipGrid = Array(nPoints) { DoubleArray(nHours) }

        for (i in 0 until 17) {
            precipGrid[i][0] = if (i < 3) 0.5 else 0.0
        }

        val grid = ModelGrid(LongArray(1) { 1000L }, precipGrid, null, null, null)
        val fraction = invokeWetFraction(grid, 0)
        assertNotNull(fraction)
        assertEquals(3.0 / 17.0, fraction!!, 0.001)
    }

    @Test
    fun testRowWithNullPAreaHasNullProbArea() {
        val row = ForecastRow(
            timeEpochSec = 1000L,
            timeIran = "14:00",
            forecastSource = "ECMWF",
            probArea = null,
            probPoint = null,
            amountP50 = null,
            amountP90 = null,
            tempC = null,
            dewC = null,
            rh = null,
            cloud = null,
            gustKmh = null,
            convScore = null
        )

        assertNull(row.probArea)
        assertNull(row.probPoint)
    }
}
