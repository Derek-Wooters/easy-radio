package com.easyradio.core.network.radiobrowser

import retrofit2.http.GET
import retrofit2.http.Query

interface RadioBrowserApi {

    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("name") name: String,
        @Query("limit") limit: Int = 20,
        @Query("hidebroken") hideBroken: Boolean = true,
    ): List<RadioBrowserStationDto>
}
