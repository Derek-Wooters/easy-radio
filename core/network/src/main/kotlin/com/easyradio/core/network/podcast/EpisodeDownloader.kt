package com.easyradio.core.network.podcast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

private const val DOWNLOAD_BUFFER_SIZE = 8 * 1024

class EpisodeDownloader(
    private val client: OkHttpClient,
    private val downloadsDir: File,
) {

    suspend fun download(id: String, audioUrl: String, onProgress: (Float) -> Unit = {}): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(audioUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body
                val totalBytes = body.contentLength()

                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destination = File(downloadsDir, "${id.hashCode()}.audio")
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                        var bytesRead = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            bytesRead += read
                            // A missing/unknown Content-Length (totalBytes <= 0) can't produce a
                            // meaningful fraction -- better to report nothing than a wrong number.
                            if (totalBytes > 0) onProgress((bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f))
                        }
                    }
                }
                destination.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    fun delete(localFilePath: String) {
        File(localFilePath).delete()
    }
}
