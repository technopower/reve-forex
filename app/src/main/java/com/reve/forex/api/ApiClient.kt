package com.reve.forex.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE = "http://10.0.2.2:8000/"
    val api: ForexApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ForexApi::class.java)
    }
}
