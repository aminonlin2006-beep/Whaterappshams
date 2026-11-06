package com.amin.weather.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * دریافت‌کننده پس‌زمینه برای پایش دوره‌ای وضعیت رادار، شبکه دیدبانی و ارسال هشدارهای هوشمند
 */
class WeatherAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = WeatherNotificationManager(context)
        val settings = manager.loadSettings()
        if (!settings.isEnabled) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = WeatherRepository(context)
                val result = repo.refreshForecast()
                val dashboard = result.getOrNull()
                if (dashboard != null) {
                    val alertEval = manager.evaluateDashboard(dashboard, settings)
                    if (alertEval.shouldNotify) {
                        manager.sendNotificationWithCooldown(alertEval, force = false)
                    }
                }
            } catch (_: Exception) {
                // خطای شبکه در پس‌زمینه؛ تلاش در گام بعدی
            } finally {
                // برنامه‌ریزی زنگ بیدارباش گام بعد
                manager.schedulePeriodicSync()
                pendingResult.finish()
            }
        }
    }
}
