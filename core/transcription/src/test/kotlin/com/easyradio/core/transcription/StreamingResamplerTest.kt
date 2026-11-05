package com.easyradio.core.transcription

import com.google.common.truth.Truth.assertThat
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Test

class StreamingResamplerTest {

    /** Reference: whole-buffer linear interpolation, independently implemented from the streaming version. */
    private fun resampleWhole(pcm: ShortArray, sourceRate: Int, targetRate: Int): ShortArray {
        val ratio = targetRate.toDouble() / sourceRate.toDouble()
        val outLength = (pcm.size * ratio).toInt()
        val out = ShortArray(outLength)
        for (i in 0 until outLength) {
            val srcPos = i / ratio
            val srcIndex = srcPos.toInt()
            val frac = srcPos - srcIndex
            val a = pcm[srcIndex.coerceIn(0, pcm.size - 1)]
            val b = pcm[(srcIndex + 1).coerceIn(0, pcm.size - 1)]
            out[i] = (a + (b - a) * frac).toInt().toShort()
        }
        return out
    }

    private fun sineWave(sampleCount: Int, sampleRate: Int, freqHz: Double): ShortArray =
        ShortArray(sampleCount) { i -> (10_000 * sin(2 * Math.PI * freqHz * i / sampleRate)).toInt().toShort() }

    @Test
    fun `chunked processing matches whole-buffer resampling for a real 44100 to 16000 conversion`() {
        val signal = sineWave(sampleCount = 44_100 * 5, sampleRate = 44_100, freqHz = 440.0)
        val expected = resampleWhole(signal, 44_100, 16_000)

        val resampler = StreamingResampler(44_100, 16_000)
        val chunkSize = 2048
        val actual = mutableListOf<Short>()
        var offset = 0
        while (offset < signal.size) {
            val end = minOf(offset + chunkSize, signal.size)
            actual += resampler.process(signal.copyOfRange(offset, end)).toList()
            offset = end
        }

        // Streaming and whole-buffer resampling can differ by one sample at the very tail
        // (the streaming version holds back an incomplete trailing interpolation window until
        // more data arrives, which never comes at true end-of-stream) -- not a correctness bug.
        assertThat(actual.size).isWithin(1).of(expected.size)
        val compareLength = minOf(actual.size, expected.size)
        for (i in 0 until compareLength) {
            assertThat(actual[i].toInt()).isWithin(1).of(expected[i].toInt())
        }
    }

    @Test
    fun `handles arbitrary chunk boundaries without crashing or losing continuity`() {
        val signal = sineWave(sampleCount = 100_000, sampleRate = 44_100, freqHz = 220.0)
        val random = Random(42)
        val resampler = StreamingResampler(44_100, 16_000)
        var offset = 0
        var totalOut = 0
        while (offset < signal.size) {
            val chunkSize = random.nextInt(1, 500)
            val end = minOf(offset + chunkSize, signal.size)
            totalOut += resampler.process(signal.copyOfRange(offset, end)).size
            offset = end
        }

        val expectedApprox = signal.size * (16_000.0 / 44_100.0)
        assertThat(totalOut.toDouble()).isWithin(expectedApprox * 0.01).of(expectedApprox)
    }

    @Test
    fun `pass-through when source and target rates match`() {
        val resampler = StreamingResampler(16_000, 16_000)
        val chunk = shortArrayOf(1, 2, 3, 4, 5)
        assertThat(resampler.process(chunk).toList()).containsExactly(1.toShort(), 2.toShort(), 3.toShort(), 4.toShort(), 5.toShort()).inOrder()
    }
}
