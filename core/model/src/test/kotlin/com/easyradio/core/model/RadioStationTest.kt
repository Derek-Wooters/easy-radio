package com.easyradio.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RadioStationTest {

    @Test
    fun `kfan test station has an https stream url`() {
        val station = RadioStation.KFAN_TEST_STATION

        assertThat(station.streamUrl).startsWith("https://")
    }

    @Test
    fun `kfan test station has a non-blank id and name`() {
        val station = RadioStation.KFAN_TEST_STATION

        assertThat(station.id).isNotEmpty()
        assertThat(station.name).isNotEmpty()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank id is rejected`() {
        RadioStation(id = "", name = "Test", streamUrl = "https://example.com/stream")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-https stream url is rejected`() {
        RadioStation(id = "test", name = "Test", streamUrl = "http://example.com/stream")
    }
}
