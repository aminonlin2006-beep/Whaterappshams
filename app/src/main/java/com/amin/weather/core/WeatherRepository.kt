package com.amin.weather.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class WeatherRepository(private val context: Context) {

    private val cacheFile: File
        get() = File(context.filesDir, "forecast_cache_v5.json")

    private val skyPrefs by lazy {
        context.getSharedPreferences("sky_obs_prefs_v5", Context.MODE_PRIVATE)
    }

    suspend fun getCachedForecast(): ForecastDashboard? = withContext(Dispatchers.IO) {
        try {
            if (!cacheFile.exists()) return@withContext null
            val raw = cacheFile.readText()
            if (raw.isBlank()) return@withContext null
            val json = JSONObject(raw)
            if (json.optInt("schema", 0) != 5) return@withContext null

            val cacheAgeMs = System.currentTimeMillis() - cacheFile.lastModified()
            val dashboard = parseDashboard(json)

            // If cache is older than 2 hours, disable stale radar nowcast info
            if (cacheAgeMs > 2 * 3600 * 1000L) {
                dashboard.copy(
                    radarNowcast = dashboard.radarNowcast.copy(
                        available = false,
                        statusText = "داده‌های رادار به علت قدمت کش (بیش از ۲ ساعت) نمایش داده نمی‌شوند."
                    )
                )
            } else {
                dashboard
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun refreshForecast(): Result<ForecastDashboard> = withContext(Dispatchers.IO) {
        try {
            val savedObs = getSavedSkyObservation()
            val dashboard = ForecastEngine.buildDashboardData(context, savedObs)
            val hourlyCount = dashboard.ranges[ForecastRange.HOURLY]?.size ?: 0
            if (hourlyCount > 0) {
                try {
                    val json = serializeDashboard(dashboard)
                    json.put("schema", 5)
                    json.put("cachedAtMs", System.currentTimeMillis())
                    cacheFile.writeText(json.toString())
                } catch (_: Exception) {}
            }
            Result.success(dashboard)
        } catch (e: Exception) {
            val fallback = getCachedForecast()
            if (fallback != null && (fallback.ranges[ForecastRange.HOURLY]?.size ?: 0) > 0) {
                Result.success(fallback)
            } else {
                Result.failure(e)
            }
        }
    }

    fun saveSkyObservationPhoto(bitmap: Bitmap): String? {
        return try {
            val obsDir = File(context.filesDir, "obs")
            if (!obsDir.exists()) obsDir.mkdirs()

            val maxDim = 600
            val width = bitmap.width
            val height = bitmap.height
            val scale = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height, 1.0f)

            val scaledBmp = if (scale < 1.0f) {
                Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
            } else bitmap

            val file = File(obsDir, "obs_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                scaledBmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun saveSkyObservation(report: SkyObservationReport) {
        skyPrefs.edit()
            .putString("id", report.id)
            .putString("photo_path", report.photoPath)
            .putString("direction", report.direction)
            .putString("phenomenon", report.phenomenon)
            .putString("user_temp", report.userTemp?.toString())
            .putString("notes", report.notes)
            .putString("timestamp_iran", report.timestampIran)
            .putLong("created_at_ms", report.createdAtEpochMs)
            .putBoolean("is_calibrated", report.isCalibrated)
            .apply()
    }

    fun getSavedSkyObservation(): SkyObservationReport? {
        val id = skyPrefs.getString("id", null) ?: return null
        val createdAtMs = skyPrefs.getLong("created_at_ms", 0L)
        val ageMin = (System.currentTimeMillis() - createdAtMs) / (60 * 1000L)

        // Expire after USER_REPORT_TTL_MIN (60 minutes)
        if (ageMin > Tuning.USER_REPORT_TTL_MIN) return null

        val phenomenon = skyPrefs.getString("phenomenon", "") ?: ""
        val direction = skyPrefs.getString("direction", "بالای سر") ?: "بالای سر"
        val photoPath = skyPrefs.getString("photo_path", null)
        val tempStr = skyPrefs.getString("user_temp", null)
        val notes = skyPrefs.getString("notes", "") ?: ""
        val timestamp = skyPrefs.getString("timestamp_iran", "") ?: ""
        val isCalibrated = skyPrefs.getBoolean("is_calibrated", false)

        return SkyObservationReport(
            id = id,
            photoPath = photoPath,
            direction = direction,
            phenomenon = phenomenon,
            userTemp = tempStr?.toDoubleOrNull(),
            notes = notes,
            timestampIran = timestamp,
            createdAtEpochMs = createdAtMs,
            isCalibrated = isCalibrated
        )
    }

    fun clearSkyObservation() {
        skyPrefs.edit().clear().apply()
    }

    private fun serializeDashboard(d: ForecastDashboard): JSONObject {
        val root = JSONObject()
        root.put("location", d.location)
        root.put("lat", d.lat)
        root.put("lon", d.lon)
        root.put("generatedIran", d.generatedIran)

        val verdictObj = JSONObject().apply {
            put("tier", d.verdict.tier.levelName)
            put("text", d.verdict.text)
            put("advisoryNote", d.verdict.advisoryNote)
        }
        root.put("verdict", verdictObj)

        val rObj = JSONObject().apply {
            put("available", d.radarNowcast.available)
            put("maxDbzInTarget", d.radarNowcast.maxDbzInTarget)
            put("wetFractionInTarget", d.radarNowcast.wetFractionInTarget)
            put("rainRateInTargetMmH", d.radarNowcast.rainRateInTargetMmH)
            put("statusText", d.radarNowcast.statusText)
            put("frameAgeMin", d.radarNowcast.frameAgeMin)
            put("coverageOk", d.radarNowcast.coverageOk)
        }
        root.put("radarNowcast", rObj)

        val nObj = JSONObject().apply {
            put("pointsWithRainNow", d.neighbourhood.pointsWithRainNow)
            put("totalPoints", d.neighbourhood.totalPoints)
            put("wettestDirectionFa", d.neighbourhood.wettestDirectionFa ?: "")
        }
        root.put("neighbourhood", nObj)

        val rangesObj = JSONObject()
        for ((range, list) in d.ranges) {
            val arr = JSONArray()
            for (row in list) {
                val rj = JSONObject().apply {
                    put("timeEpochSec", row.timeEpochSec)
                    put("timeIran", row.timeIran)
                    put("forecastSource", row.forecastSource)
                    put("probArea", row.probArea)
                    put("probPoint", row.probPoint)
                    put("amountP50", row.amountP50)
                    put("amountP90", row.amountP90)
                    put("tempC", row.tempC)
                    put("dewC", row.dewC)
                    put("rh", row.rh)
                    put("cloud", row.cloud)
                    put("gustKmh", row.gustKmh)
                    put("windSpeedKmh", row.windSpeedKmh)
                    put("windDirFromDeg", row.windDirFromDeg)
                    put("windDirLabel", row.windDirLabel ?: "")
                    put("convScore", row.convScore)
                    put("convLabel", row.convLabel)
                    put("virgaRisk", row.virgaRisk)
                    put("confidence", row.confidence)
                    put("eventLabel", row.eventLabel)
                }
                arr.put(rj)
            }
            rangesObj.put(range.key, arr)
        }
        root.put("ranges", rangesObj)
        return root
    }

    private fun parseDashboard(root: JSONObject): ForecastDashboard {
        val location = root.optString("location", "شعاع ۲۰ کیلومتری شمس‌آباد دزفول")
        val lat = root.optDouble("lat", RegionConfig.CENTER.lat)
        val lon = root.optDouble("lon", RegionConfig.CENTER.lon)
        val generatedIran = root.optString("generatedIran", "")

        val vObj = root.optJSONObject("verdict") ?: JSONObject()
        val tierStr = vObj.optString("tier", "green")
        val alertTier = when (tierStr.lowercase()) {
            "red" -> AlertTier.RED
            "orange" -> AlertTier.ORANGE
            "yellow" -> AlertTier.YELLOW
            else -> AlertTier.GREEN
        }
        val verdict = VerdictInfo(alertTier, vObj.optString("text", ""), vObj.optString("advisoryNote", ""))

        val rObj = root.optJSONObject("radarNowcast") ?: JSONObject()
        val radarNowcast = RadarNowcastResult(
            available = rObj.optBoolean("available", false),
            maxDbzInTarget = rObj.optInt("maxDbzInTarget", 0),
            wetFractionInTarget = rObj.optDouble("wetFractionInTarget", 0.0),
            rainRateInTargetMmH = rObj.optDouble("rainRateInTargetMmH", 0.0),
            statusText = rObj.optString("statusText", ""),
            frameAgeMin = rObj.optInt("frameAgeMin", 999),
            coverageOk = rObj.optBoolean("coverageOk", false)
        )

        val nObj = root.optJSONObject("neighbourhood") ?: JSONObject()
        val nSummary = NeighbourhoodSummary(
            pointsWithRainNow = nObj.optInt("pointsWithRainNow", 0),
            totalPoints = nObj.optInt("totalPoints", 17),
            wettestDirectionFa = nObj.optString("wettestDirectionFa").takeIf { it.isNotBlank() }
        )

        val rangesObj = root.optJSONObject("ranges") ?: JSONObject()
        val rangesMap = mutableMapOf<ForecastRange, List<ForecastRow>>()
        for (range in ForecastRange.entries) {
            val arr = rangesObj.optJSONArray(range.key) ?: JSONArray()
            val list = mutableListOf<ForecastRow>()
            for (i in 0 until arr.length()) {
                val rj = arr.optJSONObject(i) ?: continue
                list.add(
                    ForecastRow(
                        timeEpochSec = rj.optLong("timeEpochSec", 0L),
                        timeIran = rj.optString("timeIran", ""),
                        forecastSource = rj.optString("forecastSource", ""),
                        probArea = rj.optNullableDouble("probArea"),
                        probPoint = rj.optNullableDouble("probPoint"),
                        amountP50 = rj.optNullableDouble("amountP50"),
                        amountP90 = rj.optNullableDouble("amountP90"),
                        tempC = rj.optNullableDouble("tempC"),
                        dewC = rj.optNullableDouble("dewC"),
                        rh = rj.optNullableDouble("rh"),
                        cloud = rj.optNullableDouble("cloud"),
                        gustKmh = rj.optNullableDouble("gustKmh"),
                        windSpeedKmh = rj.optNullableDouble("windSpeedKmh"),
                        windDirFromDeg = rj.optNullableDouble("windDirFromDeg"),
                        windDirLabel = rj.optString("windDirLabel").takeIf { it.isNotBlank() },
                        convScore = rj.optNullableDouble("convScore"),
                        convLabel = rj.optString("convLabel", "پایدار"),
                        virgaRisk = rj.optBoolean("virgaRisk", false),
                        confidence = if (rj.has("confidence") && !rj.isNull("confidence")) rj.optInt("confidence") else null,
                        eventLabel = rj.optString("eventLabel", "—")
                    )
                )
            }
            rangesMap[range] = list
        }

        val pattern = PatternSummary(
            windowStartIran = if (rangesMap[ForecastRange.HOURLY]?.isNotEmpty() == true) rangesMap[ForecastRange.HOURLY]!!.first().timeIran else "",
            windowEndIran = if (rangesMap[ForecastRange.HOURLY]?.isNotEmpty() == true) rangesMap[ForecastRange.HOURLY]!!.last().timeIran else "",
            peakPrecipTimeIran = "—",
            peakPrecipMm = 0.0,
            peakTsTimeIran = "—",
            peakConvectiveScore = 0.0,
            maxCape = 0.0
        )

        return ForecastDashboard(
            location = location,
            lat = lat,
            lon = lon,
            generatedIran = generatedIran,
            verdict = verdict,
            radarNowcast = radarNowcast,
            neighbourhood = nSummary,
            synopObs = null,
            modelCurrent = null,
            pattern = pattern,
            ranges = rangesMap,
            userObservation = getSavedSkyObservation()
        )
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isNaN()) null else v
    }
}
