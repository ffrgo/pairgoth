package com.iqoid.pairgoth.client.network

import com.iqoid.pairgoth.client.model.GamesResponse
import com.iqoid.pairgoth.client.model.Game
import com.iqoid.pairgoth.client.model.Player
import com.iqoid.pairgoth.client.model.Search
import com.iqoid.pairgoth.client.model.TourListResponse
import com.iqoid.pairgoth.client.model.TournamentDetails
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface PairGothApiService {
    @GET("tour/list")
    suspend fun getTours(): Response<TourListResponse>

    @POST("search")
    suspend fun searchPlayer(@Body search: Search): Response<List<Player>>

    @POST("tour/{tourId}/part")
    suspend fun registerPlayer(
        @Path("tourId") tourId: String,
        @Body player: Player
    ): Response<Player>

    @GET("tour/{tournamentId}")
    suspend fun getTournament(@Path("tournamentId") tournamentId: String): Response<TournamentDetails>

    @GET("tour/{tournamentId}/pair/{round}")
    suspend fun getPairing(@Path("tournamentId") tournamentId: String, @Path("round") round: Int): Response<GamesResponse>

    @GET("tour/{tournamentId}/part")
    suspend fun getParticipants(@Path("tournamentId") tournamentId: String): Response<List<Player>>

    @GET("tour/{tournamentId}/part/{playerId}")
    suspend fun getParticipant(@Path("tournamentId") tournamentId: String, @Path("playerId") playerId: String): Response<Player>
}

