package com.easyradio.core.transcription

import com.easyradio.core.model.AdSegment
import kotlinx.coroutines.sync.Mutex

/**
 * Thrown by [AdDetectionPipeline.detect] when another detection is already running. whisper.cpp's
 * `whisper_full()` is a long (tens of minutes, for a full episode), blocking JNI call with no
 * cancellation checkpoints: if WorkManager's execution budget expires mid-call and retries
 * [com.easyradio.app.workers.AdDetectionWorker], the original call keeps running on its own
 * thread regardless. Without this guard, a naive retry starts an entirely new decode+transcribe
 * cycle concurrently, stacking multiple ~250MB PCM buffers (plus native compute buffers) until
 * the heap OOMs -- observed on-device. Rejecting immediately, rather than queuing, lets the
 * caller (the worker) back off instead of piling up waiting coroutines of its own.
 */
class AdDetectionBusyException : Exception("Ad detection is already running for another episode")

/**
 * End-to-end orchestration: decode a downloaded episode's audio, transcribe it, and diff
 * against the show's official transcript to find ad segments. Written against
 * [AudioDecoder]/[WhisperTranscriber] rather than concrete implementations so this class is
 * fully real and unit-testable with fakes ahead of Phase 1 (the native whisper.cpp + on-device
 * audio decoding work) landing.
 */
class AdDetectionPipeline(
    private val audioDecoder: AudioDecoder,
    private val transcriber: WhisperTranscriber,
) {
    // One process-wide instance is built by EasyRadioGraph, so this mutex enforces at most one
    // detection running app-wide -- see [AdDetectionBusyException] for why that's necessary.
    private val mutex = Mutex()

    suspend fun detect(localAudioFilePath: String, officialTranscriptText: String): List<AdSegment> {
        if (!mutex.tryLock()) throw AdDetectionBusyException()
        try {
            val wavPath = audioDecoder.decodeTo16kHzMonoWav(localAudioFilePath)
            val whisperSegments = transcriber.transcribe(wavPath)
            return AdSegmentDetector.detect(whisperSegments, officialTranscriptText)
        } finally {
            mutex.unlock()
        }
    }
}
