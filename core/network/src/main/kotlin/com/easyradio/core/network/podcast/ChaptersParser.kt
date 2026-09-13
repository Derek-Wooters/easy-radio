package com.easyradio.core.network.podcast

import com.easyradio.core.model.Chapter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ChaptersDocumentDto(val chapters: List<ChapterDto> = emptyList())

@Serializable
private data class ChapterDto(
    val startTime: Double = 0.0,
    val title: String = "",
    val img: String? = null,
    val url: String? = null,
)

/**
 * Parses the Podcasting 2.0 chapters JSON format (podcastindex.org/namespace/1.0#chapters):
 * a `chapters` array of `{startTime (seconds), title, img?, url?}` objects, fetched from the
 * URL an episode's `<podcast:chapters href="...">` tag points to.
 */
object ChaptersParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(rawJson: String): List<Chapter> = try {
        json.decodeFromString<ChaptersDocumentDto>(rawJson).chapters
            .filter { it.title.isNotBlank() }
            .map {
                Chapter(
                    startTimeMs = (it.startTime * 1000).toLong(),
                    title = it.title,
                    url = it.url?.takeIf { url -> url.startsWith("https://") },
                    imageUrl = it.img?.takeIf { img -> img.startsWith("https://") },
                )
            }
    } catch (e: Exception) {
        emptyList()
    }
}
