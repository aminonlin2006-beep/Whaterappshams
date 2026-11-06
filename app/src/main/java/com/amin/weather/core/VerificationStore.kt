package com.amin.weather.core

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.TimeZone

data class LeadStats(
    val leadBucket: Int, // 6, 12, or 24
    val totalForecasts: Int = 0,
    val evaluatedForecasts: Int = 0,
    val wetEventsCount: Int = 0,
    val brierScore: Double? = null,
    val pod: Double? = null,
    val far: Double? = null,
    val amountBiasRatio: Double? = null
)

data class VerificationStats(
    val totalRecords: Int = 0,
    val isCalibrationEnabled: Boolean = false,
    val calibrationStatusText: String = "",
    val lead6h: LeadStats = LeadStats(6),
    val lead12h: LeadStats = LeadStats(12),
    val lead24h: LeadStats = LeadStats(24)
)

object VerificationStore {
    private const val FILE_NAME = "verify.jsonl"

    fun recordStandardWindows(
        context: Context,
        nowEpochSec: Long,
        rows: List<ForecastRow>,
        ensList: List<EnsembleSet?>
    ) {
        if (rows.isEmpty()) return
        try {
            val file = File(context.filesDir, FILE_NAME)
            val records = readRecords(file)

            val nowMs = System.currentTimeMillis()
            // Find 6-hour windows ending at 00, 06, 12, 18 UTC within next 24h
            val futureUtcTimes = getNextSynopWindowEnds(nowEpochSec)

            for (windowEndSec in futureUtcTimes) {
                val leadH = ((windowEndSec - nowEpochSec) / 3600L).toInt()
                val leadBucket = when {
                    leadH <= 6 -> 6
                    leadH <= 12 -> 12
                    leadH <= 24 -> 24
                    else -> continue
                }

                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                    timeInMillis = windowEndSec * 1000L
                }
                val hourUtc = cal.get(Calendar.HOUR_OF_DAY)

                val periodsToRecord = mutableListOf(6)
                if (hourUtc == 6 || hourUtc == 18) {
                    periodsToRecord.add(12)
                }

                for (periodH in periodsToRecord) {
                    val windowStartSec = windowEndSec - periodH * 3600L
                    val subRows = rows.filter { it.timeEpochSec > windowStartSec && it.timeEpochSec <= windowEndSec }
                    if (subRows.isEmpty()) continue

                    val bs = ForecastEngine.blockStats(ensList, windowStartSec, windowEndSec, Tuning.WET_HOUR_MM, Tuning.HEAVY_6H_MM)
                    val maxPointProb = subRows.mapNotNull { it.probPoint?.let { p -> p / 100.0 } }.maxOrNull()
                    val pBlock = when {
                        bs.pWet != null && maxPointProb != null -> Math.max(bs.pWet, maxPointProb)
                        bs.pWet != null -> bs.pWet
                        maxPointProb != null -> maxPointProb
                        else -> null
                    } ?: continue

                    val amountP50 = bs.p50 ?: subRows.mapNotNull { it.amountP50 }.let { if (it.isNotEmpty()) it.sum() else null }

                    val key = "$windowEndSec:$periodH:$leadBucket"
                    val entry = JSONObject().apply {
                        put("key", key)
                        put("recordedAtMs", nowMs)
                        put("windowEndEpochSec", windowEndSec)
                        put("periodH", periodH)
                        put("leadBucket", leadBucket)
                        put("probBlock", pBlock)
                        put("amountP50", amountP50 ?: 0.0)
                        put("observedWet", JSONObject.NULL)
                        put("observedAmount", JSONObject.NULL)
                    }

                    records[key] = entry
                }
            }

            writeRecords(file, records)
        } catch (_: Exception) {}
    }

    fun recordForecast(context: Context, targetEpochSec: Long, probPoint: Double?, amountP50: Double?) {
        // Kept for backward compatibility
    }

    fun evaluateWithSynop(context: Context, synop: SynopObs) {
        if (synop.precipMm == null || synop.precipPeriodH !in Tuning.VERIFY_WINDOWS_H) return
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return

            val records = readRecords(file)
            var modified = false

            val obsWet = synop.precipMm >= 0.2
            val obsAmt = synop.precipMm

            for ((_, json) in records) {
                val endSec = json.optLong("windowEndEpochSec", 0L)
                val periodH = json.optInt("periodH", 6)
                if (endSec == synop.timeUtcEpochSec && periodH == synop.precipPeriodH && json.isNull("observedWet")) {
                    json.put("observedWet", obsWet)
                    json.put("observedAmount", obsAmt)
                    modified = true
                }
            }

            if (modified) {
                writeRecords(file, records)
            }
        } catch (_: Exception) {}
    }

    fun computeStats(context: Context): VerificationStats {
        val calEnabled = Calibration.enabled
        val calStatus = if (calEnabled) "جدول کالیبراسیون فعال است" else "جدول کالیبراسیون غیرفعال است (عدم انطباق شرایط دامنه‌ای)"

        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return VerificationStats(isCalibrationEnabled = calEnabled, calibrationStatusText = calStatus)

            val records = readRecords(file)
            val bucketRecords = mapOf(
                6 to mutableListOf<JSONObject>(),
                12 to mutableListOf<JSONObject>(),
                24 to mutableListOf<JSONObject>()
            )

            for ((_, json) in records) {
                val b = json.optInt("leadBucket", 6)
                bucketRecords[b]?.add(json)
            }

            val stat6 = computeLeadStats(6, bucketRecords[6] ?: emptyList())
            val stat12 = computeLeadStats(12, bucketRecords[12] ?: emptyList())
            val stat24 = computeLeadStats(24, bucketRecords[24] ?: emptyList())

            return VerificationStats(
                totalRecords = records.size,
                isCalibrationEnabled = calEnabled,
                calibrationStatusText = calStatus,
                lead6h = stat6,
                lead12h = stat12,
                lead24h = stat24
            )
        } catch (_: Exception) {
            return VerificationStats(isCalibrationEnabled = calEnabled, calibrationStatusText = calStatus)
        }
    }

    private fun computeLeadStats(leadBucket: Int, list: List<JSONObject>): LeadStats {
        var total = list.size
        var evaluated = 0
        var wetCount = 0
        var brierSum = 0.0
        var hits = 0
        var falseAlarms = 0
        var misses = 0
        var totalFcAmount = 0.0
        var totalObsAmount = 0.0

        for (json in list) {
            if (!json.isNull("observedWet")) {
                evaluated++
                val prob = json.optDouble("probBlock", 0.0)
                val obsWet = json.getBoolean("observedWet")
                val fcAmt = json.optDouble("amountP50", 0.0)
                val obsAmt = json.optDouble("observedAmount", 0.0)

                val y = if (obsWet) 1.0 else 0.0
                if (obsWet) wetCount++

                val err = prob - y
                brierSum += err * err

                val alert = prob >= 0.5
                if (alert && obsWet) hits++
                else if (alert && !obsWet) falseAlarms++
                else if (!alert && obsWet) misses++

                if (obsWet) {
                    totalFcAmount += fcAmt
                    totalObsAmount += obsAmt
                }
            }
        }

        if (evaluated == 0) return LeadStats(leadBucket = leadBucket, totalForecasts = total)

        val brier = brierSum / evaluated
        val pod = if (hits + misses > 0) hits.toDouble() / (hits + misses) else 0.0
        val far = if (hits + falseAlarms > 0) falseAlarms.toDouble() / (hits + falseAlarms) else 0.0
        val biasRatio = if (totalObsAmount > 0) totalFcAmount / totalObsAmount else null

        return LeadStats(
            leadBucket = leadBucket,
            totalForecasts = total,
            evaluatedForecasts = evaluated,
            wetEventsCount = wetCount,
            brierScore = brier,
            pod = pod,
            far = far,
            amountBiasRatio = biasRatio
        )
    }

    private fun getNextSynopWindowEnds(nowEpochSec: Long): List<Long> {
        val list = mutableListOf<Long>()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = nowEpochSec * 1000L
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        for (i in 1..30) {
            cal.add(Calendar.HOUR_OF_DAY, 1)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val sec = cal.timeInMillis / 1000L
            if (hour in listOf(0, 6, 12, 18) && sec > nowEpochSec && sec <= nowEpochSec + 24 * 3600L) {
                list.add(sec)
            }
        }
        return list
    }

    private fun readRecords(file: File): LinkedHashMap<String, JSONObject> {
        val map = LinkedHashMap<String, JSONObject>()
        if (!file.exists()) return map
        try {
            file.forEachLine { line ->
                if (line.isNotBlank()) {
                    val json = JSONObject(line)
                    val key = json.optString("key", "${json.optLong("windowEndEpochSec")}:${json.optInt("periodH", 6)}:${json.optInt("leadBucket")}")
                    map[key] = json
                }
            }
        } catch (_: Exception) {}
        return map
    }

    private fun writeRecords(file: File, records: Map<String, JSONObject>) {
        try {
            val list = records.values.toList().takeLast(Tuning.VERIFY_MAX_LINES)
            file.writeText(list.joinToString("\n") { it.toString() } + "\n")
        } catch (_: Exception) {}
    }
}
