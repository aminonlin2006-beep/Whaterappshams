package com.amin.weather.core

object UniversalBlue {
    // RGB values for rain colors 15..65 dBZ (Source: rainviewer_api_colors_table.csv)
    private val RGB = intArrayOf(
        0x88DDEE, 0x6CD1EB, 0x51C5E8, 0x36BAE5, 0x1BAEE2, 0x00A3E0, 0x009AD5, 0x0091CA, 0x0088BF, 0x007FB4,
        0x0077AA, 0x0070A3, 0x00699C, 0x006295, 0x005B8E, 0x005588, 0x005180, 0x004E78, 0x004A70, 0x004768,
        0xFFEE00, 0xFFE000, 0xFFD200, 0xFFC500, 0xFFB700, 0xFFAA00, 0xFF9F00, 0xFF9500, 0xFF8B00, 0xFF8100,
        0xFF4400, 0xF23600, 0xE62800, 0xD91B00, 0xCD0D00, 0xC10000, 0xA80000, 0x8F0000, 0x760000, 0x5D0000,
        0xFFAAFF, 0xFF9FFF, 0xFF95FF, 0xFF8BFF, 0xFF81FF, 0xFF77FF, 0xFF6CFF, 0xFF62FF, 0xFF58FF, 0xFF4EFF,
        0xFFFFFF
    )
    private const val FIRST_DBZ = 15
    private val cache = HashMap<Int, Int>()

    /** Returns dBZ (15..65) or -1 for no rain/weak echo/transparent */
    fun dbz(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        if (a < 255) return -1
        val rgb = argb and 0xFFFFFF
        return cache.getOrPut(rgb) {
            var best = -1
            var bestD = Int.MAX_VALUE
            for (i in RGB.indices) {
                val c = RGB[i]
                val dr = ((c shr 16) and 255) - ((rgb shr 16) and 255)
                val dg = ((c shr 8) and 255) - ((rgb shr 8) and 255)
                val db = (c and 255) - (rgb and 255)
                val d = dr * dr + dg * dg + db * db
                if (d < bestD) {
                    bestD = d
                    best = i
                }
            }
            if (bestD > 30 * 30 * 3) -1 else FIRST_DBZ + best
        }
    }
}
