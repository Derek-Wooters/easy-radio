package com.easyradio.core.transcription

/**
 * Transcribes a 16kHz mono PCM16 WAV file into timestamped segments. The real implementation
 * (native whisper.cpp via JNI) is this feature's Phase 1, not yet built -- this interface is
 * the seam [AdDetectionPipeline] is written against so the rest of the pipeline is already
 * real, testable code that only needs a concrete transcriber plugged in.
 */
interface WhisperTranscriber {
    suspend fun transcribe(wavFilePath: String): List<WhisperSegment>
}
