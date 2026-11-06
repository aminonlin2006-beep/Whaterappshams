package com.amin.weather.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    val isRecentPrecip: Boolean
)

object SynopClient {
    private var cachedObs: SynopObs? = null
    private var lastFetchTimeEpochSec: Long = 0

    fun fetchLatestSynop(cacheDir: File? = null): SourceResult<SynopObs> {
        val nowSec = System.currentTimeMillis() / 1000
        if (cachedObs != null && (nowSec - lastFetchTimeEpochSec) < Tuning.SYNOP_MIN_INTERVAL_MIN * 60) {
            return SourceResult("ogimet/synop/40794", SourceStatus.OK, cachedObs, "From in-memory cache")
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
            SourceResult("ogimet/synop/40794", SourceStatus.OK, parsed)
        } else {
            if (cachedObs != null) {
                SourceResult("ogimet/synop/40794", SourceStatus.PARTIAL, cachedObs, "Parsing failed, serving stale cache")
            } else {
                SourceResult("ogimet/synop/40794", SourceStatus.FAILED, null, "No valid SYNOP record found")
            }
        }
    }

    fun parseOgimetResponse(responseBody: String): SynopObs? {
        val lines = responseBody.lines().filter { it.contains("40794,") && !it.contains("NIL") }
        if (lines.isEmpty()) return null

        // Parse latest line
        val lastLine = lines.last()
        return parseSynopLine(lastLine)
    }

    fun parseSynopLine(line: String): SynopObs? {
        val parts = line.split(",")
        if (parts.size < 7) return null

        val yyyy = parts[1].trim().toIntOrNull() ?: return null
        val mm = parts[2].trim().toIntOrNull() ?: return null
        val dd = parts[3].trim().toIntOrNull() ?: return null
        val hh = parts[4].trim().toIntOrNull() ?: return null
        val min = parts[5].trim().toIntOrNull() ?: return null

        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(yyyy, mm - 1, dd, hh, min, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val timeSec = cal.timeInMillis / 1000

        val synopText = parts.subList(6, parts.size).joinToString(",").trim().trimEnd('=')
        val groups = synopText.split("\\s+".toRegex())

        var iR = 4
        var iX = 2
        var tempC: Double? = null
        var dewC: Double? = null
        var rh: Double? = null
        var precipMm: Double? = null
        var precipPeriodH: Int? = null
        var ww: Int? = null
        var isRainNow = false
        var isThunder = false
        var isRecentPrecip = false

        var inSection3 = false

        for (g in groups) {
            if (g == "333") {
                inSection3 = true
                continue
            }

            if (!inSection3) {
                // iRiXhVV group (usually 7th or after AAXX YYGGiw IIiii)
                if (g.length == 5 && g[0].isDigit() && g[1].isDigit()) {
                    val possibleIR = g[0] - '0'
                    val possibleIX = g[1] - '0'
                    if (possibleIR in 0..4 && possibleIX in 1..7) {
                        iR = possibleIR
                        iX = possibleIX
                        if (iR == 3) precipMm = 0.0
                    }
                }

                // 1snTTT
                if (g.length == 5 && g.startsWith("1")) {
                    val sn = g[1] - '0'
                    val ttt = g.substring(2).toDoubleOrNull()
                    if (ttt != null) {
                        tempC = (if (sn == 1) -ttt else ttt) / 10.0
                    }
                }

                // 2snTdTdTd
                if (g.length == 5 && g.startsWith("2")) {
                    val sn = g[1] - '0'
                    val ttt = g.substring(2).toDoubleOrNull()
                    if (ttt != null) {
                        if (sn == 9) {
                            rh = ttt
                        } else {
                            dewC = (if (sn == 1) -ttt else ttt) / 10.0
                        }
                    }
                }

                // 6RRRtR in Section 1
                if (g.length == 5 && g.startsWith("6") && (iR == 0 || iR == 1)) {
                    val (pMm, pH) = parseGroup6(g)
                    precipMm = pMm
                    precipPeriodH = pH
                }

                // 7wwW1W2 in Section 1
                if (g.length == 5 && g.startsWith("7") && (iX == 1 || iX == 4 || iX == 7)) {
                    val code = g.substring(1, 3).toIntOrNull()
                    if (code != null) {
                        ww = code
                        if (code in 50..69 || code in 80..84 || code in 91..99) isRainNow = true
                        if (code in 91..99 || code == 17) isThunder = true
                        if (code in 20..29) isRecentPrecip = true
                    }
                }
            } else {
                // In Section 3
                if (g.length == 5 && g.startsWith("6") && iR == 2) {
                    val (pMm, pH) = parseGroup6(g)
                    precipMm = pMm
                    precipPeriodH = pH
                }
            }
        }

        if (rh == null && tempC != null && dewC != null) {
            val e = 6.112 * exp((17.67 * tempC) / (tempC + 243.5))
            val ew = 6.112 * exp((17.67 * dewC) / (dewC + 243.5))
            rh = ((ew / e) * 100.0).coerceIn(0.0, 100.0)
        }

        return SynopObs(
            timeUtcEpochSec = timeSec,
            tempC = tempC,
            dewC = dewC,
            rh = rh,
            precipMm = precipMm,
            precipPeriodH = precipPeriodH,
            ww = ww,
            isRainNow = isRainNow,
            isThunder = isThunder,
            isRecentPrecip = isRecentPrecip
        )
    }

    private fun parseGroup6(g: String): Pair<Double?, Int?> {
        val rrr = g.substring(1, 4)
        val tr = g.last()

        val mm = when (rrr) {
            "000" -> 0.0
            "990" -> 0.05 // trace
            else -> {
                val num = rrr.toDoubleOrNull()
                if (num != null) {
                    if (num in 991.0..999.0) (num - 990.0) / 10.0 else num
                } else null
            }
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
