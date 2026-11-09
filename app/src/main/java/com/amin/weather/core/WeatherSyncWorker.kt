package com.amin.weather.core

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * وظیفه پس‌زمینه WorkManager برای پایش پایدار و بدون وقفه وضعیت هواشناسی و ارسال اعلان‌ها.
 * بر خلاف BroadcastReceiver، ابزار WorkManager محدودیت ۱۰ ثانیه‌ای ندارد و اجازه می‌دهد
 * درخواست‌های شبکه به صورت ایمن و حتی در وضعیت Doze یا اتصال با تاخیر به پایان برسند.
 */
class WeatherSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val manager = WeatherNotificationManager(applicationContext)
        val settings = manager.loadSettings()
        if (!settings.isEnabled) return@withContext Result.success()

        try {
            val repo = WeatherRepository(applicationContext)
            val dashboard = repo.refreshForecast().getOrNull()
                ?: return@withContext (if (runAttemptCount < 3) Result.retry() else Result.success())

            // دادهٔ کهنه (اتصال برقرار نشد) نباید اعلان بسازد؛ دوباره تلاش کن
            if (dashboard.isStale) {
                return@withContext (if (runAttemptCount < 3) Result.retry() else Result.success())
            }

            val alertEval = manager.evaluateDashboard(dashboard, settings)
            if (alertEval.shouldNotify) {
                manager.sendNotificationWithCooldown(alertEval, force = false)
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }
}
