package com.example.netraai.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class TextReaderTest {

    @Test
    fun testEmptyInput() {
        assertEquals("", TextReader.formatRecognizedText(emptyList()))
    }

    @Test
    fun testTwoWordsSameRowLeftToRight() {
        val lines = listOf(
            TextLine(text = "World", left = 200, top = 50, height = 30),
            TextLine(text = "Hello", left = 50, top = 50, height = 30)
        )
        val result = TextReader.formatRecognizedText(lines)
        assertEquals("Hello World", result)
    }

    @Test
    fun testTwoLinesTopToBottom() {
        val lines = listOf(
            TextLine(text = "Second Line", left = 50, top = 120, height = 30),
            TextLine(text = "First Line", left = 50, top = 50, height = 30)
        )
        val result = TextReader.formatRecognizedText(lines)
        assertEquals("First Line Second Line", result)
    }

    @Test
    fun testShuffledInput() {
        val lines = listOf(
            TextLine(text = "Right", left = 300, top = 100, height = 30),
            TextLine(text = "Top", left = 50, top = 10, height = 30),
            TextLine(text = "Left", left = 50, top = 100, height = 30)
        )
        val result = TextReader.formatRecognizedText(lines)
        assertEquals("Top Left Right", result)
    }

    @Test
    fun testExtraWhitespaceCleaning() {
        val lines = listOf(
            TextLine(text = "   Extra   Spaces   ", left = 10, top = 10, height = 30)
        )
        val result = TextReader.formatRecognizedText(lines)
        assertEquals("Extra Spaces", result)
    }
}
