package com.amin.weather

import com.amin.weather.core.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * «تست طلایی» موتور: ورودی‌های ساختگیِ ثابت ← computeDashboard ← متن خروجی.
 * خروجی باید دقیقاً با فایل ثبت‌شده یکی باشد. هدف: هنگام بازآرایی کد، رفتار بی‌صدا عوض نشود.
 * اگر تغییری در رفتار عمدی است، فایل طلایی باید آگاهانه به‌روز شود (با -Dgolden.write=true).
 */
class GoldenComputeTest {

    private val now = 1_794_085_200L          // 2026-11-07 21:00 UTC (ماه نوامبر: کالیبراسیون فعال)
    private val start = now - 24 * 3600L
    private val hours = 96
    private val times = LongArray(hours) { start + it * 3600L }

    /** عدد شبه‌تصادفی قطعی */
    private fun noise(a: Int, b: Int): Double {
        val x = sin(a * 12.9898 + b * 78.233) * 43758.5453
        return x - kotlin.math.floor(x)
    }

    /** شدت بارش «سامانه» در ساعت h نسبت به now؛ قله حدود ۳ ساعت بعد */
    private fun system(hFromNow: Int, wet: Boolean): Double {
        if (!wet) return 0.0
        val d = (hFromNow - 3).toDouble()
        return max(0.0, 2.5 - 0.25 * d * d)
    }

    private fun inputs(wet: Boolean, radarFetch: RadarFetch?, synop: SynopObs?): ForecastEngine.EngineInputs {
        val hIdx = { i: Int -> ((times[i] - now) / 3600L).toInt() }
        val ecmwf = ModelGrid(
            times,
            Array(17) { p -> DoubleArray(hours) { i -> max(0.0, system(hIdx(i), wet) * (0.6 + 0.8 * noise(p, i)) - 0.1) } },
            null, null, null
        )
        val icon = ModelGrid(
            times,
            Array(17) { p -> DoubleArray(hours) { i -> max(0.0, system(hIdx(i) - 1, wet) * (0.5 + noise(p + 31, i)) - 0.1) } },
            null, null, null
        )
        val center = CenterDetail(
            times = times,
            temp = DoubleArray(hours) { 18.0 + 4 * sin(it / 24.0 * 6.28) },
            dewPoint = DoubleArray(hours) { 12.0 + (if (wet) 4.0 else 0.0) },
            rh = DoubleArray(hours) { if (wet) 85.0 else 45.0 },
            precip = DoubleArray(hours) { i -> system(hIdx(i), wet) },
            rain = null, showers = null, weatherCode = null,
            cape = DoubleArray(hours) { i -> if (wet) 300.0 + 400 * noise(5, i) else 20.0 },
            cin = DoubleArray(hours) { -20.0 },
            li = null,
            cloud = DoubleArray(hours) { if (wet) 90.0 else 10.0 },
            cloudLow = null, cloudMid = null,
            windSpeed = DoubleArray(hours) { 15.0 },
            windDir = DoubleArray(hours) { 240.0 },
            windGust = DoubleArray(hours) { i -> 25.0 + 20 * noise(9, i) },
            modelCurrent = null
        )
        val ensE = EnsembleSet(times, Array(51) { m -> DoubleArray(hours) { i -> max(0.0, system(hIdx(i), wet) * noise(m, i) * 1.6 - 0.2) } })
        val ensI = EnsembleSet(times, Array(40) { m -> DoubleArray(hours) { i -> max(0.0, system(hIdx(i) - 1, wet) * noise(m + 100, i) * 1.4 - 0.2) } })
        val steering = SteeringWind(times, DoubleArray(hours) { 40.0 }, DoubleArray(hours) { 60.0 }, BooleanArray(hours) { wet })
        val li = Pair(times, DoubleArray(hours) { if (wet) -2.0 else 3.0 })
        return ForecastEngine.EngineInputs(
            SourceResult("n", SourceStatus.OK, mapOf("ecmwf" to ecmwf, "icon" to icon)),
            SourceResult("c", SourceStatus.OK, center),
            SourceResult("l", SourceStatus.OK, li),
            SourceResult("s", SourceStatus.OK, steering),
            SourceResult("e", SourceStatus.OK, ensE),
            SourceResult("i", SourceStatus.OK, ensI),
            SourceResult("y", if (synop != null) SourceStatus.OK else SourceStatus.FAILED, synop),
            SourceResult("r", if (radarFetch != null) SourceStatus.OK else SourceStatus.FAILED, radarFetch)
        )
    }

    private fun blob(cxKm: Double, cyKm: Double, r: Double, dbz: Int): IntArray {
        val g = IntArray(512 * 512) { -1 }
        val tx = GeoUtils.lonToTileX(RegionConfig.CENTER.lon, 7)
        val ty = GeoUtils.latToTileY(RegionConfig.CENTER.lat, 7)
        val minX = kotlin.math.floor(tx).toInt() - 1
        val minY = kotlin.math.floor(ty).toInt()
        for (py in 0 until 512) {
            val lat = GeoUtils.tileYToLat(minY + (py + 0.5) / 256.0, 7)
            for (px in 0 until 512) {
                val lon = GeoUtils.tileXToLon(minX + (px + 0.5) / 256.0, 7)
                val (x, y) = GeoUtils.toLocalKm(RegionConfig.CENTER.lat, RegionConfig.CENTER.lon, lat, lon)
                if (kotlin.math.hypot(x - cxKm, y - cyKm) <= r) g[py * 512 + px] = dbz
            }
        }
        return g
    }

    private fun f(v: Double?) = v?.let { String.format(java.util.Locale.US, "%.3f", it) } ?: "null"

    private fun render(d: ForecastDashboard): String = buildString {
        appendLine("verdict=${d.verdict.tier} | ${d.verdict.headline} | ${d.verdict.text} | ${d.verdict.advisoryNote}")
        appendLine("radar=${d.radarNowcast.available} max=${d.radarNowcast.maxDbzInTarget} wet=${f(d.radarNowcast.wetFractionInTarget)} cells=${d.radarNowcast.cells.size} first=${d.radarNowcast.firstArrival?.let { "${it.etaMinLow}-${it.etaMinHigh}" }} | ${d.radarNowcast.statusText}")
        appendLine("neigh=${d.neighbourhood.pointsWithRainNow}/${d.neighbourhood.totalPoints} models=${d.modelsAvailable}")
        val p = d.pattern
        appendLine("pattern=${p.windowStartIran}..${p.windowEndIran} peak=${p.peakPrecipTimeIran}/${f(p.peakPrecipMm)} ts=${p.peakTsTimeIran}/${f(p.peakConvectiveScore)} cape=${f(p.maxCape)} tot=${f(p.totalPrecip24hLikely)}/${f(p.totalPrecip24hMax)} maxP=${f(p.maxProb24h)} gust=${f(p.maxGust24h)} conv=${p.convectiveHoursCount} virga=${p.virgaHoursCount} | ${p.precipSummaryText}")
        for ((k, rows) in d.ranges.toSortedMap(compareBy { it.ordinal })) {
            for (r in rows) {
                appendLine("$k ${r.timeIran} src=${r.forecastSource} pA=${f(r.probArea)} pP=${f(r.probPoint)} a50=${f(r.amountP50)} a90=${f(r.amountP90)} t=${f(r.tempC)} ${f(r.tempMinC)}/${f(r.tempMaxC)} td=${f(r.dewC)} rh=${f(r.rh)} cl=${f(r.cloud)} g=${f(r.gustKmh)} ws=${f(r.windSpeedKmh)} wd=${f(r.windDirFromDeg)} ${r.windDirLabel} cs=${f(r.convScore)} ${r.convLabel} v=${r.virgaRisk} conf=${r.confidence} [${r.sources.joinToString(",")}] ${r.eventLabel}")
            }
        }
    }

    private fun scenarios(): String {
        // کالیبراسیون واقعی پروژه، تا مسیر کالیبره هم پوشش داده شود
        val calFile = listOf("src/main/assets/calibration_40794.json", "app/src/main/assets/calibration_40794.json")
            .map { File(it) }.firstOrNull { it.exists() }
        if (calFile != null) Calibration.loadFromJson(calFile.readText())

        val sb = StringBuilder()
        val wetSynop = SynopObs(now - 1200, 17.0, 15.5, 90.0, null, null, 61, true, false, false, 1)
        val drySynop = SynopObs(now - 1200, 20.0, 8.0, 45.0, 0.0, 6, 2, false, false, false, 1)
        val cellFetch = RadarFetch(listOf(
            RadarFrame(now - 900, blob(-40.0, -20.0, 6.0, 38)),
            RadarFrame(now - 300, blob(-35.0, -17.5, 6.0, 38))
        ), lastFrameAgeMin = 5, coverageVerified = true)
        val emptyFetch = RadarFetch(listOf(RadarFrame(now - 300, IntArray(512 * 512) { -1 })), 5, true)
        val rainOverhead = RadarFetch(listOf(RadarFrame(now - 300, blob(2.0, 1.0, 8.0, 28))), 5, true)

        val cases = listOf(
            Triple("A wet+cell+synopRain", inputs(true, cellFetch, wetSynop), null),
            Triple("B dry+emptyRadar+synopDry", inputs(false, emptyFetch, drySynop), null),
            Triple("C wet+emptyRadar(blind)+noSynop", inputs(true, emptyFetch, null), null),
            Triple("D wet+rainOverhead+userRain", inputs(true, rainOverhead, null),
                SkyObservationReport(phenomenon = "باران", createdAtEpochMs = (now - 600) * 1000L)),
            Triple("E dry+noRadar+noSynop", inputs(false, null, null), null)
        )
        for ((name, inp, user) in cases) {
            sb.appendLine("=== $name")
            sb.append(render(ForecastEngine.computeDashboard(inp, user, now, null)))
        }
        return sb.toString()
    }

    @Test
    fun computeDashboard_matchesGolden() {
        val actual = scenarios()
        val goldenRel = "golden/compute_dashboard.txt"
        val res = javaClass.classLoader?.getResource(goldenRel)
        if (System.getProperty("golden.write") == "true" || res == null) {
            val out = File(System.getProperty("golden.dir", "src/test/resources") + "/" + goldenRel)
            out.parentFile.mkdirs(); out.writeText(actual)
            if (res == null) throw AssertionError("فایل طلایی وجود نداشت و ساخته شد: ${out.absolutePath}")
            return
        }
        assertEquals(res.readText(), actual)
    }
}
