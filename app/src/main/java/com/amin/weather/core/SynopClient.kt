package com.amin.weather.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONObject
import kotlin.math.exp

data class SynopObs(
    val timeUtcEpochSec: Long,
    val tempC: Double?,
    val dewC: Double?,
    val rh: Double?,
    val precipMm: Double?,
    val precipPeriodH: Int?,
    val ww: Int?,
    val isRainNow: Boolean,
    val isThunder: Boolean,
    val isRecentPrecip: Boolean,
    val iX: Int? = null
)

object SynopClient {
    private var cachedObs: SynopObs? = null
    private var lastFetchTimeEpochSec: Long = 0

    fun fetchLatestSynop(cacheDir: File? = null): SourceResult<SynopObs> {
        val nowSec = System.currentTimeMillis() / 1000

        if (cachedObs == null && cacheDir != null) {
            readDiskCache(cacheDir)
        }

        if (cachedObs != null && (nowSec - lastFetchTimeEpochSec) < Tuning.SYNOP_MIN_INTERVAL_MIN * 60) {
            return SourceResult("ogimet/synop/40794", SourceStatus.OK, cachedObs, "From cache")
        }

        val sdf = SimpleDateFormat("yyyyMMddHHmm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val endStr = sdf.format(Date(nowSec * 1000))
        val beginStr = sdf.format(Date((nowSec - 24 * 3600) * 1000))

        val url = "https://www.ogimet.com/cgi-bin/getsynop?block=40794&begin=$beginStr&end=$endStr"
        val resp = Http.get(url)

        if (!resp.isSuccess || resp.bodyString.isNullOrBlank()) {
            return if (cachedObs != null) {
                SourceResult("ogimet/synop/40794", SourceStatus.PARTIAL, cachedObs, "Fetch failed, serving stale cache: ${resp.error}")
            } else {
                SourceResult("ogimet/synop/40794", SourceStatus.FAILED, null, resp.error ?: "Ogimet unavailable")
            }
        }

        val parsed = parseOgimetResponse(resp.bodyString!!)
        return if (parsed != null) {
            cachedObs = parsed
            lastFetchTimeEpochSec = nowSec
            if (cacheDir != null) {
                writeDiskCache(cacheDir, parsed, nowSec)
            }
            SourceResult("ogimet/synop/40794", SourceStatus.OK, parsed)
        } else {
            if (cachedObs != null) {
                SourceResult("ogimet/synop/40794", SourceStatus.PARTIAL, cachedObs, "Parsing failed, serving stale cache")
            } else {
                SourceResult("ogimet/synop/40794", SourceStatus.FAILED, null, "No valid SYNOP record found")
            }
        }
    }

    private fun readDiskCache(cacheDir: File) {
        try {
            val file = File(cacheDir, "synop_cache.json")
            if (file.exists()) {
                val json = JSONObject(file.readText())
                val timeSec = json.getLong("timeUtcEpochSec")
                val tempC = if (json.isNull("tempC")) null else json.getDouble("tempC")
                val dewC = if (json.isNull("dewC")) null else json.getDouble("dewC")
                val rh = if (json.isNull("rh")) null else json.getDouble("rh")
                val precipMm = if (json.isNull("precipMm")) null else json.getDouble("precipMm")
                val precipPeriodH = if (json.isNull("precipPeriodH")) null else json.getInt("precipPeriodH")
                val ww = if (json.isNull("ww")) null else json.getInt("ww")
                val isRainNow = json.getBoolean("isRainNow")
                val isThunder = json.getBoolean("isThunder")
                val isRecentPrecip = json.getBoolean("isRecentPrecip")
                val iX = if (json.isNull("iX") || !json.has("iX")) null else json.getInt("iX")

                cachedObs = SynopObs(timeSec, tempC, dewC, rh, precipMm, precipPeriodH, ww, isRainNow, isThunder, isRecentPrecip, iX)
                lastFetchTimeEpochSec = json.optLong("lastFetchTimeEpochSec", timeSec)
            }
        } catch (_: Exception) {}
    }

    private fun writeDiskCache(cacheDir: File, obs: SynopObs, fetchTimeSec: Long) {
        try {
            val file = File(cacheDir, "synop_cache.json")
            val json = JSONObject().apply {
                put("timeUtcEpochSec", obs.timeUtcEpochSec)
                put("tempC", obs.tempC ?: JSONObject.NULL)
                put("dewC", obs.dewC ?: JSONObject.NULL)
                put("rh", obs.rh ?: JSONObject.NULL)
                put("precipMm", obs.precipMm ?: JSONObject.NULL)
                put("precipPeriodH", obs.precipPeriodH ?: JSONObject.NULL)
                put("ww", obs.ww ?: JSONObject.NULL)
                put("isRainNow", obs.isRainNow)
                put("isThunder", obs.isThunder)
                put("isRecentPrecip", obs.isRecentPrecip)
                put("iX", obs.iX ?: JSONObject.NULL)
                put("lastFetchTimeEpochSec", fetchTimeSec)
            }
            file.writeText(json.toString())
        } catch (_: Exception) {}
    }

    fun parseOgimetResponse(responseBody: String): SynopObs? {
        val lines = responseBody.lines().filter { it.contains("40794,") && !it.contains("NIL") }
        if (lines.isEmpty()) return null

        val lastLine = lines.last()
        return parseSynopLine(lastLine)
    }

    fun parseSynopLine(line: String): SynopObs? {
        val head = line.split(",", limit = 7)
        if (head.size < 7) return null
        val y = head[1].trim().toIntOrNull() ?: return null
        val mo = head[2].trim().toIntOrNull() ?: return null
        val d = head[3].trim().toIntOrNull() ?: return null
        val hh = head[4].trim().toIntOrNull() ?: return null
        val mi = head[5].trim().toIntOrNull() ?: return null
        if (y !in 2000..2100 || mo !in 1..12 || d !in 1..31 || hh !in 0..23 || mi !in 0..59) return null
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            isLenient = false
            clear(); set(y, mo - 1, d, hh, mi, 0)
        }
        val timeSec = try { cal.timeInMillis / 1000 } catch (_: Exception) { return null }  // مثلاً ۳۱ آبان نامعتبر

        val toks = head[6].replace("=", " ").trim().split(Regex("\\s+"))
        if (toks.size < 5 || toks[0] != "AAXX" || toks.any { it == "NIL" }) return null
        // toks[1]=YYGGiw  toks[2]=IIiii  toks[3]=iRiXhVV  toks[4]=Nddff
        val irix = toks[3]
        if (irix.length != 5) return null
        val iR = irix[0].digitToIntOrNull()
        val iX = irix[1].digitToIntOrNull()

        var i = 5
        if (toks[4].length == 5 && toks[4].substring(3, 5) == "99" && i < toks.size && toks[i].startsWith("00")) i++  // 00fff

        var section = 1
        var tempC: Double? = null; var dewC: Double? = null; var rh: Double? = null
        var p1: Pair<Double?, Int?>? = null; var p3: Pair<Double?, Int?>? = null
        var ww: Int? = null

        while (i < toks.size) {
            val g = toks[i]; i++
            if (g == "333") { section = 3; continue }
            if (g == "555" || g == "444" || g.startsWith("222")) { section = 9; continue }
            if (section == 9 || g.length != 5) continue
            if (section == 1) {
                when (g[0]) {
                    '1' -> if (g[1] == '0' || g[1] == '1') g.substring(2).toIntOrNull()?.let {
                        tempC = (if (g[1] == '0') it else -it) / 10.0 }
                    '2' -> when (g[1]) {
                        '0', '1' -> g.substring(2).toIntOrNull()?.let { dewC = (if (g[1] == '0') it else -it) / 10.0 }
                        '9' -> g.substring(2).toIntOrNull()?.let { rh = it.toDouble() }
                    }
                    '6' -> p1 = parseGroup6(g)
                    '7' -> if (iX == 1 || iX == 4 || iX == 7) ww = g.substring(1, 3).toIntOrNull()
                }
            } else if (section == 3 && g[0] == '6') {
                p3 = parseGroup6(g)
            }
        }

        val (precipMm, periodH) = when (iR) {
            0 -> (p1 ?: p3) ?: Pair(null, null)
            1 -> p1 ?: Pair(null, null)
            2 -> p3 ?: Pair(null, null)
            3 -> Pair(0.0, null)      // نباریده؛ دوره در گزارش نیامده
            else -> Pair(null, null)  // 4 یا نامعلوم: اندازه‌گیری نشده
        }

        val code = ww
        val isAuto = iX == 7          // جدول wawa
        val isRainNow = code != null && if (isAuto) (code in 40..69 || code in 80..89)
                                       else (code in 50..69 || code in 80..90 || code in 91..99)
        val isThunder = code != null && if (isAuto) code in 90..99 else (code in 91..99 || code == 17)
        val isRecentPrecip = code != null && !isAuto && code in 20..29

        if (rh == null && tempC != null && dewC != null) {
            val e = 6.112 * exp(17.67 * tempC!! / (tempC!! + 243.5))
            val ew = 6.112 * exp(17.67 * dewC!! / (dewC!! + 243.5))
            rh = (ew / e * 100.0).coerceIn(0.0, 100.0)
        }
        return SynopObs(timeSec, tempC, dewC, rh, precipMm, periodH, ww, isRainNow, isThunder, isRecentPrecip, iX)
    }

    private fun parseGroup6(g: String): Pair<Double?, Int?> {
        if (g.length != 5) return Pair(null, null)
        val rrr = g.substring(1, 4)
        if (rrr == "///") return Pair(null, null)
        val tr = g.last()

        val num = rrr.toDoubleOrNull() ?: return Pair(null, null)
        if (num > 988.0 && num < 990.0) return Pair(null, null)

        val mm = when {
            rrr == "000" -> 0.0
            rrr == "990" -> 0.05
            num in 991.0..999.0 -> (num - 990.0) / 10.0
            else -> num
        }

        val periodH = when (tr) {
            '1' -> 6
            '2' -> 12
            '3' -> 18
            '4' -> 24
            '5' -> 1
            '6' -> 2
            '7' -> 3
            '8' -> 9
            '9' -> 15
            else -> null
        }

        return Pair(mm, periodH)
    }
}
