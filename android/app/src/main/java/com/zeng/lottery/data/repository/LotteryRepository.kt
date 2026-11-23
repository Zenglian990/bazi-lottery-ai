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
import com.zeng.lottery.data.model.DrawItem
import com.zeng.lottery.data.model.HealthResponse
import com.zeng.lottery.data.model.LotteryStats
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.LunarDate
import com.zeng.lottery.data.model.SyncLotteryRequest
import com.zeng.lottery.data.model.SyncLotteryResponse
import com.zeng.lottery.data.model.TodayFortuneRequest
import com.zeng.lottery.data.model.TodayFortuneResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import retrofit2.HttpException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LotteryRepository(
    private val context: Context? = null,
    private val apiService: LotteryApiService = ApiClient.apiService,
    private val offlineEngine: OfflineLotteryEngine = OfflineLotteryEngine(context)
) {
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private fun handleNetworkFailure(e: Exception) {
        // HTTP 4xx 说明后端服务可达且在线，仅为客户端参数或鉴权错误，不应误标为离线
        if (e is HttpException && e.code() in 400..499) {
            _isOffline.value = false
        } else {
            _isOffline.value = true
        }
    }

    suspend fun checkHealth(): HealthResponse? {
        return try {
            withTimeout(26000L) {
                val res = apiService.checkHealth()
                _isOffline.value = false
                res
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleNetworkFailure(e)
            null
        }
    }

    suspend fun getDestiny(
        dateTime: LocalDateTime,
        isMale: Boolean = true,
        calendarType: String = "solar",
        longitude: Double = 120.0
    ): BaziDestiny {
        return try {
            val req = BaziDestinyRequest(
                birthDatetime = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                calendarType = calendarType,
                gender = if (isMale) "male" else "female",
                longitude = longitude
            )
            withTimeout(26000L) {
                val remote = apiService.calculateDestiny(req)
                _isOffline.value = false
                remote
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleNetworkFailure(e)
            BaziEngine.analyzeDestiny(dateTime, isMale, longitude = longitude)
        }
    }

    suspend fun getDestinyFromLunar(
        lunarDate: LunarDate,
        isMale: Boolean = true,
        userName: String = "缘主",
        longitude: Double = 120.0
    ): BaziDestiny {
        return try {
            val solarDt = BaziEngine.lunarToSolar(lunarDate)
            val req = BaziDestinyRequest(
                birthDatetime = solarDt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                calendarType = "lunar",
                gender = if (isMale) "male" else "female",
                longitude = longitude,
                userName = userName,
                shichen = lunarDate.shichenZh,
                isLeapMonth = lunarDate.isLeap,
                lunarYear = lunarDate.year,
                lunarMonth = lunarDate.month,
                lunarDay = lunarDate.day,
                lunarHour = lunarDate.shichenZh
            )
            withTimeout(26000L) {
                val remote = apiService.calculateDestiny(req)
                _isOffline.value = false
                val finalLunarText = "${lunarDate.year}年 (${lunarDate.ganzhiYear}${lunarDate.zodiac}年) 农历${lunarDate.monthZh}${lunarDate.dayZh} ${lunarDate.shichenZh}"
                remote.copy(lunarDatetime = finalLunarText, userName = userName)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleNetworkFailure(e)
            BaziEngine.analyzeDestinyFromLunar(lunarDate, isMale, userName, longitude = longitude)
        }
    }

    suspend fun getTodayFortune(
        lunarDate: LunarDate?,
        destiny: BaziDestiny?,
        isMale: Boolean = true,
        userName: String = "缘主",
        longitude: Double = 120.0
    ): TodayFortuneResponse {
        return try {
            val req = TodayFortuneRequest(
                calendarType = if (lunarDate != null) "lunar" else "solar",
                longitude = longitude,
                userName = userName,
                shichen = lunarDate?.shichenZh ?: destiny?.shichen,
                isLeapMonth = lunarDate?.isLeap ?: false,
                lunarYear = lunarDate?.year,
                lunarMonth = lunarDate?.month,
                lunarDay = lunarDate?.day,
                lunarHour = lunarDate?.shichenZh,
                gender = if (isMale) "male" else "female"
            )
            withTimeout(22000L) {
                val remote = apiService.getTodayFortune(req)
                _isOffline.value = false
                remote
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleNetworkFailure(e)
            BaziEngine.computeTodayFortuneOffline(destiny, userName)
        }
    }

    suspend fun getStats(lotteryType: LotteryType): LotteryStats {
        return try {
            withTimeout(26000L) {
                val remote = apiService.getStats(lotteryType.code)
                _isOffline.value = false
                remote
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleNetworkFailure(e)
            offlineEngine.computeStats(lotteryType)
        }
    }

    suspend fun getHistoryDraws(lotteryType: LotteryType, limit: Int = 50): List<DrawItem> {
        return try {
            withTimeout(26000L) {
                val remote = apiService.getHistory(lotteryType.code, limit = limit)
                _isOffline.value = false
                if (remote.records.isNotEmpty()) {
                    // 联网拉取到最新开奖后立即写入磁盘持久化缓存，断网不回退旧期号
                    offlineEngine.saveRemoteDrawsCache(lotteryType, remote.records)
                    remote.records
                } else {
                    offlineEngine.getHistoryDraws(lotteryType, limit)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleNetworkFailure(e)
            offlineEngine.getHistoryDraws(lotteryType, limit)
        }
    }

    fun getOfflineHistoryDraws(lotteryType: LotteryType, limit: Int = 50): List<DrawItem> {
        return offlineEngine.getHistoryDraws(lotteryType, limit)
    }

    /**
     * 自用云端同步：使用 ADMIN_SYNC_TOKEN 触发后端抓取福彩/体彩最新开奖并持久化到 GCS。
     */
    suspend fun triggerCloudSync(
        lotteryTypeCode: String = "all",
        adminToken: String,
        forceReload: Boolean = false
    ): Result<SyncLotteryResponse> {
        val token = adminToken.trim()
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("未内置同步令牌，仅执行云端数据拉取刷新"))
        }
        return try {
            withTimeout(25000L) {
                val resp = apiService.syncLotteryData(
                    authHeader = "Bearer $token",
                    request = SyncLotteryRequest(lotteryType = lotteryTypeCode, forceReload = forceReload)
                )
                _isOffline.value = false
                Result.success(resp)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            handleNetworkFailure(e)
            val errMsg = when (e.code()) {
                401, 403 -> "同步令牌鉴权未通过 (HTTP ${e.code()})"
                429 -> "云端同步冷却中，请稍候再试 (HTTP 429)"
                else -> "云端同步失败 (HTTP ${e.code()})"
            }
            Result.failure(IllegalStateException(errMsg, e))
        } catch (e: Exception) {
            handleNetworkFailure(e)
            Result.failure(e)
        }
    }

    suspend fun getRecommendations(
        destiny: BaziDestiny,
        lotteryType: LotteryType,
        count: Int = 1,
        strategy: String = "balanced",
        userName: String = "缘主",
        lunarDate: LunarDate? = null,
        isMale: Boolean = true,
        longitude: Float = 120.0f,
        wBazi: Float? = null,
        wFreq: Float? = null,
        wOmission: Float? = null,
        lockedPrimary: List<String> = emptyList(),
        excludedPrimary: List<String> = emptyList(),
        lockedSecondary: List<String> = emptyList(),
        excludedSecondary: List<String> = emptyList()
    ): BaziRecommendationResponse {
        return try {
            val useLunar = lunarDate != null
            val dtStr = if (!useLunar) {
                if (destiny.solarDatetime.isNotBlank()) {
                    destiny.solarDatetime.replace(" ", "T")
                } else {
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                }
            } else null

            val effectiveGender = if (destiny.gender.isNotBlank()) destiny.gender else (if (isMale) "male" else "female")
            val req = BaziRecommendationRequest(
                birthDatetime = dtStr,
                calendarType = if (useLunar) "lunar" else "solar",
                lotteryType = lotteryType.code,
                count = count,
                strategy = strategy,
                userName = userName,
                shichen = lunarDate?.shichenZh ?: destiny.shichen,
                lunarYear = lunarDate?.year,
                lunarMonth = lunarDate?.month,
                lunarDay = lunarDate?.day,
                isLeapMonth = lunarDate?.isLeap ?: false,
                lunarHour = lunarDate?.shichenZh ?: destiny.shichen,
                gender = effectiveGender,
                longitude = longitude,
                wBazi = wBazi,
                wFreq = wFreq,
                wOmission = wOmission,
                lockedPrimary = lockedPrimary.ifEmpty { null },
                excludedPrimary = excludedPrimary.ifEmpty { null },
                lockedSecondary = lockedSecondary.ifEmpty { null },
                excludedSecondary = excludedSecondary.ifEmpty { null }
            )
            withTimeout(26000L) {
                val remote = apiService.getRecommendation(req)
                _isOffline.value = false
                remote
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleNetworkFailure(e)
            offlineEngine.generateRecommendation(
                destiny = destiny,
                lotteryType = lotteryType,
                count = count,
                strategy = strategy,
                userName = userName,
                wBaziOverride = wBazi,
                wFreqOverride = wFreq,
                wOmissionOverride = wOmission,
                lockedPrimary = lockedPrimary,
                excludedPrimary = excludedPrimary,
                lockedSecondary = lockedSecondary,
                excludedSecondary = excludedSecondary
            )
        }
    }

    fun getOfflineStats(lotteryType: LotteryType): LotteryStats {
        return offlineEngine.computeStats(lotteryType)
    }

    fun getOfflineDestiny(dateTime: LocalDateTime, isMale: Boolean = true, longitude: Double = 120.0): BaziDestiny {
        return BaziEngine.analyzeDestiny(dateTime, isMale, longitude = longitude)
    }

    fun getOfflineDestinyFromLunar(
        lunarDate: LunarDate,
        isMale: Boolean = true,
        userName: String = "缘主",
        longitude: Double = 120.0
    ): BaziDestiny {
        return BaziEngine.analyzeDestinyFromLunar(lunarDate, isMale, userName, longitude = longitude)
    }

    fun getOfflineRecommendations(
        destiny: BaziDestiny,
        lotteryType: LotteryType,
        count: Int = 5,
        strategy: String = "balanced",
        userName: String = "缘主",
        wBazi: Float? = null,
        wFreq: Float? = null,
        wOmission: Float? = null,
        lockedPrimary: List<String> = emptyList(),
        excludedPrimary: List<String> = emptyList(),
        lockedSecondary: List<String> = emptyList(),
        excludedSecondary: List<String> = emptyList()
    ): BaziRecommendationResponse {
        return offlineEngine.generateRecommendation(
            destiny = destiny,
            lotteryType = lotteryType,
            count = count,
            strategy = strategy,
            userName = userName,
            wBaziOverride = wBazi,
            wFreqOverride = wFreq,
            wOmissionOverride = wOmission,
            lockedPrimary = lockedPrimary,
            excludedPrimary = excludedPrimary,
            lockedSecondary = lockedSecondary,
            excludedSecondary = excludedSecondary
        )
    }
}
