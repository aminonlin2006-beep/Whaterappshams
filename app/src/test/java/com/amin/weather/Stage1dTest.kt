package com.amin.weather

import com.amin.weather.core.*
import org.junit.Assert.*
import org.junit.Test

/** تست‌های اصلاحیهٔ ۱-د (ایمنی). */
class Stage1dTest {
    private val now = 1_800_000_000L

    private fun radar(available: Boolean = true, maxDbz: Int = -1, cells: List<RadarCellInfo> = emptyList(), coverage: Boolean = true) =
        RadarNowcastResult(available, 5, coverage, maxDbz, 0.0, 0.0, cells = cells, coverageVerified = coverage)

    private fun synop(ageMin: Long, ww: Int?, rain: Boolean = false, iX: Int? = 1) =
        SynopObs(now - ageMin * 60, 20.0, 15.0, 70.0, null, null, ww, rain, false, false, iX)

    private fun user(phen: String, ageMin: Long) =
        SkyObservationReport(phenomenon = phen, createdAtEpochMs = (now - ageMin * 60) * 1000L)

    private fun dryRows() = List(12) {
        ForecastRow(0L, "", "", 5.0, 5.0, 0.0, 0.0, 20.0, dewC = 10.0, rh = 50.0, cloud = 10.0, gustKmh = 10.0, convScore = 0.0)
    }
    private val dry = ForecastEngine.BlockStats(0.0, 0.0, 0.0, 0.0)

    @Test fun blankRadar_modelsWet_radarDisabled() {
        val r = ForecastEngine.applyRadarTrustChecks(radar(), null, null, modelWetFracNow = 1.0, nowSec = now)
        assertFalse(r.available)
    }

    @Test fun blankRadar_modelsMostlyDry_radarKept() {
        val r = ForecastEngine.applyRadarTrustChecks(radar(), null, null, modelWetFracNow = 0.2, nowSec = now)
        assertTrue(r.available)
    }

    @Test fun blankRadar_userRain_radarDisabled() {
        val r = ForecastEngine.applyRadarTrustChecks(radar(), null, user("باران", 5), null, now)
        assertFalse(r.available)
    }

    @Test fun radarWithEcho_modelsWet_radarKept() {
        val r = ForecastEngine.applyRadarTrustChecks(radar(maxDbz = 30), null, null, 1.0, now)
        assertTrue(r.available)
    }

    @Test fun noModels_synopRain_isOrange() {
        val v = ForecastEngine.decideTier(radar(available = false), synop(20, 61, rain = true), emptyList(), null, null, nowSec = now)
        assertEquals(AlertTier.ORANGE, v.tier)
    }

    @Test fun noModels_nothingElse_isUnknown() {
        val v = ForecastEngine.decideTier(radar(available = false), null, emptyList(), null, null, nowSec = now)
        assertEquals(AlertTier.UNKNOWN, v.tier)
    }

    @Test fun userReportFromFuture_ignored() {
        val v = ForecastEngine.decideTier(radar(), null, dryRows(), dry, dry, false, user("باران", -60), now)
        assertNotEquals(AlertTier.ORANGE, v.tier)
    }

    @Test fun synopIx2_noPhenomenon_isLiveObservation() {
        val v = ForecastEngine.decideTier(radar(available = false), synop(30, null, iX = 2), dryRows(), dry, dry, nowSec = now)
        assertEquals(AlertTier.GREEN, v.tier)
    }

    @Test fun freshness_userWeightDecays() {
        assertEquals(1.0, Freshness.userReportWeight(user("باران", 0), now), 1e-9)
        assertEquals(0.5, Freshness.userReportWeight(user("باران", 30), now), 1e-9)
        assertEquals(0.0, Freshness.userReportWeight(user("باران", 61), now), 1e-9)
    }
}
