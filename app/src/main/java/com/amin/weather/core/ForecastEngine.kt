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
import kotlinx.coroutines.withContext

object ForecastEngine {

    // SimpleDateFormat امن برای چند رشته نیست (Worker و صفحه هم‌زمان صدا می‌زنند)؛ هر رشته نسخهٔ خودش را دارد
    private val utcFormatTL = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false   // تاریخ نامعتبر (مثل ۳۰ فوریه) بی‌صدا جابه‌جا نشود
        }
    }
    private val iranFormatTL = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT+03:30")
        }
    }
    private val utcFormat: SimpleDateFormat get() = utcFormatTL.get()!!
    private val iranFormat: SimpleDateFormat get() = iranFormatTL.get()!!

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

    /** (تعداد نقاط خیس، تعداد نقاط معتبر) در ساعت gIdx؛ اگر هیچ نقطهٔ معتبری نبود null */
    internal fun wetCount(grid: ModelGrid?, gIdx: Int): Pair<Int, Int>? {
        if (grid == null || gIdx < 0) return null
        var valid = 0; var wet = 0
        for (p in 0 until grid.pointCount) {
            val v = grid.precip[p].getOrNull(gIdx) ?: continue
            if (v.isNaN()) continue
            valid++
            if (v >= Tuning.WET_HOUR_MM) wet++
        }
        return if (valid > 0) Pair(wet, valid) else null
    }

    /** احتمال خیس بودن از اعضای ECMWF ENS با جمع ۳ ساعتهٔ منتهی به idx: (احتمال، تعداد اعضای معتبر) */
    internal fun ensembleWetEcmwf3h(ens: EnsembleSet?, idx: Int): Pair<Double, Int>? {
        if (ens == null || ens.times.isEmpty() || idx < 0) return null
        var wetMem = 0; var validMem = 0
        for (m in ens.members.indices) {
            var sum3h = 0.0; var valid3h = 0
            for (w in maxOf(0, idx - 2)..idx) {
                val v = ens.members[m].at(w)
                if (v != null) { sum3h += v; valid3h++ }
            }
            if (valid3h > 0) {
                validMem++
                if (sum3h >= Tuning.WET_3H_MM) wetMem++
            }
        }
        return if (validMem > 0) Pair(wetMem.toDouble() / validMem, validMem) else null
    }

    /** احتمال خیس بودن از اعضای ICON EPS در همان ساعت: (احتمال، تعداد اعضای معتبر) */
    internal fun ensembleWetIconHourly(ens: EnsembleSet?, idx: Int): Pair<Double, Int>? {
        if (ens == null || ens.times.isEmpty() || idx < 0) return null
        var wetMem = 0; var validMem = 0
        for (m in ens.members.indices) {
            val v = ens.members[m].at(idx) ?: continue
            validMem++
            if (v >= Tuning.WET_HOUR_MM) wetMem++
        }
        return if (validMem > 0) Pair(wetMem.toDouble() / validMem, validMem) else null
    }

    /** ترکیب دو مدل گروهی با وزن تعداد اعضای معتبر هر کدام */
    internal fun combineEnsembles(a: Pair<Double, Int>?, b: Pair<Double, Int>?): Double? = when {
        a != null && b != null -> (a.second * a.first + b.second * b.first) / (a.second + b.second)
        a != null -> a.first
        b != null -> b.first
        else -> null
    }

    /** احتمال بارش از رادار برای ساعت step (فقط ۰ تا ۲)؛ null یعنی رادار حرفی برای این ساعت ندارد */
    internal fun radarNowcastProb(radar: RadarNowcastResult, step: Int): Double? {
        if (step >= Tuning.NOWCAST_WEIGHT.size || !radar.available) return null
        if (step == 0 && radar.wetFractionInTarget >= Tuning.NOWCAST_WET_FRAC_MIN) {
            return min(Tuning.NOWCAST_P_MAX, Tuning.NOWCAST_P_WET_BASE + Tuning.NOWCAST_P_WET_SLOPE * radar.wetFractionInTarget)
        }
        val arr = radar.firstArrival
        val arrivesThisHour = arr != null && arr.etaMinLow != null && arr.etaMinLow <= (step + 1) * 60
        return when {
            arrivesThisHour && arr!!.maxDbz >= Tuning.DBZ_STRONG -> Tuning.NOWCAST_P_ARRIVAL_STRONG
            arrivesThisHour && arr!!.maxDbz >= Tuning.DBZ_RAIN -> Tuning.NOWCAST_P_ARRIVAL
            radar.cells.any { isNearMiss(it) } -> Tuning.NOWCAST_P_NEAR_MISS
            radar.maxDbzInTarget < Tuning.DBZ_LIGHT && radar.cells.isEmpty() -> Tuning.NOWCAST_P_CLEAR
            else -> null
        }
    }

    /** سلولی که از کنار دایره رد می‌شود (حداقل فاصله تا مرکز ≤ شعاع + حاشیه) */
    internal fun isNearMiss(c: RadarCellInfo): Boolean =
        c.state == CellApproachState.WILL_MISS && c.minDistanceKm != null &&
            c.minDistanceKm <= Tuning.TARGET_RADIUS_KM + Tuning.NEAR_MISS_MARGIN_KM

    /** شاخص پتانسیل همرفتی ۰ تا ۱۰۰؛ بدون CAPE نامعلوم (null) */
    internal fun convectiveScore(cape: Double?, cin: Double?, li: Double?, pEns: Double?, dewPoint: Double?, moistureInflow: Boolean): Double? {
        if (cape == null) return null
        var sc = 45.0 * (cape / 1500.0).coerceIn(0.0, 1.0)
        if (li != null) sc += 15.0 * ((-li) / 6.0).coerceIn(0.0, 1.0)
        if (pEns != null) sc += 25.0 * pEns
        if (dewPoint != null) sc += 15.0 * ((dewPoint - 5.0) / 10.0).coerceIn(0.0, 1.0)
        if (moistureInflow) sc += 10.0
        sc = min(sc, 100.0)
        if (cin != null && abs(cin) > 100.0) sc *= 0.6
        return r1(sc)
    }

/** جمع‌بندی count ساعت اول (بازه‌های ۱ تا ۲۴ ساعته) */
internal fun buildBlock(rows: List<ForecastRow>, ensembles: List<EnsembleSet?>, count: Int): ForecastRow? {
        val subList = rows.take(minOf(count, rows.size))
        if (subList.isEmpty()) return null

        val startEpoch = subList.first().timeEpochSec - 3600L
        val endEpoch = subList.last().timeEpochSec
        val bs = blockStats(ensembles, startEpoch, endEpoch, Tuning.WET_HOUR_MM, Tuning.HEAVY_6H_MM)

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
            convLabel = if (maxConv == null && blockAmountP50 == null) "نامشخص" else if ((maxConv ?: 0.0) >= Tuning.CONV_MODERATE || (blockAmountP90 ?: 0.0) >= 1.0) "پتانسیل رگبار همرفتی ⚡" else if ((blockAmountP50 ?: 0.0) > 0.1) "بارش باران 🌧️" else "بدون بارش قابل‌توجه",
            virgaRisk = anyVirga,
            confidence = first.confidence,
            sources = first.sources,
            eventLabel = if (blockAmountP50 == null) "نامشخص" else if (blockAmountP50 > 0.1) "بارش باران 🌧️" else "بدون بارش قابل‌توجه"
        )
    }

    /** سهم نقاط خیس ECMWF، کالیبره‌شده با جدول MOS صفی‌آباد (اگر برای این ماه فعال باشد) */
    internal fun calibratedNeighProb(pNeigh: Double?, grid: ModelGrid?, gIdx: Int, month: Int): Double? {
        if (pNeigh == null || grid == null || !Calibration.appliesTo(month)) return pNeigh
        val w = Calibration.windowHours
        val p6 = windowWetFraction(grid, gIdx, w, Calibration.wetThresholdMm) ?: return pNeigh
        val c6 = Calibration.calibrateProb(p6)
        val floor = Calibration.probAtZero() / w
        return if (p6 <= 0.0) floor else max(floor, min(c6, pNeigh * c6 / p6))
    }

    /**
     * مقدار بارش ساعتی P50/P90 از مدل‌ها: اعضای ICON EPS (ساعتی) + اعضای ECMWF ENS (میانگین ۳ ساعته)
     * + HRES مرکز (با تصحیح مقدار کالیبراسیون) + نقطهٔ مرکزی ICON؛ P90 دست‌کم بیشینهٔ HRES در ۱۷ نقطه.
     */
    internal fun modelAmountPercentiles(
        ensI: EnsembleSet?, idxI: Int, ensE: EnsembleSet?, idxE: Int,
        center: CenterDetail?, idxC: Int, gridI: ModelGrid?, gIdxI: Int, gridE: ModelGrid?, gIdxE: Int, month: Int
    ): Pair<Double?, Double?> {
        val memberValues = mutableListOf<Double>()
        if (ensI != null && ensI.times.isNotEmpty() && idxI >= 0) {
            for (m in ensI.members.indices) {
                val v = ensI.members[m].at(idxI)
                if (v != null) memberValues.add(v)
            }
        }
        if (ensE != null && ensE.times.isNotEmpty() && idxE >= 2) {
            for (m in ensE.members.indices) {
                val a = ensE.members[m].at(idxE)
                val b = ensE.members[m].at(idxE - 1)
                val c = ensE.members[m].at(idxE - 2)
                if (a != null && b != null && c != null) {
                    memberValues.add((a + b + c) / 3.0)
                }
            }
        }

        val centerPrecipEcmwfRaw = center?.precip.at(idxC)
        val hresCenter: Double? = centerPrecipEcmwfRaw?.let { raw ->
            if (!Calibration.qmEnabled || !Calibration.appliesTo(month)) raw
            else {
                val W = Calibration.windowHours
                val start = idxC - W + 1
                val window = if (start >= 0) (start..idxC).map { center?.precip.at(it) } else null
                if (window == null || window.any { it == null }) raw
                else {
                    val aW = window.sumOf { it!! }
                    if (aW < 0.1) raw else raw * (Calibration.calibrateAmount(aW) / aW)
                }
            }
        }
        val iconCenter = gridI?.precip?.getOrNull(0)?.at(gIdxI)

        hresCenter?.let(memberValues::add)
        iconCenter?.let(memberValues::add)

        val amtP50: Double? = if (memberValues.isNotEmpty()) percentile(memberValues, 0.5) else null
        var amtP90: Double? = if (memberValues.isNotEmpty()) percentile(memberValues, 0.9) else null

        val hresNeighMax = gridE?.let { grid ->
            if (gIdxE >= 0) {
                (0 until grid.pointCount).mapNotNull { p -> grid.precip[p].at(gIdxE) }.maxOrNull()
            } else null
        }
        if (amtP90 != null && hresNeighMax != null) {
            amtP90 = max(amtP90!!, hresNeighMax)
        }
        return Pair(amtP50, amtP90)
    }

    /** اندیس ساعتی از آرایهٔ زمان‌ها که به t نزدیک‌تر از نیم ساعت است؛ وگرنه ۱- */
    internal fun hourIndex(times: LongArray?, t: Long): Int =
        times?.indexOfFirst { abs(it - t) < 1800L } ?: -1

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
    /** همهٔ ورودی‌های خام موتور؛ یک بار دریافت می‌شوند و محاسبه روی آن‌ها خالص است. */
    data class EngineInputs(
        val neighRes: SourceResult<Map<String, ModelGrid>>,
        val centerRes: SourceResult<CenterDetail>,
        val liftedIndexRes: SourceResult<Pair<LongArray, DoubleArray>>,
        val steeringRes: SourceResult<SteeringWind>,
        val ensEcmwfRes: SourceResult<EnsembleSet>,
        val ensIconRes: SourceResult<EnsembleSet>,
        val synopRes: SourceResult<SynopObs>,
        val radarRes: SourceResult<RadarFetch>
    )

    /** فقط دریافت هم‌زمان داده‌ها از منابع؛ هیچ محاسبه‌ای این‌جا نیست. */
    suspend fun fetchInputs(context: Context?): EngineInputs = coroutineScope {
        val cacheDir: File? = context?.filesDir
        val neighDeferred = async(Dispatchers.IO) { OpenMeteoClient.fetchNeighbourhood() }
        val centerDeferred = async(Dispatchers.IO) { OpenMeteoClient.fetchCenterDetail() }
        val liftedIndexDeferred = async(Dispatchers.IO) { OpenMeteoClient.fetchLiftedIndex() }
        val steeringDeferred = async(Dispatchers.IO) { OpenMeteoClient.fetchSteeringWind() }
        val ensEcmwfDeferred = async(Dispatchers.IO) { OpenMeteoClient.fetchEnsemble("ecmwf_ifs025") }
        val ensIconDeferred = async(Dispatchers.IO) { OpenMeteoClient.fetchEnsemble("icon_seamless") }
        val synopDeferred = async(Dispatchers.IO) { SynopClient.fetchLatestSynop(cacheDir) }
        val radarDeferred = async(Dispatchers.IO) { RadarClient.fetchRadarFrames(cacheDir) }
        EngineInputs(
            neighDeferred.await(), centerDeferred.await(), liftedIndexDeferred.await(), steeringDeferred.await(),
            ensEcmwfDeferred.await(), ensIconDeferred.await(), synopDeferred.await(), radarDeferred.await()
        )
    }

    /**
     * Builds complete forecast dashboard dataset
     */
    suspend fun buildDashboardData(
        context: Context? = null,
        userObservation: SkyObservationReport? = null
    ): ForecastDashboard {
        val inputs = fetchInputs(context)
        val nowEpochSec = System.currentTimeMillis() / 1000L   // تنها جایی که «اکنون» خوانده می‌شود
        return withContext(Dispatchers.Default) {   // محاسبهٔ سنگین (رادار ۵۱۲×۵۱۲) روی رشتهٔ محاسباتی
            computeDashboard(inputs, userObservation, nowEpochSec, context)
        }
    }

    /** محاسبهٔ کامل داشبورد از ورودی‌های خام، با «اکنون» ثابت. (context فقط برای کالیبراسیون و ارزیابی) */
    fun computeDashboard(
        inputs: EngineInputs,
        userObservation: SkyObservationReport?,
        nowEpochSec: Long,
        context: Context? = null
    ): ForecastDashboard {
        val sourceHealthList = mutableListOf<SourceHealth>()
        val neighRes = inputs.neighRes
        val centerRes = inputs.centerRes
        val liftedIndexRes = inputs.liftedIndexRes
        val steeringRes = inputs.steeringRes
        val ensEcmwfRes = inputs.ensEcmwfRes
        val ensIconRes = inputs.ensIconRes
        val synopRes = inputs.synopRes
        val radarRes = inputs.radarRes

        val synop = synopRes.data
        val synopAgeMin = synop?.let { ((nowEpochSec - it.timeUtcEpochSec) / 60L).toInt() }
        val (synopHealthStatus, synopHealthMsg) = when {
            synop == null -> Pair(synopRes.status, synopRes.message)
            !Freshness.isSynopFresh(synop, nowEpochSec) ->
                Pair(SourceStatus.PARTIAL, "آخرین گزارش ${JalaliCalendar.formatNumber(synopAgeMin)} دقیقه پیش — کهنه")
            synopAgeMin != null ->
                Pair(synopRes.status, "آخرین گزارش: ${JalaliCalendar.formatNumber(synopAgeMin)} دقیقه پیش")
            else -> Pair(synopRes.status, synopRes.message)
        }

        sourceHealthList.add(SourceHealth("open-meteo/neighbourhood", neighRes.status, neighRes.message))
        sourceHealthList.add(SourceHealth("open-meteo/center", centerRes.status, centerRes.message))
        sourceHealthList.add(SourceHealth("open-meteo/lifted_index", liftedIndexRes.status, liftedIndexRes.message))
        sourceHealthList.add(SourceHealth("open-meteo/steering", steeringRes.status, steeringRes.message))
        sourceHealthList.add(SourceHealth("ensemble/ecmwf", ensEcmwfRes.status, ensEcmwfRes.message))
        sourceHealthList.add(SourceHealth("ensemble/icon", ensIconRes.status, ensIconRes.message))
        sourceHealthList.add(SourceHealth("ogimet/synop", synopHealthStatus, synopHealthMsg))
        sourceHealthList.add(SourceHealth("rainviewer/radar", radarRes.status, radarRes.message))

        val steeringWind = steeringRes.data
        val centerDetail = centerRes.data
        val liftedIndexData = liftedIndexRes.data
        val neighMap = neighRes.data ?: emptyMap()
        val ensEcmwf = ensEcmwfRes.data
        val ensIcon = ensIconRes.data
        val ecmwfGrid = neighMap["ecmwf"]
        val iconGrid = neighMap["icon"]

        // مدل ECMWF همین الان در همهٔ نقاط خشک است؟ (null = دادهٔ معتبر نیست)
        val wetCountNow = wetCount(ecmwfGrid, hourIndex(ecmwfGrid?.times, nowEpochSec))
        val modelDryNow: Boolean? = wetCountNow?.let { (wet, _) -> wet == 0 }

        var radarNowcast = RadarAnalyzer.analyzeNowcast(radarRes.data, steeringWind, modelDryNow, nowEpochSec)

        val modelWetFracNow = wetFraction(ecmwfGrid, hourIndex(ecmwfGrid?.times, nowEpochSec))
        radarNowcast = applyRadarTrustChecks(radarNowcast, synop, userObservation, modelWetFracNow, nowEpochSec)

        if (context != null) {
            Calibration.load(context)
            if (synop != null) {
                VerificationStore.evaluateWithSynop(context, synop)
            }
        }

        val nowIranStr = epochToIran(nowEpochSec)

        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+03:30")).apply { timeInMillis = nowEpochSec * 1000L }
        val currentMonth = cal.get(Calendar.MONTH) + 1

        // 4. Base time axis from first available source
        val baseTimesOrNull: LongArray? = centerDetail?.times
            ?: ecmwfGrid?.times ?: iconGrid?.times ?: ensIcon?.times ?: ensEcmwf?.times
        var nowHourIndex = baseTimesOrNull?.let { bt ->
            val i = hourIndex(bt, nowEpochSec)
            if (i >= 0) i else bt.indexOfFirst { it >= nowEpochSec - 1800L }
        } ?: -1

        // بدون مدل هم هشدار از مشاهده‌ها (صفی‌آباد، گزارش کاربر، رادار) ساخته شود؛ هرگز خطا نده
        if (baseTimesOrNull == null || nowHourIndex < 0) {
            val verdict = decideTier(radarNowcast, synop, emptyList(), null, null, false, userObservation, nowEpochSec)
            return ForecastDashboard(
                generatedEpochSec = nowEpochSec,
                generatedIran = nowIranStr,
                verdict = verdict,
                radarNowcast = radarNowcast,
                neighbourhood = NeighbourhoodSummary(pointsWithRainNow = 0, totalPoints = 0),
                synopObs = synop,
                modelCurrent = centerDetail?.modelCurrent,
                pattern = PatternSummary(
                    windowStartIran = "", windowEndIran = "", peakPrecipTimeIran = "—", peakPrecipMm = 0.0,
                    peakTsTimeIran = "—", peakConvectiveScore = 0.0, maxCape = 0.0,
                    precipSummaryText = "پیش‌بینی مدل‌ها در دسترس نیست؛ وضعیت آینده نامشخص است."
                ),
                ranges = emptyMap(),
                sourceHealth = sourceHealthList,
                userObservation = userObservation,
                modelsAvailable = false
            )
        }
        val baseTimes: LongArray = baseTimesOrNull

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

            val idxCenter = hourIndex(centerDetail?.times, hEpoch)
            val gIdxEcmwf = hourIndex(ecmwfGrid?.times, hEpoch)
            val gIdxIcon = hourIndex(iconGrid?.times, hEpoch)
            val eIdxEcmwf = hourIndex(ensEcmwf?.times, hEpoch)
            val eIdxIcon = hourIndex(ensIcon?.times, hEpoch)
            val idxSteering = hourIndex(steeringWind?.times, hEpoch)
            val idxLi = hourIndex(liftedIndexData?.first, hEpoch)

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

            // 4.2 Ensemble probability (وزن هر مدل = تعداد اعضای معتبرش)
            val ensE = ensembleWetEcmwf3h(ensEcmwf, eIdxEcmwf)
            val ensI = ensembleWetIconHourly(ensIcon, eIdxIcon)
            val pEns = combineEnsembles(ensE, ensI)

            val pNeighEcmwfCal: Double? = calibratedNeighProb(pNeighEcmwf, ecmwfGrid, gIdxEcmwf, currentMonth)

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
            val pNow: Double? = radarNowcastProb(radarNowcast, step)
            var pNowPoint: Double? = null

            if (pNow != null && step < Tuning.NOWCAST_WEIGHT.size && pArea != null) {
                val w = Tuning.NOWCAST_WEIGHT[step]
                pArea = w * pNow + (1.0 - w) * pArea
                pNowPoint = if (step == 0 && radarNowcast.maxDbzNearPoint >= Tuning.DBZ_RAIN) Tuning.NOWCAST_POINT_RAIN else Tuning.NOWCAST_POINT_FACTOR * pNow
                pPointModel = w * pNowPoint + (1.0 - w) * (pPointModel ?: pArea)
            }

            // 4.6 SYNOP Safiabad Override (h = 0)
            if (step == 0 && Freshness.isSynopFresh(synop, nowEpochSec) && synop != null) {
                if (synop.isRainNow || synop.isThunder) {
                    pArea = max(pArea ?: 0.0, Tuning.SYNOP_RAIN_P_AREA)
                    pPointModel = max(pPointModel ?: 0.0, Tuning.SYNOP_RAIN_P_POINT)
                } else if (synop.ww != null && synop.ww < 20 && radarNowcast.maxDbzInTarget < Tuning.DBZ_LIGHT) {
                    pPointModel = min(pPointModel ?: 1.0, Tuning.SYNOP_DRY_P_POINT_CAP)
                }
            }

            // 4.7 User Observation Override (h = 0)
            if (step == 0 && Freshness.isUserRainNow(userObservation, nowEpochSec) && pArea != null) {
                val k = Freshness.userReportWeight(userObservation, nowEpochSec)
                pArea = max(pArea, k * Tuning.USER_REPORT_MAX_PROB)
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
            val (amtP50Model, amtP90Model) = modelAmountPercentiles(ensIcon, eIdxIcon, ensEcmwf, eIdxEcmwf, centerDetail, idxCenter, iconGrid, gIdxIcon, ecmwfGrid, gIdxEcmwf, currentMonth)
            var amtP50: Double? = amtP50Model
            var amtP90: Double? = amtP90Model

            if (pNow != null && amtP50 != null) {
                val w = Tuning.NOWCAST_WEIGHT[step]
                val arr = radarNowcast.firstArrival
                val amountNow: Double = when {
                    radarNowcast.wetFractionInTarget >= Tuning.NOWCAST_WET_FRAC_MIN && step == 0 -> radarNowcast.rainRateInTargetMmH
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
            val convScore: Double? = convectiveScore(cape, cin, li, pEns, dewPoint, moistureInflowFlag)

            val cScoreVal = convScore ?: 0.0
            if (step < 24) {
                if (cScoreVal > peakSci) {
                    peakSci = cScoreVal
                    peakTsTime = hIran
                }
                if ((cape ?: 0.0) > maxCape) maxCape = cape ?: 0.0
                if ((windGust ?: 0.0) > maxGust) maxGust = windGust ?: 0.0

                val isConvective = cScoreVal >= Tuning.CONV_MODERATE || (amtP90 ?: 0.0) >= 1.0
                if (isConvective) convectiveCount++
                if (isVirga) virgaCount++
            }

            val convLabelStr = when {
                convScore == null -> "نامشخص"
                cScoreVal >= Tuning.CONV_HIGH && (pArea ?: 0.0) >= 0.4 -> "احتمال رگبار همرفتی و رعدوبرق ⚡"
                cScoreVal >= Tuning.CONV_MODERATE -> "ناپایداری متوسط ☁️"
                else -> "پایدار ☀️"
            }

            val confidenceVal: Int? = if (pEns != null && pNeighEcmwf != null) {
                var c = (100.0 * (1.0 - 2.0 * abs(pEns - pNeighEcmwf))).roundToInt().coerceIn(0, 100)
                if (step <= 2 && radarNowcast.available && radarNowcast.coverageOk) c = min(100, c + 15)
                c
            } else null

            val eventLabelStr = if (pArea == null && convScore == null) "نامشخص" else getEventLabel(
                sci = cScoreVal,
                pArea = pArea ?: 0.0,
                precipP90 = amtP90 ?: 0.0,
                isVirga = isVirga,
                isRadarEcho = radarNowcast.available && radarNowcast.maxDbzInTarget >= Tuning.DBZ_RAIN
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

        val alertVerdict = decideTier(radarNowcast, synop, rows, block6, block12, moistureInflowNext12h, userObservation, nowEpochSec)

        val rangesMap = mutableMapOf<ForecastRange, List<ForecastRow>>()
        rangesMap[ForecastRange.HOURLY] = rows

        buildBlock(rows, listOf(ensEcmwf, ensIcon), 1)?.let { rangesMap[ForecastRange.H1] = listOf(it) }
        buildBlock(rows, listOf(ensEcmwf, ensIcon), 3)?.let { rangesMap[ForecastRange.H3] = listOf(it) }
        buildBlock(rows, listOf(ensEcmwf, ensIcon), 6)?.let { rangesMap[ForecastRange.H6] = listOf(it) }
        buildBlock(rows, listOf(ensEcmwf, ensIcon), 12)?.let { rangesMap[ForecastRange.H12] = listOf(it) }
        buildBlock(rows, listOf(ensEcmwf, ensIcon), 24)?.let { rangesMap[ForecastRange.H24] = listOf(it) }

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
                rows.take(24).none { it.probArea != null } -> "پیش‌بینی مدل‌ها برای ۲۴ ساعت آینده در دسترس نیست؛ وضعیت نامشخص است."
                alertVerdict.tier == AlertTier.RED || alertVerdict.tier == AlertTier.ORANGE -> "جمع‌بندی: ${alertVerdict.text}"
                radarNowcast.available && radarNowcast.maxDbzInTarget >= Tuning.DBZ_RAIN -> "رادار بارش فعال را داخل شعاع ۲۰ کیلومتر نشان می‌دهد."
                (h24?.amountP50 ?: 0.0) >= 3.0 -> "بارش باران به حجم کلی حدود ${JalaliCalendar.formatNumber(h24?.amountP50 ?: 0.0)} میلی‌متر پیش‌بینی می‌شود."
                peakSci >= Tuning.CONV_MODERATE -> "پتانسیل رگبار موضعی همرفتی و صاعقه در ساعات اوج ناپایداری."
                virgaCount >= 2 -> "خشکی لایه‌های زیرین جو؛ احتمال تبخیر باران در هوا (ویرگا) و وزش تندباد محلی."
                else -> "مدل‌ها برای ۲۴ ساعت آینده بارش قابل‌توجهی پیش‌بینی نمی‌کنند."
            },
            convectiveHoursCount = convectiveCount,
            virgaHoursCount = virgaCount
        )

        val modelCurrentData = centerDetail?.modelCurrent

        val verificationStats = if (context != null) VerificationStore.computeStats(context) else null

        val wetPointsCount = wetCountNow?.first ?: 0
        val totalValidPoints = wetCountNow?.second ?: 0
        val nSummary = NeighbourhoodSummary(
            pointsWithRainNow = wetPointsCount,
            totalPoints = totalValidPoints,   // ۰ یعنی دادهٔ ECMWF نرسیده؛ UI باید «نامشخص» نشان دهد
            wettestDirectionFa = radarNowcast.firstArrival?.directionFa
        )

        return ForecastDashboard(
            generatedEpochSec = nowEpochSec,
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

    /**
     * رادار در این نوبت قابل اعتماد است؟ اگر نه، available=false با دلیل.
     * فقط رادار را کنار می‌گذارد؛ به احتمال‌ها یا سطح هشدار دست نمی‌زند (هر مشاهده فقط یک کار).
     */
    fun applyRadarTrustChecks(
        radar: RadarNowcastResult,
        synop: SynopObs?,
        userObservation: SkyObservationReport?,
        modelWetFracNow: Double?,
        nowSec: Long
    ): RadarNowcastResult {
        if (!radar.available) return radar
        val synopRainNow = Freshness.isSynopFresh(synop, nowSec) && (synop!!.isRainNow || synop.isThunder)
        val userRainNow = Freshness.isUserRainNow(userObservation, nowSec)
        if ((synopRainNow || userRainNow) && radar.maxDbzInTarget < Tuning.DBZ_LIGHT) {
            return radar.copy(
                available = false, cells = emptyList(), firstArrival = null,
                statusText = "رادار با مشاهدهٔ زمینی تناقض دارد (روی زمین باران گزارش شده ولی رادار اکویی نمی‌بیند)؛ در این نوبت از رادار استفاده نشد."
            )
        }
        val radarSeesNothing = radar.maxDbzInTarget < Tuning.DBZ_LIGHT && radar.cells.isEmpty()
        if (radarSeesNothing && modelWetFracNow != null && modelWetFracNow >= Tuning.RADAR_BLIND_MODEL_WET_FRAC) {
            val pct = (modelWetFracNow * 100).roundToInt()
            return radar.copy(
                available = false, cells = emptyList(), firstArrival = null,
                statusText = "رادار هیچ بارشی نمی‌بیند در حالی که مدل‌ها همین الان در $pct٪ نقاط اطراف باران دارند؛ احتمالاً رادار این منطقه را نمی‌بیند. در این نوبت از رادار استفاده نشد."
            )
        }
        return radar
    }

    private fun getEventLabel(
        sci: Double,
        pArea: Double,
        precipP90: Double,
        isVirga: Boolean,
        isRadarEcho: Boolean
    ): String {
        return when {
            isRadarEcho -> "بارش در رادار 📡"
            isVirga && pArea < 0.3 -> "تبخیر زیر ابر (ویرگا) 🌫️"
            sci >= 50.0 || (precipP90 >= 1.5 && sci >= 35.0) -> "رگبار همرفتی و صاعقه ⚡"
            sci >= 25.0 || precipP90 >= 0.5 -> "رگبار موضعی پراکنده 🌦️"
            pArea >= 0.6 -> "بارش باران 🌧️"
            pArea >= 0.3 -> "بارش ملایم 🌦️"
            pArea >= 0.1 -> "نم‌نم باران 🌦️"
            sci >= 15.0 -> "پتانسیل همرفتی محلی ☁️"
            else -> "بدون بارش"
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
        moistureInflowNext12h: Boolean = false,
        userObservation: SkyObservationReport? = null,
        nowSec: Long = System.currentTimeMillis() / 1000L
    ): VerdictInfo {
        val radarOk = radar.available
        val firstArrival = radar.firstArrival
        val synopFresh = Freshness.isSynopFresh(synop, nowSec)
        val userAgeMin = Freshness.userReportAgeMin(userObservation, nowSec)
        val userRain = Freshness.isUserRainNow(userObservation, nowSec)

        // RED conditions
        val isRedRadar = radarOk && radar.maxDbzInTarget >= Tuning.DBZ_SEVERE
        val isRedApproaching = radarOk && firstArrival != null && firstArrival.maxDbz >= Tuning.DBZ_STRONG && (firstArrival.etaMinLow != null && firstArrival.etaMinLow <= Tuning.RED_ETA_MIN)
        val isRedSynop = synopFresh && synop!!.isThunder
        val isRedHeavy6h = block6?.pHeavy6h != null && block6.pHeavy6h >= Tuning.RED_PROB_HEAVY_6H

        if (isRedRadar || isRedApproaching || isRedSynop || isRedHeavy6h) {
            val text = when {
                isRedRadar -> "هشدار قرمز: بارش ${PlainText.intensityFa(radar.maxDbzInTarget)} داخل دایرهٔ ۲۰ کیلومتری شمس‌آباد در رادار دیده می‌شود."
                isRedApproaching -> "هشدار قرمز: تودهٔ بارشی ${PlainText.intensityFa(firstArrival?.maxDbz)} در ${firstArrival?.distKm?.roundToInt()} کیلومتری؛ ورود به منطقه طی ${firstArrival?.etaMinLow} تا ${firstArrival?.etaMinHigh} دقیقهٔ دیگر."
                isRedSynop -> "هشدار قرمز: وقوع صاعقه و تندباد در دیدبانی زمینی ایستگاه صفی‌آباد ثبت شد."
                else -> "هشدار قرمز: احتمال ${((block6?.pHeavy6h ?: 0.0) * 100).toInt()}٪ برای بیش از ۱۰ میلی‌متر بارش در ۶ ساعت آینده."
            }
            val headline = when {
                isRedRadar || isRedSynop -> "بارش شدید یا رعدوبرق در منطقه"
                isRedApproaching -> "تودهٔ بارشی شدید در راه"
                else -> "احتمال بارش سنگین در ۶ ساعت آینده"
            }
            return VerdictInfo(AlertTier.RED, text, "از تردد غیرضروری در فضای باز و بستر رودخانه‌ها خودداری فرمایید.", headline)
        }

        // ORANGE conditions
        val isOrangeRadar = radarOk && (radar.maxDbzInTarget >= Tuning.DBZ_RAIN + 5 || radar.wetFractionInTarget >= 0.1)
        val isOrangeApproaching = radarOk && firstArrival != null && (firstArrival.etaMinLow != null && firstArrival.etaMinLow <= Tuning.ORANGE_ETA_MIN)
        val isOrangeSynop = synopFresh && synop!!.isRainNow
        val p6hWet = block6?.pWet ?: (rows.take(6).mapNotNull { it.probArea?.let { p -> p / 100.0 } }.maxOrNull() ?: 0.0)
        val isOrangeProb = p6hWet >= Tuning.ORANGE_PROB_WET_6H
        val isOrangeUser = userRain

        if (isOrangeRadar || isOrangeApproaching || isOrangeSynop || isOrangeProb || isOrangeUser) {
            val text = when {
                isOrangeUser -> "هشدار نارنجی: بارش در محل شما (گزارش خودتان: ${userObservation?.phenomenon}، $userAgeMin دقیقه پیش)."
                isOrangeApproaching -> "هشدار نارنجی: تودهٔ بارشی ${PlainText.intensityFa(firstArrival?.maxDbz)} در ${firstArrival?.distKm?.roundToInt()} کیلومتری (${firstArrival?.directionFa}) به سمت منطقه می‌آید؛ ورود محتمل ${firstArrival?.etaMinLow} تا ${firstArrival?.etaMinHigh} دقیقهٔ دیگر."
                isOrangeRadar -> "هشدار نارنجی: لکه‌های بارشی فعال در اسکن راداری محدوده ۲۰ کیلومتری دیده می‌شود."
                isOrangeSynop -> "هشدار نارنجی: ثبت بارش فعال در ایستگاه دیدبانی صفی‌آباد."
                else -> "هشدار نارنجی: مدل‌های پیش‌بینی احتمال بارش بالای ۶۰ درصد برای ۶ ساعت آینده محاسبه کرده‌اند."
            }
            val headline = when {
                isOrangeUser || isOrangeSynop || isOrangeRadar -> "همین الان در منطقه باران می‌بارد"
                isOrangeApproaching -> "تودهٔ بارشی در راه"
                else -> "بارش در ۶ ساعت آینده محتمل است"
            }
            return VerdictInfo(AlertTier.ORANGE, text, "احتمال وقوع رگبار ناگهانی و تندباد موضعی وجود دارد.", headline)
        }

        // YELLOW conditions
        val p12hWet = block12?.pWet ?: (rows.take(12).mapNotNull { it.probArea?.let { p -> p / 100.0 } }.maxOrNull() ?: 0.0)
        val isYellowRadarLight = radarOk && radar.maxDbzInTarget >= Tuning.DBZ_RAIN
        val isYellowProb = p12hWet >= Tuning.YELLOW_PROB_WET_12H
        val isYellowMoistProb = moistureInflowNext12h && p12hWet >= Tuning.YELLOW_MOIST_MIN_PROB
        val isYellowConv = rows.take(12).any { (it.convScore ?: 0.0) >= Tuning.CONV_HIGH }
        val isYellowMiss = radarOk && radar.cells.any { isNearMiss(it) }
        val isYellowUpwindEcho = radarOk && radar.cells.any {
            it.state == CellApproachState.APPROACHING && it.maxDbz >= Tuning.DBZ_STRONG
        }
        val isYellowSynopRecent = synopFresh && synop!!.isRecentPrecip

        if (isYellowRadarLight || isYellowProb || isYellowMoistProb || isYellowConv || isYellowMiss || isYellowUpwindEcho || isYellowSynopRecent) {
            val text = when {
                isYellowRadarLight -> "هشدار زرد: بارش سبک روی بخشی از دایرهٔ ۲۰ کیلومتری در رادار دیده می‌شود."
                isYellowUpwindEcho -> "هشدار زرد: تودهٔ بارشی قوی در فاصلهٔ دور به سمت محدوده در حرکت است."
                isYellowSynopRecent -> "هشدار زرد: ثبت بارش در ساعت اخیر در دیدبانی ایستگاه صفی‌آباد."
                isYellowConv -> "هشدار زرد: افزایش پتانسیل همرفتی و ناپایداری جوی طی ۱۲ ساعت آینده."
                isYellowMiss -> "هشدار زرد: توده بارشی از حاشیه دایره ۲۰ کیلومتری در حال عبور است."
                isYellowMoistProb -> "هشدار زرد: ورود رطوبت تراز پایین همراه با احتمال بارش در ۱۲ ساعت آینده."
                else -> "هشدار زرد: احتمال رگبار موضعی و ناپایداری جوی در ساعات آینده."
            }
            val headline = when {
                isYellowRadarLight -> "بارش سبک در بخشی از منطقه"
                isYellowSynopRecent -> "بارش اخیر در صفی‌آباد"
                isYellowUpwindEcho || isYellowMiss -> "تودهٔ بارشی در نزدیکی منطقه"
                else -> "احتمال بارش در ۱۲ ساعت آینده"
            }
            return VerdictInfo(AlertTier.YELLOW, text, "اوضاع جوی به طور مستمر رصد می‌شود.", headline)
        }

        // سبز فقط وقتی دست‌کم یک مشاهدهٔ زندهٔ معتبر «بدون بارش» را تأیید کند:
        // رادار در دسترس با پوشش تأییدشده، یا گزارش تازهٔ صفی‌آباد که واقعاً هوای حاضر را گزارش کرده است.
        val radarLive = radarOk && radar.coverageVerified
        val synopLive = Freshness.isSynopLiveObservation(synop, nowSec)
        val modelOk = rows.take(12).any { it.probArea != null }

        if (!modelOk || (!radarLive && !synopLive)) {
            val missing = buildList {
                if (!modelOk) add("مدل‌های پیش‌بینی")
                if (!radarOk) add("رادار") else if (!radarLive) add("رادار با پوشش تأییدشده")
                if (!synopFresh) add("دیدبانی صفی‌آباد") else if (!synopLive) add("گزارش هوای حاضر صفی‌آباد")
            }.joinToString("، ")
            return VerdictInfo(
                AlertTier.UNKNOWN,
                "وضعیت زنده نامشخص است: $missing در دسترس نیست.",
                if (modelOk) "پیش‌بینی مدل‌ها برای ۱۲ ساعت آینده نشانهٔ بارش قابل‌توجهی ندارد، اما مشاهدهٔ زنده‌ای برای تأیید نیست."
                else "به‌محض برقراری اتصال دوباره بررسی می‌شود."
            )
        }

        val liveSources = buildList {
            if (radarLive) add("رادار")
            if (synopLive) add("دیدبانی صفی‌آباد")
        }.joinToString(" و ")
        return VerdictInfo(
            AlertTier.GREEN,
            "در دایرهٔ ۲۰ کیلومتری شمس‌آباد بارشی دیده نمی‌شود و مدل‌ها برای ۱۲ ساعت آینده بارش قابل‌توجهی نشان نمی‌دهند.",
            "مبنا: $liveSources و مدل‌های پیش‌بینی."
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
