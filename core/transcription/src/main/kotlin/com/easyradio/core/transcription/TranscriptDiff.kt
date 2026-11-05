package com.easyradio.core.transcription

/** A single transcribed word from the real downloaded audio, positioned in playback time. */
data class TimedWord(val word: String, val timestampMs: Long)

/**
 * Finds word runs in the real (whisper-transcribed) audio that have no corresponding match
 * anywhere in the show's own official transcript -- i.e. content present in the real audio
 * but absent from what the publisher wrote down. These runs are ad candidates; the final
 * ad/not-ad call is [AdCandidateFilter]'s job, since plenty of unmatched runs are just
 * ASR-vs-human wording drift rather than real ads.
 *
 * Ratcliff/Obershelp longest-contiguous-match algorithm (equivalent to Python's
 * difflib.SequenceMatcher.get_matching_blocks), not a plain LCS -- LCS permits non-contiguous
 * matches that don't yield the contiguous "unmatched run" shape downstream filtering needs.
 */
object TranscriptDiff {

    /** Matches shorter than this are treated as coincidental overlap, not real alignment. */
    private const val MIN_MATCH_WORDS = 4

    fun findUnmatchedRuns(realWords: List<TimedWord>, officialWords: List<String>): List<List<TimedWord>> {
        val realTokens = realWords.map { it.word }
        val covered = BooleanArray(realTokens.size)

        markMatches(officialWords, 0, officialWords.size, realTokens, 0, realTokens.size, covered)

        val runs = mutableListOf<List<TimedWord>>()
        var i = 0
        while (i < realWords.size) {
            if (covered[i]) {
                i++
                continue
            }
            val start = i
            while (i < realWords.size && !covered[i]) i++
            runs += realWords.subList(start, i)
        }
        return runs
    }

    private fun markMatches(
        a: List<String>,
        aLo: Int,
        aHi: Int,
        b: List<String>,
        bLo: Int,
        bHi: Int,
        covered: BooleanArray,
    ) {
        if (aLo >= aHi || bLo >= bHi) return
        val match = longestMatch(a, aLo, aHi, b, bLo, bHi) ?: return
        if (match.size >= MIN_MATCH_WORDS) {
            for (k in 0 until match.size) covered[match.bStart + k] = true
        }
        markMatches(a, aLo, match.aStart, b, bLo, match.bStart, covered)
        markMatches(a, match.aStart + match.size, aHi, b, match.bStart + match.size, bHi, covered)
    }

    private data class Match(val aStart: Int, val bStart: Int, val size: Int)

    /** Longest contiguous run common to a[aLo,aHi) and b[bLo,bHi), via a hash index on `a`. */
    private fun longestMatch(a: List<String>, aLo: Int, aHi: Int, b: List<String>, bLo: Int, bHi: Int): Match? {
        val positionsInA = HashMap<String, MutableList<Int>>()
        for (i in aLo until aHi) {
            positionsInA.getOrPut(a[i]) { mutableListOf() }.add(i)
        }

        var bestA = aLo
        var bestB = bLo
        var bestSize = 0
        var runLengths = HashMap<Int, Int>()

        for (j in bLo until bHi) {
            val newRunLengths = HashMap<Int, Int>()
            for (i in positionsInA[b[j]].orEmpty()) {
                val runLength = (runLengths[i - 1] ?: 0) + 1
                newRunLengths[i] = runLength
                if (runLength > bestSize) {
                    bestSize = runLength
                    bestA = i - runLength + 1
                    bestB = j - runLength + 1
                }
            }
            runLengths = newRunLengths
        }

        return if (bestSize > 0) Match(bestA, bestB, bestSize) else null
    }
}
