package com.amin.weather

import com.amin.weather.core.*
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
    fun testPercentileCalculation() {
        val list = listOf(1.0, 2.0, 3.0, 4.0)
        val p50 = ForecastEngine.percentile(list, 0.5)
        assertEquals(2.5, p50, 1e-4)
    }

    @Test
    fun testBlockStatsCalculation() {
        val nowSec = 100000L
        val times = longArrayOf(nowSec + 3600L, nowSec + 7200L)

        // 10 members with varying precipitation sums across 2 hours
        val memberPrecip = listOf(
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(0.2, 0.3),  // sum = 0.5 (wet >= 0.2)
            doubleArrayOf(0.5, 0.5),  // sum = 1.0 (wet >= 0.2)
            doubleArrayOf(6.0, 6.0),  // sum = 12.0 (wet & heavy >= 10.0)
            doubleArrayOf(7.0, 8.0)   // sum = 15.0 (wet & heavy >= 10.0)
        )

        val ens = EnsembleSet(
            times = times,
            members = memberPrecip.toTypedArray()
        )

        val bs = ForecastEngine.blockStats(
            ensList = listOf(ens),
            startEpoch = nowSec,
            endEpoch = nowSec + 7200L,
            wetMm = Tuning.WET_HOUR_MM,
            heavyMm = Tuning.HEAVY_6H_MM
        )

        assertEquals(0.4, bs.pWet!!, 1e-4)
        assertEquals(0.2, bs.pHeavy6h!!, 1e-4)
        assertEquals(0.0, bs.p50!!, 1e-4)
    }

    @Test
    fun testDecideTierRedWhenHeavyPrecipProbabilityHigh() {
        val radar = RadarNowcastResult(
            available = true,
            frameAgeMin = 5,
            coverageOk = true,
            maxDbzInTarget = 0,
            wetFractionInTarget = 0.0,
            rainRateInTargetMmH = 0.0
        )
        val block6 = ForecastEngine.BlockStats(pWet = 0.8, p50 = 5.0, p90 = 15.0, pHeavy6h = 0.6)

        val verdict = ForecastEngine.decideTier(
            radar = radar,
            synop = null,
            rows = emptyList(),
            block6 = block6
        )

        assertEquals(AlertTier.RED, verdict.tier)
    }

    @Test
    fun testDecideTierYellowWhenMoistureInflowAndModerateProb() {
        val radar = RadarNowcastResult(
            available = true,
            frameAgeMin = 5,
            coverageOk = true,
            maxDbzInTarget = 0,
            wetFractionInTarget = 0.0,
            rainRateInTargetMmH = 0.0
        )
        val block12 = ForecastEngine.BlockStats(pWet = 0.25, p50 = 0.1, p90 = 1.0, pHeavy6h = 0.0)

        val verdict = ForecastEngine.decideTier(
            radar = radar,
            synop = null,
            rows = emptyList(),
            block12 = block12,
            moistureInflowNext12h = true
        )

        assertEquals(AlertTier.YELLOW, verdict.tier)
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
        val dummyRow = ForecastRow(
            timeEpochSec = 1000L, timeIran = "", forecastSource = "",
            probArea = 5.0, probPoint = 5.0, amountP50 = 0.0, amountP90 = 0.0,
            tempC = 20.0, dewC = 10.0, rh = 50.0, cloud = 10.0, gustKmh = 10.0,
            convScore = 0.0
        )
        val verdict = ForecastEngine.decideTier(
            radar = radar,
            synop = null,
            rows = listOf(dummyRow)
        )
        assertEquals(AlertTier.GREEN, verdict.tier)
    }

    @Test
    fun testDecideTierUnknownWhenNoLiveObservations() {
        val unavailableRadar = RadarNowcastResult(
            available = false,
            frameAgeMin = 45,
            coverageOk = true,
            maxDbzInTarget = -1,
            wetFractionInTarget = 0.0,
            rainRateInTargetMmH = 0.0
        )
        val dummyRow = ForecastRow(
            timeEpochSec = 1000L, timeIran = "", forecastSource = "",
            probArea = 5.0, probPoint = 5.0, amountP50 = 0.0, amountP90 = 0.0,
            tempC = 20.0, dewC = 10.0, rh = 50.0, cloud = 10.0, gustKmh = 10.0,
            convScore = 0.0
        )
        val verdict = ForecastEngine.decideTier(
            radar = unavailableRadar,
            synop = null,
            rows = listOf(dummyRow)
        )
        assertEquals(AlertTier.UNKNOWN, verdict.tier)
    }

    @Test
    fun testDecideTierGreenOnlyWithLiveObservationAndLowProb() {
        val radar = RadarNowcastResult(
            available = true,
            frameAgeMin = 5,
            coverageOk = true,
            maxDbzInTarget = 0,
            wetFractionInTarget = 0.0,
            rainRateInTargetMmH = 0.0
        )
        val dummyRow = ForecastRow(
            timeEpochSec = 1000L, timeIran = "", forecastSource = "",
            probArea = 5.0, probPoint = 5.0, amountP50 = 0.0, amountP90 = 0.0,
            tempC = 20.0, dewC = 10.0, rh = 50.0, cloud = 10.0, gustKmh = 10.0,
            convScore = 0.0
        )
        val verdict = ForecastEngine.decideTier(
            radar = radar,
            synop = null,
            rows = listOf(dummyRow)
        )
        assertEquals(AlertTier.GREEN, verdict.tier)
    }

    @Test
    fun testDecideTierUserObservationImpact() {
        val radar = RadarNowcastResult(
            available = true,
            frameAgeMin = 5,
            coverageOk = true,
            maxDbzInTarget = 0,
            wetFractionInTarget = 0.0,
            rainRateInTargetMmH = 0.0
        )
        val dummyRow = ForecastRow(
            timeEpochSec = 1000L, timeIran = "", forecastSource = "",
            probArea = 5.0, probPoint = 5.0, amountP50 = 0.0, amountP90 = 0.0,
            tempC = 20.0, dewC = 10.0, rh = 50.0, cloud = 10.0, gustKmh = 10.0,
            convScore = 0.0
        )

        // 1. باران ۱۰ دقیقه پیش -> ORANGE
        val obsRain10m = SkyObservationReport(
            createdAtEpochMs = System.currentTimeMillis() - 10 * 60 * 1000L,
            phenomenon = "باران"
        )
        val verdict1 = ForecastEngine.decideTier(
            radar = radar, synop = null, rows = listOf(dummyRow),
            userObservation = obsRain10m
        )
        assertEquals(AlertTier.ORANGE, verdict1.tier)

        // 2. ابری بدون بارش -> نه نارنجی (GREEN)
        val obsCloudy = SkyObservationReport(
            createdAtEpochMs = System.currentTimeMillis() - 10 * 60 * 1000L,
            phenomenon = "ابری بدون بارش"
        )
        val verdict2 = ForecastEngine.decideTier(
            radar = radar, synop = null, rows = listOf(dummyRow),
            userObservation = obsCloudy
        )
        org.junit.Assert.assertNotEquals(AlertTier.ORANGE, verdict2.tier)
        assertEquals(AlertTier.GREEN, verdict2.tier)

        // 3. گزارش ۷۰ دقیقه پیش -> بی اثر (GREEN)
        val obsRain70m = SkyObservationReport(
            createdAtEpochMs = System.currentTimeMillis() - 70 * 60 * 1000L,
            phenomenon = "باران"
        )
        val verdict3 = ForecastEngine.decideTier(
            radar = radar, synop = null, rows = listOf(dummyRow),
            userObservation = obsRain70m
        )
        assertEquals(AlertTier.GREEN, verdict3.tier)
    }

    @Test
    fun testDecideTierDistantYellowOnlyWhenApproaching() {
        val dummyRow = ForecastRow(
            timeEpochSec = 1000L, timeIran = "", forecastSource = "",
            probArea = 5.0, probPoint = 5.0, amountP50 = 0.0, amountP90 = 0.0,
            tempC = 20.0, dewC = 10.0, rh = 50.0, cloud = 10.0, gustKmh = 10.0,
            convScore = 0.0
        )

        val cellNotApproaching = RadarCellInfo(
            id = "C1", pixelCount = 10, areaSqKm = 10.0, maxDbz = 40,
            centerLat = 32.0, centerLon = 48.0, centerDistKm = 100.0,
            centerBearingDeg = 240.0, radiusKm = 5.0, speedKmh = 30.0,
            moveDirectionToDeg = 240.0, state = CellApproachState.NOT_APPROACHING,
            etaMinLow = null, etaMinHigh = null, minDistanceKm = 80.0,
            motionSource = "radar_track"
        )
        val radarNotApproaching = RadarNowcastResult(
            available = true, frameAgeMin = 5, coverageOk = true,
            maxDbzInTarget = 0, wetFractionInTarget = 0.0, rainRateInTargetMmH = 0.0,
            cells = listOf(cellNotApproaching), upwindStrongEcho = true
        )
        val verdictNotApproaching = ForecastEngine.decideTier(
            radar = radarNotApproaching, synop = null, rows = listOf(dummyRow)
        )
        org.junit.Assert.assertNotEquals(AlertTier.YELLOW, verdictNotApproaching.tier)
        assertEquals(AlertTier.GREEN, verdictNotApproaching.tier)

        val cellApproaching = RadarCellInfo(
            id = "C2", pixelCount = 10, areaSqKm = 10.0, maxDbz = 40,
            centerLat = 32.0, centerLon = 48.0, centerDistKm = 100.0,
            centerBearingDeg = 240.0, radiusKm = 5.0, speedKmh = 30.0,
            moveDirectionToDeg = 60.0, state = CellApproachState.APPROACHING,
            etaMinLow = 120, etaMinHigh = 150, minDistanceKm = 0.0,
            motionSource = "radar_track"
        )
        val radarApproaching = RadarNowcastResult(
            available = true, frameAgeMin = 5, coverageOk = true,
            maxDbzInTarget = 0, wetFractionInTarget = 0.0, rainRateInTargetMmH = 0.0,
            cells = listOf(cellApproaching)
        )
        val verdictApproaching = ForecastEngine.decideTier(
            radar = radarApproaching, synop = null, rows = listOf(dummyRow)
        )
        assertEquals(AlertTier.YELLOW, verdictApproaching.tier)
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
            etaMinLow = 45,
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
            rows = emptyList()
        )
        assertEquals(AlertTier.ORANGE, verdict.tier)
    }

    @Test
    fun testR2Rounding() {
        assertEquals(1.23, ForecastEngine.r2(1.234)!!, 1e-4)
        assertEquals(12.5, ForecastEngine.r2(12.5)!!, 1e-4)
        assertEquals(0.01, ForecastEngine.r2(0.006)!!, 1e-4)
        assertEquals(null, ForecastEngine.r2(null))
    }

    @Test
    fun testMoistureInflowPastFiltering() {
        val nowEpoch = 100000L
        // Hour in past (24 hours ago)
        val pastTime = nowEpoch - 24 * 3600L
        val futureTime = nowEpoch + 2 * 3600L

        val pastSteering = SteeringWind(
            times = longArrayOf(pastTime),
            speedKmh = doubleArrayOf(30.0),
            dirToDeg = doubleArrayOf(45.0),
            moistureInflow = booleanArrayOf(true)
        )

        // Past steering alone should NOT trigger moisture inflow
        val flowPast = ForecastEngine.checkMoistureInflowNext12h(pastSteering, nowEpoch)
        assertEquals(false, flowPast)

        val futureSteering = SteeringWind(
            times = longArrayOf(futureTime),
            speedKmh = doubleArrayOf(30.0),
            dirToDeg = doubleArrayOf(45.0),
            moistureInflow = booleanArrayOf(true)
        )

        // Future steering should trigger moisture inflow
        val flowFuture = ForecastEngine.checkMoistureInflowNext12h(futureSteering, nowEpoch)
        assertEquals(true, flowFuture)
    }
}
