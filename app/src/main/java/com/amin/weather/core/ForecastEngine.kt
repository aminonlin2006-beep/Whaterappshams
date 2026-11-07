package com.amin.weather.core

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
    internal fun r2(v: Double?): Double? = v?.let { (it * 100.0).roundToInt() / 100.0 }

    private fun DoubleArray?.at(i: Int): Double? {
        if (this == null || i < 0 || i >= size) return null
        val v = get(i)
        return if (v.isNaN()) null else v
    }

    private fun BooleanArray?.at(i: Int): Boolean? {
        if (this == null || i < 0 || i >= size) return null
        return get(i)
    }

    /** سهم نقاط بارانی در ساعت gIdx؛ اگر نقاط معتبر کمتر از حد بود null */
    private fun wetFraction(grid: ModelGrid?, gIdx: Int): Double? {
        if (grid == null || gIdx < 0) return null
        var valid = 0; var wet = 0
        for (p in 0 until grid.pointCount) {
            val v = grid.precip[p].getOrNull(gIdx) ?: continue
            if (v.isNaN()) continue
            valid++
            if (v >= Tuning.WET_HOUR_MM) wet++
        }
        return if (valid >= Tuning.NEIGH_MIN_VALID_POINTS) wet.toDouble() / valid else null
    }

    private fun windowWetFraction(grid: ModelGrid, endIdx: Int, w: Int, thr: Double): Double? {
        val start = endIdx - w + 1
        if (start < 0) return null
        var valid = 0; var wet = 0
        for (p in 0 until grid.pointCount) {
            var s = 0.0; var ok = true
            for (k in start..endIdx) {
                val v = grid.precip[p].getOrNull(k)
                if (v == null || v.isNaN()) { ok = false; break }
                s += v
            }
            if (ok) { valid++; if (s >= thr) wet++ }
        }
        return if (valid >= Tuning.NEIGH_MIN_VALID_POINTS) wet.toDouble() / valid else null
    }

    fun percentile(list: List<Double>, q: Double): Double {
        if (list.isEmpty()) return 0.0
        val sorted = list.sorted()
        val n = sorted.size
        if (n == 1) return sorted[0]
        val pos = q * (n - 1)
        val i = pos.toInt()
        val frac = pos - i
        if (i >= n - 1) return sorted.last()
        return sorted[i] + frac * (sorted[i + 1] - sorted[i])
    }

    data class BlockStats(
        val pWet: Double?,
        val p50: Double?,
        val p90: Double?,
        val pHeavy6h: Double?
    )

    /** مجموع بارش هر عضو در ساعت‌های (startEpoch, endEpoch] */
    fun blockStats(
        ensList: List<EnsembleSet?>,
        startEpoch: Long,
        endEpoch: Long,
        wetMm: Double,
        heavyMm: Double
    ): BlockStats {
        val sums = ArrayList<Double>()
        for (e in ensList) {
            if (e == null) continue
            val idx = e.times.indices.filter { e.times[it] > startEpoch && e.times[it] <= endEpoch }
            if (idx.isEmpty()) continue
            for (m in e.members) {
                var s = 0.0
                var ok = true
                for (k in idx) {
                    val v = m.getOrNull(k)
                    if (v == null || v.isNaN()) {
                        ok = false
                        break
                    }
                    s += v
                }
                if (ok) sums.add(s)
            }
        }
        if (sums.isEmpty()) return BlockStats(null, null, null, null)
        sums.sort()
        return BlockStats(
            pWet = sums.count { it >= wetMm }.toDouble() / sums.size,
            p50 = percentile(sums, 0.5),
            p90 = percentile(sums, 0.9),
            pHeavy6h = sums.count { it >= heavyMm }.toDouble() / sums.size
        )
    }

    /**
     * Builds complete forecast dashboard dataset
     */
    suspend fun buildDashboardData(
        context: Context? = null,
        userObservation: SkyObservationReport? = null
    ): ForecastDashboard = coroutineScope {
        val sourceHealthList = mutableListOf<SourceHealth>()

        val cacheDir: File? = context?.filesDir

        // 1. Fetch data from clients concurrently in parallel
        val neighDeferred = async(Dispatchers.IO) { OpenMeteoClient.fetchNeighbourhood() }
        val centerDeferred = async(Dispatchers.IO) { OpenMeteoClient.fetchCenterDetail() }
        val liftedIndexDeferred = async(Dispatchers.IO) { OpenMeteoClient.fetchLiftedIndex() }
        val steeringDeferred = async(Dispatchers.IO) { OpenMeteoClient.fetchSteeringWind() }
        val ensEcmwfDeferred = async(Dispatchers.IO) { OpenMeteoClient.fetchEnsemble("ecmwf_ifs025") }
        val ensIconDeferred = async(Dispatchers.IO) { OpenMeteoClient.fetchEnsemble("icon_seamless") }
        val synopDeferred = async(Dispatchers.IO) { SynopClient.fetchLatestSynop(cacheDir) }
        val radarDeferred = async(Dispatchers.IO) { RadarClient.fetchRadarFrames(cacheDir) }

        val neighRes = neighDeferred.await()
        val centerRes = centerDeferred.await()
        val liftedIndexRes = liftedIndexDeferred.await()
        val steeringRes = steeringDeferred.await()
        val ensEcmwfRes = ensEcmwfDeferred.await()
        val ensIconRes = ensIconDeferred.await()
        val synopRes = synopDeferred.await()
        val radarRes = radarDeferred.await()

        sourceHealthList.add(SourceHealth("open-meteo/neighbourhood", neighRes.status, neighRes.message))
        sourceHealthList.add(SourceHealth("open-meteo/center", centerRes.status, centerRes.message))
        sourceHealthList.add(SourceHealth("open-meteo/lifted_index", liftedIndexRes.status, liftedIndexRes.message))
        sourceHealthList.add(SourceHealth("open-meteo/steering", steeringRes.status, steeringRes.message))
        sourceHealthList.add(SourceHealth("ensemble/ecmwf", ensEcmwfRes.status, ensEcmwfRes.message))
        sourceHealthList.add(SourceHealth("ensemble/icon", ensIconRes.status, ensIconRes.message))
        sourceHealthList.add(SourceHealth("ogimet/synop", synopRes.status, synopRes.message))
        sourceHealthList.add(SourceHealth("rainviewer/radar", radarRes.status, radarRes.message))

        val steeringWind = steeringRes.data
        val synop = synopRes.data
        val centerDetail = centerRes.data
        val liftedIndexData = liftedIndexRes.data
        val neighMap = neighRes.data ?: emptyMap()
        val ensEcmwf = ensEcmwfRes.data
        val ensIcon = ensIconRes.data

        val nowEpochSec = System.currentTimeMillis() / 1000L
        val ecmwfGrid = neighMap["ecmwf"]
        val iconGrid = neighMap["icon"]

        val modelDryNow: Boolean? = ecmwfGrid?.let { grid ->
            val gIdx = grid.times.indexOfFirst { abs(it - nowEpochSec) < 1800L }
            if (gIdx >= 0) {
                var hasValid = false
                var allDry = true
                for (p in 0 until grid.pointCount) {
                    val pr = grid.precip[p].getOrNull(gIdx) ?: continue
                    if (!pr.isNaN()) {
                        hasValid = true
                        if (pr >= Tuning.WET_HOUR_MM) { allDry = false; break }
                    }
                }
                if (hasValid) allDry else null
            } else null
        }

        val radarNowcast = RadarAnalyzer.analyzeNowcast(radarRes.data, steeringWind, modelDryNow, nowEpochSec)

        if (context != null) {
            Calibration.load(context)
            if (synop != null) {
                VerificationStore.evaluateWithSynop(context, synop)
            }
        }

        val nowIranStr = epochToIran(nowEpochSec)

        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+03:30"))
        val currentMonth = cal.get(Calendar.MONTH) + 1

        // 4. Base time axis from first available source
        val baseTimes: LongArray = centerDetail?.times
            ?: ecmwfGrid?.times ?: iconGrid?.times ?: ensIcon?.times ?: ensEcmwf?.times
            ?: throw java.io.IOException("هیچ منبع مدل در دسترس نیست")

        var nowHourIndex = baseTimes.indexOfFirst { abs(it - nowEpochSec) < 1800L }
        if (nowHourIndex < 0) nowHourIndex = baseTimes.indexOfFirst { it >= nowEpochSec - 1800L }
        if (nowHourIndex < 0) throw java.io.IOException("داده‌های مدل برای ساعت جاری موجود نیست")

        val rows = mutableListOf<ForecastRow>()
        var peakPrecipMm = 0.0
        var peakPrecipTime = "—"
        var peakTsTime = "—"
        var peakSci = 0.0
        var maxCape = 0.0
        var maxGust = 0.0
        var virgaCount = 0
        var convectiveCount = 0

        val hoursToProcess = minOf(48, maxOf(0, baseTimes.size - nowHourIndex))

        for (step in 0 until hoursToProcess) {
            val idxBase = nowHourIndex + step
            val hEpoch = if (idxBase < baseTimes.size) baseTimes[idxBase] else nowEpochSec + step * 3600L
            val hIran = epochToIran(hEpoch)

            val idxCenter = centerDetail?.times?.indexOfFirst { abs(it - hEpoch) < 1800L } ?: -1
            val gIdxEcmwf = ecmwfGrid?.times?.indexOfFirst { abs(it - hEpoch) < 1800L } ?: -1
            val gIdxIcon = iconGrid?.times?.indexOfFirst { abs(it - hEpoch) < 1800L } ?: -1
            val eIdxEcmwf = ensEcmwf?.times?.indexOfFirst { abs(it - hEpoch) < 1800L } ?: -1
            val eIdxIcon = ensIcon?.times?.indexOfFirst { abs(it - hEpoch) < 1800L } ?: -1
            val idxSteering = steeringWind?.times?.indexOfFirst { abs(it - hEpoch) < 1800L } ?: -1
            val idxLi = liftedIndexData?.first?.indexOfFirst { abs(it - hEpoch) < 1800L } ?: -1

            val temp = centerDetail?.temp.at(idxCenter)
            val rh = centerDetail?.rh.at(idxCenter)
            val dewPoint = centerDetail?.dewPoint.at(idxCenter) ?: if (temp != null && rh != null) calculateDewPoint(temp, rh) else null
            val dpDepression = if (temp != null && dewPoint != null) max(0.0, temp - dewPoint) else null
            val cape = centerDetail?.cape.at(idxCenter)
            val cin = centerDetail?.cin.at(idxCenter)
            val li = liftedIndexData?.second.at(idxLi) ?: centerDetail?.li.at(idxCenter)
            val cloud = centerDetail?.cloud.at(idxCenter)
            val cloudMid = centerDetail?.cloudMid.at(idxCenter)
            val windSpeed = centerDetail?.windSpeed.at(idxCenter)
            val windGust = centerDetail?.windGust.at(idxCenter)
            val windDir = centerDetail?.windDir.at(idxCenter)

            // 4.1 Neighbourhood model probabilities
            val pNeighEcmwf = wetFraction(ecmwfGrid, gIdxEcmwf)
            val pNeighIcon = wetFraction(iconGrid, gIdxIcon)

            // 4.2 Ensemble probability
            var pEnsEcmwf: Double? = null
            if (ensEcmwf != null && ensEcmwf.times.isNotEmpty() && eIdxEcmwf >= 0) {
                var wetMem = 0
                var validMem = 0
                for (m in ensEcmwf.members.indices) {
                    var sum3h = 0.0
                    var valid3h = 0
                    for (w in maxOf(0, eIdxEcmwf - 2)..eIdxEcmwf) {
                        val v = ensEcmwf.members[m].at(w)
                        if (v != null) {
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

            var pEnsIcon: Double? = null
            if (ensIcon != null && ensIcon.times.isNotEmpty() && eIdxIcon >= 0) {
                var wetMem = 0
                var validMem = 0
                for (m in ensIcon.members.indices) {
                    val v = ensIcon.members[m].at(eIdxIcon)
                    if (v != null) {
                        validMem++
                        if (v >= Tuning.WET_HOUR_MM) wetMem++
                    }
                }
                if (validMem > 0) pEnsIcon = wetMem.toDouble() / validMem
            }

            val pEns = when {
                pEnsEcmwf != null && pEnsIcon != null -> (51.0 * pEnsEcmwf + 40.0 * pEnsIcon) / 91.0
                pEnsEcmwf != null -> pEnsEcmwf
                pEnsIcon != null -> pEnsIcon
                else -> null
            }

            val pNeighEcmwfCal: Double? = if (pNeighEcmwf != null && ecmwfGrid != null && Calibration.appliesTo(currentMonth)) {
                val W = Calibration.windowHours
                val p6 = windowWetFraction(ecmwfGrid, gIdxEcmwf, W, Calibration.wetThresholdMm)
                if (p6 == null) pNeighEcmwf
                else {
                    val c6 = Calibration.calibrateProb(p6)
                    val floor = Calibration.probAtZero() / W
                    if (p6 <= 0.0) floor else max(floor, min(c6, pNeighEcmwf * c6 / p6))
                }
            } else pNeighEcmwf

            // 4.3 Combined model area probability
            var wSum = 0.0
            var pModelSum = 0.0
            if (pEns != null) { pModelSum += Tuning.W_ENSEMBLE * pEns; wSum += Tuning.W_ENSEMBLE }
            if (pNeighEcmwfCal != null) { pModelSum += Tuning.W_NEIGH_ECMWF * pNeighEcmwfCal; wSum += Tuning.W_NEIGH_ECMWF }
            if (pNeighIcon != null) { pModelSum += Tuning.W_NEIGH_ICON * pNeighIcon; wSum += Tuning.W_NEIGH_ICON }

            var pArea: Double? = if (wSum > 0) pModelSum / wSum else null

            val centerPrecipEcmwf = centerDetail?.precip.at(idxCenter)
            val pCenterEcmwf = centerPrecipEcmwf?.let { if (it >= Tuning.WET_HOUR_MM) 1.0 else 0.0 }
            var pPointModel: Double? = when {
                pEns != null && pCenterEcmwf != null -> 0.6 * pEns + 0.4 * pCenterEcmwf
                pEns != null -> pEns
                pCenterEcmwf != null -> pCenterEcmwf
                else -> pArea
            }

            // 4.5 Radar Nowcast (h = 0, 1, 2)
            var pNow: Double? = null
            var pNowPoint: Double? = null
            if (step < 3 && radarNowcast.available) {
                if (radarNowcast.wetFractionInTarget >= 0.05 && step == 0) {
                    pNow = min(0.95, 0.6 + 4.0 * radarNowcast.wetFractionInTarget)
                } else {
                    val arr = radarNowcast.firstArrival
                    if (arr != null && arr.maxDbz >= Tuning.DBZ_STRONG && arr.etaMinLow != null && arr.etaMinLow <= (step + 1) * 60) {
                        pNow = 0.85
                    } else if (arr != null && arr.maxDbz >= Tuning.DBZ_RAIN && arr.etaMinLow != null && arr.etaMinLow <= (step + 1) * 60) {
                        pNow = 0.65
                    } else if (radarNowcast.cells.any { it.state == CellApproachState.WILL_MISS && (it.minDistanceKm != null && it.minDistanceKm <= Tuning.TARGET_RADIUS_KM + 15.0) }) {
                        pNow = 0.25
                    } else if (radarNowcast.maxDbzInTarget < Tuning.DBZ_LIGHT && radarNowcast.cells.isEmpty()) {
                        pNow = 0.03
                    }
                }
            }

            if (pNow != null && step < Tuning.NOWCAST_WEIGHT.size && pArea != null) {
                val w = Tuning.NOWCAST_WEIGHT[step]
                pArea = w * pNow + (1.0 - w) * pArea
                pNowPoint = if (step == 0 && radarNowcast.maxDbzNearPoint >= Tuning.DBZ_RAIN) 0.9 else 0.6 * pNow
                pPointModel = w * pNowPoint + (1.0 - w) * (pPointModel ?: pArea)
            }

            // 4.6 SYNOP Safiabad Override (h = 0)
            if (step == 0 && synop != null && (nowEpochSec - synop.timeUtcEpochSec) < Tuning.SYNOP_FRESH_SEC) {
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
            val isVirga = dpDepression != null && rh != null && dpDepression >= Tuning.VIRGA_DEW_DEPRESSION_C && rh <= Tuning.VIRGA_RH_MAX && (pArea ?: 0.0) >= 0.15
            if (isVirga) {
                if (pPointModel != null) pPointModel *= Tuning.VIRGA_PROB_FACTOR
            }

            val pPoint = if (pPointModel != null && pArea != null) min(pPointModel, pArea) else pPointModel

            // 4.10 Amounts P50 & P90
            val memberValues = mutableListOf<Double>()
            if (ensIcon != null && ensIcon.times.isNotEmpty() && eIdxIcon >= 0) {
                for (m in ensIcon.members.indices) {
                    val v = ensIcon.members[m].at(eIdxIcon)
                    if (v != null) memberValues.add(v)
                }
            }
            if (ensEcmwf != null && ensEcmwf.times.isNotEmpty() && eIdxEcmwf >= 2) {
                for (m in ensEcmwf.members.indices) {
                    val a = ensEcmwf.members[m].at(eIdxEcmwf)
                    val b = ensEcmwf.members[m].at(eIdxEcmwf - 1)
                    val c = ensEcmwf.members[m].at(eIdxEcmwf - 2)
                    if (a != null && b != null && c != null) {
                        memberValues.add((a + b + c) / 3.0)
                    }
                }
            }

            val centerPrecipEcmwfRaw = centerDetail?.precip.at(idxCenter)
            val hresCenter: Double? = centerPrecipEcmwfRaw?.let { raw ->
                if (!Calibration.qmEnabled || !Calibration.appliesTo(currentMonth)) raw
                else {
                    val W = Calibration.windowHours
                    val start = idxCenter - W + 1
                    val window = if (start >= 0) (start..idxCenter).map { centerDetail?.precip.at(it) } else null
                    if (window == null || window.any { it == null }) raw
                    else {
                        val aW = window.sumOf { it!! }
                        if (aW < 0.1) raw else raw * (Calibration.calibrateAmount(aW) / aW)
                    }
                }
            }
            val iconCenter = iconGrid?.precip?.getOrNull(0)?.at(gIdxIcon)

            hresCenter?.let(memberValues::add)
            iconCenter?.let(memberValues::add)

            var amtP50: Double? = if (memberValues.isNotEmpty()) percentile(memberValues, 0.5) else null
            var amtP90: Double? = if (memberValues.isNotEmpty()) percentile(memberValues, 0.9) else null

            val hresNeighMax = ecmwfGrid?.let { grid ->
                if (gIdxEcmwf >= 0) {
                    (0 until grid.pointCount).mapNotNull { p -> grid.precip[p].at(gIdxEcmwf) }.maxOrNull()
                } else null
            }
            if (amtP90 != null && hresNeighMax != null) {
                amtP90 = max(amtP90!!, hresNeighMax)
            }

            if (step < 3 && radarNowcast.available && pNow != null && amtP50 != null) {
                val w = Tuning.NOWCAST_WEIGHT[step]
                val arr = radarNowcast.firstArrival
                val amountNow: Double = when {
                    radarNowcast.wetFractionInTarget >= 0.05 && step == 0 -> radarNowcast.rainRateInTargetMmH
                    arr != null && arr.etaMinLow != null && arr.speedKmh != null && arr.etaMinLow <= (step + 1) * 60 ->
                        RadarAnalyzer.dbzToRainRate(arr.maxDbz) * min(1.0, 2.0 * arr.radiusKm / max(Tuning.CELL_SPEED_MIN_KMH, arr.speedKmh))
                    else -> 0.0
                }
                amtP50 = w * amountNow * pNow + (1.0 - w) * amtP50
                amtP90 = max(amtP90 ?: 0.0, amountNow)
            }

            amtP50 = r2(amtP50)
            amtP90 = r2(amtP90)

            if (step < 24) {
                if ((amtP50 ?: 0.0) > peakPrecipMm) {
                    peakPrecipMm = amtP50 ?: 0.0
                    peakPrecipTime = hIran
                }
            }

            // 4.11 Convective Potential Score
            val moistureInflowFlag = steeringWind?.moistureInflow.at(idxSteering) ?: false
            var convScore: Double? = null
            if (cape != null) {
                var sc = 45.0 * (cape / 1500.0).coerceIn(0.0, 1.0)
                if (li != null) sc += 15.0 * ((-li) / 6.0).coerceIn(0.0, 1.0)
                if (pEns != null) sc += 25.0 * pEns
                if (dewPoint != null) sc += 15.0 * ((dewPoint - 5.0) / 10.0).coerceIn(0.0, 1.0)
                if (moistureInflowFlag) sc += 10.0
                sc = min(sc, 100.0)
                if (cin != null && abs(cin) > 100.0) sc *= 0.6
                convScore = r1(sc)
            }

            val cScoreVal = convScore ?: 0.0
            if (step < 24) {
                if (cScoreVal > peakSci) {
                    peakSci = cScoreVal
                    peakTsTime = hIran
                }
                if ((cape ?: 0.0) > maxCape) maxCape = cape ?: 0.0
                if ((windGust ?: 0.0) > maxGust) maxGust = windGust ?: 0.0

                val isConvective = cScoreVal >= 35.0 || (amtP90 ?: 0.0) >= 1.0
                if (isConvective) convectiveCount++
                if (isVirga) virgaCount++
            }

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
                precipP90 = amtP90 ?: 0.0,
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
                probArea = pArea?.let { r1(it * 100.0) },
                probPoint = pPoint?.let { r1(it * 100.0) },
                amountP50 = amtP50,
                amountP90 = amtP90,
                tempC = r1(temp),
                tempMinC = r1(temp),
                tempMaxC = r1(temp),
                dewC = r1(dewPoint),
                rh = r1(rh),
                cloud = r1(cloud),
                gustKmh = r1(windGust),
                windSpeedKmh = r1(windSpeed),
                windDirFromDeg = r1(windDir),
                windDirLabel = windDir?.let { GeoUtils.dir16Fa(it) },
                convScore = convScore,
                convLabel = convLabelStr,
                virgaRisk = isVirga,
                confidence = confidenceVal,
                sources = sourcesList,
                eventLabel = eventLabelStr
            )
            rows.add(row)

        }

        if (context != null) {
            VerificationStore.recordStandardWindows(context, nowEpochSec, rows, listOf(ensEcmwf, ensIcon))
        }

        val block6 = if (rows.isNotEmpty()) {
            val startEpoch = rows.first().timeEpochSec - 3600L
            val endIdx = minOf(5, rows.size - 1)
            val endEpoch = rows[endIdx].timeEpochSec
            blockStats(listOf(ensEcmwf, ensIcon), startEpoch, endEpoch, Tuning.WET_HOUR_MM, Tuning.HEAVY_6H_MM)
        } else null

        val block12 = if (rows.isNotEmpty()) {
            val startEpoch = rows.first().timeEpochSec - 3600L
            val endIdx = minOf(11, rows.size - 1)
            val endEpoch = rows[endIdx].timeEpochSec
            blockStats(listOf(ensEcmwf, ensIcon), startEpoch, endEpoch, Tuning.WET_HOUR_MM, Tuning.HEAVY_6H_MM)
        } else null

        val moistureInflowNext12h = checkMoistureInflowNext12h(steeringWind, nowEpochSec)

        val alertVerdict = decideTier(radarNowcast, synop, rows, block6, block12, moistureInflowNext12h)

        val rangesMap = mutableMapOf<ForecastRange, List<ForecastRow>>()
        rangesMap[ForecastRange.HOURLY] = rows

        fun buildBlock(count: Int): ForecastRow? {
            val subList = rows.take(minOf(count, rows.size))
            if (subList.isEmpty()) return null

            val startEpoch = subList.first().timeEpochSec - 3600L
            val endEpoch = subList.last().timeEpochSec
            val bs = blockStats(listOf(ensEcmwf, ensIcon), startEpoch, endEpoch, Tuning.WET_HOUR_MM, Tuning.HEAVY_6H_MM)

            val maxHourlyProbArea = subList.mapNotNull { it.probArea?.let { p -> p / 100.0 } }.maxOrNull()
            val blockProbAreaFrac = when {
                bs.pWet != null && maxHourlyProbArea != null -> max(bs.pWet, maxHourlyProbArea)
                bs.pWet != null -> bs.pWet
                maxHourlyProbArea != null -> maxHourlyProbArea
                else -> null
            }

            val maxHourlyProbPoint = subList.mapNotNull { it.probPoint?.let { p -> p / 100.0 } }.maxOrNull()

            val sumHourlyP50 = subList.mapNotNull { it.amountP50 }.let { if (it.isNotEmpty()) it.sum() else null }

            val blockAmountP50 = bs.p50 ?: sumHourlyP50
            val blockAmountP90 = if (bs.p90 != null && sumHourlyP50 != null) max(bs.p90, sumHourlyP50) else bs.p90

            val tempMapList = subList.mapNotNull { it.tempC }
            val minT = tempMapList.minOrNull()
            val maxT = tempMapList.maxOrNull()

            val gustList = subList.mapNotNull { it.gustKmh }
            val maxGustVal = gustList.maxOrNull()

            val convList = subList.mapNotNull { it.convScore }
            val maxConv = convList.maxOrNull()

            val anyVirga = subList.any { it.virgaRisk }
            val first = subList.first()
            val last = subList.last()

            val dirList = subList.mapNotNull { it.windDirFromDeg }
            val dominantDir = if (dirList.isNotEmpty()) {
                var sumSin = 0.0
                var sumCos = 0.0
                for (d in dirList) {
                    val rad = Math.toRadians(d)
                    sumSin += sin(rad)
                    sumCos += cos(rad)
                }
                (Math.toDegrees(atan2(sumSin, sumCos)) + 360.0) % 360.0
            } else null

            return ForecastRow(
                timeEpochSec = first.timeEpochSec,
                timeIran = "${first.timeIran.takeLast(5)} تا ${last.timeIran.takeLast(5)}",
                forecastSource = first.forecastSource,
                probArea = blockProbAreaFrac?.let { r1(it * 100.0) },
                probPoint = maxHourlyProbPoint?.let { r1(it * 100.0) },
                amountP50 = blockAmountP50?.let { r2(it) },
                amountP90 = blockAmountP90?.let { r2(it) },
                tempC = maxT,
                tempMinC = minT,
                tempMaxC = maxT,
                dewC = first.dewC,
                rh = first.rh,
                cloud = first.cloud,
                gustKmh = maxGustVal,
                windSpeedKmh = first.windSpeedKmh,
                windDirFromDeg = dominantDir?.let { r1(it) },
                windDirLabel = dominantDir?.let { GeoUtils.dir16Fa(it) },
                convScore = maxConv,
                convLabel = if ((maxConv ?: 0.0) >= 35.0 || (blockAmountP90 ?: 0.0) >= 1.0) "پتانسیل رگبار همرفتی ⚡" else if ((blockAmountP50 ?: 0.0) > 0.1) "بارش باران 🌧️" else "آرام ☀️",
                virgaRisk = anyVirga,
                confidence = first.confidence,
                sources = first.sources,
                eventLabel = if ((blockAmountP50 ?: 0.0) > 0.1) "بارش باران 🌧️" else "آرام ☀️"
            )
        }

        buildBlock(1)?.let { rangesMap[ForecastRange.H1] = listOf(it) }
        buildBlock(3)?.let { rangesMap[ForecastRange.H3] = listOf(it) }
        buildBlock(6)?.let { rangesMap[ForecastRange.H6] = listOf(it) }
        buildBlock(12)?.let { rangesMap[ForecastRange.H12] = listOf(it) }
        buildBlock(24)?.let { rangesMap[ForecastRange.H24] = listOf(it) }

        val h24 = rangesMap[ForecastRange.H24]?.firstOrNull()

        val pattern = PatternSummary(
            windowStartIran = if (rows.isNotEmpty()) rows.first().timeIran else "",
            windowEndIran = if (rows.isNotEmpty()) rows.last().timeIran else "",
            peakPrecipTimeIran = if (peakPrecipMm > 0.0) peakPrecipTime else "—",
            peakPrecipMm = r2(peakPrecipMm) ?: 0.0,
            peakTsTimeIran = if (peakSci >= 20.0) peakTsTime else "—",
            peakConvectiveScore = r1(peakSci) ?: 0.0,
            maxCape = r1(maxCape) ?: 0.0,
            totalPrecip24hLikely = h24?.amountP50 ?: 0.0,
            totalPrecip24hMax = h24?.amountP90 ?: 0.0,
            maxProb24h = r1(rows.take(24).mapNotNull { it.probArea }.maxOrNull() ?: 0.0) ?: 0.0,
            maxGust24h = r1(rows.take(24).mapNotNull { it.gustKmh }.maxOrNull() ?: 0.0) ?: 0.0,
            precipSummaryText = when {
                radarNowcast.maxDbzInTarget >= Tuning.DBZ_RAIN -> "رادار زنده بارش فعال یا ورود توده رگباری به شعاع ۲۰ کیلومتر را تأیید کرده است."
                (h24?.amountP50 ?: 0.0) >= 3.0 -> "بارش باران به حجم کلی حدود ${JalaliCalendar.formatNumber(h24?.amountP50 ?: 0.0)} میلی‌متر پیش‌بینی می‌شود."
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
        var totalValidPoints = 0
        ecmwfGrid?.let { grid ->
            if (grid.pointCount > 0 && grid.times.isNotEmpty()) {
                val gIdx = grid.times.indexOfFirst { abs(it - nowEpochSec) < 1800L }
                if (gIdx >= 0) {
                    for (p in 0 until grid.pointCount) {
                        val pr = grid.precip[p].getOrNull(gIdx) ?: continue
                        if (!pr.isNaN()) {
                            totalValidPoints++
                            if (pr >= Tuning.WET_HOUR_MM) wetPointsCount++
                        }
                    }
                }
            }
        }
        val nSummary = NeighbourhoodSummary(
            pointsWithRainNow = wetPointsCount,
            totalPoints = if (totalValidPoints > 0) totalValidPoints else 17,
            wettestDirectionFa = radarNowcast.firstArrival?.directionFa
        )

        ForecastDashboard(
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
        rows: List<ForecastRow>,
        block6: BlockStats? = null,
        block12: BlockStats? = null,
        moistureInflowNext12h: Boolean = false
    ): VerdictInfo {
        val radarOk = radar.available
        val firstArrival = radar.firstArrival

        // RED conditions
        val isRedRadar = radarOk && radar.maxDbzInTarget >= Tuning.DBZ_SEVERE
        val isRedApproaching = radarOk && firstArrival != null && firstArrival.maxDbz >= Tuning.DBZ_STRONG && (firstArrival.etaMinLow != null && firstArrival.etaMinLow <= Tuning.RED_ETA_MIN)
        val isRedSynop = synop != null && (System.currentTimeMillis()/1000L - synop.timeUtcEpochSec < Tuning.SYNOP_FRESH_SEC) && synop.isThunder
        val isRedHeavy6h = block6?.pHeavy6h != null && block6.pHeavy6h >= Tuning.RED_PROB_HEAVY_6H

        if (isRedRadar || isRedApproaching || isRedSynop || isRedHeavy6h) {
            val text = when {
                isRedRadar -> "هشدار قرمز: پژواک شدیدی با قدرت ${radar.maxDbzInTarget} dBZ داخل محدوده هدف ۲۰ کیلومتری شناسایی شد."
                isRedApproaching -> "هشدار قرمز: سلول بارشی شدیدی با قدرت ${firstArrival?.maxDbz} dBZ در فاصله ${firstArrival?.distKm?.roundToInt()} کیلومتر، ورود به محدوده طی ${firstArrival?.etaMinLow} تا ${firstArrival?.etaMinHigh} دقیقه دیگر."
                isRedSynop -> "هشدار قرمز: وقوع صاعقه و تندباد در دیدبانی زمینی ایستگاه صفی‌آباد ثبت شد."
                else -> "هشدار قرمز: ${((block6?.pHeavy6h ?: 0.0) * 100).toInt()}٪ اعضای پیش‌بینی گروهی بیش از ۱۰ میلی‌متر بارش در ۶ ساعت آینده محاسبه کرده‌اند."
            }
            return VerdictInfo(AlertTier.RED, text, "از تردد غیرضروری در فضای باز و بستر رودخانه‌ها خودداری فرمایید.")
        }

        // ORANGE conditions
        val isOrangeRadar = radarOk && (radar.maxDbzInTarget >= Tuning.DBZ_RAIN + 5 || radar.wetFractionInTarget >= 0.1)
        val isOrangeApproaching = radarOk && firstArrival != null && (firstArrival.etaMinLow != null && firstArrival.etaMinLow <= Tuning.ORANGE_ETA_MIN)
        val isOrangeSynop = synop != null && (System.currentTimeMillis()/1000L - synop.timeUtcEpochSec < Tuning.SYNOP_FRESH_SEC) && synop.isRainNow
        val p6hWet = block6?.pWet ?: (rows.take(6).mapNotNull { it.probArea?.let { p -> p / 100.0 } }.maxOrNull() ?: 0.0)
        val isOrangeProb = p6hWet >= Tuning.ORANGE_PROB_WET_6H

        if (isOrangeRadar || isOrangeApproaching || isOrangeSynop || isOrangeProb) {
            val text = when {
                isOrangeApproaching -> "هشدار نارنجی: سلول بارشی با قدرت ${firstArrival?.maxDbz} dBZ در ${firstArrival?.distKm?.roundToInt()} کیلومتری (${firstArrival?.directionFa}) به سمت محدوده در حرکت است (ورود محتمل: ${firstArrival?.etaMinLow} تا ${firstArrival?.etaMinHigh} دقیقه دیگر)."
                isOrangeRadar -> "هشدار نارنجی: لکه‌های بارشی فعال در اسکن راداری محدوده ۲۰ کیلومتری دیده می‌شود."
                isOrangeSynop -> "هشدار نارنجی: ثبت بارش فعال در ایستگاه دیدبانی صفی‌آباد."
                else -> "هشدار نارنجی: مدل‌های پیش‌بینی احتمال بارش بالای ۶۰ درصد برای ۶ ساعت آینده محاسبه کرده‌اند."
            }
            return VerdictInfo(AlertTier.ORANGE, text, "احتمال وقوع رگبار ناگهانی و تندباد موضعی وجود دارد.")
        }

        // YELLOW conditions
        val p12hWet = block12?.pWet ?: (rows.take(12).mapNotNull { it.probArea?.let { p -> p / 100.0 } }.maxOrNull() ?: 0.0)
        val isYellowProb = p12hWet >= Tuning.YELLOW_PROB_WET_12H
        val isYellowMoistProb = moistureInflowNext12h && p12hWet >= Tuning.YELLOW_MOIST_MIN_PROB
        val isYellowConv = rows.take(12).any { (it.convScore ?: 0.0) >= 60.0 }
        val isYellowMiss = radarOk && radar.cells.any { it.state == CellApproachState.WILL_MISS && (it.minDistanceKm != null && it.minDistanceKm <= Tuning.TARGET_RADIUS_KM + 15.0) }
        val isYellowUpwindEcho = radarOk && radar.upwindStrongEcho
        val isYellowSynopRecent = synop != null && (System.currentTimeMillis()/1000L - synop.timeUtcEpochSec < Tuning.SYNOP_FRESH_SEC) && synop.isRecentPrecip

        if (isYellowProb || isYellowMoistProb || isYellowConv || isYellowMiss || isYellowUpwindEcho || isYellowSynopRecent) {
            val text = when {
                isYellowUpwindEcho -> "هشدار زرد: پژواک قوی راداری در سمت غرب/جنوب‌غرب در فاصله ۵۰ تا ۱۵۰ کیلومتری رصد شد."
                isYellowSynopRecent -> "هشدار زرد: ثبت بارش در ساعت اخیر در دیدبانی ایستگاه صفی‌آباد."
                isYellowConv -> "هشدار زرد: افزایش پتانسیل همرفتی و ناپایداری جوی طی ۱۲ ساعت آینده."
                isYellowMiss -> "هشدار زرد: توده بارشی از حاشیه دایره ۲۰ کیلومتری در حال عبور است."
                isYellowMoistProb -> "هشدار زرد: ورود رطوبت تراز پایین همراه با احتمال بارش در ۱۲ ساعت آینده."
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

    fun checkMoistureInflowNext12h(steeringWind: SteeringWind?, nowEpochSec: Long): Boolean {
        return steeringWind?.let { s ->
            s.times.indices.any { k ->
                s.times[k] > nowEpochSec - 1800L && s.times[k] <= nowEpochSec + 12 * 3600L &&
                    s.moistureInflow.getOrNull(k) == true
            }
        } ?: false
    }
}
