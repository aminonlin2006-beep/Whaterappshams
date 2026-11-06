package com.amin.weather

import com.amin.weather.core.AlertTier
import com.amin.weather.core.CellApproachState
import com.amin.weather.core.ForecastEngine
import com.amin.weather.core.RadarCellInfo
import com.amin.weather.core.RadarNowcastResult
import com.amin.weather.core.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastEngineTest {

    @Test
    fun testDewPointCalculation() {
        val dp = ForecastEngine.calculateDewPoint(25.0, 50.0)
        assertTrue(dp in 13.5..14.5)
    }

    @Test
    fun testUtcToIranConversion() {
        val iran = ForecastEngine.utcToIran("2026-11-06T12:00")
        assertTrue(iran.contains("15:30") || iran.contains("2026-11-06"))
    }

    @Test
    fun testDecideTierGreenWhenDry() {
        val radar = RadarNowcastResult(
            available = true,
            frameAgeMin = 5,
            coverageOk = true,
            maxDbzInTarget = 0,
            wetFractionInTarget = 0.0,
            rainRateInTargetMmH = 0.0
        )
        val verdict = ForecastEngine.decideTier(
            radar = radar,
            synop = null,
            ensEcmwf = null,
            rows = emptyList()
        )
        assertEquals(AlertTier.GREEN, verdict.tier)
    }

    @Test
    fun testDecideTierRedWhenSevereRadar() {
        val radar = RadarNowcastResult(
            available = true,
            frameAgeMin = 5,
            coverageOk = true,
            maxDbzInTarget = Tuning.DBZ_SEVERE + 5,
            wetFractionInTarget = 0.2,
            rainRateInTargetMmH = 15.0
        )
        val verdict = ForecastEngine.decideTier(
            radar = radar,
            synop = null,
            ensEcmwf = null,
            rows = emptyList()
        )
        assertEquals(AlertTier.RED, verdict.tier)
    }

    @Test
    fun testDecideTierOrangeWhenApproaching() {
        val cell = RadarCellInfo(
            id = "C1",
            pixelCount = 10,
            areaSqKm = 10.0,
            maxDbz = 35,
            centerLat = 32.20,
            centerLon = 48.30,
            centerDistKm = 25.0,
            centerBearingDeg = 230.0,
            radiusKm = 2.0,
            speedKmh = 30.0,
            moveDirectionToDeg = 50.0,
            state = CellApproachState.APPROACHING,
            etaMinLow = 45, // > 30 (RED_ETA_MIN) and <= 90 (ORANGE_ETA_MIN)
            etaMinHigh = 60,
            minDistanceKm = 3.0,
            motionSource = "steering_wind"
        )
        val radar = RadarNowcastResult(
            available = true,
            frameAgeMin = 5,
            coverageOk = true,
            maxDbzInTarget = 0,
            wetFractionInTarget = 0.0,
            rainRateInTargetMmH = 0.0,
            cells = listOf(cell),
            firstArrival = cell
        )
        val verdict = ForecastEngine.decideTier(
            radar = radar,
            synop = null,
            ensEcmwf = null,
            rows = emptyList()
        )
        assertEquals(AlertTier.ORANGE, verdict.tier)
    }
}
