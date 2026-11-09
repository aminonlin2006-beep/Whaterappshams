package com.amin.weather.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.amin.weather.MainActivity
import com.amin.weather.R

data class AlertSettings(
    val windowHours: Int = 3,
    val notifyThunderstorm: Boolean = true,
    val notifyHeavyRain: Boolean = true,
    val notifyRainClouds: Boolean = true,
    val isEnabled: Boolean = true
)

data class AlertEvaluationResult(
    val shouldNotify: Boolean,
    val title: String,
    val summary: String,
    val details: List<String>,
    val alertTier: AlertTier
)

class WeatherNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "shamsabad_weather_alerts"
        const val CHANNEL_NAME = "هشدارهای هواشناسی ۲۰ کیلومتری شمس‌آباد"
        const val NOTIFICATION_ID = 1001
        const val PERIODIC_WORK_NAME = "shamsabad_weather_sync_work"
        private const val PREFS_NAME = "shamsabad_alert_prefs_v5"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        createNotificationChannel()
    }

    fun loadSettings(): AlertSettings {
        return AlertSettings(
            windowHours = prefs.getInt("window_hours", 3),
            notifyThunderstorm = prefs.getBoolean("notify_thunderstorm", true),
            notifyHeavyRain = prefs.getBoolean("notify_heavy_rain", true),
            notifyRainClouds = prefs.getBoolean("notify_rain_clouds", true),
            isEnabled = prefs.getBoolean("is_enabled", true)
        )
    }

    fun saveSettings(settings: AlertSettings) {
        prefs.edit()
            .putInt("window_hours", settings.windowHours)
            .putBoolean("notify_thunderstorm", settings.notifyThunderstorm)
            .putBoolean("notify_heavy_rain", settings.notifyHeavyRain)
            .putBoolean("notify_rain_clouds", settings.notifyRainClouds)
            .putBoolean("is_enabled", settings.isEnabled)
            .apply()

        if (settings.isEnabled) {
            schedulePeriodicSync()
        } else {
            cancelPeriodicSync()
        }
    }

    fun schedulePeriodicSync() {
        val settings = loadSettings()
        if (!settings.isEnabled) return
        try {
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val intervalMin = Tuning.REFRESH_INTERVAL_MIN.toLong().coerceAtLeast(15L)
            val workRequest = androidx.work.PeriodicWorkRequestBuilder<WeatherSyncWorker>(
                intervalMin,
                java.util.concurrent.TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        } catch (_: Exception) {}

        cancelAlarmManagerSync()
    }

    fun cancelPeriodicSync() {
        try {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        } catch (_: Exception) {}
        cancelAlarmManagerSync()
    }

    private fun cancelAlarmManagerSync() {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
            val intent = Intent(context, WeatherAlertReceiver::class.java)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 2001, intent, flags)
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                importance
            ).apply {
                description = "هشدارهای پایش راداری و دیدبانی محدوده ۲۰ کیلومتری شمس‌آباد"
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun evaluateDashboard(
        dashboard: ForecastDashboard,
        settings: AlertSettings
    ): AlertEvaluationResult {
        if (!settings.isEnabled) {
            return AlertEvaluationResult(false, "", "", emptyList(), dashboard.verdict.tier)
        }

        val verdict = dashboard.verdict
        if (verdict.tier == AlertTier.GREEN || verdict.tier == AlertTier.UNKNOWN) {
            return AlertEvaluationResult(
                shouldNotify = false,
                title = verdict.tier.title,
                summary = verdict.text,
                details = emptyList(),
                alertTier = verdict.tier
            )
        }

        val title = verdict.tier.title
        val summary = verdict.text
        val details = mutableListOf<String>()
        if (verdict.advisoryNote.isNotBlank()) {
            details.add(verdict.advisoryNote)
        }
        if (dashboard.radarNowcast.available && dashboard.radarNowcast.statusText.isNotBlank()) {
            details.add("📡 رادار: ${dashboard.radarNowcast.statusText}")
        }

        return AlertEvaluationResult(
            shouldNotify = true,
            title = title,
            summary = summary,
            details = details,
            alertTier = verdict.tier
        )
    }

    fun sendNotificationWithCooldown(result: AlertEvaluationResult, force: Boolean = false): Boolean {
        if (!result.shouldNotify) return false

        val lastTime = prefs.getLong("last_alert_time", 0L)
        val lastTierName = prefs.getString("last_alert_tier", "green") ?: "green"
        val now = System.currentTimeMillis()
        val cooldownMs = 60 * 60 * 1000L // 60 minutes cooldown

        val lastSeverity = AlertTier.fromLevelName(lastTierName)?.severity ?: 0
        val isTierEscalated = result.alertTier.severity > lastSeverity

        if (!force && (now - lastTime < cooldownMs) && !isTierEscalated) {
            return false
        }

        val sent = sendNotification(result)
        // اعلان آزمایشی (force) نباید زمان‌بندی هشدارهای واقعی را عقب بیندازد
        if (sent && !force) {
            prefs.edit()
                .putLong("last_alert_time", now)
                .putString("last_alert_tier", result.alertTier.levelName)
                .apply()
        }
        return sent
    }

    fun sendNotification(result: AlertEvaluationResult): Boolean {
        if (!result.shouldNotify) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val bigText = buildString {
            append(result.summary)
            if (result.details.isNotEmpty()) {
                append("\n\n")
                result.details.forEach { append("• $it\n") }
            }
            append("\nموقعیت: شعاع ۲۰ کیلومتری شمس‌آباد دزفول")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(result.title)
            .setContentText(result.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
