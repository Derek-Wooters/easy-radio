package com.easyradio.core.network.podcast

private val TIMESTAMP_LINE = Regex("""\d{2}:\d{2}:\d{2}[,.]\d{3}\s*-->\s*\d{2}:\d{2}:\d{2}[,.]\d{3}.*""")
private val CUE_INDEX_LINE = Regex("""^\d+$""")

/**
 * Converts a fetched `<podcast:transcript>` document to plain reading text. Feeds can publish
 * transcripts as plain text, HTML, or a caption format (SRT/VTT) meant for video players -- the
 * caption formats carry per-line cue numbers and timestamps that read as noise in a text view.
 */
object TranscriptParser {

    fun parse(raw: String, type: String?): String {
        val text = when (type?.lowercase()) {
            "text/html" -> stripHtml(raw)
            "text/vtt", "application/srt", "application/x-subrip" -> stripCaptionMarkup(raw)
            else -> raw
        }
        return text.trim()
    }

    private fun stripHtml(raw: String): String =
        raw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()

    private fun stripCaptionMarkup(raw: String): String =
        raw.lineSequence()
            .map { it.trim() }
            .filterNot { it.isBlank() }
            .filterNot { it.equals("WEBVTT", ignoreCase = true) }
            .filterNot { CUE_INDEX_LINE.matches(it) }
            .filterNot { TIMESTAMP_LINE.matches(it) }
            .joinToString(" ")
}
