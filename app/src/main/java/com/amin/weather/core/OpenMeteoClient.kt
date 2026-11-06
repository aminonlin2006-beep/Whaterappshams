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
    val temp: DoubleArray,
    val dewPoint: DoubleArray,
    val rh: DoubleArray,
    val precip: DoubleArray,
    val rain: DoubleArray,
    val showers: DoubleArray,
    val weatherCode: IntArray,
    val cape: DoubleArray?,
    val cin: DoubleArray?,
    val li: DoubleArray?,
    val cloud: DoubleArray,
    val cloudLow: DoubleArray?,
    val cloudMid: DoubleArray?,
    val windSpeed: DoubleArray,
    val windDir: DoubleArray,
    val windGust: DoubleArray,
    val modelCurrent: ModelCurrent?
) {
    val hourlyTimes: LongArray get() = times
    val hourlyTemp: DoubleArray get() = temp
    val hourlyDew: DoubleArray get() = dewPoint
    val hourlyRh: DoubleArray get() = rh
    val hourlyPrecip: DoubleArray get() = precip
    val hourlyCape: DoubleArray? get() = cape
    val hourlyCin: DoubleArray? get() = cin
    val hourlyLi: DoubleArray? get() = li
    val hourlyCloud: DoubleArray get() = cloud
    val hourlyWindSpeed: DoubleArray get() = windSpeed
    val hourlyWindDir: DoubleArray get() = windDir
    val hourlyWindGust: DoubleArray get() = windGust

    val currentTemp: Double? get() = modelCurrent?.tempC
    val currentRh: Double? get() = modelCurrent?.rh
    val currentDew: Double? get() = modelCurrent?.dewC
    val currentPrecip: Double? get() = modelCurrent?.precipMm
    val currentWeatherCode: Int? get() = modelCurrent?.weatherCode
    val currentCloud: Double? get() = modelCurrent?.cloudCover
    val currentWindSpeed: Double? get() = modelCurrent?.windSpeedKmh
    val currentWindDir: Double? get() = modelCurrent?.windDirDeg
    val currentWindGust: Double? get() = modelCurrent?.windGustKmh
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
)

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
                "&models=$ecmwfModel,$iconModel&forecast_days=3&timezone=GMT&timeformat=unixtime"
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
                var nHours = 0

                val firstObj = jsonArray.getJSONObject(0)
                val firstHourly = firstObj.optJSONObject("hourly") ?: continue
                val timesJson = firstHourly.optJSONArray("time") ?: continue
                nHours = timesJson.length()
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
                        precipGrid[p][h] = pArr?.optDouble(h, 0.0) ?: 0.0
                        showersGrid[p][h] = sArr?.optDouble(h, 0.0) ?: 0.0
                        capeGrid[p][h] = cArr?.optDouble(h, 0.0) ?: 0.0
                        cloudGrid[p][h] = clArr?.optDouble(h, 0.0) ?: 0.0
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
        val lat = RegionConfig.CENTER.lat
        val lon = RegionConfig.CENTER.lon
        val url = String.format(
            java.util.Locale.US,
            "$FORECAST_API?latitude=%.4f&longitude=%.4f" +
                    "&hourly=temperature_2m,dew_point_2m,relative_humidity_2m,precipitation,rain,showers,weather_code,cape,convective_inhibition,lifted_index,cloud_cover,cloud_cover_low,cloud_cover_mid,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
                    "&models=ecmwf_ifs,icon_global,gfs_seamless" +
                    "&current=temperature_2m,relative_humidity_2m,dew_point_2m,precipitation,weather_code,cloud_cover,wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
                    "&forecast_days=3&timezone=GMT&timeformat=unixtime",
            lat, lon
        )

        val resp = Http.get(url)
        if (!resp.isSuccess || resp.bodyString == null) {
            return SourceResult("open-meteo/center", SourceStatus.FAILED, null, resp.error ?: "HTTP ${resp.code}")
        }

        return try {
            val json = JSONObject(resp.bodyString!!)
            if (json.optBoolean("error", false)) {
                return SourceResult("open-meteo/center", SourceStatus.FAILED, null, json.optString("reason", "API error"))
            }

            val hourly = json.getJSONObject("hourly")
            val timesJson = hourly.getJSONArray("time")
            val nHours = timesJson.length()
            val times = LongArray(nHours) { timesJson.getLong(it) }

            val temp = parseDoubleArray(hourly, "temperature_2m", "ecmwf_ifs", nHours)
            val dewPoint = parseDoubleArray(hourly, "dew_point_2m", "ecmwf_ifs", nHours)
            val rh = parseDoubleArray(hourly, "relative_humidity_2m", "ecmwf_ifs", nHours)
            val precip = parseDoubleArray(hourly, "precipitation", "ecmwf_ifs", nHours)
            val rain = parseDoubleArray(hourly, "rain", "ecmwf_ifs", nHours)
            val showers = parseDoubleArray(hourly, "showers", "ecmwf_ifs", nHours)
            val weatherCode = parseIntArray(hourly, "weather_code", "ecmwf_ifs", nHours)
            val cape = parseNullableDoubleArray(hourly, "cape", "ecmwf_ifs", nHours)
            val cin = parseNullableDoubleArray(hourly, "convective_inhibition", "ecmwf_ifs", nHours)
            val li = parseNullableDoubleArray(hourly, "lifted_index", "gfs_seamless", nHours)
            val cloud = parseDoubleArray(hourly, "cloud_cover", "ecmwf_ifs", nHours)
            val cloudLow = parseNullableDoubleArray(hourly, "cloud_cover_low", "ecmwf_ifs", nHours)
            val cloudMid = parseNullableDoubleArray(hourly, "cloud_cover_mid", "ecmwf_ifs", nHours)
            val windSpeed = parseDoubleArray(hourly, "wind_speed_10m", "ecmwf_ifs", nHours)
            val windDir = parseDoubleArray(hourly, "wind_direction_10m", "ecmwf_ifs", nHours)
            val windGust = parseDoubleArray(hourly, "wind_gusts_10m", "ecmwf_ifs", nHours)

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

            val detail = CenterDetail(
                times, temp, dewPoint, rh, precip, rain, showers, weatherCode,
                cape, cin, li, cloud, cloudLow, cloudMid, windSpeed, windDir, windGust, modelCurrent
            )
            SourceResult("open-meteo/center", SourceStatus.OK, detail)
        } catch (e: Exception) {
            SourceResult("open-meteo/center", SourceStatus.FAILED, null, e.message)
        }
    }

    fun fetchSteeringWind(): SourceResult<SteeringWind> {
        val lat = RegionConfig.CENTER.lat
        val lon = RegionConfig.CENTER.lon
        val url = String.format(
            java.util.Locale.US,
            "$FORECAST_API?latitude=%.4f&longitude=%.4f" +
                    "&hourly=wind_speed_700hPa,wind_direction_700hPa,wind_speed_500hPa,wind_direction_500hPa,wind_speed_850hPa,wind_direction_850hPa,relative_humidity_850hPa" +
                    "&models=ecmwf_ifs025&forecast_days=3&timezone=GMT&timeformat=unixtime",
            lat, lon
        )

        val resp = Http.get(url)
        if (!resp.isSuccess || resp.bodyString == null) {
            return SourceResult("open-meteo/steering_wind", SourceStatus.FAILED, null, resp.error ?: "HTTP ${resp.code}")
        }

        return try {
            val json = JSONObject(resp.bodyString!!)
            val hourly = json.getJSONObject("hourly")
            val timesJson = hourly.getJSONArray("time")
            val nHours = timesJson.length()
            val times = LongArray(nHours) { timesJson.getLong(it) }

            val spd700 = parseDoubleArray(hourly, "wind_speed_700hPa", "ecmwf_ifs025", nHours)
            val dir700 = parseDoubleArray(hourly, "wind_direction_700hPa", "ecmwf_ifs025", nHours)
            val spd500 = parseDoubleArray(hourly, "wind_speed_500hPa", "ecmwf_ifs025", nHours)
            val dir500 = parseDoubleArray(hourly, "wind_direction_500hPa", "ecmwf_ifs025", nHours)
            val spd850 = parseDoubleArray(hourly, "wind_speed_850hPa", "ecmwf_ifs025", nHours)
            val dir850 = parseDoubleArray(hourly, "wind_direction_850hPa", "ecmwf_ifs025", nHours)
            val rh850 = parseDoubleArray(hourly, "relative_humidity_850hPa", "ecmwf_ifs025", nHours)

            val speedKmh = DoubleArray(nHours)
            val dirToDeg = DoubleArray(nHours)
            val moistureInflow = BooleanArray(nHours)

            for (h in 0 until nHours) {
                val u700 = -spd700[h] * sin(Math.toRadians(dir700[h]))
                val v700 = -spd700[h] * cos(Math.toRadians(dir700[h]))
                val u500 = -spd500[h] * sin(Math.toRadians(dir500[h]))
                val v500 = -spd500[h] * cos(Math.toRadians(dir500[h]))

                val uMean = (u700 + u500) / 2.0
                val vMean = (v700 + v500) / 2.0

                speedKmh[h] = hypot(uMean, vMean)
                dirToDeg[h] = (Math.toDegrees(atan2(uMean, vMean)) + 360.0) % 360.0

                val dir850From = dir850[h]
                val isSouthToWest = dir850From in 160.0..280.0
                moistureInflow[h] = isSouthToWest && spd850[h] >= Tuning.MOIST_INFLOW_MIN_KMH && rh850[h] >= Tuning.MOIST_INFLOW_MIN_RH
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
                "$ENSEMBLE_API?latitude=%.4f&longitude=%.4f&models=$m&hourly=precipitation&forecast_days=3&timezone=GMT&timeformat=unixtime",
                lat, lon
            )

            val resp = Http.get(url)
            if (resp.isSuccess && resp.bodyString != null && !resp.bodyString!!.contains("\"error\":true")) {
                try {
                    val json = JSONObject(resp.bodyString!!)
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
                                    mValues[h] = arr.getDouble(h)
                                    allNull = false
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

    private fun parseDoubleArray(hourly: JSONObject, baseVar: String, model: String, nHours: Int): DoubleArray {
        val key = getHourlyKey(baseVar, model, hourly)
        val result = DoubleArray(nHours)
        if (key == null) return result
        val arr = hourly.optJSONArray(key) ?: return result
        for (i in 0 until nHours) {
            result[i] = arr.optDouble(i, 0.0)
        }
        return result
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

    private fun parseIntArray(hourly: JSONObject, baseVar: String, model: String, nHours: Int): IntArray {
        val key = getHourlyKey(baseVar, model, hourly)
        val result = IntArray(nHours)
        if (key == null) return result
        val arr = hourly.optJSONArray(key) ?: return result
        for (i in 0 until nHours) {
            result[i] = arr.optInt(i, 0)
        }
        return result
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val d = optDouble(key, Double.NaN)
        return if (d.isNaN()) null else d
    }
}
