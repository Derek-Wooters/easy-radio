package com.easyradio.core.network.podcast

import com.easyradio.core.model.Podcast
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/** One podcast subscription as read from an OPML `<outline>` element. */
data class OpmlEntry(val title: String, val feedUrl: String)

/**
 * Reads and writes the OPML subscription-list format used by every podcast app to
 * import/export subscriptions -- the de facto standard for migrating between apps.
 */
object OpmlSupport {

    fun write(podcasts: List<Podcast>): String {
        val outlines = podcasts.joinToString("\n") { podcast ->
            "    <outline text=\"${escape(podcast.title)}\" title=\"${escape(podcast.title)}\" " +
                "type=\"rss\" xmlUrl=\"${escape(podcast.feedUrl)}\"/>"
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <opml version="2.0">
              <head>
                <title>Easy Radio Subscriptions</title>
              </head>
              <body>
            $outlines
              </body>
            </opml>
        """.trimIndent().trim()
    }

    fun parse(xml: String): List<OpmlEntry> {
        val handler = OutlineHandler()
        return try {
            SAXParserFactory.newInstance().newSAXParser().parse(InputSource(StringReader(xml)), handler)
            handler.entries
        } catch (e: Exception) {
            emptyList()
        }
    }

    private class OutlineHandler : DefaultHandler() {
        val entries = mutableListOf<OpmlEntry>()

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
            if (qName != "outline") return
            val xmlUrl = attributes.getValue("xmlUrl")?.trim().orEmpty()
            if (!xmlUrl.startsWith("https://")) return
            val title = attributes.getValue("title")?.trim()?.ifBlank { null }
                ?: attributes.getValue("text")?.trim()?.ifBlank { null }
                ?: return
            entries.add(OpmlEntry(title = title, feedUrl = xmlUrl))
        }
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
