package com.easyradio.core.network.podcast

import com.easyradio.core.model.Podcast
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpmlSupportTest {

    @Test
    fun `write produces an outline per podcast with title and feed url`() {
        val podcasts = listOf(
            Podcast(id = "1", title = "Show One", author = "A", artworkUrl = null, feedUrl = "https://example.com/one.xml"),
            Podcast(id = "2", title = "Show Two", author = "B", artworkUrl = null, feedUrl = "https://example.com/two.xml"),
        )

        val opml = OpmlSupport.write(podcasts)

        assertThat(opml).contains("xmlUrl=\"https://example.com/one.xml\"")
        assertThat(opml).contains("xmlUrl=\"https://example.com/two.xml\"")
        assertThat(opml).contains("text=\"Show One\"")
        assertThat(opml).contains("text=\"Show Two\"")
    }

    @Test
    fun `write escapes ampersands and quotes in the title`() {
        val podcasts = listOf(
            Podcast(id = "1", title = "Rock & Roll \"Hour\"", author = "A", artworkUrl = null, feedUrl = "https://example.com/feed.xml"),
        )

        val opml = OpmlSupport.write(podcasts)

        assertThat(opml).contains("Rock &amp; Roll &quot;Hour&quot;")
        assertThat(opml).doesNotContain("Rock & Roll \"Hour\"")
    }

    @Test
    fun `write starts with the XML declaration with no leading whitespace`() {
        // Android's Expat-based XML parser (unlike desktop Xerces, used in these unit
        // tests) rejects a document whose XML declaration isn't the very first thing
        // in the string -- any leading whitespace throws "XML or text declaration not
        // at start of entity" at runtime even though this parses fine on the JVM.
        val podcasts = listOf(
            Podcast(id = "1", title = "Show One", author = "A", artworkUrl = null, feedUrl = "https://example.com/one.xml"),
            Podcast(id = "2", title = "Show Two", author = "B", artworkUrl = null, feedUrl = "https://example.com/two.xml"),
        )

        val opml = OpmlSupport.write(podcasts)

        assertThat(opml).startsWith("<?xml")
    }

    @Test
    fun `write then parse round-trips title and feed url`() {
        val podcasts = listOf(
            Podcast(id = "1", title = "Rock & Roll", author = "A", artworkUrl = null, feedUrl = "https://example.com/feed.xml"),
        )

        val entries = OpmlSupport.parse(OpmlSupport.write(podcasts))

        assertThat(entries).containsExactly(OpmlEntry(title = "Rock & Roll", feedUrl = "https://example.com/feed.xml"))
    }

    @Test
    fun `parse reads a real-world OPML file exported by another app`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="1.0">
              <head><title>My podcasts</title></head>
              <body>
                <outline text="Planet Money" type="rss" xmlUrl="https://feeds.npr.org/510289/podcast.xml" htmlUrl="https://npr.org"/>
                <outline text="Radiolab" type="rss" xmlUrl="https://feeds.simplecast.com/EmVW7VGp"/>
              </body>
            </opml>
        """.trimIndent()

        val entries = OpmlSupport.parse(xml)

        assertThat(entries).containsExactly(
            OpmlEntry(title = "Planet Money", feedUrl = "https://feeds.npr.org/510289/podcast.xml"),
            OpmlEntry(title = "Radiolab", feedUrl = "https://feeds.simplecast.com/EmVW7VGp"),
        ).inOrder()
    }

    @Test
    fun `parse skips outlines with no xmlUrl or a non-https xmlUrl`() {
        val xml = """
            <opml version="1.0">
              <body>
                <outline text="Folder"/>
                <outline text="Insecure" xmlUrl="http://example.com/feed.xml"/>
                <outline text="Valid" xmlUrl="https://example.com/feed.xml"/>
              </body>
            </opml>
        """.trimIndent()

        val entries = OpmlSupport.parse(xml)

        assertThat(entries).containsExactly(OpmlEntry(title = "Valid", feedUrl = "https://example.com/feed.xml"))
    }

    @Test
    fun `parse returns an empty list for malformed xml instead of throwing`() {
        assertThat(OpmlSupport.parse("not valid xml <<<")).isEmpty()
    }
}
