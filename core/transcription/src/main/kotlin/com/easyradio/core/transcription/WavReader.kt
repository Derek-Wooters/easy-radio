package com.easyradio.core.transcription

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads a 16-bit PCM mono WAV file (the format [AndroidAudioDecoder] writes) into whisper.cpp's
 * expected float input. whisper_full() requires the whole episode as one contiguous float
 * buffer -- for a long episode that's unavoidably large (e.g. ~250MB for 85 minutes at 16kHz),
 * so this streams the file through a small fixed-size read buffer rather than also holding the
 * full raw byte array alongside the float array (confirmed on-device: that doubling blew the
 * app heap even after AndroidAudioDecoder's own streaming fix).
 */
internal object WavReader {

    private const val HEADER_SIZE = 44
    private const val READ_CHUNK_BYTES = 1 shl 20 // 1MB

    fun readPcm16MonoAsFloat(wavFilePath: String): FloatArray {
        val file = File(wavFilePath)
        val sampleCount = ((file.length() - HEADER_SIZE) / 2).toInt()
        val out = FloatArray(sampleCount)

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(HEADER_SIZE.toLong())
            val readBuffer = ByteArray(READ_CHUNK_BYTES)
            var outIndex = 0
            while (outIndex < sampleCount) {
                val bytesToRead = minOf(readBuffer.size, (sampleCount - outIndex) * 2)
                val bytesRead = raf.read(readBuffer, 0, bytesToRead)
                if (bytesRead <= 0) break
                val shortBuffer = ByteBuffer.wrap(readBuffer, 0, bytesRead)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                while (shortBuffer.hasRemaining() && outIndex < sampleCount) {
                    out[outIndex] = shortBuffer.get() / 32768f
                    outIndex++
                }
            }
        }
        return out
    }
}
