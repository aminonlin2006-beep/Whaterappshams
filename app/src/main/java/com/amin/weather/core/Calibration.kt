package com.amin.weather.core

import android.content.Context
import org.json.JSONObject
import java.io.InputStream

object Calibration {
    var loaded: Boolean = false
        private set
    var enabled: Boolean = false
        private set
    var qmEnabled: Boolean = false
        private set

    private var stationWmo: String = ""
    private var modelName: String = ""
    var windowHours: Int = 6
        private set
    var wetThresholdMm: Double = 0.2
        private set
    private var months: Set<Int> = setOf(1, 2, 3, 4, 5, 10, 11, 12)

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
        if (loaded) return
        try {
            val inputStream: InputStream = context.assets.open(Tuning.CALIBRATION_ASSET)
            val jsonStr = inputStream.bufferedReader().use { it.readText() }
            loadFromJson(jsonStr)
            loaded = true
        } catch (_: Exception) {
            enabled = false
            qmEnabled = false
            loaded = true
        }
    }

    fun loadFromJson(jsonStr: String) {
        enabled = false
        qmEnabled = false
        try {
            val root = JSONObject(jsonStr)
            val schema = root.optInt("schema", 0)
            if (schema != 1) return

            stationWmo = root.optString("station_wmo", "")
            modelName = root.optString("model", "")
            if (modelName != "ecmwf_ifs" && modelName != "ecmwf_ifs025") return

            windowHours = root.optInt("window_hours", 6).coerceIn(1, 24)
            wetThresholdMm = root.optDouble("wet_threshold_mm", 0.2)

            val mArr = root.optJSONArray("months")
            if (mArr != null && mArr.length() > 0) {
                val mSet = mutableSetOf<Int>()
                for (i in 0 until mArr.length()) {
                    mSet.add(mArr.getInt(i))
                }
                months = mSet
            }

            val skill = root.optJSONObject("skill_cross_validated") ?: return
            if (skill.isNull("brier_raw_fraction") || skill.isNull("brier_calibrated") || skill.isNull("brier_climatology")) {
                return
            }
            brierRaw = skill.optDouble("brier_raw_fraction", 1.0)
            brierCalibrated = skill.optDouble("brier_calibrated", 1.0)
            brierClimatology = skill.optDouble("brier_climatology", 1.0)

            if (brierCalibrated >= brierClimatology) return

            val probObj = root.optJSONObject("prob_table")
            if (probObj != null) {
                val xArr = probObj.optJSONArray("x")
                val yArr = probObj.optJSONArray("y")
                if (xArr != null && yArr != null && xArr.length() == yArr.length() && xArr.length() > 1) {
                    val tempX = DoubleArray(xArr.length()) { i -> xArr.getDouble(i) }
                    val tempY = DoubleArray(yArr.length()) { i -> yArr.getDouble(i) }

                    var xValid = tempX.first() >= 0.0 && tempX.last() <= 1.0
                    for (i in 0 until tempX.size - 1) {
                        if (tempX[i] >= tempX[i + 1]) { xValid = false; break }
                    }

                    var yValid = tempY.all { it in 0.0..1.0 }
                    for (i in 0 until tempY.size - 1) {
                        if (tempY[i] > tempY[i + 1]) { yValid = false; break }
                    }

                    if (xValid && yValid) {
                        probX = tempX
                        probY = tempY
                        enabled = true
                    }
                }
            }

            if (enabled) {
                val qmObj = root.optJSONObject("amount_qm")
                if (qmObj != null) {
                    val fcArr = qmObj.optJSONArray("fc")
                    val obsArr = qmObj.optJSONArray("obs")
                    if (fcArr != null && obsArr != null && fcArr.length() == obsArr.length() && fcArr.length() > 0) {
                        val tempFc = DoubleArray(fcArr.length()) { i -> fcArr.getDouble(i) }
                        val tempObs = DoubleArray(obsArr.length()) { i -> obsArr.getDouble(i) }

                        var fcValid = true
                        for (i in 0 until tempFc.size - 1) {
                            if (tempFc[i] >= tempFc[i + 1]) { fcValid = false; break }
                        }
                        var obsValid = true
                        for (i in 0 until tempObs.size - 1) {
                            if (tempObs[i] > tempObs[i + 1]) { obsValid = false; break }
                        }

                        if (fcValid && obsValid) {
                            qmFc = tempFc
                            qmObs = tempObs
                            qmEnabled = true
                        }
                    }
                }
            }
        } catch (_: Exception) {
            enabled = false
            qmEnabled = false
        }
    }

    fun appliesTo(month: Int): Boolean = enabled && month in months

    fun probAtZero(): Double = probY.firstOrNull() ?: 0.0

    fun calibrateProb(p6: Double): Double {
        if (!enabled || probX.isEmpty()) return p6
        return interp(p6, probX, probY).coerceIn(0.0, 1.0)
    }

    fun calibrateAmount(aW: Double): Double {
        if (!enabled || !qmEnabled || qmFc.isEmpty() || qmObs.isEmpty() || aW < 0.1) return aW
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
