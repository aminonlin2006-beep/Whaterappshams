package com.amin.weather.core

import kotlin.math.*

object GeoUtils {
    const val EARTH_R_KM = 6371.0088

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
        val h = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * EARTH_R_KM * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** سمت از نقطه‌ی ۱ به ۲، درجه از شمال جغرافیایی، ساعتگرد، 0..360 */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun destination(lat: Double, lon: Double, bearingDeg: Double, distKm: Double): Pair<Double, Double> {
        val p1 = Math.toRadians(lat); val l1 = Math.toRadians(lon)
        val b = Math.toRadians(bearingDeg); val dr = distKm / EARTH_R_KM
        val p2 = asin(sin(p1) * cos(dr) + cos(p1) * sin(dr) * cos(b))
        val l2 = l1 + atan2(sin(b) * sin(dr) * cos(p1), cos(dr) - sin(p1) * sin(p2))
        return Pair(Math.toDegrees(p2), Math.toDegrees(l2))
    }

    /** مختصات محلی تخت (km): x به شرق، y به شمال، نسبت به مرکز. فقط تا ~۲۰۰ km دقت کافی دارد. */
    fun toLocalKm(lat0: Double, lon0: Double, lat: Double, lon: Double): Pair<Double, Double> {
        val d = haversineKm(lat0, lon0, lat, lon)
        val b = Math.toRadians(bearingDeg(lat0, lon0, lat, lon))
        return Pair(d * sin(b), d * cos(b))
    }

    // ریاضی کاشی Web-Mercator (Slippy map)
    fun lonToTileX(lon: Double, z: Int): Double = (lon + 180.0) / 360.0 * (1 shl z)
    fun latToTileY(lat: Double, z: Int): Double {
        val r = Math.toRadians(lat)
        return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)
    }
    fun tileXToLon(x: Double, z: Int): Double = x / (1 shl z) * 360.0 - 180.0
    fun tileYToLat(y: Double, z: Int): Double =
        Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y / (1 shl z)))))

    /** برچسب فارسی ۱۶ جهته؛ ورودی سمت «به سوی» یا «از سوی» است؛ فراخواننده معنا را مشخص کند. */
    private val DIRS16 = listOf(
        "شمال", "شمال‌شمال‌شرق", "شمال‌شرق", "شرق‌شمال‌شرق", "شرق", "شرق‌جنوب‌شرق", "جنوب‌شرق", "جنوب‌جنوب‌شرق",
        "جنوب", "جنوب‌جنوب‌غرب", "جنوب‌غرب", "غرب‌جنوب‌غرب", "غرب", "غرب‌شمال‌غرب", "شمال‌غرب", "شمال‌شمال‌غرب")
    fun dir16Fa(deg: Double): String = DIRS16[(((deg % 360 + 360) % 360 + 11.25) / 22.5).toInt() % 16]
}
