package com.example.netraai.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudUnitTest {

    @Test
    fun testCalculateTargetDimensions() {
        // Under 1280, remains same
        val (w1, h1) = ImageDownscaler.calculateTargetDimensions(800, 600, 1280)
        assertEquals(800, w1)
        assertEquals(600, h1)

        // Oversize 1920x1080 -> 1280x720
        val (w2, h2) = ImageDownscaler.calculateTargetDimensions(1920, 1080, 1280)
        assertEquals(1280, w2)
        assertEquals(720, h2)

        // Vertical 1080x1920 -> 720x1280
        val (w3, h3) = ImageDownscaler.calculateTargetDimensions(1080, 1920, 1280)
        assertEquals(720, w3)
        assertEquals(1280, h3)
    }

    @Test
    fun testMultipartBodyBuilder() {
        val builder = MultipartBodyBuilder("test-boundary")
        val fakeBytes = byteArrayOf(0x01, 0x02, 0x03)
        val body = builder.buildBody(fakeBytes, "en", "What is ahead?")

        val bodyString = String(body, Charsets.UTF_8)
        assertTrue(bodyString.contains("name=\"language\""))
        assertTrue(bodyString.contains("en"))
        assertTrue(bodyString.contains("name=\"question\""))
        assertTrue(bodyString.contains("What is ahead?"))
        assertTrue(bodyString.contains("filename=\"scene.jpg\""))
        assertTrue(bodyString.contains("--test-boundary--"))
    }
}
