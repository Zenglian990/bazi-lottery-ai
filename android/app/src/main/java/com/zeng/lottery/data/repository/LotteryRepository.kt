package com.zeng.lottery.data.repository

import android.content.Context
import com.zeng.lottery.data.api.ApiClient
import com.zeng.lottery.data.api.LotteryApiService
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.local.OfflineLotteryEngine
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.BaziDestinyRequest
import com.zeng.lottery.data.model.BaziRecommendationRequest
import com.zeng.lottery.data.model.BaziRecommendationResponse
import com.zeng.lottery.data.model.HealthResponse
import com.zeng.lottery.data.model.LotteryStats
import com.zeng.lottery.data.model.LotteryType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LotteryRepository(
    private val context: Context,
    private val apiService: LotteryApiService = ApiClient.apiService,
    private val offlineEngine: OfflineLotteryEngine = OfflineLotteryEngine(context)
) {
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    suspend fun checkHealth(): HealthResponse? {
        return try {
            val res = apiService.checkHealth()
            _isOffline.value = false
            res
        } catch (e: Exception) {
            _isOffline.value = true
            null
        }
    }

    suspend fun getDestiny(
        dateTime: LocalDateTime,
        isMale: Boolean = true,
        calendarType: String = "solar"
    ): BaziDestiny {
        return try {
            val req = BaziDestinyRequest(
                birthDatetime = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                calendarType = calendarType,
                gender = if (isMale) "male" else "female"
            )
            val remote = apiService.calculateDestiny(req)
            _isOffline.value = false
            remote
        } catch (e: Exception) {
            _isOffline.value = true
            BaziEngine.analyzeDestiny(dateTime, isMale)
        }
    }

    suspend fun getStats(lotteryType: LotteryType): LotteryStats {
        return try {
            val remote = apiService.getStats(lotteryType.code)
            _isOffline.value = false
            remote
        } catch (e: Exception) {
            _isOffline.value = true
            offlineEngine.computeStats(lotteryType)
        }
    }

    suspend fun getRecommendations(
        destiny: BaziDestiny,
        lotteryType: LotteryType,
        count: Int = 1,
        strategy: String = "balanced",
        wBazi: Float = 0.5f,
        wFreq: Float = 0.3f,
        wOmission: Float = 0.2f
    ): BaziRecommendationResponse {
        return try {
            val req = BaziRecommendationRequest(
                birthDatetime = destiny.solarDatetime.replace(" ", "T"),
                calendarType = "solar",
                lotteryType = lotteryType.code,
                count = count,
                strategy = strategy,
                wBazi = wBazi,
                wFreq = wFreq,
                wOmission = wOmission
            )
            val remote = apiService.getRecommendation(req)
            _isOffline.value = false
            remote
        } catch (e: Exception) {
            _isOffline.value = true
            offlineEngine.generateRecommendation(destiny, lotteryType, count, strategy)
        }
    }
}
