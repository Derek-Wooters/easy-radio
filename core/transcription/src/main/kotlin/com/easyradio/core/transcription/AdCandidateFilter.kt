package com.easyradio.core.transcription

import com.easyradio.core.model.AdSegment

/**
 * Separates genuine ad reads from ordinary ASR-vs-official-transcript wording drift among
 * the unmatched word runs [TranscriptDiff] finds. Real ad reads are dense -- hundreds of
 * words over 85-190+ seconds of a typical mid-roll/pre-roll read -- while ASR noise and minor
 * paraphrasing produce short, sparse runs. Thresholds were tuned against a real downloaded
 * episode during this feature's research spike (see TranscriptDiffTest's fixture, which
 * reproduces that same 6-block result through this exact pipeline).
 */
object AdCandidateFilter {

    /** Runs shorter than this are ASR/paraphrasing noise, not a real ad read. */
    private const val MIN_AD_WORD_COUNT = 60

    /** Separate ad-adjacent runs within this gap get merged into one segment. */
    private const val MERGE_GAP_MS = 5_000L

    /** Below this unique-word ratio, a run is almost certainly an ASR repetition glitch. */
    private const val MAX_UNIQUE_WORD_RATIO_FOR_HALLUCINATION = 0.15
    private const val MIN_WORDS_TO_CHECK_HALLUCINATION = 10

    fun detect(unmatchedRuns: List<List<TimedWord>>): List<AdSegment> {
        val candidates = unmatchedRuns
            .filter { it.size >= MIN_AD_WORD_COUNT }
            .filterNot(::isRepetitionHallucination)
        return mergeAdjacent(candidates)
    }

    private fun isRepetitionHallucination(run: List<TimedWord>): Boolean {
        if (run.size < MIN_WORDS_TO_CHECK_HALLUCINATION) return false
        val uniqueRatio = run.map { it.word }.distinct().size.toDouble() / run.size
        return uniqueRatio <= MAX_UNIQUE_WORD_RATIO_FOR_HALLUCINATION
    }

    private fun mergeAdjacent(runs: List<List<TimedWord>>): List<AdSegment> {
        if (runs.isEmpty()) return emptyList()
        val segments = runs
            .map { AdSegment(it.first().timestampMs, it.last().timestampMs) }
            .sortedBy { it.startMs }

        val merged = mutableListOf<AdSegment>()
        for (segment in segments) {
            val last = merged.lastOrNull()
            if (last != null && segment.startMs - last.endMs < MERGE_GAP_MS) {
                merged[merged.lastIndex] = last.copy(endMs = maxOf(last.endMs, segment.endMs))
            } else {
                merged += segment
            }
        }
        return merged
    }
}
