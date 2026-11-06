package com.amin.weather.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

data class ModelGrid(
    val times: LongArray,
    val precip: Array<DoubleArray>,       // precip[pointIndex][hourIndex]
    val showers: Array<DoubleArray>?,
    val cape: Array<DoubleArray>?,
    val cloud: Array<DoubleArray>?
) {
    val pointCount: Int get() = precip.size
}

data class CenterDetail(
    val times: LongArray,
    val temp: DoubleArray?,
    val dewPoint: DoubleArray?,
    val rh: DoubleArray?,
    val precip: DoubleArray?,
    val rain: DoubleArray?,
    val showers: DoubleArray?,
    val weatherCode: IntArray?,
    val cape: DoubleArray?,
    val cin: DoubleArray?,
    val li: DoubleArray?,
    val cloud: DoubleArray?,
    val cloudLow: DoubleArray?,
    val cloudMid: DoubleArray?,
    val windSpeed: DoubleArray?,
    val windDir: DoubleArray?,
    val windGust: DoubleArray?,
    val modelCurrent: ModelCurrent?
) {
    val hourlyTimes: LongArray get() = times
}

data class SteeringWind(
    val times: LongArray,
    val speedKmh: DoubleArray,
    val dirToDeg: DoubleArray,
    val moistureInflow: BooleanArray
)

data class EnsembleSet(
    val times: LongArray,
    val members: Array<DoubleArray>      // members[memberIndex][hourIndex]
) {
    fun idxOf(epochSec: Long): Int {
        return times.indexOfFirst { abs(it - epochSec) < 1800L }
    }
}

object OpenMeteoClient {
    private const val FORECAST_API = "https://api.open-meteo.com/v1/forecast"
    private const val ENSEMBLE_API = "https://ensemble-api.open-meteo.com/v1/ensemble"

    fun fetchNeighbourhood(): SourceResult<Map<String, ModelGrid>> {
        val lats = RegionConfig.NEIGHBOURHOOD.joinToString(",") { String.format(java.util.Locale.US, "%.4f", it.lat) }
        val lons = RegionConfig.NEIGHBOURHOOD.joinToString(",") { String.format(java.util.Locale.US, "%.4f", it.lon) }

        var ecmwfModel = "ecmwf_ifs"
        var iconModel = "icon_global"
        var message: String? = null

        var result = tryFetchNeighbourhood(lats, lons, ecmwfModel, iconModel)
        if (result == null || result.isEmpty()) {
            ecmwfModel = "ecmwf_ifs025"
            iconModel = "icon_seamless"
            message = "Fallback to ecmwf_ifs025 / icon_seamless"
            result = tryFetchNeighbourhood(lats, lons, ecmwfModel, iconModel)
        }

        return if (result != null && result.isNotEmpty()) {
            SourceResult("open-meteo/neighbourhood", SourceStatus.OK, result, message)
        } else {
            SourceResult("open-meteo/neighbourhood", SourceStatus.FAILED, null, "Failed to fetch neighbourhood data")
        }
    }

    private fun tryFetchNeighbourhood(lats: String, lons: String, ecmwfModel: String, iconModel: String): Map<String, ModelGrid>? {
        val url = "$FORECAST_API?latitude=$lats&longitude=$lons" +
                "&hourly=precipitation,showers,cape,cloud_cover" +
                "&models=$ecmwfModel,$iconModel&forecast_days=3&past_days=1&timezone=GMT&timeformat=unixtime"
        val resp = Http.get(url)
        if (!resp.isSuccess || resp.bodyString == null) return null

        return try {
            val body = resp.bodyString!!
            if (body.contains("\"error\":true")) return null

            val jsonArray = JSONArray(body)
            if (jsonArray.length() != RegionConfig.NEIGHBOURHOOD.size) return null

            val map = HashMap<String, ModelGrid>()
            val models = listOf(ecmwfModel, iconModel)

            for (model in models) {
                var times: LongArray? = null
                val nPoints = RegionConfig.NEIGHBOURHOOD.size

                val firstObj = jsonArray.getJSONObject(0)
                val firstHourly = firstObj.optJSONObject("hourly") ?: continue
                val timesJson = firstHourly.optJSONArray("time") ?: continue
                val nHours = timesJson.length()
                times = LongArray(nHours) { timesJson.getLong(it) }

                val precipGrid = Array(nPoints) { DoubleArray(nHours) }
                val showersGrid = Array(nPoints) { DoubleArray(nHours) }
                val capeGrid = Array(nPoints) { DoubleArray(nHours) }
                val cloudGrid = Array(nPoints) { DoubleArray(nHours) }

                var hasPrecipKey = false

                for (p in 0 until nPoints) {
                    val pObj = jsonArray.getJSONObject(p)
                    val hourly = pObj.optJSONObject("hourly") ?: continue

                    val pKey = getHourlyKey("precipitation", model, hourly)
                    val sKey = getHourlyKey("showers", model, hourly)
                    val cKey = getHourlyKey("cape", model, hourly)
                    val clKey = getHourlyKey("cloud_cover", model, hourly)

                    if (pKey != null) hasPrecipKey = true

                    val pArr = hourly.optJSONArray(pKey)
                    val sArr = hourly.optJSONArray(sKey)
                    val cArr = hourly.optJSONArray(cKey)
                    val clArr = hourly.optJSONArray(clKey)

                    for (h in 0 until nHours) {
                        precipGrid[p][h] = if (pArr == null || pArr.isNull(h)) Double.NaN else pArr.optDouble(h, Double.NaN)
                        showersGrid[p][h] = if (sArr == null || sArr.isNull(h)) Double.NaN else sArr.optDouble(h, Double.NaN)
                        capeGrid[p][h] = if (cArr == null || cArr.isNull(h)) Double.NaN else cArr.optDouble(h, Double.NaN)
                        cloudGrid[p][h] = if (clArr == null || clArr.isNull(h)) Double.NaN else clArr.optDouble(h, Double.NaN)
                    }
                }

                if (hasPrecipKey && times != null) {
                    val gridKey = if (model.contains("ecmwf")) "ecmwf" else "icon"
                    map[gridKey] = ModelGrid(times, precipGrid, showersGrid, capeGrid, cloudGrid)
                }
            }
            map
        } catch (e: Exception) {
            null
        }
    }

    fun fetchCenterDetail(): SourceResult<CenterDetail> {
        var model = "ecmwf_ifs"
        var result = tryFetchCenterDetail(model)
        if (result == null) {
            model = "ecmwf_ifs025"
            result = tryFetchCenterDetail(model)
        }

        return if (result != null) {
            SourceResult("open-meteo/center", SourceStatus.OK, result)
        } else {
            SourceResult("open-meteo/center", SourceStatus.FAILED, null, "Failed to fetch center detail")
        }
    }

    private fun tryFetchCenterDetail(model: String): CenterDetail? {
        val lat = RegionConfig.CENTER.lat
        val lon = RegionConfig.CENTER.lon
        val url = String.format(
            java.util.Locale.US,
            "$FORECAST_API?latitude=%.4f&longitude=%.4f" +
                    "&hourly=temperature_2m,dew_point_2m,relative_humidity_2m,precipitation,rain,showers,weather_code,cape,convective_inhibition,cloud_cover,cloud_cover_low,cloud_cover_mid,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
                    "&models=$model" +
                    "&current=temperature_2m,relative_humidity_2m,dew_point_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
                    "&forecast_days=3&past_days=1&timezone=GMT&timeformat=unixtime",
            lat, lon
        )

        val resp = Http.get(url)
        if (!resp.isSuccess || resp.bodyString == null) return null

        return try {
            val json = JSONObject(resp.bodyString!!)
            if (json.optBoolean("error", false)) return null

            val hourly = json.getJSONObject("hourly")
            val timesJson = hourly.getJSONArray("time")
            val nHours = timesJson.length()
            val times = LongArray(nHours) { timesJson.getLong(it) }

            val precip = parseNullableDoubleArray(hourly, "precipitation", model, nHours)
            if (precip == null || precip.all { it.isNaN() }) return null

            val temp = parseNullableDoubleArray(hourly, "temperature_2m", model, nHours)
            val dewPoint = parseNullableDoubleArray(hourly, "dew_point_2m", model, nHours)
            val rh = parseNullableDoubleArray(hourly, "relative_humidity_2m", model, nHours)
            val rain = parseNullableDoubleArray(hourly, "rain", model, nHours)
            val showers = parseNullableDoubleArray(hourly, "showers", model, nHours)
            val weatherCode = parseIntArray(hourly, "weather_code", model, nHours)
            val cape = parseNullableDoubleArray(hourly, "cape", model, nHours)
            val cin = parseNullableDoubleArray(hourly, "convective_inhibition", model, nHours)
            val cloud = parseNullableDoubleArray(hourly, "cloud_cover", model, nHours)
            val cloudLow = parseNullableDoubleArray(hourly, "cloud_cover_low", model, nHours)
            val cloudMid = parseNullableDoubleArray(hourly, "cloud_cover_mid", model, nHours)
            val windSpeed = parseNullableDoubleArray(hourly, "wind_speed_10m", model, nHours)
            val windDir = parseNullableDoubleArray(hourly, "wind_direction_10m", model, nHours)
            val windGust = parseNullableDoubleArray(hourly, "wind_gusts_10m", model, nHours)

            val currentObj = json.optJSONObject("current")
            val modelCurrent = if (currentObj != null) {
                ModelCurrent(
                    tempC = currentObj.optNullableDouble("temperature_2m"),
                    rh = currentObj.optNullableDouble("relative_humidity_2m"),
                    dewC = currentObj.optNullableDouble("dew_point_2m"),
                    precipMm = currentObj.optNullableDouble("precipitation"),
                    weatherCode = if (currentObj.has("weather_code") && !currentObj.isNull("weather_code")) currentObj.getInt("weather_code") else null,
                    cloudCover = currentObj.optNullableDouble("cloud_cover"),
                    windSpeedKmh = currentObj.optNullableDouble("wind_speed_10m"),
                    windDirDeg = currentObj.optNullableDouble("wind_direction_10m"),
                    windGustKmh = currentObj.optNullableDouble("wind_gusts_10m")
                )
            } else null

            CenterDetail(
                times = times,
                temp = temp,
                dewPoint = dewPoint,
                rh = rh,
                precip = precip,
                rain = rain,
                showers = showers,
                weatherCode = weatherCode,
                cape = cape,
                cin = cin,
                li = null,
                cloud = cloud,
                cloudLow = cloudLow,
                cloudMid = cloudMid,
                windSpeed = windSpeed,
                windDir = windDir,
                windGust = windGust,
                modelCurrent = modelCurrent
            )
        } catch (e: Exception) {
            null
        }
    }

    fun fetchLiftedIndex(): SourceResult<Pair<LongArray, DoubleArray>> {
        val lat = RegionConfig.CENTER.lat
        val lon = RegionConfig.CENTER.lon
        val url = String.format(
            java.util.Locale.US,
            "$FORECAST_API?latitude=%.4f&longitude=%.4f&models=gfs_seamless&hourly=lifted_index&forecast_days=3&past_days=1&timezone=GMT&timeformat=unixtime",
            lat, lon
        )

        val resp = Http.get(url)
        if (!resp.isSuccess || resp.bodyString == null) {
            return SourceResult("open-meteo/lifted_index", SourceStatus.PARTIAL, null, resp.error ?: "HTTP ${resp.code}")
        }

        return try {
            val json = JSONObject(resp.bodyString!!)
            if (json.optBoolean("error", false)) {
                return SourceResult("open-meteo/lifted_index", SourceStatus.PARTIAL, null, json.optString("reason", "API error"))
            }

            val hourly = json.getJSONObject("hourly")
            val timesJson = hourly.getJSONArray("time")
            val nHours = timesJson.length()
            val times = LongArray(nHours) { timesJson.getLong(it) }

            val liArr = parseNullableDoubleArray(hourly, "lifted_index", "gfs_seamless", nHours)
            if (liArr != null) {
                SourceResult("open-meteo/lifted_index", SourceStatus.OK, Pair(times, liArr))
            } else {
                SourceResult("open-meteo/lifted_index", SourceStatus.PARTIAL, null, "No lifted index data")
            }
        } catch (e: Exception) {
            SourceResult("open-meteo/lifted_index", SourceStatus.PARTIAL, null, e.message)
        }
    }

    fun fetchSteeringWind(): SourceResult<SteeringWind> {
        val lat = RegionConfig.CENTER.lat
        val lon = RegionConfig.CENTER.lon
        val url = String.format(
            java.util.Locale.US,
            "$FORECAST_API?latitude=%.4f&longitude=%.4f" +
                    "&hourly=wind_speed_700hPa,wind_direction_700hPa,wind_speed_500hPa,wind_direction_500hPa,wind_speed_850hPa,wind_direction_850hPa,relative_humidity_850hPa" +
                    "&models=ecmwf_ifs025&forecast_days=3&past_days=1&timezone=GMT&timeformat=unixtime",
            lat, lon
        )

        val resp = Http.get(url)
        if (!resp.isSuccess || resp.bodyString == null) {
            return SourceResult("open-meteo/steering_wind", SourceStatus.FAILED, null, resp.error ?: "HTTP ${resp.code}")
        }

        return try {
            val json = JSONObject(resp.bodyString!!)
            if (json.optBoolean("error", false)) {
                return SourceResult("open-meteo/steering_wind", SourceStatus.FAILED, null, json.optString("reason", "API error"))
            }

            val hourly = json.getJSONObject("hourly")
            val timesJson = hourly.getJSONArray("time")
            val nHours = timesJson.length()
            val times = LongArray(nHours) { timesJson.getLong(it) }

            val spd700 = parseNullableDoubleArray(hourly, "wind_speed_700hPa", "ecmwf_ifs025", nHours) ?: DoubleArray(nHours) { Double.NaN }
            val dir700 = parseNullableDoubleArray(hourly, "wind_direction_700hPa", "ecmwf_ifs025", nHours) ?: DoubleArray(nHours) { Double.NaN }
            val spd500 = parseNullableDoubleArray(hourly, "wind_speed_500hPa", "ecmwf_ifs025", nHours) ?: DoubleArray(nHours) { Double.NaN }
            val dir500 = parseNullableDoubleArray(hourly, "wind_direction_500hPa", "ecmwf_ifs025", nHours) ?: DoubleArray(nHours) { Double.NaN }
            val spd850 = parseNullableDoubleArray(hourly, "wind_speed_850hPa", "ecmwf_ifs025", nHours) ?: DoubleArray(nHours) { Double.NaN }
            val dir850 = parseNullableDoubleArray(hourly, "wind_direction_850hPa", "ecmwf_ifs025", nHours) ?: DoubleArray(nHours) { Double.NaN }
            val rh850 = parseNullableDoubleArray(hourly, "relative_humidity_850hPa", "ecmwf_ifs025", nHours) ?: DoubleArray(nHours) { Double.NaN }

            val speedKmh = DoubleArray(nHours)
            val dirToDeg = DoubleArray(nHours)
            val moistureInflow = BooleanArray(nHours)

            for (h in 0 until nHours) {
                if (spd700[h].isNaN() || dir700[h].isNaN() || spd500[h].isNaN() || dir500[h].isNaN()) {
                    speedKmh[h] = Double.NaN
                    dirToDeg[h] = Double.NaN
                    moistureInflow[h] = false
                    continue
                }

                val u700 = -spd700[h] * sin(Math.toRadians(dir700[h]))
                val v700 = -spd700[h] * cos(Math.toRadians(dir700[h]))
                val u500 = -spd500[h] * sin(Math.toRadians(dir500[h]))
                val v500 = -spd500[h] * cos(Math.toRadians(dir500[h]))

                val uMean = (u700 + u500) / 2.0
                val vMean = (v700 + v500) / 2.0

                speedKmh[h] = hypot(uMean, vMean)
                dirToDeg[h] = (Math.toDegrees(atan2(uMean, vMean)) + 360.0) % 360.0

                val dir850From = dir850[h]
                val isSouthToWest = !dir850From.isNaN() && dir850From in 160.0..280.0
                moistureInflow[h] = isSouthToWest && !spd850[h].isNaN() && spd850[h] >= Tuning.MOIST_INFLOW_MIN_KMH && !rh850[h].isNaN() && rh850[h] >= Tuning.MOIST_INFLOW_MIN_RH
            }

            val result = SteeringWind(times, speedKmh, dirToDeg, moistureInflow)
            SourceResult("open-meteo/steering_wind", SourceStatus.OK, result)
        } catch (e: Exception) {
            SourceResult("open-meteo/steering_wind", SourceStatus.FAILED, null, e.message)
        }
    }

    fun fetchEnsemble(requestedModel: String): SourceResult<EnsembleSet> {
        val lat = RegionConfig.CENTER.lat
        val lon = RegionConfig.CENTER.lon

        val modelsToTry = if (requestedModel.contains("icon")) {
            listOf("icon_seamless", "icon_global", "dwd_icon_seamless_eps")
        } else {
            listOf("ecmwf_ifs025")
        }

        for (m in modelsToTry) {
            val url = String.format(
                java.util.Locale.US,
                "$ENSEMBLE_API?latitude=%.4f&longitude=%.4f&models=$m&hourly=precipitation&forecast_days=3&past_days=1&timezone=GMT&timeformat=unixtime",
                lat, lon
            )

            val resp = Http.get(url)
            if (resp.isSuccess && resp.bodyString != null) {
                if (resp.bodyString!!.contains("\"error\":true")) continue
                try {
                    val json = JSONObject(resp.bodyString!!)
                    if (json.optBoolean("error", false)) continue

                    val hourly = json.getJSONObject("hourly")
                    val timesJson = hourly.getJSONArray("time")
                    val nHours = timesJson.length()
                    val times = LongArray(nHours) { timesJson.getLong(it) }

                    val memberList = ArrayList<DoubleArray>()
                    val keys = hourly.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key == "precipitation" || key.startsWith("precipitation_member")) {
                            val arr = hourly.getJSONArray(key)
                            val mValues = DoubleArray(nHours)
                            var allNull = true
                            for (h in 0 until nHours) {
                                if (arr.isNull(h)) {
                                    mValues[h] = Double.NaN
                                } else {
                                    mValues[h] = arr.optDouble(h, Double.NaN)
                                    if (!mValues[h].isNaN()) allNull = false
                                }
                            }
                            if (!allNull) {
                                memberList.add(mValues)
                            }
                        }
                    }

                    if (memberList.isNotEmpty()) {
                        val set = EnsembleSet(times, memberList.toTypedArray())
                        return SourceResult("open-meteo/ensemble/$m", SourceStatus.OK, set)
                    }
                } catch (_: Exception) {}
            }
        }

        return SourceResult("open-meteo/ensemble/$requestedModel", SourceStatus.FAILED, null, "Failed to fetch ensemble for $requestedModel")
    }

    private fun getHourlyKey(baseVar: String, model: String, hourly: JSONObject): String? {
        val k1 = "${baseVar}_$model"
        if (hourly.has(k1)) return k1
        if (hourly.has(baseVar)) return baseVar
        return null
    }

    private fun parseNullableDoubleArray(hourly: JSONObject, baseVar: String, model: String, nHours: Int): DoubleArray? {
        val key = getHourlyKey(baseVar, model, hourly) ?: return null
        val arr = hourly.optJSONArray(key) ?: return null
        val result = DoubleArray(nHours)
        var hasValid = false
        for (i in 0 until nHours) {
            if (arr.isNull(i)) {
                result[i] = Double.NaN
            } else {
                result[i] = arr.optDouble(i, Double.NaN)
                if (!result[i].isNaN()) hasValid = true
            }
        }
        return if (hasValid) result else null
    }

    private fun parseIntArray(hourly: JSONObject, baseVar: String, model: String, nHours: Int): IntArray? {
        val key = getHourlyKey(baseVar, model, hourly) ?: return null
        val arr = hourly.optJSONArray(key) ?: return null
        val result = IntArray(nHours)
        var hasValid = false
        for (i in 0 until nHours) {
            if (arr.isNull(i)) {
                result[i] = -1
            } else {
                result[i] = arr.optInt(i, -1)
                if (result[i] != -1) hasValid = true
            }
        }
        return if (hasValid) result else null
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val d = optDouble(key, Double.NaN)
        return if (d.isNaN()) null else d
    }
}
