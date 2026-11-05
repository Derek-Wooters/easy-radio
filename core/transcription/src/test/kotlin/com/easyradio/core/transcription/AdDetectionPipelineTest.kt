package com.easyradio.core.transcription

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AdDetectionPipelineTest {

    private class FakeAudioDecoder : AudioDecoder {
        override suspend fun decodeTo16kHzMonoWav(audioFilePath: String): String = "$audioFilePath.wav"
    }

    private class FakeWhisperTranscriber(private val segments: List<WhisperSegment>) : WhisperTranscriber {
        override suspend fun transcribe(wavFilePath: String): List<WhisperSegment> = segments
    }

    @Test
    fun `wires audio decoding and transcription into AdSegmentDetector`() = runTest {
        val adRead = (0 until 80).joinToString(" ") { "sponsorword$it" }
        val transcriber = FakeWhisperTranscriber(
            listOf(WhisperSegment(startMs = 0, endMs = 20_000, text = adRead)),
        )
        val pipeline = AdDetectionPipeline(FakeAudioDecoder(), transcriber)

        val segments = pipeline.detect(localAudioFilePath = "episode.audio", officialTranscriptText = "none of this")

        assertThat(segments).hasSize(1)
        assertThat(segments.single().startMs).isEqualTo(0)
    }

    @Test
    fun `matching official transcript content is not flagged as an ad`() = runTest {
        val realContent = (0 until 80).joinToString(" ") { "word$it" }
        val transcriber = FakeWhisperTranscriber(
            listOf(WhisperSegment(startMs = 0, endMs = 20_000, text = realContent)),
        )
        val pipeline = AdDetectionPipeline(FakeAudioDecoder(), transcriber)

        val segments = pipeline.detect(localAudioFilePath = "episode.audio", officialTranscriptText = realContent)

        assertThat(segments).isEmpty()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `rejects a concurrent detect call while one is already running`() = runTest {
        val releaseFirstCall = CompletableDeferred<Unit>()
        var transcribeCallCount = 0
        val slowTranscriber = object : WhisperTranscriber {
            override suspend fun transcribe(wavFilePath: String): List<WhisperSegment> {
                transcribeCallCount++
                releaseFirstCall.await()
                return emptyList()
            }
        }
        val pipeline = AdDetectionPipeline(FakeAudioDecoder(), slowTranscriber)

        launch { pipeline.detect("episode.audio", "transcript") }
        runCurrent() // let the first call reach transcribe() and suspend on releaseFirstCall

        var threwBusy = false
        try {
            pipeline.detect("episode.audio", "transcript")
        } catch (e: AdDetectionBusyException) {
            threwBusy = true
        }

        assertThat(threwBusy).isTrue()
        assertThat(transcribeCallCount).isEqualTo(1)

        releaseFirstCall.complete(Unit)
    }
}
