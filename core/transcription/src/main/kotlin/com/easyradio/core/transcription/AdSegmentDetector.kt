package com.easyradio.core.transcription

import com.easyradio.core.model.AdSegment

/** One transcribed segment of the real downloaded audio, as whisper.cpp produces it. */
data class WhisperSegment(val startMs: Long, val endMs: Long, val text: String)

private val WORD_PATTERN = Regex("[a-z0-9']+")

private fun tokenize(text: String): List<String> =
    WORD_PATTERN.findAll(text.lowercase()).map { it.value }.toList()

/** Spreads a segment's words evenly across its [startMs, endMs) span for per-word timing. */
private fun WhisperSegment.toTimedWords(): List<TimedWord> {
    val words = tokenize(text)
    if (words.isEmpty()) return emptyList()
    val span = (endMs - startMs).coerceAtLeast(10L)
    return words.mapIndexed { i, word -> TimedWord(word, startMs + span * i / words.size) }
}

/**
 * End-to-end entry point: given the real whisper.cpp transcription of a downloaded episode
 * and the show's own official transcript text, returns the detected ad segments.
 */
object AdSegmentDetector {

    fun detect(realTranscript: List<WhisperSegment>, officialTranscriptText: String): List<AdSegment> {
        val realWords = realTranscript.flatMap { it.toTimedWords() }
        val officialWords = tokenize(officialTranscriptText)
        val unmatchedRuns = TranscriptDiff.findUnmatchedRuns(realWords, officialWords)
        return AdCandidateFilter.detect(unmatchedRuns)
    }
}
