package com.easyradio.core.network.podcast

import com.easyradio.core.model.Episode
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

object PodcastFeedParser {

    fun parse(xml: String, podcastId: String): List<Episode> {
        val document = try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        } catch (e: Exception) {
            return emptyList()
        }

        val itemNodes = document.getElementsByTagName("item")
        val episodes = mutableListOf<Episode>()

        for (i in 0 until itemNodes.length) {
            val item = itemNodes.item(i) as? Element ?: continue
            parseItem(item, podcastId)?.let { episodes.add(it) }
        }

        return episodes
    }

    private fun parseItem(item: Element, podcastId: String): Episode? {
        val title = item.firstChildTextOrNull("title")?.trim().orEmpty()
        val audioUrl = item.enclosureUrl() ?: return null
        val guid = item.firstChildTextOrNull("guid")?.trim()
        val id = guid?.takeIf { it.isNotBlank() } ?: audioUrl

        if (title.isBlank() || !audioUrl.startsWith("https://")) return null

        return try {
            Episode(
                id = id,
                podcastId = podcastId,
                title = title,
                audioUrl = audioUrl,
                publishedAtEpochMillis = item.firstChildTextOrNull("pubDate")?.let(::parsePubDate),
                durationSeconds = item.firstChildTextOrNull("itunes:duration")?.let(::parseDurationSeconds),
                description = item.firstChildTextOrNull("description")?.trim().orEmpty(),
            )
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun Element.firstChildTextOrNull(tagName: String): String? {
        val nodes = getElementsByTagName(tagName)
        if (nodes.length == 0) return null
        return nodes.item(0).textContent
    }

    private fun Element.enclosureUrl(): String? {
        val enclosures = getElementsByTagName("enclosure")
        if (enclosures.length == 0) return null
        val enclosure = enclosures.item(0) as? Element ?: return null
        return enclosure.getAttribute("url").takeIf { it.isNotBlank() }
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
}
