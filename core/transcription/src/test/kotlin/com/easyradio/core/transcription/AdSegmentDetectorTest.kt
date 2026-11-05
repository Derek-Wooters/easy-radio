package com.easyradio.core.transcription

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Regression test using the real "HR. 1 -- Frog Marriage" episode of The Power Trip: a
 * genuine on-device whisper.cpp transcript of the downloaded audio, diffed against the
 * show's own official transcript. This reproduces, through the production Kotlin pipeline,
 * the same 6 ad blocks (~873.8s total) this feature's offline Python research spike already
 * validated against duration math and manual review -- porting an already-known-correct
 * result across runtimes rather than hoping a fresh port is correct.
 */
class AdSegmentDetectorTest {

    private val toleranceMs = 2_000L

    @Test
    fun `detects the 6 known ad blocks in the real Frog Marriage episode`() {
        val segments = AdSegmentDetector.detect(loadWhisperSegments(), loadOfficialTranscriptText())

        val expectedMs = listOf(
            0L to 119_700L,
            987_700L to 1_146_400L,
            1_914_800L to 2_073_500L,
            2_742_100L to 2_898_500L,
            3_945_900L to 4_138_500L,
            5_025_700L to 5_113_400L,
        )

        assertThat(segments).hasSize(expectedMs.size)
        segments.zip(expectedMs).forEachIndexed { index, (actual, expected) ->
            assertThat(actual.startMs).isWithin(toleranceMs).of(expected.first)
            assertThat(actual.endMs).isWithin(toleranceMs).of(expected.second)
        }

        val totalAdSeconds = segments.sumOf { it.endMs - it.startMs } / 1000.0
        assertThat(totalAdSeconds).isWithin(15.0).of(873.8)
    }

    private fun loadOfficialTranscriptText(): String {
        val timestampLine = Regex("""^\d{2}:\d{2}:\d{2}$""")
        val sb = StringBuilder()
        for (raw in resourceLines("frog_marriage_official_transcript.txt")) {
            val line = raw.trim()
            if (line.isEmpty() || timestampLine.matches(line)) continue
            val text = if (line.startsWith("Speaker") && line.contains(":")) {
                line.substringAfter(":").trim()
            } else {
                line
            }
            sb.append(text).append(' ')
        }
        return sb.toString()
    }

    private fun loadWhisperSegments(): List<WhisperSegment> {
        val segmentPattern = Regex(
            """\[(\d{2}):(\d{2}):(\d{2})\.(\d{3}) --> (\d{2}):(\d{2}):(\d{2})\.(\d{3})\]\s+(.*)""",
        )
        return resourceLines("frog_marriage_whisper_transcript.txt").mapNotNull { raw ->
            val match = segmentPattern.matchEntire(raw.trim()) ?: return@mapNotNull null
            val (sh, sm, ss, sms, eh, em, es, ems, text) = match.destructured
            val start = (sh.toLong() * 3600 + sm.toLong() * 60 + ss.toLong()) * 1000 + sms.toLong()
            val end = (eh.toLong() * 3600 + em.toLong() * 60 + es.toLong()) * 1000 + ems.toLong()
            WhisperSegment(start, end, text)
        }
    }

    private fun resourceLines(name: String): List<String> =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing test resource: $name" }
            .bufferedReader(Charsets.UTF_8)
            .readLines()
}
