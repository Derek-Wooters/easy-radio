package com.easyradio.core.transcription

import android.content.Context
import java.io.File

/**
 * Copies the bundled whisper.cpp model asset to a stable file path on first use --
 * whisper_init_from_file needs a real filesystem path, not an Android asset stream.
 * Idempotent: subsequent calls are a no-op once the file already exists at the right size.
 */
object WhisperModelProvider {

    private const val ASSET_NAME = "ggml-base.en-q5_1.bin"

    fun modelFile(context: Context): File {
        val assetManager = context.assets
        val outFile = File(context.filesDir, ASSET_NAME)
        val expectedSize = assetManager.openFd(ASSET_NAME).use { it.length }
        if (!outFile.exists() || outFile.length() != expectedSize) {
            assetManager.open(ASSET_NAME).use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return outFile
    }
}
