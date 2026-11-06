package com.amin.weather

import com.amin.weather.core.Calibration
import com.amin.weather.core.SynopObs
import com.amin.weather.core.VerificationStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CalibrationTest {

    @Before
    fun setup() {
        val validJson = JSONObject().apply {
            put("schema", 1)
            put("station_wmo", "40794")
            put("model", "ecmwf_ifs")
            put("window_hours", 6)
            put("wet_threshold_mm", 0.2)
            put("months", org.json.JSONArray(listOf(1, 2, 3, 4, 5, 10, 11, 12)))
            put("skill_cross_validated", JSONObject().apply {
                put("brier_raw_fraction", 0.15)
                put("brier_calibrated", 0.10)
                put("brier_climatology", 0.20)
            })
            put("prob_table", JSONObject().apply {
                put("x", org.json.JSONArray(listOf(0.0, 0.5, 1.0)))
                put("y", org.json.JSONArray(listOf(0.02, 0.3, 0.8)))
            })
            put("amount_qm", JSONObject().apply {
                put("fc", org.json.JSONArray(listOf(0.5, 1.0, 2.0)))
                put("obs", org.json.JSONArray(listOf(1.0, 2.0, 5.0)))
            })
        }
        Calibration.loadFromJson(validJson.toString())
    }

    @Test
    fun testValidCalibrationLoaded() {
        assertTrue(Calibration.enabled)
        assertTrue(Calibration.qmEnabled)
        assertTrue(Calibration.appliesTo(1))
        assertFalse(Calibration.appliesTo(7))
    }

    @Test
    fun testProbCalibrationInterpolation() {
        // x=[0, 0.5, 1.0], y=[0.02, 0.3, 0.8]
        // p6 = 0.25 -> c6 = 0.02 + (0.25/0.5)*(0.3-0.02) = 0.16
        val c6 = Calibration.calibrateProb(0.25)
        assertEquals(0.16, c6, 1e-4)

        // pNeigh = 0.1 -> pNeigh * c6 / p6 = 0.1 * 0.16 / 0.25 = 0.064
        val calibrated = 0.1 * c6 / 0.25
        assertEquals(0.064, calibrated, 1e-4)
    }

    @Test
    fun testAmountCalibrationQuantileMatching() {
        // fc=[0.5, 1.0, 2.0], obs=[1.0, 2.0, 5.0]
        // aW = 1.5 -> interp between (1.0, 2.0) and (2.0, 5.0) -> 2.0 + (0.5/1.0)*3.0 = 3.5
        val calAmt = Calibration.calibrateAmount(1.5)
        assertEquals(3.5, calAmt, 1e-4)

        // aW = 4.0 -> fcLast = 2.0, obsLast = 5.0 -> ratio = 2.5 capped at CALIB_QM_MAX_RATIO (2.0) -> 4.0 * 2.0 = 8.0
        val calAmtOver = Calibration.calibrateAmount(4.0)
        assertEquals(8.0, calAmtOver, 1e-4)
    }

    @Test
    fun testInvalidModelDisablesCalibration() {
        val json = JSONObject().apply {
            put("schema", 1)
            put("station_wmo", "40794")
            put("model", "icon_global") // Invalid model
            put("window_hours", 6)
            put("skill_cross_validated", JSONObject().apply {
                put("brier_raw_fraction", 0.15)
                put("brier_calibrated", 0.10)
                put("brier_climatology", 0.20)
            })
            put("prob_table", JSONObject().apply {
                put("x", org.json.JSONArray(listOf(0.0, 0.5, 1.0)))
                put("y", org.json.JSONArray(listOf(0.02, 0.3, 0.8)))
            })
        }
        Calibration.loadFromJson(json.toString())
        assertFalse(Calibration.enabled)
    }

    @Test
    fun testDecreasingXDisablesCalibration() {
        val json = JSONObject().apply {
            put("schema", 1)
            put("station_wmo", "40794")
            put("model", "ecmwf_ifs")
            put("window_hours", 6)
            put("skill_cross_validated", JSONObject().apply {
                put("brier_raw_fraction", 0.15)
                put("brier_calibrated", 0.10)
                put("brier_climatology", 0.20)
            })
            put("prob_table", JSONObject().apply {
                put("x", org.json.JSONArray(listOf(0.5, 0.2, 1.0))) // Decreasing
                put("y", org.json.JSONArray(listOf(0.02, 0.3, 0.8)))
            })
        }
        Calibration.loadFromJson(json.toString())
        assertFalse(Calibration.enabled)
    }
}
