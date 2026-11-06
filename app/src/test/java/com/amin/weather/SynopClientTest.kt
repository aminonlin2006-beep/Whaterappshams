package com.amin.weather

import com.amin.weather.core.SynopClient
import org.junit.Assert.*
import org.junit.Test

class SynopClientTest {

    @Test
    fun testParseSynopLineStandard() {
        val line = "40794,2026,11,06,09,00,AAXX 06091 40794 11970 81205 10183 20155 30012 40098 52010 60052 76166 83530="
        val obs = SynopClient.parseSynopLine(line)

        assertNotNull(obs)
        assertEquals(18.3, obs!!.tempC!!, 0.01)
        assertEquals(15.5, obs.dewC!!, 0.01)
        assertEquals(5.0, obs.precipMm!!, 0.01)
        assertEquals(12, obs.precipPeriodH!!)
        assertEquals(61, obs.ww!!)
        assertTrue(obs.isRainNow)
        assertFalse(obs.isThunder)
    }

    @Test
    fun testParseSynopNoPrecipGroup() {
        val line = "40794,2026,11,06,09,00,AAXX 06091 40794 31970 81205 10183 20155="
        val obs = SynopClient.parseSynopLine(line)

        assertNotNull(obs)
        assertEquals(0.0, obs!!.precipMm!!, 0.001)
    }

    @Test
    fun testParseSynopPrecipNotMeasured() {
        val line = "40794,2026,11,06,09,00,AAXX 06091 40794 41970 81205 10183 20155="
        val obs = SynopClient.parseSynopLine(line)

        assertNotNull(obs)
        assertNull(obs!!.precipMm)
    }
}
