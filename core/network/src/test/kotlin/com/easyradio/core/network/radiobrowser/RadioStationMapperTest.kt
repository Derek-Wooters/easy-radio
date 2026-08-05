package com.easyradio.core.network.radiobrowser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RadioStationMapperTest {

    @Test
    fun `verified https station maps to a playable RadioStation`() {
        val dto = RadioBrowserStationDto(
            stationUuid = "abc-123",
            name = "Test Station",
            urlResolved = "https://example.com/stream",
            homepage = "https://example.com",
            favicon = "https://example.com/favicon.png",
            lastCheckOk = 1,
        )

        val station = dto.toRadioStationOrNull()

        assertThat(station).isNotNull()
        assertThat(station!!.id).isEqualTo("abc-123")
        assertThat(station.name).isEqualTo("Test Station")
        assertThat(station.streamUrl).isEqualTo("https://example.com/stream")
        assertThat(station.imageUrl).isEqualTo("https://example.com/favicon.png")
    }

    @Test
    fun `station without a stable uuid falls back to the stream url as id`() {
        val dto = RadioBrowserStationDto(
            stationUuid = "",
            name = "Test Station",
            urlResolved = "https://example.com/stream",
            lastCheckOk = 1,
        )

        val station = dto.toRadioStationOrNull()

        assertThat(station!!.id).isEqualTo("https://example.com/stream")
    }

    @Test
    fun `non-https stream url is filtered out`() {
        val dto = RadioBrowserStationDto(
            name = "Test Station",
            urlResolved = "http://example.com/stream",
            lastCheckOk = 1,
        )

        assertThat(dto.toRadioStationOrNull()).isNull()
    }

    @Test
    fun `blank name is filtered out`() {
        val dto = RadioBrowserStationDto(
            name = "",
            urlResolved = "https://example.com/stream",
            lastCheckOk = 1,
        )

        assertThat(dto.toRadioStationOrNull()).isNull()
    }

    @Test
    fun `station that failed its last health check is filtered out`() {
        val dto = RadioBrowserStationDto(
            name = "Test Station",
            urlResolved = "https://example.com/stream",
            lastCheckOk = 0,
        )

        assertThat(dto.toRadioStationOrNull()).isNull()
    }
}
