package com.amin.weather.core

/**
 * متن‌های ساده برای کشاورز: تبدیل اصطلاحات فنی به زبان روزمره.
 * هر جای صفحه که عدد فنی (dBZ، نام منبع، وضعیت منبع) نمایش می‌دهد باید از این‌جا بگیرد.
 */
object PlainText {

    /** شدت بارش از روی قدرت اکوی رادار */
    fun intensityFa(dbz: Int?): String = when {
        dbz == null || dbz < Tuning.DBZ_RAIN -> "خیلی ضعیف"
        dbz < 30 -> "سبک"
        dbz < Tuning.DBZ_STRONG -> "متوسط"
        dbz < Tuning.DBZ_SEVERE -> "شدید"
        else -> "بسیار شدید"
    }

    /** نام فارسی منبع داده در کارت «سلامت منابع» */
    fun sourceNameFa(source: String): String = when (source) {
        "open-meteo/neighbourhood" -> "مدل‌ها در ۱۷ نقطهٔ اطراف"
        "open-meteo/center" -> "مدل در مرکز شمس‌آباد"
        "open-meteo/lifted_index" -> "شاخص ناپایداری هوا"
        "open-meteo/steering" -> "باد ارتفاعات"
        "ensemble/ecmwf" -> "پیش‌بینی گروهی اروپا"
        "ensemble/icon" -> "پیش‌بینی گروهی آلمان"
        "ogimet/synop" -> "ایستگاه صفی‌آباد"
        "rainviewer/radar" -> "رادار"
        else -> source
    }

    fun statusFa(status: SourceStatus): String = when (status) {
        SourceStatus.OK -> "سالم"
        SourceStatus.PARTIAL -> "ناقص"
        SourceStatus.FAILED -> "قطع"
        SourceStatus.UNAVAILABLE -> "ناموجود"
    }

    /** ارقام فارسی/عربی را به لاتین برمی‌گرداند تا toDoubleOrNull کار کند («۲۵٫۵» ← "25.5") */
    fun toLatinDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(when (ch) {
                in '۰'..'۹' -> '0' + (ch - '۰')
                in '٠'..'٩' -> '0' + (ch - '٠')
                '٫', '،' -> '.'
                else -> ch
            })
        }
        return sb.toString()
    }

    /**
     * برچسب ساعت برای کارت‌های ساعتی: «۱۴:۰۰» برای امروز، «فردا ۰۲:۰۰» برای فردا، «پس‌فردا …» بعد از آن.
     * روزها بر اساس تقویم ایران (UTC+3:30).
     */
    fun hourLabelFa(epochSec: Long, nowSec: Long): String {
        val off = 12_600L   // ۳٫۵ ساعت
        val dayNow = Math.floorDiv(nowSec + off, 86_400L)
        val day = Math.floorDiv(epochSec + off, 86_400L)
        val secOfDay = Math.floorMod(epochSec + off, 86_400L)
        val hh = (secOfDay / 3600).toString().padStart(2, '0')
        val mm = ((secOfDay % 3600) / 60).toString().padStart(2, '0')
        val t = JalaliCalendar.toPersianDigits("$hh:$mm")
        return when (day - dayNow) {
            0L -> t
            1L -> "فردا $t"
            2L -> "پس‌فردا $t"
            else -> t
        }
    }
}
