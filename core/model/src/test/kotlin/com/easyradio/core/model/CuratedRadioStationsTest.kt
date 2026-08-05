package com.easyradio.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CuratedRadioStationsTest {

    @Test
    fun `curated list includes the KFAN test station`() {
        assertThat(CuratedRadioStations.ALL).contains(RadioStation.KFAN_TEST_STATION)
    }

    @Test
    fun `curated list has more than one station`() {
        assertThat(CuratedRadioStations.ALL.size).isAtLeast(3)
    }

    @Test
    fun `curated list has no duplicate ids`() {
        val ids = CuratedRadioStations.ALL.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }
}
