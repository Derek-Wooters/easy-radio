package com.easyradio.core.network.podcast

import com.easyradio.core.model.Chapter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChaptersParserTest {

    @Test
    fun `parses chapters with title and start time in seconds converted to milliseconds`() {
        val json = """
            {"version":"1.2.0","chapters":[
              {"startTime":0,"title":"Intro"},
              {"startTime":125.5,"title":"Segment One"}
            ]}
        """.trimIndent()

        val chapters = ChaptersParser.parse(json)

        assertThat(chapters).containsExactly(
            Chapter(startTimeMs = 0, title = "Intro"),
            Chapter(startTimeMs = 125_500, title = "Segment One"),
        ).inOrder()
    }

    @Test
    fun `carries through https image and link urls`() {
        val json = """
            {"chapters":[{"startTime":10,"title":"Sponsor","img":"https://example.com/art.png","url":"https://example.com"}]}
        """.trimIndent()

        val chapter = ChaptersParser.parse(json).first()

        assertThat(chapter.imageUrl).isEqualTo("https://example.com/art.png")
        assertThat(chapter.url).isEqualTo("https://example.com")
    }

    @Test
    fun `drops non-https image and link urls`() {
        val json = """
            {"chapters":[{"startTime":10,"title":"Sponsor","img":"http://example.com/art.png","url":"http://example.com"}]}
        """.trimIndent()

        val chapter = ChaptersParser.parse(json).first()

        assertThat(chapter.imageUrl).isNull()
        assertThat(chapter.url).isNull()
    }

    @Test
    fun `skips chapters with a blank title`() {
        val json = """{"chapters":[{"startTime":0,"title":""},{"startTime":5,"title":"Real chapter"}]}"""

        val chapters = ChaptersParser.parse(json)

        assertThat(chapters.map { it.title }).containsExactly("Real chapter")
    }

    @Test
    fun `returns an empty list for malformed json instead of throwing`() {
        assertThat(ChaptersParser.parse("not json")).isEmpty()
    }

    @Test
    fun `returns an empty list when the chapters array is missing`() {
        assertThat(ChaptersParser.parse("""{"version":"1.2.0"}""")).isEmpty()
    }
}
