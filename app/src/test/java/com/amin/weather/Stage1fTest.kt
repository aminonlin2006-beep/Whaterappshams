package com.amin.weather

import com.amin.weather.core.*
import org.junit.Assert.*
import org.junit.Test

/** تست‌های اصلاحیهٔ ۱-و (زبان ساده و عنوان هشدار). */
class Stage1fTest {
    private val now = 1_794_085_200L   // 2026-11-07 21:00 UTC = 00:30 بامداد ۸ نوامبر به وقت ایران

    private fun radar(available: Boolean = true, maxDbz: Int = -1, wet: Double = 0.0) =
        RadarNowcastResult(available, 5, true, maxDbz, wet, 0.0)

    @Test fun headline_synopRain_saysRainingNow() {
        val s = SynopObs(now - 600, 17.0, 15.0, 90.0, null, null, 61, true, false, false, 1)
        val v = ForecastEngine.decideTier(radar(), s, emptyList(), null, null, nowSec = now)
        assertEquals(AlertTier.ORANGE, v.tier)
        assertEquals("همین الان در منطقه باران می‌بارد", v.headline)
    }

    @Test fun headline_probOnly_saysLikely() {
        val rows = List(12) { ForecastRow(0L, "", "", 70.0, 70.0, 0.5, 1.0, 20.0, dewC = 10.0, rh = 50.0, cloud = 90.0, gustKmh = 10.0, convScore = 0.0) }
        val v = ForecastEngine.decideTier(radar(), null, rows, ForecastEngine.BlockStats(0.7, 0.0, 1.0, 0.0), null, nowSec = now)
        assertEquals(AlertTier.ORANGE, v.tier)
        assertEquals("بارش در ۶ ساعت آینده محتمل است", v.headline)
    }

    @Test fun headline_defaultsToTierTitle() {
        assertEquals(AlertTier.GREEN.title, VerdictInfo(AlertTier.GREEN, "x").headline)
    }

    @Test fun noDbzInVerdictText() {
        val v = ForecastEngine.decideTier(radar(maxDbz = 50, wet = 0.3), null, emptyList(), null, null, nowSec = now)
        assertFalse(v.text, v.text.contains("dBZ"))
    }

    @Test fun intensityWords() {
        assertEquals("سبک", PlainText.intensityFa(22))
        assertEquals("متوسط", PlainText.intensityFa(32))
        assertEquals("شدید", PlainText.intensityFa(40))
        assertEquals("بسیار شدید", PlainText.intensityFa(50))
        assertEquals("خیلی ضعیف", PlainText.intensityFa(null))
    }

    @Test fun latinDigits() {
        assertEquals(25.5, PlainText.toLatinDigits("۲۵٫۵").toDouble(), 1e-9)
        assertEquals(18.0, PlainText.toLatinDigits("١٨").toDouble(), 1e-9)
    }

    @Test fun hourLabels_todayTomorrow() {
        // now = 00:30 ایران ۸ نوامبر؛ ساعت ۰۲:۳۰ همان روز = امروز
        assertEquals("۰۲:۳۰", PlainText.hourLabelFa(now + 2 * 3600, now))
        // ۲۴ ساعت بعد = فردا
        assertEquals("فردا ۰۰:۳۰", PlainText.hourLabelFa(now + 24 * 3600, now))
    }

    @Test fun sourceNamesPersian() {
        assertEquals("رادار", PlainText.sourceNameFa("rainviewer/radar"))
        assertEquals("قطع", PlainText.statusFa(SourceStatus.FAILED))
    }
}
