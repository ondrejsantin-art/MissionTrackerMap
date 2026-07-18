package com.example.missiontrackermap.ui

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParserTest {

    @Test
    fun testParsePlaintext() {
        val input = "Hello World"
        val result = parseMarkdownToAnnotatedString(input)
        assertEquals("Hello World", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun testParseNewlines() {
        val input = "Line 1\\nLine 2\nLine 3"
        val result = parseMarkdownToAnnotatedString(input)
        assertEquals("Line 1\nLine 2\nLine 3", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun testParseBold() {
        val input = "Hello **World**"
        val result = parseMarkdownToAnnotatedString(input)
        assertEquals("Hello World", result.text)
        assertEquals(1, result.spanStyles.size)
        val styleRange = result.spanStyles[0]
        assertEquals(6, styleRange.start)
        assertEquals(11, styleRange.end)
        assertEquals(FontWeight.Bold, styleRange.item.fontWeight)
    }

    @Test
    fun testParseItalic() {
        val input = "Hello *World*"
        val result = parseMarkdownToAnnotatedString(input)
        assertEquals("Hello World", result.text)
        assertEquals(1, result.spanStyles.size)
        val styleRange = result.spanStyles[0]
        assertEquals(6, styleRange.start)
        assertEquals(11, styleRange.end)
        assertEquals(FontStyle.Italic, styleRange.item.fontStyle)
    }

    @Test
    fun testParseMixed() {
        val input = "**Bold** and *Italic*\\nNext line"
        val result = parseMarkdownToAnnotatedString(input)
        assertEquals("Bold and Italic\nNext line", result.text)
        assertEquals(2, result.spanStyles.size)

        val boldRange = result.spanStyles.first { it.item.fontWeight == FontWeight.Bold }
        assertEquals(0, boldRange.start)
        assertEquals(4, boldRange.end)

        val italicRange = result.spanStyles.first { it.item.fontStyle == FontStyle.Italic }
        assertEquals(9, italicRange.start)
        assertEquals(15, italicRange.end)
    }
}
