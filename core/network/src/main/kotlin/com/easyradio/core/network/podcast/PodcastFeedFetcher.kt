package com.easyradio.core.network.podcast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class PodcastFeedFetcher(private val client: OkHttpClient) {

    suspend fun fetch(feedUrl: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(feedUrl).build()).execute().use { response ->
            if (!response.isSuccessful) return@withContext ""
            response.body?.string().orEmpty()
        }
    }
}
