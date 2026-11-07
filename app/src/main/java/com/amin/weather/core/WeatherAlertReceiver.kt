package com.amin.weather.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * دریافت‌کننده پس‌زمینه (مثلاً هنگام روشن شدن گوشی BOOT_COMPLETED).
 * به جای انجام کارهای سنگین و نامطمئن شبکه داخل برودکست‌رسیور با محدودیت ۱۰ ثانیه‌ای،
 * وظیفه به سرعت به WorkManager محول می‌شود تا پایدار، بدون قطعی و با مدیریت هوشمند انرژی انجام گیرد.
 */
class WeatherAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = WeatherNotificationManager(context)
        val settings = manager.loadSettings()
        if (!settings.isEnabled) return

        // اطمینان از برقراری پایش دوره‌ای
        manager.schedulePeriodicSync()

        // اجرای یک نوبت پایش فوری در بستر WorkManager
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val immediateWork = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueue(immediateWork)
        } catch (_: Exception) {}
    }
}
