package com.easyradio.core.network.podcast

import retrofit2.http.GET
import retrofit2.http.Query

interface ItunesSearchApi {

    @GET("search")
    suspend fun searchPodcasts(
        @Query("term") term: String,
        @Query("media") media: String = "podcast",
        @Query("limit") limit: Int = 20,
    ): ItunesSearchResponseDto
}
