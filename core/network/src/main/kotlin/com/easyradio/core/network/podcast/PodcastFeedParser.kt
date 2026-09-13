package com.easyradio.core.network.podcast

import com.easyradio.core.model.Episode
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.time.format.DateTimeFormatter
import javax.xml.parsers.SAXParserFactory

/**
 * Streaming (SAX) RSS parser. Some hosted feeds (e.g. long-running daily radio shows) run to
 * several megabytes and thousands of `<item>`s -- a DOM parser has to build the whole tree in
 * memory before returning anything, which is measurably slower and heavier for feeds that size.
 * SAX processes the document in one forward pass without materializing a full tree.
 */
object PodcastFeedParser {

    fun parse(xml: String, podcastId: String): List<Episode> {
        val handler = FeedHandler(podcastId)
        return try {
            SAXParserFactory.newInstance().newSAXParser().parse(InputSource(StringReader(xml)), handler)
            handler.episodes
        } catch (e: Exception) {
            emptyList()
        }
    }

    private class FeedHandler(private val podcastId: String) : DefaultHandler() {
        val episodes = mutableListOf<Episode>()

        private var inItem = false
        private var currentTag: String? = null
        private val text = StringBuilder()

        private var title: String? = null
        private var guid: String? = null
        private var pubDate: String? = null
        private var duration: String? = null
        private var description: String? = null
        private var audioUrl: String? = null
        private var chaptersUrl: String? = null
        private var transcriptUrl: String? = null
        private var transcriptType: String? = null

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
            if (qName == "item") {
                inItem = true
                title = null
                guid = null
                pubDate = null
                duration = null
                description = null
                audioUrl = null
                chaptersUrl = null
                transcriptUrl = null
                transcriptType = null
            } else if (inItem && qName == "enclosure" && audioUrl == null) {
                audioUrl = attributes.getValue("url")
            } else if (inItem && qName == "podcast:chapters" && chaptersUrl == null) {
                chaptersUrl = attributes.getValue("href")
            } else if (inItem && qName == "podcast:transcript") {
                val url = attributes.getValue("url")
                val type = attributes.getValue("type").orEmpty()
                if (url != null && transcriptTypeRank(type) < transcriptTypeRank(transcriptType.orEmpty())) {
                    transcriptUrl = url
                    transcriptType = type
                }
            }
            currentTag = qName
            text.setLength(0)
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inItem) text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            if (inItem && qName == currentTag) {
                val value = text.toString()
                when (qName) {
                    "title" -> if (title == null) title = value
                    "guid" -> if (guid == null) guid = value
                    "pubDate" -> if (pubDate == null) pubDate = value
                    "description" -> if (description == null) description = value
                    "itunes:duration" -> if (duration == null) duration = value
                }
            }
            if (qName == "item") {
                inItem = false
                finishItem()
            }
            text.setLength(0)
        }

        private fun finishItem() {
            val finalTitle = title?.trim().orEmpty()
            val finalAudioUrl = audioUrl
            if (finalTitle.isBlank() || finalAudioUrl == null || !finalAudioUrl.startsWith("https://")) return

            val id = guid?.trim()?.takeIf { it.isNotBlank() } ?: finalAudioUrl
            val episode = try {
                Episode(
                    id = id,
                    podcastId = podcastId,
                    title = finalTitle,
                    audioUrl = finalAudioUrl,
                    publishedAtEpochMillis = pubDate?.let(::parsePubDate),
                    durationSeconds = duration?.let(::parseDurationSeconds),
                    description = description?.let(::stripHtml).orEmpty(),
                    chaptersUrl = chaptersUrl?.trim()?.takeIf { it.startsWith("https://") },
                    transcriptUrl = transcriptUrl?.trim()?.takeIf { it.startsWith("https://") },
                    transcriptType = transcriptType,
                )
            } catch (e: IllegalArgumentException) {
                null
            }
            episode?.let { episodes.add(it) }
        }
    }
}

/**
 * Some feeds put raw HTML in `<description>` (show notes with links, line breaks, etc.) --
 * strip tags so the plain-text UI doesn't render them literally.
 */
private fun stripHtml(raw: String): String =
    raw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()

/**
 * A feed can publish the same transcript in multiple formats via repeated
 * `<podcast:transcript>` tags -- prefer plain-text/HTML (trivial to display) over caption
 * formats (SRT/VTT, which need cue-timestamp stripping) over JSON (schema varies by provider).
 */
private fun transcriptTypeRank(type: String): Int = when (type.lowercase()) {
    "text/plain" -> 0
    "text/html" -> 1
    "application/srt", "application/x-subrip" -> 2
    "text/vtt" -> 3
    "application/json" -> 4
    else -> Int.MAX_VALUE
}

private fun parsePubDate(raw: String): Long? = try {
    java.time.ZonedDateTime.parse(raw.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
        .toInstant()
        .toEpochMilli()
} catch (e: Exception) {
    null
}

private fun parseDurationSeconds(raw: String): Int? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    if (!trimmed.contains(":")) return trimmed.toIntOrNull()

    val parts = trimmed.split(":").map { it.toIntOrNull() }
    if (parts.any { it == null }) return null

    return when (parts.size) {
        3 -> parts[0]!! * 3600 + parts[1]!! * 60 + parts[2]!!
        2 -> parts[0]!! * 60 + parts[1]!!
        else -> null
    }
}
