package com.iqoid.pairgoth.client.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkManager {
    private const val BASE_URL = "http://192.168.0.138:8080/api/" // Replace with your API's base URL

    val pairGothApiService: PairGothApiService by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY //optional logging
        }

        // Create an interceptor to add the Accept header
        val acceptHeaderInterceptor = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/json") // Add the Accept header
                    .build()
                return chain.proceed(request)
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor) //optional logging
            .addInterceptor(acceptHeaderInterceptor) // Add the Accept header interceptor
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(PairGothApiService::class.java)
    }
}
