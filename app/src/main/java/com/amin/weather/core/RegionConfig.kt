package com.amin.weather.core

data class GeoPoint(val id: String, val nameFa: String, val lat: Double, val lon: Double)

object RegionConfig {
    val CENTER = GeoPoint("shamsabad", "شمس‌آباد", 32.2978, 48.4283)

    // ۱۷ نقطه‌ی همسایگی: مرکز + ۸ نقطه در ۱۰ km + ۸ نقطه در ۲۰ km (سمت‌های ۰، ۴۵، …، ۳۱۵)
    val NEIGHBOURHOOD: List<GeoPoint> by lazy {
        val list = mutableListOf(CENTER)
        val dirs = listOf("N","NE","E","SE","S","SW","W","NW")
        for (ring in listOf(10.0, 20.0)) {
            dirs.forEachIndexed { i, d ->
                val p = GeoUtils.destination(CENTER.lat, CENTER.lon, i * 45.0, ring)
                list += GeoPoint("r${ring.toInt()}_$d", "${ring.toInt()}km-$d", p.first, p.second)
            }
        }
        list
    }

    val SYNOP_STATIONS = listOf(
        GeoPoint("40794", "صفی‌آباد دزفول", 32.2667, 48.4167)
    )
}
