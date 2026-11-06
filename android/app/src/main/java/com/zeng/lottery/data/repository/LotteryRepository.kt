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

import kotlinx.coroutines.withTimeout

class LotteryRepository(
    private val context: Context,
    private val apiService: LotteryApiService = ApiClient.apiService,
    private val offlineEngine: OfflineLotteryEngine = OfflineLotteryEngine(context)
) {
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    suspend fun checkHealth(): HealthResponse? {
        return try {
            withTimeout(3500L) {
                val res = apiService.checkHealth()
                _isOffline.value = false
                res
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
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
            withTimeout(4000L) {
                val remote = apiService.calculateDestiny(req)
                _isOffline.value = false
                remote
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _isOffline.value = true
            BaziEngine.analyzeDestiny(dateTime, isMale)
        }
    }

    suspend fun getDestinyFromLunar(
        lunarDate: com.zeng.lottery.data.model.LunarDate,
        isMale: Boolean = true,
        userName: String = "缘主"
    ): BaziDestiny {
        return try {
            val solarDt = BaziEngine.lunarToSolar(lunarDate)
            val req = BaziDestinyRequest(
                birthDatetime = solarDt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                calendarType = "lunar",
                gender = if (isMale) "male" else "female",
                userName = userName,
                shichen = lunarDate.shichenZh,
                isLeapMonth = lunarDate.isLeap,
                lunarYear = lunarDate.year,
                lunarMonth = lunarDate.month,
                lunarDay = lunarDate.day,
                lunarHour = lunarDate.shichenZh
            )
            withTimeout(4000L) {
                val remote = apiService.calculateDestiny(req)
                _isOffline.value = false
                // Ensure lunarDatetime in remote response preserves standard formatted string
                val finalLunarText = "${lunarDate.year}年 (${lunarDate.ganzhiYear}${lunarDate.zodiac}年) 农历${lunarDate.monthZh}${lunarDate.dayZh} ${lunarDate.shichenZh}"
                remote.copy(lunarDatetime = finalLunarText, userName = userName)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _isOffline.value = true
            BaziEngine.analyzeDestinyFromLunar(lunarDate, isMale, userName)
        }
    }

    suspend fun getStats(lotteryType: LotteryType): LotteryStats {
        return try {
            withTimeout(4000L) {
                val remote = apiService.getStats(lotteryType.code)
                _isOffline.value = false
                remote
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _isOffline.value = true
            offlineEngine.computeStats(lotteryType)
        }
    }

    suspend fun getHistoryDraws(lotteryType: LotteryType, limit: Int = 50): List<com.zeng.lottery.data.model.DrawItem> {
        return try {
            withTimeout(4000L) {
                val remote = apiService.getHistory(lotteryType.code, limit = limit)
                _isOffline.value = false
                if (remote.records.isNotEmpty()) remote.records else offlineEngine.getHistoryDraws(lotteryType, limit)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _isOffline.value = true
            offlineEngine.getHistoryDraws(lotteryType, limit)
        }
    }

    fun getOfflineHistoryDraws(lotteryType: LotteryType, limit: Int = 50): List<com.zeng.lottery.data.model.DrawItem> {
        return offlineEngine.getHistoryDraws(lotteryType, limit)
    }

    suspend fun getRecommendations(
        destiny: BaziDestiny,
        lotteryType: LotteryType,
        count: Int = 1,
        strategy: String = "balanced",
        userName: String = "缘主",
        lunarDate: com.zeng.lottery.data.model.LunarDate? = null,
        wBazi: Float = 0.5f,
        wFreq: Float = 0.3f,
        wOmission: Float = 0.2f
    ): BaziRecommendationResponse {
        return try {
            val dtStr = if (destiny.solarDatetime.isNotBlank()) {
                destiny.solarDatetime.replace(" ", "T")
            } else {
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
            }
            val req = BaziRecommendationRequest(
                birthDatetime = dtStr,
                calendarType = "solar",
                lotteryType = lotteryType.code,
                count = count,
                strategy = strategy,
                userName = userName,
                shichen = destiny.shichen,
                lunarYear = lunarDate?.year,
                lunarMonth = lunarDate?.month,
                lunarDay = lunarDate?.day,
                isLeapMonth = lunarDate?.isLeap ?: false,
                lunarHour = lunarDate?.shichenZh ?: destiny.shichen,
                wBazi = wBazi,
                wFreq = wFreq,
                wOmission = wOmission
            )
            withTimeout(5000L) {
                val remote = apiService.getRecommendation(req)
                _isOffline.value = false
                remote
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _isOffline.value = true
            offlineEngine.generateRecommendation(destiny, lotteryType, count, strategy, userName)
        }
    }

    fun getOfflineStats(lotteryType: LotteryType): LotteryStats {
        return offlineEngine.computeStats(lotteryType)
    }

    fun getOfflineDestiny(dateTime: LocalDateTime, isMale: Boolean = true): BaziDestiny {
        return BaziEngine.analyzeDestiny(dateTime, isMale)
    }

    fun getOfflineDestinyFromLunar(lunarDate: com.zeng.lottery.data.model.LunarDate, isMale: Boolean = true, userName: String = "缘主"): BaziDestiny {
        return BaziEngine.analyzeDestinyFromLunar(lunarDate, isMale, userName)
    }

    fun getOfflineRecommendations(
        destiny: BaziDestiny,
        lotteryType: LotteryType,
        count: Int = 5,
        strategy: String = "balanced",
        userName: String = "缘主"
    ): BaziRecommendationResponse {
        return offlineEngine.generateRecommendation(destiny, lotteryType, count, strategy, userName)
    }
}
