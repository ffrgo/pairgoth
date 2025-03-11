package com.iqoid.pairgoth.client.network

import com.iqoid.pairgoth.client.model.Player
import com.iqoid.pairgoth.client.model.Search
import com.iqoid.pairgoth.client.model.Tournament
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface PairGothApiService {
    @GET("list")
    suspend fun getTours(): Response<List<Tournament>>

    @POST("search")
    suspend fun searchPlayer(@Body search: Search): Response<List<Player>>

    @POST("tour/{tourId}/part")
    suspend fun registerPlayer(
        @Path("tourId") tourId: String,
        @Body player: Player
    ): Response<Player>
}
