package com.amin.weather

import com.amin.weather.core.CellApproachState
import com.amin.weather.core.RadarAnalyzer
import com.amin.weather.core.UniversalBlue
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class RadarTest {

    @Test
    fun testUniversalBlueColorMapping() {
        assertEquals(20, UniversalBlue.dbz(0xFF00A3E0.toInt()))
        assertEquals(40, UniversalBlue.dbz(0xFFFFAA00.toInt()))
        assertEquals(50, UniversalBlue.dbz(0xFFC10000.toInt()))

        // Translucent / transparent
        assertEquals(-1, UniversalBlue.dbz(0xBEDED097.toInt()))
        assertEquals(-1, UniversalBlue.dbz(0x00000000.toInt()))

        // Pure green (unknown color)
        assertEquals(-1, UniversalBlue.dbz(0xFF00FF00.toInt()))
    }

    @Test
    fun testDbzToRainRate() {
        assertEquals(0.65, RadarAnalyzer.dbzToRainRate(20), 0.05)
        assertEquals(2.73, RadarAnalyzer.dbzToRainRate(30), 0.1)
        assertEquals(12.24, RadarAnalyzer.dbzToRainRate(40), 0.5)
        assertEquals(63.4, RadarAnalyzer.dbzToRainRate(50), 2.0)
    }

    @Test
    fun testEtaApproaching() {
        // Cell at 60 km, bearing 250° -> local coords x = 60 * sin(250°), y = 60 * cos(250°)
        val dist = 60.0
        val bRad = Math.toRadians(250.0)
        val x = dist * sin(bRad)
        val y = dist * cos(bRad)

        // Moving TOWARDS 70° at 36 km/h -> vx = 36 * sin(70°), vy = 36 * cos(70°)
        val moveRad = Math.toRadians(70.0)
        val speed = 36.0
        val vx = speed * sin(moveRad)
        val vy = speed * cos(moveRad)

        val result = RadarAnalyzer.computeApproachEta(x, y, vx, vy, cellRadiusKm = 5.0)

        assertEquals(CellApproachState.APPROACHING, result.state)
        assertNotNull(result.etaLow)
        assertNotNull(result.etaHigh)
        // tEnter = ~58.3 min -> etaLow = ~40, etaHigh = ~75
        assertTrue(result.etaLow!! in 30..50)
        assertTrue(result.etaHigh!! in 65..85)
    }

    @Test
    fun testEtaWillMiss() {
        val dist = 60.0
        val bRad = Math.toRadians(250.0)
        val x = dist * sin(bRad)
        val y = dist * cos(bRad)

        // Moving TOWARDS 100°
        val moveRad = Math.toRadians(100.0)
        val speed = 36.0
        val vx = speed * sin(moveRad)
        val vy = speed * cos(moveRad)

        val result = RadarAnalyzer.computeApproachEta(x, y, vx, vy, cellRadiusKm = 5.0)

        assertEquals(CellApproachState.WILL_MISS, result.state)
        assertEquals(30.0, result.dMinKm, 3.0)
    }

    @Test
    fun testEtaNotApproaching() {
        val dist = 60.0
        val bRad = Math.toRadians(250.0)
        val x = dist * sin(bRad)
        val y = dist * cos(bRad)

        // Moving TOWARDS 250° (moving away)
        val moveRad = Math.toRadians(250.0)
        val speed = 36.0
        val vx = speed * sin(moveRad)
        val vy = speed * cos(moveRad)

        val result = RadarAnalyzer.computeApproachEta(x, y, vx, vy, cellRadiusKm = 5.0)

        assertEquals(CellApproachState.NOT_APPROACHING, result.state)
    }
}
