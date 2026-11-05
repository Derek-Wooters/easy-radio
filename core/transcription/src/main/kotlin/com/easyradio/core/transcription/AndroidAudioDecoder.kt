package com.easyradio.core.transcription

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "AndroidAudioDecoder"

/**
 * Real, on-device implementation of [AudioDecoder]: demuxes a downloaded episode's audio file
 * with [MediaExtractor], decodes it with [MediaCodec], downmixes to mono, and resamples to
 * 16kHz -- whisper.cpp's required input. There's no ffmpeg/soundfile equivalent on Android, so
 * this is new, nontrivial code with no in-repo precedent.
 *
 * Decode -> downmix -> resample -> write is fully streamed per output chunk rather than
 * buffering the whole file in memory: a long episode's decoded stereo 44.1kHz PCM can be
 * several hundred MB, which blew Android's ~256MB default app heap and crashed with
 * OutOfMemoryError when this held the entire file in memory at once (confirmed on-device).
 * Peak memory here is proportional to one MediaCodec output buffer, not the episode length.
 */
class AndroidAudioDecoder(private val outputDir: File) : AudioDecoder {

    companion object {
        private const val TARGET_SAMPLE_RATE = 16_000
        private const val TIMEOUT_US = 10_000L
        private const val WAV_HEADER_SIZE = 44
    }

    override suspend fun decodeTo16kHzMonoWav(audioFilePath: String): String = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        extractor.setDataSource(audioFilePath)

        val trackIndex = selectAudioTrack(extractor)
        extractor.selectTrack(trackIndex)
        val format = extractor.getTrackFormat(trackIndex)
        val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME)) { "track has no MIME type" }
        val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        Log.d(TAG, "decode start: mime=$mime sourceSampleRate=$sourceSampleRate sourceChannels=$sourceChannels")

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val outputFile = File(outputDir, "${File(audioFilePath).name}.wav")
        val resampler = StreamingResampler(sourceSampleRate, TARGET_SAMPLE_RATE)
        var samplesWritten = 0L

        RandomAccessFile(outputFile, "rw").use { raf ->
            raf.setLength(0)
            raf.write(ByteArray(WAV_HEADER_SIZE)) // placeholder, patched below once the real size is known

            decodeLoop(extractor, codec) { chunk ->
                val mono = downmixToMono(chunk, sourceChannels)
                val resampled = resampler.process(mono)
                if (resampled.isNotEmpty()) {
                    raf.write(shortsToBytes(resampled))
                    samplesWritten += resampled.size
                }
            }

            patchWavHeader(raf, samplesWritten, TARGET_SAMPLE_RATE)
        }

        codec.stop()
        codec.release()
        extractor.release()

        Log.d(TAG, "wav written: ${outputFile.absolutePath} ($samplesWritten samples)")
        outputFile.absolutePath
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        error("no audio track found")
    }

    /** Invokes [onChunk] once per decoded output buffer rather than returning the whole file. */
    private fun decodeLoop(extractor: MediaExtractor, codec: MediaCodec, onChunk: (ShortArray) -> Unit) {
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false
        var iterations = 0

        while (!sawOutputEOS) {
            iterations++
            if (iterations % 2000 == 0) {
                Log.d(TAG, "decodeLoop: iter=$iterations sawInputEOS=$sawInputEOS sampleTime=${extractor.sampleTime}")
            }
            if (!sawInputEOS) {
                val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inputIndex >= 0) {
                    val inputBuffer = requireNotNull(codec.getInputBuffer(inputIndex))
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEOS = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            if (outputIndex >= 0) {
                if (bufferInfo.size > 0) {
                    val outputBuffer = requireNotNull(codec.getOutputBuffer(outputIndex))
                    val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val chunk = ShortArray(shortBuffer.remaining())
                    shortBuffer.get(chunk)
                    onChunk(chunk)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    sawOutputEOS = true
                }
            }
        }
        Log.d(TAG, "decodeLoop: finished after $iterations iterations")
    }

    private fun downmixToMono(pcm: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return pcm
        val frameCount = pcm.size / channels
        val mono = ShortArray(frameCount)
        for (i in 0 until frameCount) {
            var sum = 0
            for (c in 0 until channels) sum += pcm[i * channels + c]
            mono[i] = (sum / channels).toShort()
        }
        return mono
    }

    private fun shortsToBytes(samples: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) buffer.putShort(sample)
        return buffer.array()
    }

    private fun patchWavHeader(raf: RandomAccessFile, sampleCount: Long, sampleRate: Int) {
        val dataSize = sampleCount * 2
        val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt((36 + dataSize).toInt())
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16) // PCM header size
        header.putShort(1) // PCM format
        header.putShort(1) // mono
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2) // byte rate (mono, 16-bit)
        header.putShort(2) // block align
        header.putShort(16) // bits per sample
        header.put("data".toByteArray())
        header.putInt(dataSize.toInt())
        raf.seek(0)
        raf.write(header.array())
    }
}

/**
 * Linear-interpolation resampler that carries a small amount of state (a handful of samples,
 * not the whole file) between [process] calls so chunk-boundary output stays continuous.
 */
internal class StreamingResampler(sourceRate: Int, private val targetRate: Int) {

    private val ratio = targetRate.toDouble() / sourceRate.toDouble()
    private val passThrough = sourceRate == targetRate
    private var carry = ShortArray(0)
    private var phase = 0.0

    fun process(chunk: ShortArray): ShortArray {
        if (passThrough) return chunk
        if (chunk.isEmpty()) return ShortArray(0)

        val buf = if (carry.isEmpty()) chunk else carry + chunk
        if (buf.size < 2) {
            carry = buf
            return ShortArray(0)
        }

        val step = 1.0 / ratio
        val estimatedCount = ((buf.size - phase) * ratio).toInt() + 1
        val out = ShortArray(maxOf(estimatedCount, 0))
        var outCount = 0
        var pos = phase

        while (true) {
            val idx = pos.toInt()
            if (idx + 1 >= buf.size) break
            val frac = pos - idx
            val a = buf[idx]
            val b = buf[idx + 1]
            out[outCount] = (a + (b - a) * frac).toInt().toShort()
            outCount++
            pos += step
        }

        val consumedUpTo = pos.toInt().coerceIn(0, buf.size)
        carry = buf.copyOfRange(consumedUpTo, buf.size)
        phase = pos - consumedUpTo

        return if (outCount == out.size) out else out.copyOf(outCount)
    }
}
