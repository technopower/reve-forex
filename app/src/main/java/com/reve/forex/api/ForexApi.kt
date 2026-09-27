package com.reve.forex.api

import retrofit2.http.GET

data class MarketQuote(
    val symbol: String,
    val price: Double,
    val change: Double = 0.0,
    val percent_change: Double = 0.0,
    val datetime: String? = null,
    val source: String = "LIVE"
)

data class MarketsResponse(
    val success: Boolean = false,
    val provider: String = "DEMO",
    val markets: List<MarketQuote> = emptyList(),
    val errors: List<Map<String, String>> = emptyList()
)

data class Signal(
    val id: Int,
    val symbol: String,
    val timeframe: String,
    val direction: String,
    val score: Int,
    val entry: Double?,
    val stop_loss: Double?,
    val take_profit: Double?,
    val reasons: String = "",
    val created_at: String = "",
    val expires_at: String = "",
    val status: String = "ACTIVE",
    val source: String = "LIVE"
)

data class SignalsResponse(
    val success: Boolean = false,
    val signals: List<Signal> = emptyList()
)

interface ForexApi {
    @GET("health") suspend fun health(): Map<String, Any>
    @GET("signals") suspend fun signals(): SignalsResponse
    @GET("markets") suspend fun markets(): MarketsResponse
}
