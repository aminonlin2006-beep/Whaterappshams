package com.amin.weather.core

import android.content.Context
import org.json.JSONObject
import java.io.File

data class VerificationStats(
    val totalForecasts: Int = 0,
    val evaluatedForecasts: Int = 0,
    val wetEventsCount: Int = 0,
    val brierScore: Double? = null,
    val pod: Double? = null,
    val far: Double? = null,
    val amountBiasRatio: Double? = null
)

object VerificationStore {
    private const val FILE_NAME = "verify.jsonl"
    private const val MAX_AGE_MS = 365L * 86400L * 1000L

    fun recordForecast(context: Context, targetEpochSec: Long, probPoint: Double?, amountP50: Double?) {
        if (probPoint == null) return
        try {
            val file = File(context.filesDir, FILE_NAME)
            val nowMs = System.currentTimeMillis()
            val entry = JSONObject().apply {
                put("recordedAtMs", nowMs)
                put("targetEpochSec", targetEpochSec)
                put("probPoint", probPoint)
                put("amountP50", amountP50 ?: 0.0)
                put("observedWet", null)
                put("observedAmount", null)
            }
            file.appendText(entry.toString() + "\n")
            cleanOldRecords(file, nowMs)
        } catch (_: Exception) {}
    }

    fun evaluateWithSynop(context: Context, synop: SynopObs) {
        if (synop.precipMm == null && !synop.isRainNow) return
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return

            val lines = file.readLines()
            val newLines = mutableListOf<String>()

            val isWet = (synop.precipMm ?: 0.0) >= 0.2 || synop.isRainNow
            val obsAmount = synop.precipMm ?: if (synop.isRainNow) 0.5 else 0.0

            for (line in lines) {
                if (line.isBlank()) continue
                val json = JSONObject(line)
                val target = json.optLong("targetEpochSec", 0L)
                val diffSec = Math.abs(target - synop.timeUtcEpochSec)

                if (diffSec <= 3600L && json.isNull("observedWet")) {
                    json.put("observedWet", isWet)
                    json.put("observedAmount", obsAmount)
                }
                newLines.add(json.toString())
            }
            file.writeText(newLines.joinToString("\n") + "\n")
        } catch (_: Exception) {}
    }

    fun computeStats(context: Context): VerificationStats {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return VerificationStats()

            var total = 0
            var evaluated = 0
            var wetCount = 0
            var brierSum = 0.0
            var hits = 0
            var falseAlarms = 0
            var misses = 0
            var totalFcAmount = 0.0
            var totalObsAmount = 0.0

            val lines = file.readLines()
            for (line in lines) {
                if (line.isBlank()) continue
                val json = JSONObject(line)
                total++
                if (!json.isNull("observedWet")) {
                    evaluated++
                    val prob = json.optDouble("probPoint", 0.0)
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

            if (evaluated == 0) return VerificationStats(totalForecasts = total)

            val brier = brierSum / evaluated
            val pod = if (hits + misses > 0) hits.toDouble() / (hits + misses) else 0.0
            val far = if (hits + falseAlarms > 0) falseAlarms.toDouble() / (hits + falseAlarms) else 0.0
            val biasRatio = if (totalObsAmount > 0) totalFcAmount / totalObsAmount else null

            return VerificationStats(
                totalForecasts = total,
                evaluatedForecasts = evaluated,
                wetEventsCount = wetCount,
                brierScore = brier,
                pod = pod,
                far = far,
                amountBiasRatio = biasRatio
            )
        } catch (_: Exception) {
            return VerificationStats()
        }
    }

    private fun cleanOldRecords(file: File, nowMs: Long) {
        try {
            val lines = file.readLines()
            val valid = lines.filter { line ->
                if (line.isBlank()) false
                else {
                    val json = JSONObject(line)
                    val recMs = json.optLong("recordedAtMs", 0L)
                    (nowMs - recMs) <= MAX_AGE_MS
                }
            }
            if (valid.size < lines.size) {
                file.writeText(valid.joinToString("\n") + "\n")
            }
        } catch (_: Exception) {}
    }
}
