package com.easyradio.core.network.podcast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class EpisodeDownloader(
    private val client: OkHttpClient,
    private val downloadsDir: File,
) {

    suspend fun download(id: String, audioUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(audioUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null

                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destination = File(downloadsDir, "${id.hashCode()}.audio")
                body.byteStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
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
