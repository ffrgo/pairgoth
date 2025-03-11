package com.iqoid.pairgoth.client.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkManager {
    private const val BASE_URL = "http://localhost:8080/api/tour/" // Replace with your API's base URL

    val pairGothApiService: PairGothApiService by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY //optional logging
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor) //optional logging
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(PairGothApiService::class.java)
    }
}
