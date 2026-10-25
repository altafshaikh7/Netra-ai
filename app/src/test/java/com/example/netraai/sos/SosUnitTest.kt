package com.example.netraai.sos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SosUnitTest {

    @Test
    fun testSosMessageBuilding() {
        val msgWithLoc = SosMessageBuilder.buildMessage(28.6139, 77.2090, isLastKnown = false)
        assertTrue(msgWithLoc.contains("EMERGENCY from Netra user"))
        assertTrue(msgWithLoc.contains("https://maps.google.com/?q=28.6139,77.209"))

        val msgLastKnown = SosMessageBuilder.buildMessage(28.6139, 77.2090, isLastKnown = true)
        assertTrue(msgLastKnown.contains("(last known)"))

        val msgNoLoc = SosMessageBuilder.buildMessage(null, null)
        assertEquals("EMERGENCY from Netra user. Location unavailable.", msgNoLoc)
    }
}
