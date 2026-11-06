package com.amin.weather.core

import android.content.Context
import org.json.JSONObject
import java.io.InputStream

object Calibration {
    var enabled: Boolean = false
        private set

    private var stationWmo: String = ""
    private var modelName: String = ""
    var windowHours: Int = 6
        private set
    private var wetThresholdMm: Double = 0.2

    private var probX = doubleArrayOf()
    private var probY = doubleArrayOf()

    private var qmFc = doubleArrayOf()
    private var qmObs = doubleArrayOf()

    var brierRaw: Double = 0.0
        private set
    var brierCalibrated: Double = 0.0
        private set
    var brierClimatology: Double = 0.0
        private set

    fun load(context: Context) {
        try {
            val inputStream: InputStream = context.assets.open(Tuning.CALIBRATION_ASSET)
            val jsonStr = inputStream.bufferedReader().use { it.readText() }
            loadFromJson(jsonStr)
        } catch (_: Exception) {
            enabled = false
        }
    }

    fun loadFromJson(jsonStr: String) {
        try {
            val root = JSONObject(jsonStr)
            val schema = root.optInt("schema", 0)
            if (schema != 1) {
                enabled = false
                return
            }

            stationWmo = root.optString("station_wmo", "")
            modelName = root.optString("model", "")
            windowHours = root.optInt("window_hours", 6)
            wetThresholdMm = root.optDouble("wet_threshold_mm", 0.2)

            val skill = root.optJSONObject("skill_cross_validated")
            if (skill == null) {
                enabled = false
                return
            }
            brierRaw = skill.optDouble("brier_raw_fraction", 1.0)
            brierCalibrated = skill.optDouble("brier_calibrated", 1.0)
            brierClimatology = skill.optDouble("brier_climatology", 1.0)

            // Safety rule: brierCalibrated must be strictly less than brierClimatology
            if (brierCalibrated >= brierClimatology) {
                enabled = false
                return
            }

            val probObj = root.optJSONObject("prob_table")
            if (probObj != null) {
                val xArr = probObj.optJSONArray("x")
                val yArr = probObj.optJSONArray("y")
                if (xArr != null && yArr != null && xArr.length() == yArr.length() && xArr.length() > 1) {
                    probX = DoubleArray(xArr.length()) { i -> xArr.getDouble(i) }
                    probY = DoubleArray(yArr.length()) { i -> yArr.getDouble(i) }

                    // Verify probY is non-decreasing
                    for (i in 0 until probY.size - 1) {
                        if (probY[i] > probY[i + 1]) {
                            enabled = false
                            return
                        }
                    }
                }
            }

            val qmObj = root.optJSONObject("amount_qm")
            if (qmObj != null) {
                val fcArr = qmObj.optJSONArray("fc")
                val obsArr = qmObj.optJSONArray("obs")
                if (fcArr != null && obsArr != null && fcArr.length() == obsArr.length() && fcArr.length() > 0) {
                    qmFc = DoubleArray(fcArr.length()) { i -> fcArr.getDouble(i) }
                    qmObs = DoubleArray(obsArr.length()) { i -> obsArr.getDouble(i) }
                }
            }

            enabled = probX.isNotEmpty() && probY.isNotEmpty()
        } catch (_: Exception) {
            enabled = false
        }
    }

    /**
     * Calibrates ECMWF 6-hour probability via linear interpolation
     */
    fun calibrateProb(p6: Double): Double {
        if (!enabled || probX.isEmpty()) return p6
        return interp(p6, probX, probY).coerceIn(0.0, 1.0)
    }

    /**
     * Quantile matching adjustment for precip amount
     */
    fun calibrateAmount(aW: Double): Double {
        if (!enabled || qmFc.isEmpty() || qmObs.isEmpty() || aW < 0.1) return aW
        val fcLast = qmFc.last()
        val obsLast = qmObs.last()
        if (fcLast <= 0.0) return aW

        return if (aW <= fcLast) {
            interp(aW, qmFc, qmObs)
        } else {
            val ratio = (obsLast / fcLast).coerceIn(Tuning.CALIB_QM_MIN_RATIO, Tuning.CALIB_QM_MAX_RATIO)
            aW * ratio
        }
    }

    private fun interp(x: Double, xp: DoubleArray, fp: DoubleArray): Double {
        if (x <= xp.first()) return fp.first()
        if (x >= xp.last()) return fp.last()
        val idx = xp.binarySearch(x)
        if (idx >= 0) return fp[idx]
        val ins = -idx - 1
        val x0 = xp[ins - 1]
        val x1 = xp[ins]
        val y0 = fp[ins - 1]
        val y1 = fp[ins]
        val t = (x - x0) / (x1 - x0)
        return y0 + t * (y1 - y0)
    }
}
