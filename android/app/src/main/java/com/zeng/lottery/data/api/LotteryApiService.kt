package com.zeng.lottery.data.api

import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.BaziDestinyRequest
import com.zeng.lottery.data.model.BaziRecommendationRequest
import com.zeng.lottery.data.model.BaziRecommendationResponse
import com.zeng.lottery.data.model.HealthResponse
import com.zeng.lottery.data.model.LotteryRulesResponse
import com.zeng.lottery.data.model.LotteryStats
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface LotteryApiService {

    @GET("/api/health")
    suspend fun checkHealth(): HealthResponse

    @GET("/api/lottery/rules")
    suspend fun getRules(): LotteryRulesResponse

    @GET("/api/lottery/stats/{type}")
    suspend fun getStats(@Path("type") type: String): LotteryStats

    @GET("/api/lottery/hot-cold/{type}")
    suspend fun getHotCold(
        @Path("type") type: String,
        @Query("window") window: Int = 30
    ): LotteryStats

    @GET("/api/lottery/omission/{type}")
    suspend fun getOmission(@Path("type") type: String): LotteryStats

    @POST("/api/bazi/destiny")
    suspend fun calculateDestiny(@Body request: BaziDestinyRequest): BaziDestiny

    @POST("/api/bazi/recommend")
    suspend fun getRecommendation(@Body request: BaziRecommendationRequest): BaziRecommendationResponse
}
