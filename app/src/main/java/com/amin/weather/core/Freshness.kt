package com.amin.weather.core

/**
 * تنها جای تعریف «تازه بودن» مشاهده‌ها.
 * هر جای کد که بخواهد بداند یک گزارش هنوز معتبر است، باید از این‌جا بپرسد.
 * nowSec همیشه از بیرون داده می‌شود تا تست‌پذیر باشد.
 */
object Freshness {

    /** گزارش صفی‌آباد هنوز «هوای حاضر» حساب می‌شود؟ (زمان آینده‌تر از تلورانس = نامعتبر) */
    fun isSynopFresh(obs: SynopObs?, nowSec: Long): Boolean {
        if (obs == null) return false
        val age = nowSec - obs.timeUtcEpochSec
        return age >= -Tuning.FUTURE_TOLERANCE_SEC && age < Tuning.SYNOP_FRESH_SEC
    }

    /**
     * گزارش صفی‌آباد تازه است و واقعاً دربارهٔ هوای حاضر چیزی می‌گوید.
     * iX=2 یا 5 یعنی «پدیدهٔ قابل‌گزارشی نبود» که خودش یک مشاهده است؛
     * iX=3 یا 6 یا نبودن گروه ۷ یعنی «مشاهده نشده».
     */
    fun isSynopLiveObservation(obs: SynopObs?, nowSec: Long): Boolean {
        if (!isSynopFresh(obs, nowSec)) return false
        return obs!!.ww != null || obs.iX == 2 || obs.iX == 5
    }

    /** سن گزارش کاربر به دقیقه؛ اگر نامعتبر (آینده یا بیش از TTL) باشد null */
    fun userReportAgeMin(report: SkyObservationReport?, nowSec: Long): Long? {
        if (report == null) return null
        val ageSec = nowSec - report.createdAtEpochMs / 1000L
        if (ageSec < -Tuning.FUTURE_TOLERANCE_SEC) return null
        val ageMin = ageSec.coerceAtLeast(0L) / 60L
        return if (ageMin < Tuning.USER_REPORT_TTL_MIN) ageMin else null
    }

    /** کاربر همین الان (در TTL) بارش گزارش کرده است؟ */
    fun isUserRainNow(report: SkyObservationReport?, nowSec: Long): Boolean =
        userReportAgeMin(report, nowSec) != null && report!!.phenomenon in Tuning.USER_RAIN_PHENOMENA

    /** وزن گزارش کاربر: ۱ در لحظهٔ ثبت، خطی تا ۰ در پایان TTL */
    fun userReportWeight(report: SkyObservationReport?, nowSec: Long): Double {
        val age = userReportAgeMin(report, nowSec) ?: return 0.0
        return 1.0 - age.toDouble() / Tuning.USER_REPORT_TTL_MIN
    }
}
