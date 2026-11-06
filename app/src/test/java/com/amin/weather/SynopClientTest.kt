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
    fun testParseSynopPartlyCloudyGroup6() {
        val line = "40794,2026,11,06,12,00,AAXX 06121 40794 11970 31205 10183 20155 30012 40198 52010 60082 78062 83530="
        val obs = SynopClient.parseSynopLine(line)

        assertNotNull(obs)
        assertEquals(18.3, obs!!.tempC!!, 0.01)
        assertEquals(8.0, obs.precipMm!!, 0.01)
        assertEquals(12, obs.precipPeriodH!!)
        assertEquals(80, obs.ww!!)
        assertTrue(obs.isRainNow)
    }

    @Test
    fun testParseSynopMissingTempGroupNotMinus97() {
        val line = "40794,2026,11,06,12,00,AAXX 06121 40794 11970 31205 20155 60082="
        val obs = SynopClient.parseSynopLine(line)

        assertNotNull(obs)
        assertNull(obs!!.tempC)
        assertEquals(8.0, obs.precipMm!!, 0.01)
    }

    @Test
    fun testParseSynopNoPrecipIR3() {
        val line = "40794,2026,11,06,15,00,AAXX 06151 40794 32970 31205 10221 20101="
        val obs = SynopClient.parseSynopLine(line)

        assertNotNull(obs)
        assertEquals(0.0, obs!!.precipMm!!, 0.001)
        assertNull(obs.precipPeriodH)
    }

    @Test
    fun testParseSynopSection3Precip() {
        val line = "40794,2026,11,06,18,00,AAXX 06181 40794 21970 41208 10150 20120 333 60031="
        val obs = SynopClient.parseSynopLine(line)

        assertNotNull(obs)
        assertEquals(3.0, obs!!.precipMm!!, 0.01)
        assertEquals(6, obs.precipPeriodH!!)
    }

    @Test
    fun testParseSynopPrecipSlashNull() {
        val line = "40794,2026,11,06,21,00,AAXX 06211 40794 41/// 80000 10221="
        val obs = SynopClient.parseSynopLine(line)

        assertNotNull(obs)
        assertNull(obs!!.precipMm)
    }
}
