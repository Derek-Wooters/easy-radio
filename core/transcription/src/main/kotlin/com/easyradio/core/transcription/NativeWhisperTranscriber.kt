package com.easyradio.core.transcription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Real, on-device implementation of [WhisperTranscriber], backed by the vendored whisper.cpp via JNI. */
class NativeWhisperTranscriber(
    private val modelPath: String,
    private val numThreads: Int = 4,
) : WhisperTranscriber {

    override suspend fun transcribe(wavFilePath: String): List<WhisperSegment> = withContext(Dispatchers.Default) {
        val pcm = WavReader.readPcm16MonoAsFloat(wavFilePath)
        val result = WhisperNative.transcribe(modelPath, pcm, pcm.size, numThreads)
        result.startMs.indices.map { i ->
            WhisperSegment(startMs = result.startMs[i], endMs = result.endMs[i], text = result.texts[i])
        }
    }
}
