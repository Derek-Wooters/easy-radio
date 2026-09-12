package com.easyradio.core.network.podcast

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class PodcastFeedParserTest {

    @Test
    fun `parses a well-formed feed with guid, pubDate, and HH-MM-SS duration`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
              <channel>
                <title>Test Show</title>
                <item>
                  <title>Episode One</title>
                  <guid>guid-1</guid>
                  <description>First episode</description>
                  <pubDate>Wed, 02 Oct 2024 15:00:00 -0700</pubDate>
                  <enclosure url="https://example.com/ep1.mp3" length="123456" type="audio/mpeg"/>
                  <itunes:duration>1:02:03</itunes:duration>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val episodes = PodcastFeedParser.parse(xml, podcastId = "podcast-1")

        assertThat(episodes).hasSize(1)
        val episode = episodes.first()
        assertThat(episode.id).isEqualTo("guid-1")
        assertThat(episode.podcastId).isEqualTo("podcast-1")
        assertThat(episode.title).isEqualTo("Episode One")
        assertThat(episode.audioUrl).isEqualTo("https://example.com/ep1.mp3")
        assertThat(episode.description).isEqualTo("First episode")
        assertThat(episode.durationSeconds).isEqualTo(3600 + 2 * 60 + 3)
        assertThat(episode.publishedAtEpochMillis).isEqualTo(
            Instant.parse("2024-10-02T22:00:00Z").toEpochMilli(),
        )
    }

    @Test
    fun `parses plain-seconds duration format`() {
        val xml = """
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
              <channel>
                <item>
                  <title>Episode Two</title>
                  <guid>guid-2</guid>
                  <enclosure url="https://example.com/ep2.mp3"/>
                  <itunes:duration>1800</itunes:duration>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val episodes = PodcastFeedParser.parse(xml, podcastId = "podcast-1")

        assertThat(episodes.first().durationSeconds).isEqualTo(1800)
    }

    @Test
    fun `falls back to enclosure url as id when guid is missing`() {
        val xml = """
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
              <channel>
                <item>
                  <title>Episode Three</title>
                  <enclosure url="https://example.com/ep3.mp3"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val episodes = PodcastFeedParser.parse(xml, podcastId = "podcast-1")

        assertThat(episodes.first().id).isEqualTo("https://example.com/ep3.mp3")
    }

    @Test
    fun `skips items with a non-https enclosure url`() {
        val xml = """
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
              <channel>
                <item>
                  <title>Bad Episode</title>
                  <guid>guid-bad</guid>
                  <enclosure url="http://example.com/ep.mp3"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val episodes = PodcastFeedParser.parse(xml, podcastId = "podcast-1")

        assertThat(episodes).isEmpty()
    }

    @Test
    fun `skips items with no title`() {
        val xml = """
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
              <channel>
                <item>
                  <guid>guid-no-title</guid>
                  <enclosure url="https://example.com/ep.mp3"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val episodes = PodcastFeedParser.parse(xml, podcastId = "podcast-1")

        assertThat(episodes).isEmpty()
    }

    @Test
    fun `missing pubDate and duration produce null fields without throwing`() {
        val xml = """
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
              <channel>
                <item>
                  <title>Minimal Episode</title>
                  <guid>guid-minimal</guid>
                  <enclosure url="https://example.com/ep.mp3"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val episode = PodcastFeedParser.parse(xml, podcastId = "podcast-1").first()

        assertThat(episode.publishedAtEpochMillis).isNull()
        assertThat(episode.durationSeconds).isNull()
    }

    @Test
    fun `malformed xml returns an empty list instead of throwing`() {
        val episodes = PodcastFeedParser.parse("not valid xml <<<", podcastId = "podcast-1")

        assertThat(episodes).isEmpty()
    }

    @Test
    fun `fields from one item don't leak into the next when a field is missing`() {
        val xml = """
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
              <channel>
                <item>
                  <title>Episode With Guid</title>
                  <guid>guid-shared</guid>
                  <enclosure url="https://example.com/ep-a.mp3"/>
                </item>
                <item>
                  <title>Episode Without Guid</title>
                  <enclosure url="https://example.com/ep-b.mp3"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val episodes = PodcastFeedParser.parse(xml, podcastId = "podcast-1")

        assertThat(episodes).hasSize(2)
        assertThat(episodes[0].id).isEqualTo("guid-shared")
        // Second item has no guid of its own -- must fall back to its own enclosure url,
        // not silently inherit the first item's guid from stale parser state.
        assertThat(episodes[1].id).isEqualTo("https://example.com/ep-b.mp3")
    }

    @Test
    fun `parses many items in document order without dropping any`() {
        val items = (1..50).joinToString("\n") { i ->
            """
            <item>
              <title>Episode $i</title>
              <guid>guid-$i</guid>
              <enclosure url="https://example.com/ep$i.mp3"/>
            </item>
            """.trimIndent()
        }
        val xml = """
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
              <channel>$items</channel>
            </rss>
        """.trimIndent()

        val episodes = PodcastFeedParser.parse(xml, podcastId = "podcast-1")

        assertThat(episodes).hasSize(50)
        assertThat(episodes.map { it.title }).containsExactlyElementsIn((1..50).map { "Episode $it" }).inOrder()
    }

    @Test
    fun `CDATA-wrapped description has its HTML tags stripped for display`() {
        val xml = """
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
              <channel>
                <item>
                  <title>CDATA Episode</title>
                  <guid>guid-cdata</guid>
                  <description><![CDATA[Show notes with <b>bold</b> text & an ampersand]]></description>
                  <enclosure url="https://example.com/ep.mp3"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val episode = PodcastFeedParser.parse(xml, podcastId = "podcast-1").first()

        assertThat(episode.description).isEqualTo("Show notes with bold text & an ampersand")
    }

    @Test
    fun `description with paragraph, link, and line-break tags renders as clean plain text`() {
        val xml = """
            <rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" version="2.0">
              <channel>
                <item>
                  <title>HTML Episode</title>
                  <guid>guid-html</guid>
                  <description>&lt;p&gt;&lt;a href="https://tix.to/tour" target="_blank"&gt;Live event info here.&lt;/a&gt; &lt;br/&gt;&lt;br/&gt;For more than 60 years&lt;/p&gt;</description>
                  <enclosure url="https://example.com/ep.mp3"/>
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val episode = PodcastFeedParser.parse(xml, podcastId = "podcast-1").first()

        assertThat(episode.description).isEqualTo("Live event info here. For more than 60 years")
    }
}
