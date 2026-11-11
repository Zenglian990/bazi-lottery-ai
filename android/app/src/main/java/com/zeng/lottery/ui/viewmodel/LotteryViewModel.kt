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
import com.zeng.lottery.data.local.SavedTicket
import com.zeng.lottery.data.local.UserPickHistoryStore
import com.zeng.lottery.data.local.UserPreferencesManager
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.BaziRecommendationResponse
import com.zeng.lottery.data.model.RecommendationItem
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

class LotteryViewModel @JvmOverloads constructor(
    application: Application,
    private val preferencesManager: UserPreferencesManager = UserPreferencesManager(
        try { application.applicationContext ?: application } catch (e: Throwable) { null }
    ),
    private val historyStore: UserPickHistoryStore = UserPickHistoryStore(
        try { application.applicationContext ?: application } catch (e: Throwable) { null }
    )
) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "lottery_app_settings"
        private const val KEY_SERVER_URL = "server_url"
    }

    private val safeContext: Context? = try {
        application.applicationContext ?: application
    } catch (e: Throwable) {
        null
    }

    private val prefs: SharedPreferences? = try {
        safeContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (e: Throwable) {
        null
    }

    private val repository = LotteryRepository(safeContext)
    private val offlineEngine = OfflineLotteryEngine(safeContext)
    private val aiDivinationManager = AiDivinationManager()

    private var divinationJob: Job? = null
    private var rerollJob: Job? = null
    private var refreshJob: Job? = null
    private var selectJob: Job? = null
    private var destinyJob: Job? = null

    private val _userName = MutableStateFlow(preferencesManager.getUserName())
    val userName: StateFlow<String> = _userName.asStateFlow()
    val effectiveUserName: String
        get() = if (_userName.value.isBlank()) "缘主" else _userName.value.trim()

    private val _betCount = MutableStateFlow(preferencesManager.getBetCount())
    val betCount: StateFlow<Int> = _betCount.asStateFlow()

    private val _selectedLottery = MutableStateFlow(preferencesManager.getSelectedLottery())
    val selectedLottery: StateFlow<LotteryType> = _selectedLottery.asStateFlow()

    private val _lunarDate = MutableStateFlow(preferencesManager.getLunarDate())
    val lunarDate: StateFlow<LunarDate> = _lunarDate.asStateFlow()

    private val _birthDateTime = MutableStateFlow(BaziEngine.lunarToSolar(_lunarDate.value))
    val birthDateTime: StateFlow<LocalDateTime> = _birthDateTime.asStateFlow()

    private val _isLunar = MutableStateFlow(preferencesManager.getIsLunar())
    val isLunar: StateFlow<Boolean> = _isLunar.asStateFlow()

    private val _isMale = MutableStateFlow(preferencesManager.getGender())
    val isMale: StateFlow<Boolean> = _isMale.asStateFlow()

    private val _destiny = MutableStateFlow<BaziDestiny?>(null)
    val destiny: StateFlow<BaziDestiny?> = _destiny.asStateFlow()

    private val _stats = MutableStateFlow<LotteryStats?>(null)
    val stats: StateFlow<LotteryStats?> = _stats.asStateFlow()

    private val _historyDraws = MutableStateFlow<List<DrawItem>>(emptyList())
    val historyDraws: StateFlow<List<DrawItem>> = _historyDraws.asStateFlow()

    private val _recommendation = MutableStateFlow<BaziRecommendationResponse?>(null)
    val recommendation: StateFlow<BaziRecommendationResponse?> = _recommendation.asStateFlow()

    private val _selectedStrategy = MutableStateFlow(preferencesManager.getSelectedStrategy())
    val selectedStrategy: StateFlow<String> = _selectedStrategy.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val isOffline: StateFlow<Boolean> = repository.isOffline

    private val _serverUrl = MutableStateFlow(ApiClient.getBaseUrl())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _aiConfig = MutableStateFlow(KeyStoreManager.loadAiConfig(safeContext))
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

    private val _savedTickets = MutableStateFlow<List<SavedTicket>>(emptyList())
    val savedTickets: StateFlow<List<SavedTicket>> = _savedTickets.asStateFlow()

    private fun isNetworkAvailable(): Boolean {
        val ctx = safeContext ?: try { getApplication<Application>()?.applicationContext ?: getApplication<Application>() } catch (e: Throwable) { null }
        if (ctx == null) return false
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val net = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(net)
            caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Throwable) {
            true
        }
    }

    init {
        // Prime saved tickets history synchronously
        _savedTickets.value = historyStore.getAllTickets()

        // Automatic migration away from legacy 10.0.2.2 emulator loopback to official Cloud Run
        val savedUrl = try {
            prefs?.getString(KEY_SERVER_URL, null)
        } catch (e: Throwable) {
            null
        }
        val initialUrl = if (savedUrl.isNullOrBlank() || savedUrl.contains("10.0.2.2")) {
            ApiClient.PRODUCTION_CLOUD_URL
        } else {
            savedUrl
        }
        ApiClient.setBaseUrl(initialUrl)
        try {
            prefs?.edit()?.putString(KEY_SERVER_URL, initialUrl)?.apply()
        } catch (e: Throwable) {
            // Defensive swallow: never crash cold start on SharedPreferences write issues
        }
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
        preferencesManager.saveUserName(name)
        try {
            prefs?.edit()?.putString("user_name", name)?.apply()
        } catch (e: Throwable) {
            // Defensive swallow
        }
    }

    fun setBetCount(count: Int) {
        val validCount = count.coerceIn(1, 20)
        _betCount.value = validCount
        preferencesManager.saveBetCount(validCount)
        reroll()
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    fun setLunarDate(date: LunarDate) {
        _lunarDate.value = date
        _birthDateTime.value = BaziEngine.lunarToSolar(date)
        preferencesManager.saveLunarDate(date)
        refreshDestinyAndRecommend()
    }

    fun resetToCurrentTime() {
        val now = LocalDateTime.now()
        val lunar = BaziEngine.solarToLunar(now)
        setLunarDate(lunar)
        _toastMessage.value = "已对齐当前农历：${lunar.year}年${lunar.monthZh}${lunar.dayZh} ${lunar.shichenZh}"
    }

    fun setBirthDateTime(dt: LocalDateTime) {
        _birthDateTime.value = dt
        val lunar = BaziEngine.solarToLunar(dt)
        _lunarDate.value = lunar
        preferencesManager.saveLunarDate(lunar)
        refreshDestinyAndRecommend()
    }

    fun setLunar(lunar: Boolean) {
        _isLunar.value = lunar
        preferencesManager.saveIsLunar(lunar)
        refreshDestinyAndRecommend()
    }

    fun setGender(male: Boolean) {
        _isMale.value = male
        preferencesManager.saveGender(male)
        refreshDestinyAndRecommend()
    }

    fun selectLottery(type: LotteryType) {
        _selectedLottery.value = type
        preferencesManager.saveSelectedLottery(type)
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

        if (!isNetworkAvailable() || repository.isOffline.value) {
            _isLoading.value = false
            return
        }

        selectJob?.cancel()
        selectJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                withTimeout(2500L) {
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
                        userName = effectiveUserName,
                        lunarDate = _lunarDate.value
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
        preferencesManager.saveSelectedStrategy(strategy)
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

        if (!isNetworkAvailable() || repository.isOffline.value) {
            _toastMessage.value = "当前处于离线模式，已启动离线纯算引擎极速推演"
            _isLoading.value = false
            return
        }

        rerollJob?.cancel()
        rerollJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                withTimeout(2500L) {
                    val rec = repository.getRecommendations(
                        destiny = currentDestiny,
                        lotteryType = _selectedLottery.value,
                        count = _betCount.value,
                        strategy = _selectedStrategy.value,
                        userName = effectiveUserName,
                        lunarDate = _lunarDate.value
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

        if (!isNetworkAvailable() || repository.isOffline.value) {
            _isLoading.value = false
            return
        }

        destinyJob?.cancel()
        destinyJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                withTimeout(2500L) {
                    val remoteD = repository.getDestinyFromLunar(_lunarDate.value, _isMale.value, effectiveUserName)
                    _destiny.value = remoteD
                    _recommendation.value = repository.getRecommendations(
                        destiny = remoteD,
                        lotteryType = _selectedLottery.value,
                        count = _betCount.value,
                        strategy = _selectedStrategy.value,
                        userName = effectiveUserName,
                        lunarDate = _lunarDate.value
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
        if (!isNetworkAvailable() || repository.isOffline.value) {
            return
        }
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
                    userName = effectiveUserName,
                    lunarDate = _lunarDate.value
                )
            } catch (e: Exception) {
                // Keep already primed local state
            }
        }
    }

    fun refreshAll() {
        if (!isNetworkAvailable() || repository.isOffline.value) {
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
            _isLoading.value = false
            return
        }

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                withTimeout(3000L) {
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
            val ctx = safeContext ?: try { getApplication<Application>()?.applicationContext ?: getApplication<Application>() } catch (e: Throwable) { null }
            KeyStoreManager.saveAiConfig(ctx, config)
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
                val ctx = safeContext ?: try { getApplication<Application>()?.applicationContext ?: getApplication<Application>() } catch (e: Throwable) { null }
                aiDivinationManager.streamDivination(
                    context = ctx,
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

    fun saveCurrentTicket(item: RecommendationItem, index: Int = 0) {
        val priInts = item.primaryNumbers.mapNotNull { it.toIntOrNull() }
        val secInts = item.secondaryNumbers.mapNotNull { it.toIntOrNull() }
        val ticket = SavedTicket(
            lotteryType = _selectedLottery.value,
            userName = effectiveUserName,
            primaryNumbers = priInts,
            secondaryNumbers = secInts,
            strategy = _selectedStrategy.value,
            rationale = item.scoreRationale
        )
        historyStore.saveTicket(ticket)
        _savedTickets.value = historyStore.getAllTickets()
        val label = if (index < 26) ('A' + index).toString() else "${index + 1}"
        _toastMessage.value = "已收藏第 [$label] 注灵感号码"
    }

    fun saveAllCurrentTickets() {
        val items = _recommendation.value?.recommendations ?: return
        if (items.isEmpty()) return
        val newTickets = items.map { item ->
            SavedTicket(
                lotteryType = _selectedLottery.value,
                userName = effectiveUserName,
                primaryNumbers = item.primaryNumbers.mapNotNull { it.toIntOrNull() },
                secondaryNumbers = item.secondaryNumbers.mapNotNull { it.toIntOrNull() },
                strategy = _selectedStrategy.value,
                rationale = item.scoreRationale
            )
        }
        historyStore.saveTickets(newTickets)
        _savedTickets.value = historyStore.getAllTickets()
        _toastMessage.value = "已将全部 ${newTickets.size} 注灵感号码保存至历史收藏"
    }

    fun deleteSavedTicket(id: String) {
        historyStore.deleteTicket(id)
        _savedTickets.value = historyStore.getAllTickets()
        _toastMessage.value = "已删除历史记录"
    }

    fun clearAllSavedTickets() {
        historyStore.clearAll()
        _savedTickets.value = emptyList()
        _toastMessage.value = "已清空所有历史灵感记录"
    }

    fun refreshSavedTickets() {
        _savedTickets.value = historyStore.getAllTickets()
    }
}
