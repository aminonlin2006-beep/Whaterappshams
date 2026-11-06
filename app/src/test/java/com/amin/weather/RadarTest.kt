package com.amin.weather

import com.amin.weather.core.*
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
        val dist = 60.0
        val bRad = Math.toRadians(250.0)
        val x = dist * sin(bRad)
        val y = dist * cos(bRad)

        val moveRad = Math.toRadians(70.0)
        val speed = 36.0
        val vx = speed * sin(moveRad)
        val vy = speed * cos(moveRad)

        val result = RadarAnalyzer.computeApproachEta(x, y, vx, vy, cellRadiusKm = 5.0)

        assertEquals(CellApproachState.APPROACHING, result.state)
        assertNotNull(result.etaLow)
        assertNotNull(result.etaHigh)
        assertTrue(result.etaLow!! in 30..50)
        assertTrue(result.etaHigh!! in 65..85)
    }

    @Test
    fun testEtaWillMiss() {
        val dist = 60.0
        val bRad = Math.toRadians(250.0)
        val x = dist * sin(bRad)
        val y = dist * cos(bRad)

        val moveRad = Math.toRadians(100.0)
        val speed = 36.0
        val vx = speed * sin(moveRad)
        val vy = speed * cos(moveRad)

        val result = RadarAnalyzer.computeApproachEta(x, y, vx, vy, cellRadiusKm = 5.0)

        assertEquals(CellApproachState.WILL_MISS, result.state)
        assertEquals(30.0, result.dMinKm!!, 3.0)
    }

    @Test
    fun testEtaNotApproaching() {
        val dist = 60.0
        val bRad = Math.toRadians(250.0)
        val x = dist * sin(bRad)
        val y = dist * cos(bRad)

        val moveRad = Math.toRadians(250.0)
        val speed = 36.0
        val vx = speed * sin(moveRad)
        val vy = speed * cos(moveRad)

        val result = RadarAnalyzer.computeApproachEta(x, y, vx, vy, cellRadiusKm = 5.0)

        assertEquals(CellApproachState.NOT_APPROACHING, result.state)
    }

    @Test
    fun testStaleRadarFrameAvailableFalse() {
        val fetch = RadarFetch(
            frames = listOf(RadarFrame(1000L, IntArray(512 * 512) { -1 })),
            lastFrameAgeMin = 45,
            coverageVerified = true
        )
        val res = RadarAnalyzer.analyzeNowcast(fetch)
        assertFalse(res.available)
        assertTrue(res.cells.isEmpty())
    }

    @Test
    fun testRadarTrackVelocity() {
        val nowSec = 100000L
        val gridPrev = IntArray(512 * 512) { -1 }
        val gridCurr = IntArray(512 * 512) { -1 }

        for (dx in -2..2) {
            for (dy in -2..2) {
                gridPrev[(256 + dy) * 512 + (250 + dx)] = 40
                gridCurr[(256 + dy) * 512 + (256 + dx)] = 40
            }
        }

        val framePrev = RadarFrame(nowSec - 600L, gridPrev)
        val frameCurr = RadarFrame(nowSec, gridCurr)
        val fetch = RadarFetch(listOf(framePrev, frameCurr), lastFrameAgeMin = 5, coverageVerified = true)

        val res = RadarAnalyzer.analyzeNowcast(fetch, nowSec = nowSec)
        assertTrue(res.available)
        assertFalse(res.cells.isEmpty())

        val cell = res.cells.first()
        assertEquals("radar_track", cell.motionSource)
        assertNotNull(cell.speedKmh)
        assertNotNull(cell.moveDirectionToDeg)

        assertEquals(37.0, cell.speedKmh!!, 4.0)
        assertEquals(90.0, cell.moveDirectionToDeg!!, 10.0)
    }

    @Test
    fun testSingleFrameUnknownMotion() {
        val nowSec = 100000L
        val gridCurr = IntArray(512 * 512) { -1 }
        for (dx in -2..2) {
            for (dy in -2..2) {
                gridCurr[(256 + dy) * 512 + (256 + dx)] = 40
            }
        }

        val frameCurr = RadarFrame(nowSec, gridCurr)
        val fetch = RadarFetch(listOf(frameCurr), lastFrameAgeMin = 5, coverageVerified = true)

        val res = RadarAnalyzer.analyzeNowcast(fetch, steering = null, modelDryNow = null, nowSec = nowSec)
        assertTrue(res.available)
        assertFalse(res.cells.isEmpty())

        val cell = res.cells.first()
        assertEquals(CellApproachState.UNKNOWN_MOTION, cell.state)
        assertNull(cell.speedKmh)
        assertNull(cell.moveDirectionToDeg)
        assertNull(cell.etaMinLow)
    }

    @Test
    fun testClutterFilterWithModelDryTrue() {
        val nowSec = 100000L
        val grid1 = IntArray(512 * 512) { -1 }
        val grid2 = IntArray(512 * 512) { -1 }
        val grid3 = IntArray(512 * 512) { -1 }

        grid1[200 * 512 + 200] = 30
        grid2[200 * 512 + 200] = 30
        grid3[200 * 512 + 200] = 30

        val fetch = RadarFetch(
            listOf(
                RadarFrame(nowSec - 1200L, grid1),
                RadarFrame(nowSec - 600L, grid2),
                RadarFrame(nowSec, grid3)
            ),
            lastFrameAgeMin = 5,
            coverageVerified = true
        )

        val resClutterRemoved = RadarAnalyzer.analyzeNowcast(fetch, modelDryNow = true, nowSec = nowSec)
        assertTrue(resClutterRemoved.clutterPixelsRemoved > 0)

        val resClutterKept = RadarAnalyzer.analyzeNowcast(fetch, modelDryNow = false, nowSec = nowSec)
        assertEquals(0, resClutterKept.clutterPixelsRemoved)
    }

    @Test
    fun testDecideTierRadarUnavailableGuard() {
        val unavailableRadar = RadarNowcastResult(
            available = false,
            frameAgeMin = 45,
            coverageOk = true,
            maxDbzInTarget = 50,
            wetFractionInTarget = 0.5,
            rainRateInTargetMmH = 10.0
        )

        val verdict = ForecastEngine.decideTier(
            radar = unavailableRadar,
            synop = null,
            rows = emptyList()
        )

        assertEquals(AlertTier.GREEN, verdict.tier)
    }
}
