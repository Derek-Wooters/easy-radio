package com.easyradio.core.network.podcast

import com.easyradio.core.model.Episode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class EpisodeDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var downloadsDir: File
    private lateinit var downloader: EpisodeDownloader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloadsDir = File.createTempFile("downloads", "").apply {
            delete()
            mkdirs()
        }
        downloader = EpisodeDownloader(client = OkHttpClient(), downloadsDir = downloadsDir)
    }

    @After
    fun tearDown() {
        server.shutdown()
        downloadsDir.deleteRecursively()
    }

    private fun episode(url: String) = Episode(
        id = "ep-1",
        podcastId = "podcast-1",
        title = "Test Episode",
        audioUrl = url,
        publishedAtEpochMillis = null,
        durationSeconds = null,
    )

    @Test
    fun `downloads the episode body to a local file`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("fake-audio-bytes"))
        val url = server.url("/ep-1.mp3").toString()

        val path = downloader.download(episode(url))

        assertThat(path).isNotNull()
        val file = File(path!!)
        assertThat(file.exists()).isTrue()
        assertThat(file.readText()).isEqualTo("fake-audio-bytes")
    }

    @Test
    fun `returns null when the server responds with an error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val url = server.url("/missing.mp3").toString()

        val path = downloader.download(episode(url))

        assertThat(path).isNull()
    }

    @Test
    fun `delete removes the downloaded file`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("fake-audio-bytes"))
        val url = server.url("/ep-1.mp3").toString()
        val path = downloader.download(episode(url))!!

        downloader.delete(path)

        assertThat(File(path).exists()).isFalse()
    }
}
