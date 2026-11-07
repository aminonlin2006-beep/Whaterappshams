package com.amin.weather.core

import kotlin.math.roundToInt

/**
 * تبدیل تاریخ میلادی به شمسی (جلالی) — الگوریتم Borkowski
 * و تبدیل ارقام به فارسی.
 */
object JalaliCalendar {

    private val BREAKS = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181,
        1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    private val FA_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    private val JALALI_MONTHS = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    data class JalaliDate(val jy: Int, val jm: Int, val jd: Int)
    private data class JalCalResult(val leap: Int, val gy: Int, val march: Int)
    private data class GregDate(val gy: Int, val gm: Int, val gd: Int)

    private fun div(a: Int, b: Int): Int = a / b
    private fun mod(a: Int, b: Int): Int = a - (a / b) * b

    private fun jalCal(jy: Int): JalCalResult {
        val bl = BREAKS.size
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        var jm: Int
        var jump = 0
        for (i in 1 until bl) {
            jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1
        val leapG = div(gy, 4) - div((div(gy, 100) + 1) * 3, 4) - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return JalCalResult(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var d = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) +
            div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
        d = d - div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4) + 752
        return d
    }

    private fun d2g(jdn: Int): GregDate {
        var j = 4 * jdn + 139361631
        j = j + div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return GregDate(gy, gm, gd)
    }

    private fun d2j(jdn: Int): JalaliDate {
        val gy = d2g(jdn).gy
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) {
                return JalaliDate(jy, 1 + div(k, 31), mod(k, 31) + 1)
            } else {
                k -= 186
            }
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return JalaliDate(jy, 7 + div(k, 30), mod(k, 30) + 1)
    }

    fun g2j(gy: Int, gm: Int, gd: Int): JalaliDate = d2j(g2d(gy, gm, gd))

    fun toPersianDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch in '0'..'9') {
                sb.append(FA_DIGITS[ch - '0'])
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun formatNumber(v: Double?): String {
        if (v == null || v.isNaN()) return "—"
        val rounded = (v * 100.0).roundToInt() / 100.0
        val str = if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            rounded.toString()
        }
        return toPersianDigits(str)
    }

    fun formatNumber(v: Int?): String {
        if (v == null) return "—"
        return toPersianDigits(v.toString())
    }

    private val DT_REGEX = Regex("""^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})""")
    private val DATE_ONLY_REGEX = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    private val WEEK_DAYS = arrayOf(
        "شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"
    )

    fun formatLong(dtStr: String?): String {
        if (dtStr.isNullOrBlank()) return "—"
        if (dtStr.contains(" تا ")) {
            val parts = dtStr.split(" تا ")
            return parts.joinToString(" تا ") { formatLong(it.trim()) }
        }
        val m = DT_REGEX.find(dtStr)
        if (m != null) {
            val (y, mo, d, h, mi) = m.destructured
            val j = g2j(y.toInt(), mo.toInt(), d.toInt())
            val jm = j.jm.toString().padStart(2, '0')
            val jd = j.jd.toString().padStart(2, '0')
            return toPersianDigits("${j.jy}/$jm/$jd $h:$mi")
        }
        val dOnly = DATE_ONLY_REGEX.find(dtStr)
        if (dOnly != null) {
            val (y, mo, d) = dOnly.destructured
            val j = g2j(y.toInt(), mo.toInt(), d.toInt())
            val jm = j.jm.toString().padStart(2, '0')
            val jd = j.jd.toString().padStart(2, '0')
            return toPersianDigits("${j.jy}/$jm/$jd")
        }
        return toPersianDigits(dtStr)
    }

    fun formatShort(dtStr: String?): String {
        if (dtStr.isNullOrBlank()) return "—"
        if (dtStr.contains(" تا ")) {
            val parts = dtStr.split(" تا ")
            return parts.joinToString(" تا ") { formatShort(it.trim()) }
        }
        val m = DT_REGEX.find(dtStr)
        if (m != null) {
            val (y, mo, d, h, mi) = m.destructured
            val j = g2j(y.toInt(), mo.toInt(), d.toInt())
            val monthName = JALALI_MONTHS.getOrElse(j.jm - 1) { "" }
            return "${toPersianDigits(j.jd.toString())} $monthName ${toPersianDigits("$h:$mi")}"
        }
        val dOnly = DATE_ONLY_REGEX.find(dtStr)
        if (dOnly != null) {
            val (y, mo, d) = dOnly.destructured
            val j = g2j(y.toInt(), mo.toInt(), d.toInt())
            val monthName = JALALI_MONTHS.getOrElse(j.jm - 1) { "" }
            return "${toPersianDigits(j.jd.toString())} $monthName"
        }
        return toPersianDigits(dtStr)
    }

    fun formatTimeOnly(dtStr: String?): String {
        if (dtStr.isNullOrBlank()) return "—"
        val m = DT_REGEX.find(dtStr)
        if (m != null) {
            val (_, _, _, h, mi) = m.destructured
            return toPersianDigits("$h:$mi")
        }
        return toPersianDigits(dtStr)
    }

    fun formatDateOnly(dtStr: String?): String {
        if (dtStr.isNullOrBlank()) return "—"
        val m = DT_REGEX.find(dtStr)
        if (m != null) {
            val (y, mo, d) = m.destructured
            val j = g2j(y.toInt(), mo.toInt(), d.toInt())
            val monthName = JALALI_MONTHS.getOrElse(j.jm - 1) { "" }
            return "${toPersianDigits(j.jd.toString())} $monthName"
        }
        val dOnly = DATE_ONLY_REGEX.find(dtStr)
        if (dOnly != null) {
            val (y, mo, d) = dOnly.destructured
            val j = g2j(y.toInt(), mo.toInt(), d.toInt())
            val monthName = JALALI_MONTHS.getOrElse(j.jm - 1) { "" }
            return "${toPersianDigits(j.jd.toString())} $monthName"
        }
        return toPersianDigits(dtStr)
    }
}

