package com.zeng.lottery.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeng.lottery.ai.AiConfig
import com.zeng.lottery.ai.AiDivinationManager
import com.zeng.lottery.ai.KeyStoreManager
import com.zeng.lottery.data.api.ApiClient
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.local.OfflineLotteryEngine
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.BaziRecommendationResponse
import com.zeng.lottery.data.model.DrawItem
import com.zeng.lottery.data.model.LotteryStats
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.repository.LotteryRepository
import com.zeng.lottery.data.model.LunarDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDateTime

class LotteryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "lottery_app_settings"
        private const val KEY_SERVER_URL = "server_url"
    }

    private val prefs: SharedPreferences? = try {
        application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (e: Throwable) {
        null
    }

    private val repository = LotteryRepository(application.applicationContext)
    private val offlineEngine = OfflineLotteryEngine(application.applicationContext)
    private val aiDivinationManager = AiDivinationManager()

    private var divinationJob: Job? = null
    private var rerollJob: Job? = null
    private var refreshJob: Job? = null
    private var selectJob: Job? = null
    private var destinyJob: Job? = null

    private val _userName = MutableStateFlow(prefs?.getString("user_name", "缘主") ?: "缘主")
    val userName: StateFlow<String> = _userName.asStateFlow()
    val effectiveUserName: String
        get() = if (_userName.value.isBlank()) "缘主" else _userName.value.trim()

    private val _betCount = MutableStateFlow(5)
    val betCount: StateFlow<Int> = _betCount.asStateFlow()

    private val _selectedLottery = MutableStateFlow(LotteryType.SSQ)
    val selectedLottery: StateFlow<LotteryType> = _selectedLottery.asStateFlow()

    private val _lunarDate = MutableStateFlow(LunarDate(1990, 6, false, 24, 8, 30))
    val lunarDate: StateFlow<LunarDate> = _lunarDate.asStateFlow()

    private val _birthDateTime = MutableStateFlow(LocalDateTime.of(1990, 8, 15, 8, 30))
    val birthDateTime: StateFlow<LocalDateTime> = _birthDateTime.asStateFlow()

    private val _isLunar = MutableStateFlow(true)
    val isLunar: StateFlow<Boolean> = _isLunar.asStateFlow()

    private val _isMale = MutableStateFlow(true)
    val isMale: StateFlow<Boolean> = _isMale.asStateFlow()

    private val _destiny = MutableStateFlow<BaziDestiny?>(null)
    val destiny: StateFlow<BaziDestiny?> = _destiny.asStateFlow()

    private val _stats = MutableStateFlow<LotteryStats?>(null)
    val stats: StateFlow<LotteryStats?> = _stats.asStateFlow()

    private val _historyDraws = MutableStateFlow<List<DrawItem>>(emptyList())
    val historyDraws: StateFlow<List<DrawItem>> = _historyDraws.asStateFlow()

    private val _recommendation = MutableStateFlow<BaziRecommendationResponse?>(null)
    val recommendation: StateFlow<BaziRecommendationResponse?> = _recommendation.asStateFlow()

    private val _selectedStrategy = MutableStateFlow("命理吉数优先")
    val selectedStrategy: StateFlow<String> = _selectedStrategy.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val isOffline: StateFlow<Boolean> = repository.isOffline

    private val _serverUrl = MutableStateFlow(ApiClient.getBaseUrl())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _aiConfig = MutableStateFlow(KeyStoreManager.loadAiConfig(application.applicationContext))
    val aiConfig: StateFlow<AiConfig> = _aiConfig.asStateFlow()

    private val _aiDivinationText = MutableStateFlow("")
    val aiDivinationText: StateFlow<String> = _aiDivinationText.asStateFlow()

    private val _isAiStreaming = MutableStateFlow(false)
    val isAiStreaming: StateFlow<Boolean> = _isAiStreaming.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _showDivinationSheet = MutableStateFlow(false)
    val showDivinationSheet: StateFlow<Boolean> = _showDivinationSheet.asStateFlow()

    private val _showAiSettingsDialog = MutableStateFlow(false)
    val showAiSettingsDialog: StateFlow<Boolean> = _showAiSettingsDialog.asStateFlow()

    init {
        // Automatic migration away from legacy 10.0.2.2 emulator loopback to official Cloud Run
        val savedUrl = prefs?.getString(KEY_SERVER_URL, null)
        val initialUrl = if (savedUrl.isNullOrBlank() || savedUrl.contains("10.0.2.2")) {
            ApiClient.PRODUCTION_CLOUD_URL
        } else {
            savedUrl
        }
        ApiClient.setBaseUrl(initialUrl)
        prefs?.edit()?.putString(KEY_SERVER_URL, initialUrl)?.apply()
        _serverUrl.value = ApiClient.getBaseUrl()

        // Eagerly prime UI within 5ms from offline engine to prevent any 0-draw or blank card states
        val initialDestiny = repository.getOfflineDestinyFromLunar(_lunarDate.value, _isMale.value, effectiveUserName)
        _destiny.value = initialDestiny
        _stats.value = repository.getOfflineStats(_selectedLottery.value)
        _historyDraws.value = repository.getOfflineHistoryDraws(_selectedLottery.value, 50)
        _recommendation.value = repository.getOfflineRecommendations(
            destiny = initialDestiny,
            lotteryType = _selectedLottery.value,
            count = _betCount.value,
            strategy = _selectedStrategy.value,
            userName = effectiveUserName
        )

        // Then non-blocking cloud sync in background
        syncFromCloud()
    }

    fun setUserName(name: String) {
        _userName.value = name
        prefs?.edit()?.putString("user_name", name)?.apply()
    }

    fun setBetCount(count: Int) {
        val validCount = count.coerceIn(1, 20)
        _betCount.value = validCount
        reroll()
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    fun setLunarDate(date: LunarDate) {
        _lunarDate.value = date
        _birthDateTime.value = BaziEngine.lunarToSolar(date)
        refreshDestinyAndRecommend()
    }

    fun resetToCurrentTime() {
        val now = LocalDateTime.now()
        setLunarDate(BaziEngine.solarToLunar(now))
    }

    fun setBirthDateTime(dt: LocalDateTime) {
        _birthDateTime.value = dt
        _lunarDate.value = BaziEngine.solarToLunar(dt)
        refreshDestinyAndRecommend()
    }

    fun setLunar(lunar: Boolean) {
        _isLunar.value = lunar
        refreshDestinyAndRecommend()
    }

    fun setGender(male: Boolean) {
        _isMale.value = male
        refreshDestinyAndRecommend()
    }

    fun selectLottery(type: LotteryType) {
        _selectedLottery.value = type
        // Immediately show offline stats, history & recommendations for instant responsiveness
        val currentDestiny = _destiny.value ?: repository.getOfflineDestinyFromLunar(_lunarDate.value, _isMale.value, effectiveUserName)
        _stats.value = repository.getOfflineStats(type)
        _historyDraws.value = repository.getOfflineHistoryDraws(type, 50)
        _recommendation.value = repository.getOfflineRecommendations(
            destiny = currentDestiny,
            lotteryType = type,
            count = _betCount.value,
            strategy = _selectedStrategy.value,
            userName = effectiveUserName
        )

        selectJob?.cancel()
        selectJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                withTimeout(5500L) {
                    _stats.value = repository.getStats(type)
                    val remoteHist = repository.getHistoryDraws(type, 50)
                    if (remoteHist.isNotEmpty()) {
                        _historyDraws.value = remoteHist
                    }
                    _recommendation.value = repository.getRecommendations(
                        destiny = currentDestiny,
                        lotteryType = type,
                        count = _betCount.value,
                        strategy = _selectedStrategy.value,
                        userName = effectiveUserName
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                return@launch
            } catch (e: Exception) {
                // Keep offline values on network error or timeout
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectStrategy(strategy: String) {
        _selectedStrategy.value = strategy
        reroll()
    }

    fun reroll() {
        // Immediate local reroll
        val currentDestiny = _destiny.value ?: repository.getOfflineDestinyFromLunar(_lunarDate.value, _isMale.value, effectiveUserName)
        _recommendation.value = repository.getOfflineRecommendations(
            destiny = currentDestiny,
            lotteryType = _selectedLottery.value,
            count = _betCount.value,
            strategy = _selectedStrategy.value,
            userName = effectiveUserName
        )

        rerollJob?.cancel()
        rerollJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                withTimeout(5500L) {
                    val rec = repository.getRecommendations(
                        destiny = currentDestiny,
                        lotteryType = _selectedLottery.value,
                        count = _betCount.value,
                        strategy = _selectedStrategy.value,
                        userName = effectiveUserName
                    )
                    _recommendation.value = rec
                    if (repository.isOffline.value) {
                        _toastMessage.value = "云端响应离线，已启动离线纯算推演引擎为您选号"
                    }
                }
            } catch (e: TimeoutCancellationException) {
                _toastMessage.value = "云端响应超时，已为您无缝切换至离线纯算引擎极速推演"
            } catch (e: kotlinx.coroutines.CancellationException) {
                return@launch
            } catch (e: Exception) {
                _toastMessage.value = "网络连接异常，已启动离线易道推演引擎为您选号"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshStats(onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            var success = false
            try {
                withTimeout(4000L) {
                    val s = repository.getStats(_selectedLottery.value)
                    _stats.value = s
                    val remoteHist = repository.getHistoryDraws(_selectedLottery.value, 50)
                    if (remoteHist.isNotEmpty()) {
                        _historyDraws.value = remoteHist
                    }
                    success = s.totalDraws > 0
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                return@launch
            } catch (e: Exception) {
                _stats.value = repository.getOfflineStats(_selectedLottery.value)
                _historyDraws.value = repository.getOfflineHistoryDraws(_selectedLottery.value, 50)
                success = true
            } finally {
                _isLoading.value = false
                onComplete?.invoke(success)
            }
        }
    }

    fun updateServerUrl(url: String) {
        ApiClient.setBaseUrl(url)
        val current = ApiClient.getBaseUrl()
        _serverUrl.value = current
        try {
            prefs?.edit()?.putString(KEY_SERVER_URL, current)?.apply()
        } catch (e: Throwable) {
            // Ignore
        }
        _toastMessage.value = "服务地址已更新: $current"
        refreshAll()
    }

    private fun refreshDestinyAndRecommend() {
        val d = repository.getOfflineDestinyFromLunar(_lunarDate.value, _isMale.value, effectiveUserName)
        _destiny.value = d
        _recommendation.value = repository.getOfflineRecommendations(
            destiny = d,
            lotteryType = _selectedLottery.value,
            count = _betCount.value,
            strategy = _selectedStrategy.value,
            userName = effectiveUserName
        )

        destinyJob?.cancel()
        destinyJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                withTimeout(6500L) {
                    val remoteD = repository.getDestinyFromLunar(_lunarDate.value, _isMale.value, effectiveUserName)
                    _destiny.value = remoteD
                    _recommendation.value = repository.getRecommendations(
                        destiny = remoteD,
                        lotteryType = _selectedLottery.value,
                        count = _betCount.value,
                        strategy = _selectedStrategy.value,
                        userName = effectiveUserName
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                return@launch
            } catch (e: Exception) {
                // Keep offline values on network error or timeout
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun syncFromCloud() {
        viewModelScope.launch {
            try {
                repository.checkHealth()
                val d = repository.getDestinyFromLunar(_lunarDate.value, _isMale.value, effectiveUserName)
                _destiny.value = d
                _stats.value = repository.getStats(_selectedLottery.value)
                val remoteHist = repository.getHistoryDraws(_selectedLottery.value, 50)
                if (remoteHist.isNotEmpty()) {
                    _historyDraws.value = remoteHist
                }
                _recommendation.value = repository.getRecommendations(
                    destiny = d,
                    lotteryType = _selectedLottery.value,
                    count = _betCount.value,
                    strategy = _selectedStrategy.value,
                    userName = effectiveUserName
                )
            } catch (e: Exception) {
                // Keep already primed local state
            }
        }
    }

    fun refreshAll() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                withTimeout(8000L) {
                    repository.checkHealth()
                    val d = repository.getDestinyFromLunar(_lunarDate.value, _isMale.value, effectiveUserName)
                    _destiny.value = d
                    _stats.value = repository.getStats(_selectedLottery.value)
                    val remoteHist = repository.getHistoryDraws(_selectedLottery.value, 50)
                    if (remoteHist.isNotEmpty()) {
                        _historyDraws.value = remoteHist
                    }
                    _recommendation.value = repository.getRecommendations(
                        destiny = d,
                        lotteryType = _selectedLottery.value,
                        count = _betCount.value,
                        strategy = _selectedStrategy.value,
                        userName = effectiveUserName
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                return@launch
            } catch (e: Exception) {
                val d = _destiny.value ?: repository.getOfflineDestinyFromLunar(_lunarDate.value, _isMale.value, effectiveUserName)
                _destiny.value = d
                if (_stats.value == null) {
                    _stats.value = repository.getOfflineStats(_selectedLottery.value)
                }
                if (_historyDraws.value.isEmpty()) {
                    _historyDraws.value = repository.getOfflineHistoryDraws(_selectedLottery.value, 50)
                }
                if (_recommendation.value == null) {
                    _recommendation.value = repository.getOfflineRecommendations(
                        destiny = d,
                        lotteryType = _selectedLottery.value,
                        count = _betCount.value,
                        strategy = _selectedStrategy.value,
                        userName = effectiveUserName
                    )
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun openDivinationSheet() {
        _showDivinationSheet.value = true
        if (_aiDivinationText.value.isEmpty()) {
            startDivination()
        }
    }

    fun closeDivinationSheet() {
        _showDivinationSheet.value = false
    }

    fun openAiSettings() {
        _showAiSettingsDialog.value = true
    }

    fun closeAiSettings() {
        _showAiSettingsDialog.value = false
    }

    fun updateAiConfig(config: AiConfig) {
        try {
            KeyStoreManager.saveAiConfig(getApplication<Application>().applicationContext, config)
            _aiConfig.value = config
            _toastMessage.value = "AI 解盘配置已加密保存"
        } catch (e: Throwable) {
            _aiConfig.value = config
        }
    }

    fun cancelDivination() {
        divinationJob?.cancel()
        divinationJob = null
        _isAiStreaming.value = false
    }

    fun startDivination() {
        cancelDivination()
        _aiDivinationText.value = ""
        _aiError.value = null
        _isAiStreaming.value = true

        val rec = _recommendation.value?.recommendations?.firstOrNull()
        val primary = rec?.primaryNumbers ?: emptyList()
        val secondary = rec?.secondaryNumbers ?: emptyList()
        val currentDestiny = _destiny.value
        val dt = _birthDateTime.value
        val currentLottery = _selectedLottery.value
        val config = _aiConfig.value
        val effectiveName = effectiveUserName

        divinationJob = viewModelScope.launch {
            try {
                aiDivinationManager.streamDivination(
                    context = getApplication<Application>().applicationContext,
                    lotteryType = currentLottery,
                    primaryNumbers = primary,
                    secondaryNumbers = secondary,
                    destiny = currentDestiny,
                    birthDateTime = dt,
                    userName = effectiveName,
                    configOverride = config
                ).collect { delta ->
                    _aiDivinationText.value += delta
                }
            } catch (e: Exception) {
                _aiError.value = e.message
            } finally {
                _isAiStreaming.value = false
            }
        }
    }
}
