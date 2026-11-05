package com.easyradio.core.transcription

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "WhisperBenchmark"

/**
 * Measures the real-time factor of the on-device native transcribe() call directly, without
 * waiting out a real hour-long episode. Uses synthetic low-amplitude noise rather than silence:
 * whisper.cpp's no-speech heuristic can skip decoding early on true silence, which would
 * understate the compute cost of real speech and defeat the point of this benchmark.
 *
 * Guards against a real regression hit during development: AGP maps the Gradle "debug" variant
 * straight to CMake's Debug build type, which silently drops all compiler optimization
 * (confirmed via compile_commands.json -- no -O flag at all). That alone made transcription run
 * at ~0.2x realtime, over 25x slower than the ~6-7x realtime this asserts on now that
 * core/transcription/build.gradle.kts forces -DCMAKE_BUILD_TYPE=RelWithDebInfo.
 */
@RunWith(AndroidJUnit4::class)
class WhisperNativeBenchmarkTest {

    @Test
    fun measuresRealtimeFactorForSyntheticAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val modelFile = WhisperModelProvider.modelFile(context)

        val durationSeconds = 60
        val sampleCount = durationSeconds * 16_000
        val random = Random(seed = 42)
        val pcm = FloatArray(sampleCount) { (random.nextFloat() - 0.5f) * 0.1f }

        val elapsedMs = measureTimeMillis {
            WhisperNative.transcribe(modelFile.absolutePath, pcm, pcm.size, numThreads = 4)
        }

        val realtimeFactor = (durationSeconds * 1000.0) / elapsedMs
        Log.i(TAG, "Transcribed ${durationSeconds}s of synthetic audio in ${elapsedMs}ms ($realtimeFactor x realtime)")

        // Floor set well below the ~6-7x measured on a Pixel 6 so this only catches a real
        // regression (e.g. the native build silently losing optimization again), not normal
        // device-to-device variance.
        assertThat(realtimeFactor).isGreaterThan(3.0)
    }
}
