package com.easyradio.core.transcription

/**
 * Parallel-array result from the native transcribe() call -- deliberately not a List<WhisperSegment>
 * directly, since constructing arbitrary Kotlin objects from JNI is far more fragile than
 * marshalling primitive/String arrays. [NativeWhisperTranscriber] zips this into real segments.
 */
internal class NativeSegments(
    val startMs: LongArray,
    val endMs: LongArray,
    val texts: Array<String>,
)

internal object WhisperNative {
    init {
        // Must load before whisper_jni -- whisper_jni's OpenMP-enabled ggml build links
        // against this at runtime, and the NDK doesn't put it on the default library path.
        System.loadLibrary("omp")
        System.loadLibrary("whisper_jni")
    }

    /** [pcm] must be 16kHz mono, normalized to [-1, 1], with exactly [sampleCount] valid samples. */
    external fun transcribe(modelPath: String, pcm: FloatArray, sampleCount: Int, numThreads: Int): NativeSegments
}
