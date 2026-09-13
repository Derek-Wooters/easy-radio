package com.easyradio.core.network.podcast

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranscriptParserTest {

    @Test
    fun `plain text is passed through trimmed`() {
        val result = TranscriptParser.parse("  Hello, welcome to the show.  \n", "text/plain")

        assertThat(result).isEqualTo("Hello, welcome to the show.")
    }

    @Test
    fun `html transcript has tags stripped`() {
        val result = TranscriptParser.parse("<p>Hello <b>there</b>.</p>", "text/html")

        assertThat(result).isEqualTo("Hello there .")
    }

    @Test
    fun `srt captions have cue numbers and timestamps stripped`() {
        val srt = """
            1
            00:00:00,000 --> 00:00:02,500
            Hello and welcome.

            2
            00:00:02,500 --> 00:00:05,000
            This is the show.
        """.trimIndent()

        val result = TranscriptParser.parse(srt, "application/srt")

        assertThat(result).isEqualTo("Hello and welcome. This is the show.")
    }

    @Test
    fun `vtt captions have the header and timestamps stripped`() {
        val vtt = """
            WEBVTT

            00:00:00.000 --> 00:00:02.500
            Hello and welcome.

            00:00:02.500 --> 00:00:05.000 align:middle
            This is the show.
        """.trimIndent()

        val result = TranscriptParser.parse(vtt, "text/vtt")

        assertThat(result).isEqualTo("Hello and welcome. This is the show.")
    }

    @Test
    fun `unknown type is passed through as-is`() {
        val result = TranscriptParser.parse("raw content", "application/json")

        assertThat(result).isEqualTo("raw content")
    }
}
