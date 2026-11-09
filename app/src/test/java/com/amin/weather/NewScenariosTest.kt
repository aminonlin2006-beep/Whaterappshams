package com.amin.weather

import com.amin.weather.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * سناریوهای تازهٔ آزمون (داور «تستر»). هر تست یک قاعدهٔ توافق‌شده یا یک لبهٔ ورودی را بررسی می‌کند.
 * تست‌هایی که با پیشوند REG_ دارند، باگ‌های پیداشده در بازبینی ۱۷ آبان را قفل می‌کنند تا دوباره برنگردند.
 */
class NewScenariosTest {

    // ---------------------------------------------------------------- کمکی‌ها
    private val nowSec get() = System.currentTimeMillis() / 1000L

    private fun radar(
        available: Boolean = true, maxDbz: Int = -1, wet: Double = 0.0,
        cells: List<RadarCellInfo> = emptyList(), first: RadarCellInfo? = null, coverage: Boolean = true
    ) = RadarNowcastResult(
        available = available, frameAgeMin = 5, coverageOk = coverage, maxDbzInTarget = maxDbz,
        wetFractionInTarget = wet, rainRateInTargetMmH = 0.0, cells = cells, firstArrival = first,
        coverageVerified = coverage
    )

    private fun row(probArea: Double?, conv: Double? = 0.0, t: Long = 0L) = ForecastRow(
        timeEpochSec = t, timeIran = "", forecastSource = "", probArea = probArea, probPoint = probArea,
        amountP50 = 0.0, amountP90 = 0.0, tempC = 20.0, dewC = 10.0, rh = 50.0, cloud = 10.0,
        gustKmh = 10.0, convScore = conv
    )

    private fun dryRows(n: Int = 12) = List(n) { row(5.0) }
    private val dryBlock = ForecastEngine.BlockStats(0.0, 0.0, 0.0, 0.0)

    private fun synop(ageMin: Long, ww: Int?, rain: Boolean = false, thunder: Boolean = false,
                      recent: Boolean = false, iX: Int? = 1) =
        SynopObs(nowSec - ageMin * 60, 20.0, 15.0, 70.0, null, null, ww, rain, thunder, recent, iX)

    private fun cell(state: CellApproachState, dbz: Int, etaLow: Int?, etaHigh: Int?, minDist: Double? = null) =
        RadarCellInfo("C1", 20, 21.0, dbz, 32.0, 48.0, 40.0, 270.0, 2.5, 30.0, 90.0,
            state, etaLow, etaHigh, minDist, "radar_track")

    private fun user(phen: String, ageMin: Long) =
        SkyObservationReport(phenomenon = phen, createdAtEpochMs = System.currentTimeMillis() - ageMin * 60_000L)

    /** شبکهٔ ۵۱۲×۵۱۲ با پیکسل‌های داخل دایرهٔ radiusKm حول نقطهٔ محلی (cx, cy) کیلومتر */
    private fun blobGrid(cxKm: Double, cyKm: Double, radiusKm: Double, dbz: Int): IntArray {
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
                if (kotlin.math.hypot(x - cxKm, y - cyKm) <= radiusKm) g[py * 512 + px] = dbz
            }
        }
        return g
    }

    // ================================================================ decideTier

    /** قاعده: اکوی باران داخل دایره نباید GREEN بدهد. */
    @Test
    fun REG_T1_weakRainEchoInsideCircle_mustNotBeGreen() {
        val v = ForecastEngine.decideTier(radar(maxDbz = 22, wet = 0.05), null, dryRows(), dryBlock, dryBlock)
        assertNotEquals("اکوی ۲۲dBZ داخل دایره ولی GREEN شد", AlertTier.GREEN, v.tier)
    }

    /** همان، با تحلیل واقعیِ رادار: لکهٔ ۲۲dBZ به شعاع ۳km روی شمس‌آباد، مدل خشک */
    @Test
    fun REG_T1b_endToEnd_smallEchoOverShamsabad_mustNotBeGreen() {
        val g = blobGrid(0.0, 0.0, 3.0, 22)
        val f = RadarFetch(listOf(RadarFrame(nowSec, g)), lastFrameAgeMin = 5, coverageVerified = true)
        val r = RadarAnalyzer.analyzeNowcast(f, nowSec = nowSec)
        assertTrue(r.available)
        assertTrue(r.maxDbzInTarget >= Tuning.DBZ_RAIN)
        val v = ForecastEngine.decideTier(r, null, dryRows(), dryBlock, dryBlock)
        assertNotEquals("باران روی خود شمس‌آباد ولی GREEN", AlertTier.GREEN, v.tier)
    }

    /** قاعده: «نامعلوم هرگز آرام نیست». SYNOP تازه بدون گروه ۷ (iX=3) هیچ اطلاعی از هوای حاضر نمی‌دهد. */
    @Test
    fun REG_T2_freshSynopWithoutWeatherGroup_radarDown_mustNotBeGreen() {
        val s = synop(ageMin = 30, ww = null, iX = 3)
        val v = ForecastEngine.decideTier(radar(available = false), s, dryRows(), dryBlock, dryBlock)
        assertNotEquals(AlertTier.GREEN, v.tier)
    }

    /** پوشش رادار تأیید نشده + تصویر خالی: مشاهدهٔ زندهٔ معتبر نیست. */
    @Test
    fun REG_T3_unverifiedRadarCoverage_emptyImage_mustNotBeGreen() {
        val v = ForecastEngine.decideTier(radar(coverage = false), null, dryRows(), dryBlock, dryBlock)
        assertNotEquals(AlertTier.GREEN, v.tier)
    }

    /** حادثهٔ دیشب، پیش از گزارش صفی‌آباد: رادار خالی، SYNOP کهنه، مدل‌ها مرطوب */
    @Test
    fun T4_incident_beforeSynop_radarEmpty_modelsWet_notGreenNotUnknown() {
        val rows = listOf(32.0, 61.0, 85.0, 100.0, 100.0, 100.0, 90.0, 90.0, 80.0, 70.0, 60.0, 50.0).map { row(it) }
        val b6 = ForecastEngine.BlockStats(0.55, 0.4, 3.0, 0.0)
        val b12 = ForecastEngine.BlockStats(0.6, 0.8, 5.0, 0.0)
        val v = ForecastEngine.decideTier(radar(), synop(150, 2), rows, b6, b12)
        assertTrue("tier=${v.tier}", v.tier == AlertTier.YELLOW || v.tier == AlertTier.ORANGE || v.tier == AlertTier.RED)
    }

    /** حادثه: رادار ۳ تصویر کاملاً خالی و یکسان، ولی مدل‌ها همین الان در همهٔ ۱۷ نقطه باران دارند. */
    @Test
    fun REG_T4b_incident_blankRadarWhileModelsAllWet_radarMustNotBeTrustedAsDry() {
        val empty = IntArray(512 * 512) { -1 }
        val f = RadarFetch(listOf(RadarFrame(nowSec - 1200, empty.clone()), RadarFrame(nowSec - 600, empty.clone()),
            RadarFrame(nowSec, empty.clone())), lastFrameAgeMin = 5, coverageVerified = true)
        val r = RadarAnalyzer.analyzeNowcast(f, modelDryNow = false, nowSec = nowSec)
        assertFalse("رادار خالیِ یخ‌زده با مدل کاملاً مرطوب نباید «خشک معتبر» حساب شود", r.available)
    }

    @Test
    fun T5_incident_afterSafiabadRain_isOrange() {
        val v = ForecastEngine.decideTier(radar(), synop(20, 61, rain = true), dryRows(), dryBlock, dryBlock)
        assertEquals(AlertTier.ORANGE, v.tier)
    }

    @Test
    fun T6_userRain59min_orange_61min_notFromUser() {
        val a = ForecastEngine.decideTier(radar(), null, dryRows(), dryBlock, dryBlock, false, user("باران", 59))
        assertEquals(AlertTier.ORANGE, a.tier)
        val b = ForecastEngine.decideTier(radar(), null, dryRows(), dryBlock, dryBlock, false, user("باران", 61))
        assertEquals(AlertTier.GREEN, b.tier)
    }

    @Test
    fun T6b_userRain_withRadarAndSynopDown_isStillOrange() {
        val v = ForecastEngine.decideTier(radar(available = false), null, emptyList(), null, null, false, user("تگرگ", 5))
        assertEquals(AlertTier.ORANGE, v.tier)
    }

    @Test
    fun T7_userRain_withSevereRadar_isRed() {
        val v = ForecastEngine.decideTier(radar(maxDbz = 50, wet = 0.3), null, dryRows(), dryBlock, dryBlock, false, user("رگبار", 5))
        assertEquals(AlertTier.RED, v.tier)
    }

    @Test
    fun T8_userClearSky_noLiveSource_isUnknown() {
        val v = ForecastEngine.decideTier(radar(available = false), null, dryRows(), dryBlock, dryBlock, false, user("صاف", 1))
        assertEquals(AlertTier.UNKNOWN, v.tier)
    }

    @Test
    fun T9_synopThunder_freshIsRed_staleIsNot() {
        val fresh = ForecastEngine.decideTier(radar(), synop(80, 95, rain = true, thunder = true), dryRows(), dryBlock, dryBlock)
        assertEquals(AlertTier.RED, fresh.tier)
        val stale = ForecastEngine.decideTier(radar(), synop(100, 95, rain = true, thunder = true), dryRows(), dryBlock, dryBlock)
        assertEquals(AlertTier.GREEN, stale.tier)
    }

    @Test
    fun T10_noRows_radarOk_isUnknown() {
        val v = ForecastEngine.decideTier(radar(), null, emptyList(), null, null)
        assertEquals(AlertTier.UNKNOWN, v.tier)
    }

    @Test
    fun T10b_rowsAllNullProb_radarOk_isUnknown() {
        val v = ForecastEngine.decideTier(radar(), null, List(12) { row(null) }, null, null)
        assertEquals(AlertTier.UNKNOWN, v.tier)
    }

    /** گزارشی با زمان آینده (ساعت گوشی یا داده خراب) نباید برای همیشه «تازه» بماند. */
    @Test
    fun REG_T11_futureDatedSynop_mustNotCountAsFresh() {
        val s = SynopObs(nowSec + 86_400, 20.0, 15.0, 70.0, null, null, 61, true, false, false, 1)
        val v = ForecastEngine.decideTier(radar(available = false), s, dryRows(), dryBlock, dryBlock)
        assertNotEquals(AlertTier.ORANGE, v.tier)
    }

    @Test
    fun T12_allDry_radarOk_isGreen() {
        val v = ForecastEngine.decideTier(radar(), null, dryRows(), dryBlock, dryBlock)
        assertEquals(AlertTier.GREEN, v.tier)
    }

    @Test
    fun T13_arrivedCell_eta0_isOrange() {
        val c = cell(CellApproachState.APPROACHING, 25, 0, 0)
        val v = ForecastEngine.decideTier(radar(cells = listOf(c), first = c), null, dryRows(), dryBlock, dryBlock)
        assertEquals(AlertTier.ORANGE, v.tier)
    }

    @Test
    fun T14_willMissCellNearEdge_isYellow() {
        val c = cell(CellApproachState.WILL_MISS, 30, null, null, minDist = 30.0)
        val v = ForecastEngine.decideTier(radar(cells = listOf(c)), null, dryRows(), dryBlock, dryBlock)
        assertEquals(AlertTier.YELLOW, v.tier)
    }

    @Test
    fun T15_redHeavy6h_exactThreshold() {
        val v = ForecastEngine.decideTier(radar(), null, dryRows(), ForecastEngine.BlockStats(1.0, 8.0, 15.0, 0.50), dryBlock)
        assertEquals(AlertTier.RED, v.tier)
    }

    // ================================================================ SYNOP

    @Test
    fun S1_onlyNilLine_returnsNull() {
        assertNull(SynopClient.parseOgimetResponse("40794,2026,11,07,18,00,AAXX 07181 40794 NIL="))
    }

    @Test
    fun S1b_emptyOrGarbageBody_returnsNull() {
        assertNull(SynopClient.parseOgimetResponse(""))
        assertNull(SynopClient.parseOgimetResponse("<html>Error 503</html>"))
    }

    @Test
    fun S2_nilLastLine_keepsOlderReport_whichFreshnessThenRejects() {
        val body = "40794,2026,11,07,12,00,AAXX 07121 40794 11970 31205 10200 20120 76166=\n" +
                "40794,2026,11,07,15,00,AAXX 07151 40794 NIL="
        val o = SynopClient.parseOgimetResponse(body)!!
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, 10, 7, 12, 0, 0) }
        assertEquals(cal.timeInMillis / 1000, o.timeUtcEpochSec)
        assertTrue(o.isRainNow)
    }

    @Test
    fun S3_iX3_noGroup7_wwNull_notRain() {
        val o = SynopClient.parseSynopLine("40794,2026,11,07,12,00,AAXX 07121 40794 43370 31205 10200 20120=")!!
        assertNull(o.ww); assertFalse(o.isRainNow); assertFalse(o.isThunder); assertEquals(3, o.iX)
    }

    @Test
    fun S3b_iX1_butGroup7Missing_wwNull() {
        val o = SynopClient.parseSynopLine("40794,2026,11,07,12,00,AAXX 07121 40794 41170 31205 10200 20120=")!!
        assertNull(o.ww); assertFalse(o.isRainNow)
    }

    @Test
    fun S4_autoStation_iX7_wawa() {
        val rain = SynopClient.parseSynopLine("40794,2026,11,07,12,00,AAXX 07121 40794 41770 81205 10200 20120 76300=")!!
        assertEquals(63, rain.ww); assertTrue(rain.isRainNow); assertFalse(rain.isThunder)
        val ts = SynopClient.parseSynopLine("40794,2026,11,07,12,00,AAXX 07121 40794 41770 81205 10200 20120 79200=")!!
        assertTrue(ts.isThunder)
    }

    /** ww=89/90: رگبار تگرگ (با یا بدون باران) — در دزفول رخ می‌دهد */
    @Test
    fun REG_S5_hailShowerWw89_isPrecipitationNow() {
        val o = SynopClient.parseSynopLine("40794,2026,11,07,12,00,AAXX 07121 40794 41170 81205 10200 20120 78966=")!!
        assertEquals(89, o.ww)
        assertTrue("ww=89 رگبار تگرگ است ولی isRainNow=false", o.isRainNow)
    }

    @Test
    fun S6_malformedLines_returnNullWithoutException() {
        val bad = listOf(
            "", "40794", "40794,2026,11,07,12,00", "40794,abcd,11,07,12,00,AAXX 07121 40794 41170 81205",
            "40794,2026,11,07,12,00,AAXX 07121 40794 4117 81205 10200", "40794,2026,11,07,12,00,BBXX 07121 40794 41170 81205 10200",
            "40794,2026,11,07,12,00,AAXX 07121", "40794,2026,11,07,12,00,AAXX 07121 40794 NIL 81205 10200"
        )
        for (l in bad) assertNull("line: '$l'", SynopClient.parseSynopLine(l))
    }

    @Test
    fun S6b_weirdGroups_doNotThrow() {
        val o = SynopClient.parseSynopLine("40794,2026,11,07,12,00,AAXX 07121 40794 11170 81205 1//// 2//// 6//// 7////=")
        assertNotNull(o); assertNull(o!!.tempC); assertNull(o.dewC); assertNull(o.ww); assertNull(o.precipMm)
    }

    /** تاریخ نامعتبر (ماه ۱۳) باید رد شود؛ Calendar به‌صورت پیش‌فرض lenient است. */
    @Test
    fun REG_S7_invalidDate_month13_rejected() {
        assertNull(SynopClient.parseSynopLine("40794,2026,13,40,25,00,AAXX 07121 40794 11170 81205 10200 76166="))
    }

    @Test
    fun S8_iR4_precipNotMeasured_null() {
        val o = SynopClient.parseSynopLine("40794,2026,11,07,12,00,AAXX 07121 40794 41170 81205 10200 60052=")!!
        assertNull(o.precipMm)
    }

    @Test
    fun S9_section2_thenSection3Precip_iR2() {
        val o = SynopClient.parseSynopLine("40794,2026,11,07,12,00,AAXX 07121 40794 21170 81205 10200 20120 76166 22200 00150 333 60071=")!!
        assertEquals(7.0, o.precipMm!!, 1e-9); assertEquals(6, o.precipPeriodH); assertEquals(61, o.ww)
    }

    @Test
    fun S10_trace990_and_tenths() {
        assertEquals(0.05, SynopClient.parseSynopLine("40794,2026,11,07,12,00,AAXX 07121 40794 11170 81205 69901=")!!.precipMm!!, 1e-9)
        assertEquals(0.3, SynopClient.parseSynopLine("40794,2026,11,07,12,00,AAXX 07121 40794 11170 81205 69931=")!!.precipMm!!, 1e-9)
    }

    @Test
    fun S11_negativeTemp_and_rhGroup29() {
        val o = SynopClient.parseSynopLine("40794,2026,01,07,03,00,AAXX 07031 40794 41170 81205 11015 21030 29085=")!!
        assertEquals(-1.5, o.tempC!!, 1e-9); assertEquals(-3.0, o.dewC!!, 1e-9); assertEquals(85.0, o.rh!!, 1e-9)
    }

    @Test
    fun S12_ww17_thunderNoPrecip() {
        val o = SynopClient.parseSynopLine("40794,2026,11,07,12,00,AAXX 07121 40794 41170 81205 71700=")!!
        assertTrue(o.isThunder); assertFalse(o.isRainNow)
    }

    @Test
    fun S13_ww21_recentRain_isYellowWhenFresh() {
        val o = SynopClient.parseSynopLine("40794,2026,11,07,12,00,AAXX 07121 40794 41170 81205 72166=")!!
        assertTrue(o.isRecentPrecip); assertFalse(o.isRainNow)
        val fresh = o.copy(timeUtcEpochSec = nowSec - 600)
        assertEquals(AlertTier.YELLOW, ForecastEngine.decideTier(radar(), fresh, dryRows(), dryBlock, dryBlock).tier)
    }

    @Test
    fun S14_crlfLines_parsed() {
        val o = SynopClient.parseOgimetResponse("40794,2026,11,07,12,00,AAXX 07121 40794 41170 81205 76166=\r\n")
        assertNotNull(o); assertEquals(61, o!!.ww)
    }

    // ================================================================ رادار

    @Test
    fun R1_eta_west50km_eastward30kmh() {
        val t = RadarAnalyzer.computeApproachEta(-50.0, 0.0, 30.0, 0.0, 2.0)
        assertEquals(CellApproachState.APPROACHING, t.state)
        // ورود در فاصلهٔ ۲۲km → (50-22)/30 h = 56 min
        assertEquals((56 * 0.7).toInt(), t.etaLow); assertEquals((56 * 1.3).toInt(), t.etaHigh)
    }

    @Test
    fun R2_adjustEta_nonApproachingUnchanged_and_exactBoundary() {
        val miss = RadarAnalyzer.TupleEta(CellApproachState.WILL_MISS, null, null, 30.0)
        assertEquals(miss, RadarAnalyzer.adjustEtaForFrameAge(miss, 20))
        val t = RadarAnalyzer.TupleEta(CellApproachState.APPROACHING, 10, 20, 5.0)
        assertEquals(t, RadarAnalyzer.adjustEtaForFrameAge(t, 0))
        val at = RadarAnalyzer.adjustEtaForFrameAge(t, 20)
        assertEquals(0, at.etaLow); assertEquals(0, at.etaHigh)
        val mid = RadarAnalyzer.adjustEtaForFrameAge(t, 15)
        assertEquals(0, mid.etaLow); assertEquals(5, mid.etaHigh)
    }

    @Test
    fun R3_insideNow_and_zeroSpeed() {
        assertEquals(CellApproachState.INSIDE_NOW, RadarAnalyzer.computeApproachEta(10.0, 10.0, 0.0, 0.0, 1.0).state)
        val z = RadarAnalyzer.computeApproachEta(40.0, 0.0, 0.0, 0.0, 1.0)
        assertEquals(CellApproachState.NOT_APPROACHING, z.state); assertEquals(40.0, z.dMinKm!!, 1e-9)
    }

    @Test
    fun R4_frozenThreshold_19vs20WetPixels() {
        fun gridWith(n: Int) = IntArray(512 * 512) { -1 }.also { g -> for (i in 0 until n) g[(100 + i) * 512 + 100] = 30 }
        val g19 = gridWith(19); val g20 = gridWith(20)
        val f19 = RadarFetch(listOf(RadarFrame(nowSec - 600, g19.clone()), RadarFrame(nowSec, g19.clone())), 5, true)
        val f20 = RadarFetch(listOf(RadarFrame(nowSec - 600, g20.clone()), RadarFrame(nowSec, g20.clone())), 5, true)
        assertTrue(RadarAnalyzer.analyzeNowcast(f19, nowSec = nowSec).available)
        assertFalse(RadarAnalyzer.analyzeNowcast(f20, nowSec = nowSec).available)
    }

    @Test
    fun R5_frameAge30available_31not() {
        val g = IntArray(512 * 512) { -1 }
        assertTrue(RadarAnalyzer.analyzeNowcast(RadarFetch(listOf(RadarFrame(nowSec, g)), 30, true), nowSec = nowSec).available)
        assertFalse(RadarAnalyzer.analyzeNowcast(RadarFetch(listOf(RadarFrame(nowSec, g)), 31, true), nowSec = nowSec).available)
        assertFalse(RadarAnalyzer.analyzeNowcast(RadarFetch(emptyList(), 0, true), nowSec = nowSec).available)
        assertFalse(RadarAnalyzer.analyzeNowcast(null, nowSec = nowSec).available)
    }

    /** ETA از «الان»: همان صحنه با عمر فریم ۰ و ۱۰ دقیقه باید دقیقاً ۱۰ دقیقه اختلاف داشته باشد. */
    @Test
    fun R6_trackedCell_etaCountedFromNow() {
        val frames = listOf(-70.0, -65.0, -60.0).mapIndexed { i, x ->
            RadarFrame(nowSec - (2 - i) * 600L, blobGrid(x, 0.0, 4.0, 40))
        }
        val r0 = RadarAnalyzer.analyzeNowcast(RadarFetch(frames, 0, true), nowSec = nowSec)
        val r10 = RadarAnalyzer.analyzeNowcast(RadarFetch(frames, 10, true), nowSec = nowSec)
        val a0 = r0.firstArrival!!; val a10 = r10.firstArrival!!
        assertEquals("radar_track", a0.motionSource)
        assertEquals(30.0, a0.speedKmh!!, 5.0)
        assertEquals(90.0, a0.moveDirectionToDeg!!, 10.0)
        assertEquals(a0.etaMinLow!! - 10, a10.etaMinLow)
        assertEquals(a0.etaMinHigh!! - 10, a10.etaMinHigh)
        val v = ForecastEngine.decideTier(r10, null, dryRows(), dryBlock, dryBlock)
        assertTrue(v.tier == AlertTier.ORANGE || v.tier == AlertTier.RED)
    }

    @Test
    fun R7_clutterFilter_notAppliedWhenModelUnknown() {
        val g = blobGrid(0.0, 0.0, 3.0, 30)
        val f = RadarFetch(listOf(RadarFrame(nowSec - 1200, g.clone()), RadarFrame(nowSec - 600, g.clone()),
            RadarFrame(nowSec, g.clone().also { it[0] = 25 })), 5, true)
        val r = RadarAnalyzer.analyzeNowcast(f, modelDryNow = null, nowSec = nowSec)
        assertEquals(0, r.clutterPixelsRemoved); assertEquals(30, r.maxDbzInTarget)
    }

    @Test
    fun R8_stationaryRainOverTarget_removedAsClutter_whenModelDry() {
        // رفتار فعلی را ثبت می‌کند: باران ساکن ۲۰ دقیقه‌ای با مدلِ خشک «کلاتر» حذف می‌شود → اکو صفر
        val g = blobGrid(0.0, 0.0, 3.0, 30)
        val f = RadarFetch(listOf(RadarFrame(nowSec - 1200, g.clone()), RadarFrame(nowSec - 600, g.clone()),
            RadarFrame(nowSec, g.clone().also { it[0] = 25 })), 5, true)
        val r = RadarAnalyzer.analyzeNowcast(f, modelDryNow = true, nowSec = nowSec)
        assertTrue(r.clutterPixelsRemoved > 0); assertEquals(-1, r.maxDbzInTarget)
    }

    @Test
    fun R9_universalBlue_edges() {
        assertEquals(-1, UniversalBlue.dbz(0x00FFEE00))           // شفاف
        assertEquals(-1, UniversalBlue.dbz(0x80FFEE00.toInt()))   // نیمه‌شفاف
        assertEquals(35, UniversalBlue.dbz(0xFFFFEE00.toInt()))
        assertEquals(65, UniversalBlue.dbz(0xFFFFFFFF.toInt()))
        assertEquals(-1, UniversalBlue.dbz(0xFF00FF00.toInt()))   // سبز خالص — در جدول نیست
    }

    // ================================================================ blockStats / percentile

    @Test
    fun B1_emptyInputs_allNull() {
        val n = ForecastEngine.BlockStats(null, null, null, null)
        assertEquals(n, ForecastEngine.blockStats(emptyList(), 0, 10, 0.2, 10.0))
        assertEquals(n, ForecastEngine.blockStats(listOf(null, null), 0, 10, 0.2, 10.0))
        assertEquals(n, ForecastEngine.blockStats(listOf(EnsembleSet(longArrayOf(5), emptyArray())), 0, 10, 0.2, 10.0))
        assertEquals(n, ForecastEngine.blockStats(listOf(EnsembleSet(longArrayOf(100), arrayOf(doubleArrayOf(1.0)))), 0, 10, 0.2, 10.0))
        assertEquals(n, ForecastEngine.blockStats(listOf(EnsembleSet(longArrayOf(5), arrayOf(doubleArrayOf(Double.NaN)))), 0, 10, 0.2, 10.0))
    }

    @Test
    fun B2_nanAndShortMembersSkipped_boundsHalfOpen() {
        val e = EnsembleSet(longArrayOf(0, 5, 10), arrayOf(
            doubleArrayOf(9.0, 0.1, 0.2), doubleArrayOf(0.0, Double.NaN, 1.0), doubleArrayOf(0.0, 0.0)))
        val b = ForecastEngine.blockStats(listOf(e), 0, 10, 0.2, 10.0)
        // فقط عضو ۱ معتبر است و ساعت t=0 بیرون پنجره است → مجموع 0.3
        assertEquals(1.0, b.pWet!!, 1e-9); assertEquals(0.3, b.p50!!, 1e-9); assertEquals(0.0, b.pHeavy6h!!, 1e-9)
    }

    @Test
    fun B3_percentile_edges() {
        assertEquals(0.0, ForecastEngine.percentile(emptyList(), 0.5), 0.0) // ⚠ مقدار ساختگی؛ فراخواننده‌ها محافظت می‌کنند
        assertEquals(7.0, ForecastEngine.percentile(listOf(7.0), 0.9), 0.0)
        assertEquals(1.0, ForecastEngine.percentile(listOf(3.0, 1.0, 2.0), 0.0), 0.0)
        assertEquals(3.0, ForecastEngine.percentile(listOf(3.0, 1.0, 2.0), 1.0), 0.0)
        assertEquals(2.8, ForecastEngine.percentile(listOf(3.0, 1.0, 2.0), 0.9), 1e-9)
    }

    // ================================================================ Calibration

    private val goodJson = """{"schema":1,"model":"ecmwf_ifs025","window_hours":6,"wet_threshold_mm":0.2,
        "months":[1,2,3,4,5,10,11,12],
        "skill_cross_validated":{"brier_raw_fraction":0.12,"brier_calibrated":0.08,"brier_climatology":0.10},
        "prob_table":{"x":[0.0,0.5,1.0],"y":[0.02,0.4,0.9]},
        "amount_qm":{"fc":[0.1,1.0,5.0],"obs":[0.2,1.5,6.0]}}"""

    @Test
    fun C1_badJsonVariants_disableWithoutThrow() {
        for (bad in listOf("", "{not json", "[]", "null", """{"schema":2}""", """{"schema":1,"model":"gfs"}""",
            """{"schema":1,"model":"ecmwf_ifs"}""",
            """{"schema":1,"model":"ecmwf_ifs","skill_cross_validated":{"brier_raw_fraction":null,"brier_calibrated":0.1,"brier_climatology":0.2}}""",
            """{"schema":1,"model":"ecmwf_ifs","skill_cross_validated":{"brier_raw_fraction":0.2,"brier_calibrated":0.2,"brier_climatology":0.2},"prob_table":{"x":[0,1],"y":[0,1]}}""",
            """{"schema":1,"model":"ecmwf_ifs","skill_cross_validated":{"brier_raw_fraction":0.2,"brier_calibrated":0.1,"brier_climatology":0.2},"prob_table":{"x":[0,0.5,1],"y":[0,1]}}""",
            """{"schema":1,"model":"ecmwf_ifs","skill_cross_validated":{"brier_raw_fraction":0.2,"brier_calibrated":0.1,"brier_climatology":0.2},"prob_table":{"x":["a","b"],"y":[0,1]}}""")) {
            Calibration.loadFromJson(goodJson); assertTrue(Calibration.enabled)
            Calibration.loadFromJson(bad)
            assertFalse("should disable for: $bad", Calibration.enabled)
            assertFalse(Calibration.qmEnabled)
            assertEquals(0.37, Calibration.calibrateProb(0.37), 1e-12)
        }
    }

    @Test
    fun REG_C2_probTableLastYAboveOne_rejected() {
        Calibration.loadFromJson(goodJson.replace("[0.02,0.4,0.9]", "[0.02,0.4,1.5]"))
        assertFalse("y آخر 1.5 است ولی جدول پذیرفته شد", Calibration.enabled)
    }

    @Test
    fun C3_qmObsDecreasing_qmOffProbStillOn() {
        Calibration.loadFromJson(goodJson.replace("[0.2,1.5,6.0]", "[0.2,1.5,1.0]"))
        assertTrue(Calibration.enabled); assertFalse(Calibration.qmEnabled)
        assertEquals(3.0, Calibration.calibrateAmount(3.0), 1e-12)
    }

    @Test
    fun C4_windowHoursClamped() {
        Calibration.loadFromJson(goodJson.replace("\"window_hours\":6", "\"window_hours\":0"))
        assertEquals(1, Calibration.windowHours)
        Calibration.loadFromJson(goodJson.replace("\"window_hours\":6", "\"window_hours\":99"))
        assertEquals(24, Calibration.windowHours)
        Calibration.loadFromJson(goodJson)
    }

    @Test
    fun C5_loadFromContext_missingAsset_noThrow() {
        Calibration.load(android.content.ContextWrapper(null))
        assertTrue(Calibration.loaded)
    }

    // ================================================================ زمان ایران

    @Test
    fun I1_iranTime_rollover_noDst() {
        assertEquals("2026-11-07 01:30", ForecastEngine.utcToIran("2026-11-06T22:00"))
        assertEquals("2026-03-22 00:00", ForecastEngine.utcToIran("2026-03-21T20:30"))
        assertEquals("2026-06-15 15:30", ForecastEngine.utcToIran("2026-06-15T12:00")) // بدون ساعت تابستانی
        assertEquals("2026-11-06 15:30", ForecastEngine.utcToIran("2026-11-06T12:00:00Z"))
        assertEquals("2026-11-06 15:30", ForecastEngine.utcToIran("2026-11-06 12:00"))
        assertEquals("1970-01-01 03:30", ForecastEngine.epochToIran(0))
    }

    @Test
    fun I2_iranTime_nullBlankGarbage() {
        assertEquals("", ForecastEngine.utcToIran(null))
        assertEquals("", ForecastEngine.utcToIran("  "))
        assertEquals("abc", ForecastEngine.utcToIran("abc"))
    }

    @Test
    fun REG_I3_invalidDate_notSilentlyShifted() {
        val r = ForecastEngine.utcToIran("2026-02-30T12:00")
        assertFalse("تاریخ نامعتبر به $r تبدیل شد", r.startsWith("2026-03-02"))
    }

    // ================================================================ GeoUtils

    @Test
    fun G1_dir16_boundaries() {
        assertEquals("شمال", GeoUtils.dir16Fa(0.0))
        assertEquals("شمال", GeoUtils.dir16Fa(11.24))
        assertEquals("شمال‌شمال‌شرق", GeoUtils.dir16Fa(11.25))
        assertEquals("شمال", GeoUtils.dir16Fa(348.75))
        assertEquals("شمال", GeoUtils.dir16Fa(360.0))
        assertEquals("شمال", GeoUtils.dir16Fa(-10.0))
        assertEquals("غرب", GeoUtils.dir16Fa(-90.0))
        assertEquals("شرق", GeoUtils.dir16Fa(450.0))
    }

    @Test
    fun G2_distance_bearing_roundtrip() {
        val c = RegionConfig.CENTER
        assertEquals(0.0, GeoUtils.haversineKm(c.lat, c.lon, c.lat, c.lon), 1e-9)
        for (b in listOf(0.0, 45.0, 90.0, 225.0, 315.0)) {
            val (la, lo) = GeoUtils.destination(c.lat, c.lon, b, 20.0)
            assertEquals(20.0, GeoUtils.haversineKm(c.lat, c.lon, la, lo), 1e-6)
            assertEquals(b, GeoUtils.bearingDeg(c.lat, c.lon, la, lo), 0.05)
            val (x, y) = GeoUtils.toLocalKm(c.lat, c.lon, la, lo)
            assertEquals(20.0, kotlin.math.hypot(x, y), 1e-6)
        }
    }

    @Test
    fun G3_neighbourhood_17points_ringDistances() {
        val pts = RegionConfig.NEIGHBOURHOOD
        assertEquals(17, pts.size)
        val c = RegionConfig.CENTER
        pts.drop(1).take(8).forEach { assertEquals(10.0, GeoUtils.haversineKm(c.lat, c.lon, it.lat, it.lon), 1e-6) }
        pts.drop(9).forEach { assertEquals(20.0, GeoUtils.haversineKm(c.lat, c.lon, it.lat, it.lon), 1e-6) }
    }

    @Test
    fun G4_targetEdge_insideVsOutside() {
        // لبهٔ دایره: سلول نقطه‌ای (شعاع ۰) در ۱۹.۹۹ داخل، در ۲۰.۰۱ بیرون
        assertEquals(CellApproachState.INSIDE_NOW, RadarAnalyzer.computeApproachEta(19.99, 0.0, 0.0, 0.0, 0.0).state)
        assertEquals(CellApproachState.NOT_APPROACHING, RadarAnalyzer.computeApproachEta(20.01, 0.0, 0.0, 0.0, 0.0).state)
    }

    @Test
    fun G5_safiabadIsInsideTargetCircle() {
        val s = RegionConfig.SYNOP_STATIONS.first(); val c = RegionConfig.CENTER
        assertTrue(GeoUtils.haversineKm(c.lat, c.lon, s.lat, s.lon) < 5.0)
    }
}
