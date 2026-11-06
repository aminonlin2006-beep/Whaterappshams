package com.amin.weather

import com.amin.weather.core.GeoUtils
import com.amin.weather.core.RegionConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun testShamsabadToSafiabad() {
        val dist = GeoUtils.haversineKm(
            RegionConfig.CENTER.lat, RegionConfig.CENTER.lon,
            32.2667, 48.4167
        )
        assertEquals(3.6, dist, 0.1)

        val bearing = GeoUtils.bearingDeg(
            RegionConfig.CENTER.lat, RegionConfig.CENTER.lon,
            32.2667, 48.4167
        )
        assertEquals(198.0, bearing, 1.0)
    }

    @Test
    fun testShamsabadToAndimeshk() {
        val dist = GeoUtils.haversineKm(
            RegionConfig.CENTER.lat, RegionConfig.CENTER.lon,
            32.4578, 48.3567
        )
        assertEquals(19.0, dist, 0.1)
    }

    @Test
    fun testShamsabadToShush() {
        val dist = GeoUtils.haversineKm(
            RegionConfig.CENTER.lat, RegionConfig.CENTER.lon,
            32.1942, 48.2436
        )
        assertEquals(20.8, dist, 0.1)
    }

    @Test
    fun testShamsabadToAhvaz() {
        val dist = GeoUtils.haversineKm(
            RegionConfig.CENTER.lat, RegionConfig.CENTER.lon,
            31.319, 48.684
        )
        assertEquals(111.5, dist, 0.5)
    }

    @Test
    fun testTileMath() {
        val tileX = GeoUtils.lonToTileX(48.4283, 7)
        assertEquals(81.219, tileX, 0.001)

        val tileY = GeoUtils.latToTileY(32.2978, 7)
        assertEquals(51.855, tileY, 0.001)

        val latFromY = GeoUtils.tileYToLat(52.0, 7)
        assertEquals(31.952, latFromY, 0.001)

        val lonFromX = GeoUtils.tileXToLon(81.0, 7)
        assertEquals(47.8125, lonFromX, 0.0001)
    }

    @Test
    fun testNeighbourhoodGrid() {
        val points = RegionConfig.NEIGHBOURHOOD
        assertEquals(17, points.size)

        // Point 0 is center
        val centerDist = GeoUtils.haversineKm(
            RegionConfig.CENTER.lat, RegionConfig.CENTER.lon,
            points[0].lat, points[0].lon
        )
        assertEquals(0.0, centerDist, 0.001)

        // Points 1..8 are 10km ring
        for (i in 1..8) {
            val d = GeoUtils.haversineKm(
                RegionConfig.CENTER.lat, RegionConfig.CENTER.lon,
                points[i].lat, points[i].lon
            )
            assertEquals(10.0, d, 0.05)
        }

        // Points 9..16 are 20km ring
        for (i in 9..16) {
            val d = GeoUtils.haversineKm(
                RegionConfig.CENTER.lat, RegionConfig.CENTER.lon,
                points[i].lat, points[i].lon
            )
            assertEquals(20.0, d, 0.05)
        }
    }
}
