package com.amin.weather.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*

object ForecastEngine {

    private val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val iranFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("GMT+03:30")
    }

    fun utcToIran(utcIso: String?): String {
        if (utcIso.isNullOrBlank()) return ""
        return try {
            val clean = utcIso.take(16).replace(' ', 'T')
            val date = utcFormat.parse(clean) ?: return utcIso.replace('T', ' ')
            iranFormat.format(date)
        } catch (_: Exception) {
            utcIso.replace('T', ' ')
        }
    }

    fun epochToIran(epochSec: Long): String {
        val date = Date(epochSec * 1000L)
        return iranFormat.format(date)
    }

    fun calculateDewPoint(temp: Double, rh: Double): Double {
        val a = 17.27
        val b = 237.7
        val clampedRh = rh.coerceIn(1.0, 100.0)
        val alpha = ((a * temp) / (b + temp)) + ln(clampedRh / 100.0)
        val denom = a - alpha
        return if (abs(denom) > 1e-6) (b * alpha) / denom else temp
    }

    private fun r1(v: Double?): Double? = v?.let { (it * 10.0).roundToInt() / 10.0 }
    private fun r2(v: Double?): Double? = v?.let { (it * 100.0).roundToInt() / 100.0 }

    /**
     * Builds complete forecast dashboard dataset
     */
    fun buildDashboardData(
        context: Context? = null,
        userObservation: SkyObservationReport? = null
    ): ForecastDashboard {
        val sourceHealthList = mutableListOf<SourceHealth>()

        val cacheDir: File? = context?.filesDir

        // 1. Fetch data from clients
        val neighRes = OpenMeteoClient.fetchNeighbourhood()
        sourceHealthList.add(SourceHealth("open-meteo/neighbourhood", neighRes.status, neighRes.message))

        val centerRes = OpenMeteoClient.fetchCenterDetail()
        sourceHealthList.add(SourceHealth("open-meteo/center", centerRes.status, centerRes.message))

        val steeringRes = OpenMeteoClient.fetchSteeringWind()
        sourceHealthList.add(SourceHealth("open-meteo/steering", steeringRes.status, steeringRes.message))

        val ensEcmwfRes = OpenMeteoClient.fetchEnsemble("ecmwf_ifs025")
        sourceHealthList.add(SourceHealth("ensemble/ecmwf", ensEcmwfRes.status, ensEcmwfRes.message))

        val ensIconRes = OpenMeteoClient.fetchEnsemble("icon_seamless")
        sourceHealthList.add(SourceHealth("ensemble/icon", ensIconRes.status, ensIconRes.message))

        val synopRes = SynopClient.fetchLatestSynop(cacheDir)
        sourceHealthList.add(SourceHealth("ogimet/synop", synopRes.status, synopRes.message))

        val radarRes = RadarClient.fetchRadarFrames(cacheDir)
        sourceHealthList.add(SourceHealth("rainviewer/radar", radarRes.status, radarRes.message))

        val steeringWind = steeringRes.data
        val radarNowcast = RadarAnalyzer.analyzeNowcast(radarRes.data, steeringWind)
        val synop = synopRes.data
        val centerDetail = centerRes.data
        val neighMap = neighRes.data ?: emptyMap()
        val ensEcmwf = ensEcmwfRes.data
        val ensIcon = ensIconRes.data

        if (context != null) {
            Calibration.load(context)
            if (synop != null) {
                VerificationStore.evaluateWithSynop(context, synop)
            }
        }

        val nowEpochSec = System.currentTimeMillis() / 1000L
        val nowIranStr = epochToIran(nowEpochSec)

        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+03:30"))
        val currentMonth = cal.get(Calendar.MONTH) + 1

        val times = centerDetail?.hourlyTimes ?: LongArray(0)
        val nowHourIndex = times.indexOfFirst { it >= nowEpochSec - 1800L }.coerceAtLeast(0)

        val ecmwfGrid = neighMap["ecmwf"]
        val iconGrid = neighMap["icon"]

        val rows = mutableListOf<ForecastRow>()
        var peakPrecipMm = 0.0
        var peakPrecipTime = "—"
        var peakTsTime = "—"
        var peakSci = 0.0
        var maxCape = 0.0
        var maxGust = 0.0
        var sumPrecipP50 = 0.0
        var sumPrecipP90 = 0.0
        var virgaCount = 0
        var convectiveCount = 0

        val hoursToProcess = minOf(48, maxOf(0, times.size - nowHourIndex))

        for (step in 0 until hoursToProcess) {
            val idx = nowHourIndex + step
            val hEpoch = if (idx < times.size) times[idx] else nowEpochSec + step * 3600L
            val hIran = epochToIran(hEpoch)

            val temp = centerDetail?.hourlyTemp?.getOrNull(idx)
            val rh = centerDetail?.hourlyRh?.getOrNull(idx) ?: 50.0
            val dewPoint = centerDetail?.hourlyDew?.getOrNull(idx) ?: calculateDewPoint(temp ?: 24.0, rh)
            val dpDepression = if (temp != null) max(0.0, temp - dewPoint) else 10.0
            val cape = centerDetail?.hourlyCape?.getOrNull(idx)
            val li = centerDetail?.hourlyLi?.getOrNull(idx)
            val cloud = centerDetail?.hourlyCloud?.getOrNull(idx)
            val cloudMid = centerDetail?.cloudMid?.getOrNull(idx) ?: 0.0
            val windSpeed = centerDetail?.hourlyWindSpeed?.getOrNull(idx)
            val windGust = centerDetail?.hourlyWindGust?.getOrNull(idx)
            val windDir = centerDetail?.hourlyWindDir?.getOrNull(idx)

            // 4.1 Neighbourhood model probabilities
            val pNeighEcmwf = ecmwfGrid?.let { grid ->
                if (grid.times.isEmpty()) null
                else {
                    val gIdx = grid.times.indexOfFirst { abs(it - hEpoch) < 1800L }
                    if (gIdx >= 0) {
                        var wetCount = 0
                        var totalValid = 0
                        for (p in 0 until grid.pointCount) {
                            val pr = grid.precip[p].getOrNull(gIdx) ?: 0.0
                            val sh = grid.showers?.get(p)?.getOrNull(gIdx) ?: 0.0
                            val cp = grid.cape?.get(p)?.getOrNull(gIdx) ?: 0.0
                            if (grid.precip[p].getOrNull(gIdx) != null) {
                                totalValid++
                                if (pr >= Tuning.WET_HOUR_MM || sh >= Tuning.WET_HOUR_MM || cp >= 250.0) wetCount++
                            }
                        }
                        if (totalValid > 0) wetCount.toDouble() / totalValid else null
                    } else null
                }
            }

            val pNeighIcon = iconGrid?.let { grid ->
                if (grid.times.isEmpty()) null
                else {
                    val gIdx = grid.times.indexOfFirst { abs(it - hEpoch) < 1800L }
                    if (gIdx >= 0) {
                        var wetCount = 0
                        var totalValid = 0
                        for (p in 0 until grid.pointCount) {
                            val pr = grid.precip[p].getOrNull(gIdx)
                            if (pr != null) {
                                totalValid++
                                if (pr >= Tuning.WET_HOUR_MM) wetCount++
                            }
                        }
                        if (totalValid > 0) wetCount.toDouble() / totalValid else null
                    } else null
                }
            }

            // 4.2 Ensemble probability
            var pEnsEcmwf: Double? = null
            if (ensEcmwf != null && ensEcmwf.times.isNotEmpty()) {
                val eIdx = ensEcmwf.times.indexOfFirst { abs(it - hEpoch) < 1800L }
                if (eIdx >= 0) {
                    var wetMem = 0
                    var validMem = 0
                    for (m in ensEcmwf.members.indices) {
                        var sum3h = 0.0
                        var valid3h = 0
                        for (w in maxOf(0, eIdx - 2)..eIdx) {
                            val v = ensEcmwf.members[m].getOrNull(w)
                            if (v != null && !v.isNaN()) {
                                sum3h += v
                                valid3h++
                            }
                        }
                        if (valid3h > 0) {
                            validMem++
                            if (sum3h >= Tuning.WET_3H_MM) wetMem++
                        }
                    }
                    if (validMem > 0) pEnsEcmwf = wetMem.toDouble() / validMem
                }
            }

            var pEnsIcon: Double? = null
            if (ensIcon != null && ensIcon.times.isNotEmpty()) {
                val eIdx = ensIcon.times.indexOfFirst { abs(it - hEpoch) < 1800L }
                if (eIdx >= 0) {
                    var wetMem = 0
                    var validMem = 0
                    for (m in ensIcon.members.indices) {
                        val v = ensIcon.members[m].getOrNull(eIdx)
                        if (v != null && !v.isNaN()) {
                            validMem++
                            if (v >= Tuning.WET_HOUR_MM) wetMem++
                        }
                    }
                    if (validMem > 0) pEnsIcon = wetMem.toDouble() / validMem
                }
            }

            val pEns = when {
                pEnsEcmwf != null && pEnsIcon != null -> (51.0 * pEnsEcmwf + 40.0 * pEnsIcon) / 91.0
                pEnsEcmwf != null -> pEnsEcmwf
                pEnsIcon != null -> pEnsIcon
                else -> null
            }

            val pNeighEcmwfCal = if (Calibration.enabled && pNeighEcmwf != null) {
                Calibration.calibrateProb(pNeighEcmwf)
            } else pNeighEcmwf

            // 4.3 Combined model area probability
            var wSum = 0.0
            var pModelSum = 0.0
            if (pEns != null) { pModelSum += Tuning.W_ENSEMBLE * pEns; wSum += Tuning.W_ENSEMBLE }
            if (pNeighEcmwfCal != null) { pModelSum += Tuning.W_NEIGH_ECMWF * pNeighEcmwfCal; wSum += Tuning.W_NEIGH_ECMWF }
            if (pNeighIcon != null) { pModelSum += Tuning.W_NEIGH_ICON * pNeighIcon; wSum += Tuning.W_NEIGH_ICON }

            var pArea: Double? = if (wSum > 0) pModelSum / wSum else null

            val pCenterEcmwf = centerDetail?.hourlyPrecip?.getOrNull(idx)?.let { if (it >= Tuning.WET_HOUR_MM) 1.0 else 0.0 }
            var pPointModel: Double? = when {
                pEns != null && pCenterEcmwf != null -> 0.6 * pEns + 0.4 * pCenterEcmwf
                pEns != null -> pEns
                pCenterEcmwf != null -> pCenterEcmwf
                else -> pArea
            }

            // 4.5 Radar Nowcast (h = 0, 1, 2)
            var pNow: Double? = null
            var pNowPoint: Double? = null
            if (step < 3 && radarNowcast.available && radarNowcast.frameAgeMin <= Tuning.RADAR_MAX_FRAME_AGE_MIN) {
                if (radarNowcast.wetFractionInTarget >= 0.05 && step == 0) {
                    pNow = min(0.95, 0.6 + 4.0 * radarNowcast.wetFractionInTarget)
                } else {
                    val arr = radarNowcast.firstArrival
                    if (arr != null && arr.maxDbz >= Tuning.DBZ_STRONG && arr.etaLowMin <= (step + 1) * 60) {
                        pNow = 0.85
                    } else if (arr != null && arr.maxDbz >= Tuning.DBZ_RAIN && arr.etaLowMin <= (step + 1) * 60) {
                        pNow = 0.65
                    } else if (radarNowcast.cells.any { it.state == CellApproachState.WILL_MISS && it.closestDistKm <= Tuning.TARGET_RADIUS_KM + 15.0 }) {
                        pNow = 0.25
                    } else if (radarNowcast.maxDbzInTarget < Tuning.DBZ_LIGHT && radarNowcast.cells.isEmpty()) {
                        pNow = 0.03
                    }
                }
            }

            if (pNow != null && step < Tuning.NOWCAST_WEIGHT.size && pArea != null) {
                val w = Tuning.NOWCAST_WEIGHT[step]
                pArea = w * pNow + (1.0 - w) * pArea
                pNowPoint = if (radarNowcast.maxDbzInTarget >= Tuning.DBZ_RAIN) 0.9 else 0.6 * pNow
                pPointModel = w * pNowPoint + (1.0 - w) * (pPointModel ?: pArea)
            }

            // 4.6 SYNOP Safiabad Override (h = 0)
            if (step == 0 && synop != null && (nowEpochSec - synop.timeUtcEpochSec) < 5400L) {
                if (synop.isRainNow || synop.isThunder) {
                    pArea = max(pArea ?: 0.0, 0.9)
                    pPointModel = max(pPointModel ?: 0.0, 0.8)
                } else if (synop.ww != null && synop.ww < 20 && radarNowcast.maxDbzInTarget < Tuning.DBZ_LIGHT) {
                    pPointModel = min(pPointModel ?: 1.0, 0.15)
                }
            }

            // 4.7 User Observation Override (h = 0)
            if (step == 0 && userObservation != null) {
                val ageMin = (nowEpochSec - (userObservation.createdAtEpochMs / 1000L)) / 60L
                if (ageMin < Tuning.USER_REPORT_TTL_MIN) {
                    val k = 1.0 - (ageMin.toDouble() / Tuning.USER_REPORT_TTL_MIN)
                    val isUserRain = userObservation.phenomenon.contains("باران") ||
                            userObservation.phenomenon.contains("رگبار") ||
                            userObservation.phenomenon.contains("رعدوبرق")
                    if (isUserRain && pArea != null) {
                        pArea = max(pArea, k * Tuning.USER_REPORT_MAX_PROB)
                    }
                }
            }

            // 4.8 Climate Guard
            if (currentMonth in Tuning.DRY_MONTHS && (pArea ?: 0.0) < Tuning.DRY_MONTH_STRONG_SIGNAL && radarNowcast.maxDbzInTarget < Tuning.DBZ_RAIN) {
                if (pArea != null) pArea = min(pArea, Tuning.DRY_MONTH_PROB_CAP)
            }

            // 4.9 Virga Risk
            val isVirga = dpDepression >= Tuning.VIRGA_DEW_DEPRESSION_C && rh <= Tuning.VIRGA_RH_MAX && (pArea ?: 0.0) >= 0.15
            if (isVirga) {
                virgaCount++
                if (pPointModel != null) pPointModel *= Tuning.VIRGA_PROB_FACTOR
            }

            val pPoint = if (pPointModel != null && pArea != null) min(pPointModel, pArea) else pPointModel

            // 4.10 Amounts P50 & P90
            val memberValues = mutableListOf<Double>()
            if (ensIcon != null && ensIcon.times.isNotEmpty()) {
                val eIdx = ensIcon.times.indexOfFirst { abs(it - hEpoch) < 1800L }
                if (eIdx >= 0) {
                    for (m in ensIcon.members.indices) {
                        val v = ensIcon.members[m].getOrNull(eIdx)
                        if (v != null && !v.isNaN()) memberValues.add(v)
                    }
                }
            }
            val centerRawPrecip = centerDetail?.hourlyPrecip?.getOrNull(idx) ?: 0.0
            val centerPrecipCal = if (Calibration.enabled) Calibration.calibrateAmount(centerRawPrecip) else centerRawPrecip
            memberValues.add(centerPrecipCal)

            memberValues.sort()
            var amtP50 = if (memberValues.isNotEmpty()) memberValues[memberValues.size / 2] else 0.0
            var amtP90 = if (memberValues.isNotEmpty()) memberValues[(memberValues.size * 0.9).toInt().coerceIn(0, memberValues.size - 1)] else 0.0

            if (step < 3 && radarNowcast.available && pArea != null) {
                val w = Tuning.NOWCAST_WEIGHT[step]
                val amountNow = radarNowcast.rainRateInTargetMmH * min(1.0, 2.0 * 10.0 / max(Tuning.CELL_SPEED_MIN_KMH, radarNowcast.firstArrival?.speedKmh ?: 30.0))
                amtP50 = w * amountNow * (pNow ?: 0.0) + (1.0 - w) * amtP50
                amtP90 = max(amtP90, amountNow)
            }

            amtP50 = r2(amtP50) ?: 0.0
            amtP90 = r2(amtP90) ?: 0.0

            if (amtP50 > peakPrecipMm) {
                peakPrecipMm = amtP50
                peakPrecipTime = hIran
            }
            sumPrecipP50 += amtP50
            sumPrecipP90 += amtP90

            // 4.11 Convective Potential Score
            val moistureInflowFlag = steeringWind?.moistureInflow?.getOrNull(idx) ?: false
            var convScore: Double? = null
            if (cape != null) {
                var sc = 45.0 * (cape / 1500.0).coerceIn(0.0, 1.0)
                if (li != null) sc += 15.0 * ((-li) / 6.0).coerceIn(0.0, 1.0)
                if (pEns != null) sc += 25.0 * pEns
                val td = centerDetail?.hourlyDew?.getOrNull(idx) ?: 10.0
                sc += 15.0 * ((td - 5.0) / 10.0).coerceIn(0.0, 1.0)
                if (moistureInflowFlag) sc += 10.0
                sc = min(sc, 100.0)
                val cin = centerDetail?.hourlyCin?.getOrNull(idx) ?: 0.0
                if (abs(cin) > 100.0) sc *= 0.6
                convScore = r1(sc)
            }

            val cScoreVal = convScore ?: 0.0
            if (cScoreVal > peakSci) {
                peakSci = cScoreVal
                peakTsTime = hIran
            }
            if ((cape ?: 0.0) > maxCape) maxCape = cape ?: 0.0
            if ((windGust ?: 0.0) > maxGust) maxGust = windGust ?: 0.0

            val isConvective = cScoreVal >= 35.0 || amtP90 >= 1.0
            if (isConvective) convectiveCount++

            val convLabelStr = when {
                cScoreVal >= 60.0 && (pArea ?: 0.0) >= 0.4 -> "احتمال رگبار همرفتی و رعدوبرق ⚡"
                cScoreVal >= 35.0 -> "ناپایداری متوسط ☁️"
                else -> "پایدار ☀️"
            }

            val confidenceVal: Int? = if (pEns != null && pNeighEcmwf != null) {
                var c = (100.0 * (1.0 - 2.0 * abs(pEns - pNeighEcmwf))).roundToInt().coerceIn(0, 100)
                if (step <= 2 && radarNowcast.available && radarNowcast.coverageOk) c = min(100, c + 15)
                c
            } else null

            val eventLabelStr = getEventLabel(
                sci = cScoreVal,
                pArea = pArea ?: 0.0,
                precipP90 = amtP90,
                isVirga = isVirga,
                isRadarEcho = radarNowcast.maxDbzInTarget >= Tuning.DBZ_RAIN
            )

            val sourcesList = mutableListOf<String>()
            if (step <= 2 && radarNowcast.available) sourcesList.add("رادار RainViewer")
            if (pNeighEcmwf != null) sourcesList.add("ECMWF IFS")
            if (pNeighIcon != null) sourcesList.add("DWD ICON")
            if (synop != null && step == 0) sourcesList.add("دیدبانی صفی‌آباد (SYNOP)")

            val row = ForecastRow(
                timeEpochSec = hEpoch,
                timeIran = hIran,
                forecastSource = if (step <= 2 && radarNowcast.available && radarNowcast.maxDbzInTarget >= Tuning.DBZ_LIGHT) "📡 رادار زنده" else "🇪🇺 مدل اروپایی ECMWF",
                probArea = r1((pArea ?: 0.0) * 100.0),
                probPoint = r1((pPoint ?: 0.0) * 100.0),
                amountP50 = amtP50,
                amountP90 = amtP90,
                tempC = r1(temp),
                dewC = r1(dewPoint),
                rh = r1(rh),
                cloud = r1(cloud),
                gustKmh = r1(windGust),
                windSpeedKmh = r1(windSpeed),
                windDirFromDeg = r1(windDir),
                windDirLabel = GeoUtils.dir16Fa(windDir ?: 270.0),
                convScore = convScore,
                convLabel = convLabelStr,
                virgaRisk = isVirga,
                confidence = confidenceVal,
                sources = sourcesList,
                eventLabel = eventLabelStr
            )
            rows.add(row)

            if (context != null) {
                VerificationStore.recordForecast(context, hEpoch, pPoint, amtP50)
            }
        }

        val alertVerdict = decideTier(radarNowcast, synop, ensEcmwfRes.data, rows)

        val rangesMap = mutableMapOf<ForecastRange, List<ForecastRow>>()
        rangesMap[ForecastRange.HOURLY] = rows

        fun buildBlock(count: Int): ForecastRow {
            val subList = rows.take(minOf(count, rows.size))
            if (subList.isEmpty()) return rows.first()
            val maxProbArea = subList.maxOfOrNull { it.probArea ?: 0.0 } ?: 0.0
            val maxProbPoint = subList.maxOfOrNull { it.probPoint ?: 0.0 } ?: 0.0
            val sumP50 = subList.sumOf { it.amountP50 ?: 0.0 }
            val sumP90 = subList.sumOf { it.amountP90 ?: 0.0 }
            val maxT = subList.maxOfOrNull { it.tempC ?: -99.0 }
            val maxGustVal = subList.maxOfOrNull { it.gustKmh ?: 0.0 }
            val maxConv = subList.maxOfOrNull { it.convScore ?: 0.0 }
            val anyVirga = subList.any { it.virgaRisk }
            val first = subList.first()
            val last = subList.last()

            return ForecastRow(
                timeEpochSec = first.timeEpochSec,
                timeIran = "${first.timeIran.takeLast(5)} تا ${last.timeIran.takeLast(5)}",
                forecastSource = first.forecastSource,
                probArea = r1(maxProbArea),
                probPoint = r1(maxProbPoint),
                amountP50 = r2(sumP50),
                amountP90 = r2(sumP90),
                tempC = r1(maxT),
                dewC = first.dewC,
                rh = first.rh,
                cloud = first.cloud,
                gustKmh = r1(maxGustVal),
                windSpeedKmh = first.windSpeedKmh,
                windDirFromDeg = first.windDirFromDeg,
                windDirLabel = first.windDirLabel,
                convScore = r1(maxConv),
                convLabel = if ((maxConv ?: 0.0) >= 35.0 || sumP90 >= 1.0) "پتانسیل رگبار همرفتی ⚡" else if (sumP50 > 0.1) "بارش باران 🌧️" else "آرام ☀️",
                virgaRisk = anyVirga,
                confidence = first.confidence,
                sources = first.sources,
                eventLabel = if (sumP50 > 0.1) "بارش باران 🌧️" else "آرام ☀️"
            )
        }

        rangesMap[ForecastRange.H1] = listOf(buildBlock(1))
        rangesMap[ForecastRange.H3] = listOf(buildBlock(3))
        rangesMap[ForecastRange.H6] = listOf(buildBlock(6))
        rangesMap[ForecastRange.H12] = listOf(buildBlock(12))
        rangesMap[ForecastRange.H24] = listOf(buildBlock(24))

        val pattern = PatternSummary(
            windowStartIran = if (rows.isNotEmpty()) rows.first().timeIran else "",
            windowEndIran = if (rows.isNotEmpty()) rows.last().timeIran else "",
            peakPrecipTimeIran = if (peakPrecipMm > 0.0) peakPrecipTime else "—",
            peakPrecipMm = r2(peakPrecipMm) ?: 0.0,
            peakTsTimeIran = if (peakSci >= 20.0) peakTsTime else "—",
            peakConvectiveScore = r1(peakSci) ?: 0.0,
            maxCape = r1(maxCape) ?: 0.0,
            totalPrecip24hLikely = r2(sumPrecipP50) ?: 0.0,
            totalPrecip24hMax = r2(sumPrecipP90) ?: 0.0,
            maxProb24h = r1(rows.maxOfOrNull { it.probArea ?: 0.0 } ?: 0.0) ?: 0.0,
            maxGust24h = r1(maxGust) ?: 0.0,
            precipSummaryText = when {
                radarNowcast.maxDbzInTarget >= Tuning.DBZ_RAIN -> "رادار زنده بارش فعال یا ورود توده رگباری به شعاع ۲۰ کیلومتر را تأیید کرده است."
                sumPrecipP50 >= 3.0 -> "بارش باران به حجم کلی حدود ${JalaliCalendar.formatNumber(sumPrecipP50)} میلی‌متر پیش‌بینی می‌شود."
                peakSci >= 35.0 -> "پتانسیل رگبار موضعی همرفتی و صاعقه در ساعات اوج ناپایداری."
                virgaCount >= 2 -> "خشکی لایه‌های زیرین جو؛ احتمال تبخیر باران در هوا (ویرگا) و وزش تندباد محلی."
                else -> "۲۴ ساعت آینده در شعاع ۲۰ کیلومتری شمس‌آباد شرایط جوی عمدتاً آرام پیش‌بینی می‌شود."
            },
            convectiveHoursCount = convectiveCount,
            virgaHoursCount = virgaCount
        )

        val modelCurrentData = centerDetail?.modelCurrent

        val verificationStats = if (context != null) VerificationStore.computeStats(context) else null

        var wetPointsCount = 0
        ecmwfGrid?.let { grid ->
            if (grid.pointCount > 0 && grid.times.isNotEmpty()) {
                val gIdx = grid.times.indexOfFirst { abs(it - nowEpochSec) < 1800L }.coerceAtLeast(0)
                for (p in 0 until grid.pointCount) {
                    val pr = grid.precip[p].getOrNull(gIdx) ?: 0.0
                    if (pr >= Tuning.WET_HOUR_MM) wetPointsCount++
                }
            }
        }
        val nSummary = NeighbourhoodSummary(
            pointsWithRainNow = wetPointsCount,
            totalPoints = 17,
            wettestDirectionFa = radarNowcast.firstArrival?.directionFa
        )

        return ForecastDashboard(
            location = "شعاع ۲۰ کیلومتری شمس‌آباد دزفول",
            lat = RegionConfig.CENTER.lat,
            lon = RegionConfig.CENTER.lon,
            generatedIran = nowIranStr,
            verdict = alertVerdict,
            radarNowcast = radarNowcast,
            neighbourhood = nSummary,
            synopObs = synop,
            modelCurrent = modelCurrentData,
            pattern = pattern,
            ranges = rangesMap,
            sourceHealth = sourceHealthList,
            verificationStats = verificationStats,
            userObservation = userObservation
        )
    }

    private fun getEventLabel(
        sci: Double,
        pArea: Double,
        precipP90: Double,
        isVirga: Boolean,
        isRadarEcho: Boolean
    ): String {
        return when {
            isRadarEcho -> "رگبار راداری در راه 📡"
            isVirga && pArea < 0.3 -> "تبخیر زیر ابر (ویرگا) 🌫️"
            sci >= 50.0 || (precipP90 >= 1.5 && sci >= 35.0) -> "رگبار همرفتی و صاعقه ⚡"
            sci >= 25.0 || precipP90 >= 0.5 -> "رگبار موضعی پراکنده 🌦️"
            pArea >= 0.6 -> "بارش باران 🌧️"
            pArea >= 0.3 -> "بارش ملایم 🌦️"
            pArea >= 0.1 -> "نم‌نم باران 🌦️"
            sci >= 15.0 -> "پتانسیل همرفتی محلی ☁️"
            else -> "بدون بارش ☀️"
        }
    }

    /**
     * 4.14 Alert level decision function decideTier()
     */
    fun decideTier(
        radar: RadarNowcastResult,
        synop: SynopObs?,
        ensEcmwf: EnsembleSet?,
        rows: List<ForecastRow>
    ): VerdictInfo {
        val firstArrival = radar.firstArrival

        // RED conditions
        val isRedRadar = radar.maxDbzInTarget >= Tuning.DBZ_SEVERE
        val isRedApproaching = firstArrival != null && firstArrival.maxDbz >= Tuning.DBZ_STRONG && firstArrival.etaLowMin <= Tuning.RED_ETA_MIN
        val isRedSynop = synop != null && (System.currentTimeMillis()/1000L - synop.timeUtcEpochSec < 5400L) && synop.isThunder

        if (isRedRadar || isRedApproaching || isRedSynop) {
            val text = when {
                isRedRadar -> "هشدار قرمز: پژواک شدیدی با قدرت ${radar.maxDbzInTarget} dBZ داخل محدوده هدف ۲۰ کیلومتری شناسایی شد."
                isRedApproaching -> "هشدار قرمز: سلول بارشی شدیدی با قدرت ${firstArrival?.maxDbz} dBZ در فاصله ${firstArrival?.distKm?.roundToInt()} کیلومتر، ورود به محدوده طی ${firstArrival?.etaLowMin} تا ${firstArrival?.etaHighMin} دقیقه دیگر."
                else -> "هشدار قرمز: وقوع صاعقه و تندباد در دیدبانی زمینی ایستگاه صفی‌آباد ثبت شد."
            }
            return VerdictInfo(AlertTier.RED, text, "از تردد غیرضروری در فضای باز و بستر رودخانه‌ها خودداری فرمایید.")
        }

        // ORANGE conditions
        val isOrangeRadar = radar.maxDbzInTarget >= Tuning.DBZ_RAIN + 5 || radar.wetFractionInTarget >= 0.1
        val isOrangeApproaching = firstArrival != null && firstArrival.etaLowMin <= Tuning.ORANGE_ETA_MIN
        val isOrangeSynop = synop != null && (System.currentTimeMillis()/1000L - synop.timeUtcEpochSec < 5400L) && synop.isRainNow
        val p6hWet = rows.take(6).maxOfOrNull { it.probArea ?: 0.0 } ?: 0.0
        val isOrangeProb = p6hWet >= Tuning.ORANGE_PROB_WET_6H * 100.0

        if (isOrangeRadar || isOrangeApproaching || isOrangeSynop || isOrangeProb) {
            val text = when {
                isOrangeApproaching -> "هشدار نارنجی: سلول بارشی با قدرت ${firstArrival?.maxDbz} dBZ در ${firstArrival?.distKm?.roundToInt()} کیلومتری (${firstArrival?.directionFa}) به سمت محدوده در حرکت است (ورود محتمل: ${firstArrival?.etaLowMin} تا ${firstArrival?.etaHighMin} دقیقه دیگر)."
                isOrangeRadar -> "هشدار نارنجی: لکه‌های بارشی فعال در اسکن راداری محدوده ۲۰ کیلومتری دیده می‌شود."
                isOrangeSynop -> "هشدار نارنجی: ثبت بارش فعال در ایستگاه دیدبانی صفی‌آباد."
                else -> "هشدار نارنجی: مدل‌های پیش‌بینی احتمال بارش بالای ۶۰ درصد برای ۶ ساعت آینده محاسبه کرده‌اند."
            }
            return VerdictInfo(AlertTier.ORANGE, text, "احتمال وقوع رگبار ناگهانی و تندباد موضعی وجود دارد.")
        }

        // YELLOW conditions
        val p12hWet = rows.take(12).maxOfOrNull { it.probArea ?: 0.0 } ?: 0.0
        val isYellowProb = p12hWet >= Tuning.YELLOW_PROB_WET_12H * 100.0
        val isYellowConv = rows.take(12).any { (it.convScore ?: 0.0) >= 60.0 }
        val isYellowMiss = radar.cells.any { it.state == CellApproachState.WILL_MISS && it.closestDistKm <= Tuning.TARGET_RADIUS_KM + 15.0 }
        val isYellowSynopRecent = synop != null && (System.currentTimeMillis()/1000L - synop.timeUtcEpochSec < 5400L) && synop.isRecentPrecip

        if (isYellowProb || isYellowConv || isYellowMiss || isYellowSynopRecent) {
            val text = when {
                isYellowSynopRecent -> "هشدار زرد: ثبت بارش در ساعت اخیر در دیدبانی ایستگاه صفی‌آباد."
                isYellowConv -> "هشدار زرد: افزایش پتانسیل همرفتی و ناپایداری جوی طی ۱۲ ساعت آینده."
                isYellowMiss -> "هشدار زرد: توده بارشی از حاشیه دایره ۲۰ کیلومتری در حال عبور است."
                else -> "هشدار زرد: احتمال رگبار موضعی و ناپایداری جوی در ساعات آینده."
            }
            return VerdictInfo(AlertTier.YELLOW, text, "اوضاع جوی به طور مستمر رصد می‌شود.")
        }

        // GREEN default
        return VerdictInfo(
            AlertTier.GREEN,
            "آسمان آرام و پایدار در محدوده ۲۰ کیلومتری شمس‌آباد؛ هیچ توده بارشی فعالی رصد نشد.",
            "پایش مستمر با رادار و دیدبانی صفی‌آباد برقرار است."
        )
    }
}
