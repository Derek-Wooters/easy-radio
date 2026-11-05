package com.easyradio.core.transcription

/**
 * Decodes a downloaded episode's audio file to 16kHz mono PCM16 WAV, whisper.cpp's required
 * input format. The real implementation (Android's MediaExtractor/MediaCodec -- there's no
 * ffmpeg/soundfile equivalent on-device) is this feature's Phase 1, not yet built; see
 * [WhisperTranscriber] for the same seam-now/implement-later approach.
 */
interface AudioDecoder {
    suspend fun decodeTo16kHzMonoWav(audioFilePath: String): String
}
