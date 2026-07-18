package com.example.missiontrackermap.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Parses simple Markdown-like text containing **bold**, *italic*, and newlines (both literal and escaped as \\n).
 * Returns a Compose [AnnotatedString].
 */
fun parseMarkdownToAnnotatedString(text: String): AnnotatedString {
    val processedText = text.replace("\\n", "\n")
    return buildAnnotatedString {
        var i = 0
        val length = processedText.length
        while (i < length) {
            when {
                processedText.startsWith("**", i) -> {
                    val end = processedText.indexOf("**", i + 2)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                        append(processedText.substring(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }
                processedText.startsWith("*", i) -> {
                    val end = processedText.indexOf("*", i + 1)
                    if (end != -1) {
                        pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                        append(processedText.substring(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append("*")
                        i += 1
                    }
                }
                else -> {
                    append(processedText[i])
                    i += 1
                }
            }
        }
    }
}
