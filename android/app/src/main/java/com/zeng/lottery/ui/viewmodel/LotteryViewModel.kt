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
import com.zeng.lottery.data.local.PrizeCheckResult
import com.zeng.lottery.data.local.PrizeChecker
import com.zeng.lottery.data.local.SavedTicket
import com.zeng.lottery.data.local.UserPickHistoryStore
import com.zeng.lottery.data.local.UserPreferencesManager
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.BaziRecommendationResponse
import com.zeng.lottery.data.model.DrawItem
import com.zeng.lottery.data.model.LotteryStats
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.model.LunarDate
import com.zeng.lottery.data.model.RecommendationItem
import com.zeng.lottery.data.model.SavedBirthProfile
import com.zeng.lottery.data.model.TodayFortuneResponse
import com.zeng.lottery.data.repository.LotteryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

class LotteryViewModel @JvmOverloads constructor(
    application: Application,
    private val preferencesManager: UserPreferencesManager = UserPreferencesManager(
        try { application.applicationContext ?: application } catch (_: Throwable) { null }
    ),
    private val historyStore: UserPickHistoryStore = UserPickHistoryStore(
        try { application.applicationContext ?: application } catch (_: Throwable) { null }
    )
) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "lottery_app_settings"
        private const val KEY_SERVER_URL = "server_url"
    }

    private val safeContext: Context? = try {
        application.applicationContext ?: application
    } catch (_: Throwable) {
        null
    }

    private val prefs: SharedPreferences? = try {
        safeContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?: application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    } catch (_: Throwable) {
        null
    }

    private val repository = LotteryRepository(safeContext)
    private val aiDivinationManager = AiDivinationManager()

    private var divinationJob: Job? = null
    private var rerollJob: Job? = null
    private var refreshJob: Job? = null
    private var selectJob: Job? = null
    private var destinyJob: Job? = null
    private var statsJob: Job? = null
    private var nameDebounceJob: Job? = null

    // 跨彩种最近开奖响应式缓存，支持收藏列表多彩种一键自动对奖与往期期号切换
    private val latestDrawsByType = ConcurrentHashMap<LotteryType, List<DrawItem>>()
    private val _drawsByType = MutableStateFlow<Map<LotteryType, List<DrawItem>>>(emptyMap())
    val drawsByType: StateFlow<Map<LotteryType, List<DrawItem>>> = _drawsByType.asStateFlow()

    private fun updateDrawsCache(type: LotteryType, draws: List<DrawItem>) {
        if (draws.isEmpty()) return
        latestDrawsByType[type] = draws
        _drawsByType.value = HashMap(latestDrawsByType)
    }

    private val _userName = MutableStateFlow(preferencesManager.getUserName())
    val userName: StateFlow<String> = _userName.asStateFlow()
    val effectiveUserName: String
        get() = if (_userName.value.isBlank()) "缘主" else _userName.value.trim().take(20)

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

    private val _birthLongitude = MutableStateFlow(preferencesManager.getBirthLongitude())
    val birthLongitude: StateFlow<Double> = _birthLongitude.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(preferencesManager.getDarkTheme())
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _birthProfiles = MutableStateFlow(preferencesManager.getBirthProfiles())
    val birthProfiles: StateFlow<List<SavedBirthProfile>> = _birthProfiles.asStateFlow()

    private val _hasCustomizedBirth = MutableStateFlow(preferencesManager.hasCustomizedBirth())
    val hasCustomizedBirth: StateFlow<Boolean> = _hasCustomizedBirth.asStateFlow()

    private val _hasAcceptedCompliance = MutableStateFlow(preferencesManager.hasAcceptedCompliance())
    val hasAcceptedCompliance: StateFlow<Boolean> = _hasAcceptedCompliance.asStateFlow()

    fun acceptCompliance() {
        _hasAcceptedCompliance.value = true
        preferencesManager.saveAcceptedCompliance(true)
    }

    private val _destiny = MutableStateFlow<BaziDestiny?>(null)
    val destiny: StateFlow<BaziDestiny?> = _destiny.asStateFlow()

    private val _todayFortune = MutableStateFlow<TodayFortuneResponse?>(null)
    val todayFortune: StateFlow<TodayFortuneResponse?> = _todayFortune.asStateFlow()

    private val _stats = MutableStateFlow<LotteryStats?>(null)
    val stats: StateFlow<LotteryStats?> = _stats.asStateFlow()

    private val _historyDraws = MutableStateFlow<List<DrawItem>>(emptyList())
    val historyDraws: StateFlow<List<DrawItem>> = _historyDraws.asStateFlow()

    private val _recommendation = MutableStateFlow<BaziRecommendationResponse?>(null)
    val recommendation: StateFlow<BaziRecommendationResponse?> = _recommendation.asStateFlow()

    private val _selectedStrategy = MutableStateFlow(preferencesManager.getSelectedStrategy())
    val selectedStrategy: StateFlow<String> = _selectedStrategy.asStateFlow()

    // 高级选号条件（自定义三元权重 + 定胆/杀号）
    private val _useCustomWeights = MutableStateFlow(false)
    val useCustomWeights: StateFlow<Boolean> = _useCustomWeights.asStateFlow()

    private val _customWBazi = MutableStateFlow(0.45f)
    val customWBazi: StateFlow<Float> = _customWBazi.asStateFlow()

    private val _customWFreq = MutableStateFlow(0.35f)
    val customWFreq: StateFlow<Float> = _customWFreq.asStateFlow()

    private val _customWOmission = MutableStateFlow(0.20f)
    val customWOmission: StateFlow<Float> = _customWOmission.asStateFlow()

    private val _lockedPrimary = MutableStateFlow<Set<String>>(emptySet())
    val lockedPrimary: StateFlow<Set<String>> = _lockedPrimary.asStateFlow()

    private val _excludedPrimary = MutableStateFlow<Set<String>>(emptySet())
    val excludedPrimary: StateFlow<Set<String>> = _excludedPrimary.asStateFlow()

    private val _lockedSecondary = MutableStateFlow<Set<String>>(emptySet())
    val lockedSecondary: StateFlow<Set<String>> = _lockedSecondary.asStateFlow()

    private val _excludedSecondary = MutableStateFlow<Set<String>>(emptySet())
    val excludedSecondary: StateFlow<Set<String>> = _excludedSecondary.asStateFlow()

    // 拆分独立 Loading 状态，同时保留聚合 isLoading 向后兼容
    private val _isRecommending = MutableStateFlow(false)
    val isRecommending: StateFlow<Boolean> = _isRecommending.asStateFlow()

    private val _isStatsLoading = MutableStateFlow(false)
    val isStatsLoading: StateFlow<Boolean> = _isStatsLoading.asStateFlow()

    private val _isDestinyLoading = MutableStateFlow(false)
    val isDestinyLoading: StateFlow<Boolean> = _isDestinyLoading.asStateFlow()

    private val _isSyncingCloud = MutableStateFlow(false)
    val isSyncingCloud: StateFlow<Boolean> = _isSyncingCloud.asStateFlow()

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

    private val _aiReasoningText = MutableStateFlow("")
    val aiReasoningText: StateFlow<String> = _aiReasoningText.asStateFlow()

    private val _divinationBetIndex = MutableStateFlow(0)
    val divinationBetIndex: StateFlow<Int> = _divinationBetIndex.asStateFlow()

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

    private fun updateAggregateLoading() {
        _isLoading.value = _isRecommending.value || _isStatsLoading.value || _isDestinyLoading.value || _isSyncingCloud.value
    }

    private fun isNetworkAvailable(): Boolean {
        val ctx = safeContext ?: try {
            getApplication<Application>()?.applicationContext ?: getApplication<Application>()
        } catch (_: Throwable) {
            null
        }
        if (ctx == null) return true
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val net = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(net)
            caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (_: Throwable) {
            true
        }
    }

    private fun activeWeightTriple(): Triple<Float?, Float?, Float?> {
        return if (_useCustomWeights.value) {
            Triple(_customWBazi.value, _customWFreq.value, _customWOmission.value)
        } else {
            Triple(null, null, null)
        }
    }

    private fun cancelPendingDataJobs() {
        rerollJob?.cancel()
        selectJob?.cancel()
        destinyJob?.cancel()
        refreshJob?.cancel()
    }

    private fun invalidateAiDivination() {
        cancelDivination()
        _aiDivinationText.value = ""
        _aiReasoningText.value = ""
        _aiError.value = null
        _divinationBetIndex.value = 0
    }

    init {
        _savedTickets.value = historyStore.getAllTickets()

        // 尊重用户手动配置的自定义服务器地址（仅当未保存过时默认官方云端地址）
        val savedUrl = try {
            prefs?.getString(KEY_SERVER_URL, null)
        } catch (_: Throwable) {
            null
        }
        val initialUrl = if (savedUrl.isNullOrBlank()) {
            ApiClient.PRODUCTION_CLOUD_URL
        } else {
            savedUrl
        }
        ApiClient.setBaseUrl(initialUrl)
        _serverUrl.value = ApiClient.getBaseUrl()

        // 冷启动同步极速预热（<5ms），避免首帧空白
        val initLottery = _selectedLottery.value
        val initialDestiny = repository.getOfflineDestinyFromLunar(
            _lunarDate.value, _isMale.value, effectiveUserName, longitude = _birthLongitude.value
        )
        _destiny.value = initialDestiny
        _todayFortune.value = BaziEngine.computeTodayFortuneOffline(initialDestiny, effectiveUserName)
        _stats.value = repository.getOfflineStats(initLottery)
        val initDraws = repository.getOfflineHistoryDraws(initLottery, 50)
        _historyDraws.value = initDraws
        updateDrawsCache(initLottery, initDraws)
        // 同步预载其余彩种本地缓存，确保打开收藏夹即刻可对奖
        LotteryType.entries.forEach { t ->
            if (t != initLottery) {
                val cached = repository.getOfflineHistoryDraws(t, 15)
                if (cached.isNotEmpty()) updateDrawsCache(t, cached)
            }
        }
        _recommendation.value = repository.getOfflineRecommendations(
            destiny = initialDestiny,
            lotteryType = initLottery,
            count = _betCount.value,
            strategy = _selectedStrategy.value,
            userName = effectiveUserName
        )

        syncFromCloud()
    }

    fun setUserName(name: String) {
        _userName.value = name
        preferencesManager.saveUserName(name)
        invalidateAiDivination()

        nameDebounceJob?.cancel()
        if (name.isNotBlank()) {
            nameDebounceJob = viewModelScope.launch {
                delay(350L)
                refreshDestinyAndRecommend()
            }
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
        _hasCustomizedBirth.value = true
        invalidateAiDivination()
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
        _hasCustomizedBirth.value = true
        invalidateAiDivination()
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
        invalidateAiDivination()
        refreshDestinyAndRecommend()
    }

    fun setBirthLongitude(longitude: Double) {
        val clamped = longitude.coerceIn(-180.0, 180.0)
        _birthLongitude.value = clamped
        preferencesManager.saveBirthLongitude(clamped)
        invalidateAiDivination()
        refreshDestinyAndRecommend()
    }

    fun toggleTheme() {
        val next = !_isDarkTheme.value
        _isDarkTheme.value = next
        preferencesManager.saveDarkTheme(next)
    }

    // ============ 多亲友生辰档案管理 ============

    fun saveCurrentAsProfile(label: String) {
        val cleanLabel = label.trim().take(12).ifBlank { effectiveUserName }
        val profile = SavedBirthProfile(
            label = cleanLabel,
            userName = effectiveUserName,
            lunarDate = _lunarDate.value,
            isMale = _isMale.value,
            longitude = _birthLongitude.value
        )
        _birthProfiles.value = preferencesManager.upsertBirthProfile(profile)
        _toastMessage.value = "已保存生辰档案【$cleanLabel】"
    }

    fun applyBirthProfile(profile: SavedBirthProfile) {
        _userName.value = profile.userName
        preferencesManager.saveUserName(profile.userName)
        _isMale.value = profile.isMale
        preferencesManager.saveGender(profile.isMale)
        _birthLongitude.value = profile.longitude
        preferencesManager.saveBirthLongitude(profile.longitude)
        _lunarDate.value = profile.lunarDate
        _birthDateTime.value = BaziEngine.lunarToSolar(profile.lunarDate)
        preferencesManager.saveLunarDate(profile.lunarDate)
        _hasCustomizedBirth.value = true
        invalidateAiDivination()
        refreshDestinyAndRecommend()
        _toastMessage.value = "已切换至档案【${profile.label}】"
    }

    fun deleteBirthProfile(profileId: String) {
        _birthProfiles.value = preferencesManager.deleteBirthProfile(profileId)
        _toastMessage.value = "已移除该生辰档案"
    }

    // ============ 高级选号条件（自定义权重 + 定胆/杀号） ============

    fun setUseCustomWeights(enabled: Boolean) {
        _useCustomWeights.value = enabled
        reroll()
    }

    fun updateCustomWeights(bazi: Float, freq: Float, omission: Float) {
        _customWBazi.value = bazi.coerceIn(0f, 1f)
        _customWFreq.value = freq.coerceIn(0f, 1f)
        _customWOmission.value = omission.coerceIn(0f, 1f)
    }

    fun togglePrimaryNumberConstraint(ball: String) {
        // 三态循环：默认 -> 定胆(金锁) -> 杀号(灰叉) -> 默认；校验定胆/杀号上限防后端 422
        val curLocked = _lockedPrimary.value
        val curExcluded = _excludedPrimary.value
        val lt = _selectedLottery.value
        val maxLock = (lt.primaryCount - 1).coerceAtLeast(1)
        val poolSize = lt.primaryRange.last - lt.primaryRange.first + 1
        val maxExclude = if (lt.primaryAllowDuplicate) {
            (poolSize - 1).coerceAtLeast(1)
        } else {
            minOf(25, (poolSize - lt.primaryCount).coerceAtLeast(1))
        }
        when {
            ball in curLocked -> {
                _lockedPrimary.value = curLocked - ball
                if (curExcluded.size < maxExclude) {
                    _excludedPrimary.value = curExcluded + ball
                } else {
                    _toastMessage.value = "主区杀号已达上限 ($maxExclude 个)，已取消该球定胆"
                }
            }
            ball in curExcluded -> {
                _excludedPrimary.value = curExcluded - ball
            }
            else -> {
                if (curLocked.size < maxLock) {
                    _lockedPrimary.value = curLocked + ball
                } else if (curExcluded.size < maxExclude) {
                    _excludedPrimary.value = curExcluded + ball
                    _toastMessage.value = "主区定胆已达上限 ($maxLock 个)，已自动将该球设为杀号"
                } else {
                    _toastMessage.value = "主区杀号已达上限 ($maxExclude 个)，请先取消部分杀号"
                }
            }
        }
    }

    fun toggleSecondaryNumberConstraint(ball: String) {
        val curLocked = _lockedSecondary.value
        val curExcluded = _excludedSecondary.value
        val lt = _selectedLottery.value
        val secRange = lt.secondaryRange ?: return
        val maxLock = lt.secondaryCount.coerceAtLeast(1)
        val poolSize = secRange.last - secRange.first + 1
        // 与后端 Pydantic excluded_secondary max_length=12 严格对齐，杜绝 422
        val maxExclude = minOf(12, (poolSize - lt.secondaryCount).coerceAtLeast(1))
        when {
            ball in curLocked -> {
                _lockedSecondary.value = curLocked - ball
                if (curExcluded.size < maxExclude) {
                    _excludedSecondary.value = curExcluded + ball
                } else {
                    _toastMessage.value = "后区杀号已达上限 ($maxExclude 个)，已取消该球定胆"
                }
            }
            ball in curExcluded -> {
                _excludedSecondary.value = curExcluded - ball
            }
            else -> {
                if (curLocked.size < maxLock) {
                    _lockedSecondary.value = curLocked + ball
                } else if (curExcluded.size < maxExclude) {
                    _excludedSecondary.value = curExcluded + ball
                    _toastMessage.value = "后区定胆已达上限 ($maxLock 个)，已自动将该球设为杀号"
                } else {
                    _toastMessage.value = "后区杀号已达上限 ($maxExclude 个)，请先取消部分杀号"
                }
            }
        }
    }

    fun clearNumberConstraints(triggerReroll: Boolean = true) {
        val hadAny = _lockedPrimary.value.isNotEmpty() || _excludedPrimary.value.isNotEmpty() ||
            _lockedSecondary.value.isNotEmpty() || _excludedSecondary.value.isNotEmpty()
        _lockedPrimary.value = emptySet()
        _excludedPrimary.value = emptySet()
        _lockedSecondary.value = emptySet()
        _excludedSecondary.value = emptySet()
        if (triggerReroll && hadAny) {
            reroll()
        }
    }

    /**
     * 供「冷热走势」热力矩阵点击号码格直接一键设为定胆或取消定胆，并立即刷新推荐号码。
     */
    fun lockPrimaryFromStats(ball: String) {
        val lt = _selectedLottery.value
        val maxLock = (lt.primaryCount - 1).coerceAtLeast(1)
        val curLocked = _lockedPrimary.value
        if (ball in curLocked) {
            _lockedPrimary.value = curLocked - ball
            reroll()
            _toastMessage.value = "已取消号码 $ball 的【定胆】，吉数已自动刷新"
            return
        }
        if (curLocked.size >= maxLock) {
            _toastMessage.value = "主区定胆已满 ($maxLock 个)，请先取消部分胆码"
            return
        }
        _excludedPrimary.value = _excludedPrimary.value - ball
        _lockedPrimary.value = curLocked + ball
        reroll()
        _toastMessage.value = "已将号码 $ball 设为【定胆】并立即重算吉数"
    }

    /**
     * 供「冷热走势」热力矩阵点击号码格直接一键设为杀号或取消杀号，并立即刷新推荐号码。
     */
    fun excludePrimaryFromStats(ball: String) {
        val lt = _selectedLottery.value
        val poolSize = lt.primaryRange.last - lt.primaryRange.first + 1
        val maxExclude = if (lt.primaryAllowDuplicate) {
            (poolSize - 1).coerceAtLeast(1)
        } else {
            minOf(25, (poolSize - lt.primaryCount).coerceAtLeast(1))
        }
        val curExcluded = _excludedPrimary.value
        if (ball in curExcluded) {
            _excludedPrimary.value = curExcluded - ball
            reroll()
            _toastMessage.value = "已取消号码 $ball 的【杀号】，吉数已自动刷新"
            return
        }
        if (curExcluded.size >= maxExclude) {
            _toastMessage.value = "主区杀号已达上限 ($maxExclude 个)"
            return
        }
        _lockedPrimary.value = _lockedPrimary.value - ball
        _excludedPrimary.value = curExcluded + ball
        reroll()
        _toastMessage.value = "已将号码 $ball 设为【杀号】并立即重算吉数"
    }

    fun selectLottery(type: LotteryType) {
        val changed = _selectedLottery.value != type
        _selectedLottery.value = type
        preferencesManager.saveSelectedLottery(type)
        if (changed) {
            clearNumberConstraints(triggerReroll = false)
            invalidateAiDivination()
        }

        val (wb, wf, wo) = activeWeightTriple()
        val currentDestiny = _destiny.value ?: repository.getOfflineDestinyFromLunar(
            _lunarDate.value, _isMale.value, effectiveUserName, longitude = _birthLongitude.value
        )
        _stats.value = repository.getOfflineStats(type)
        val offlineHist = repository.getOfflineHistoryDraws(type, 50)
        _historyDraws.value = offlineHist
        updateDrawsCache(type, offlineHist)
        _recommendation.value = repository.getOfflineRecommendations(
            destiny = currentDestiny,
            lotteryType = type,
            count = _betCount.value,
            strategy = _selectedStrategy.value,
            userName = effectiveUserName,
            wBazi = wb,
            wFreq = wf,
            wOmission = wo,
            lockedPrimary = _lockedPrimary.value.toList(),
            excludedPrimary = _excludedPrimary.value.toList(),
            lockedSecondary = _lockedSecondary.value.toList(),
            excludedSecondary = _excludedSecondary.value.toList()
        )

        if (!isNetworkAvailable()) {
            _isRecommending.value = false
            _isStatsLoading.value = false
            updateAggregateLoading()
            return
        }

        cancelPendingDataJobs()
        selectJob = viewModelScope.launch {
            _isRecommending.value = true
            _isStatsLoading.value = true
            updateAggregateLoading()
            try {
                withTimeout(28000L) {
                    coroutineScope {
                        val statsDef = async { repository.getStats(type) }
                        val histDef = async { repository.getHistoryDraws(type, 50) }
                        val recDef = async {
                            repository.getRecommendations(
                                destiny = currentDestiny,
                                lotteryType = type,
                                count = _betCount.value,
                                strategy = _selectedStrategy.value,
                                userName = effectiveUserName,
                                lunarDate = _lunarDate.value,
                                isMale = _isMale.value,
                                longitude = _birthLongitude.value.toFloat(),
                                wBazi = wb,
                                wFreq = wf,
                                wOmission = wo,
                                lockedPrimary = _lockedPrimary.value.toList(),
                                excludedPrimary = _excludedPrimary.value.toList(),
                                lockedSecondary = _lockedSecondary.value.toList(),
                                excludedSecondary = _excludedSecondary.value.toList()
                            )
                        }
                        val remoteStats = statsDef.await()
                        val remoteHist = histDef.await()
                        val remoteRec = recDef.await()
                        if (_selectedLottery.value != type) return@coroutineScope
                        _stats.value = remoteStats
                        if (remoteHist.isNotEmpty()) {
                            _historyDraws.value = remoteHist
                            updateDrawsCache(type, remoteHist)
                        }
                        _recommendation.value = remoteRec
                    }
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Exception) {
                // 保持离线极速计算结果
            } finally {
                _isRecommending.value = false
                _isStatsLoading.value = false
                updateAggregateLoading()
            }
        }
    }

    fun selectStrategy(strategy: String) {
        _selectedStrategy.value = strategy
        preferencesManager.saveSelectedStrategy(strategy)
        reroll()
    }

    fun reroll() {
        invalidateAiDivination()
        val targetType = _selectedLottery.value
        val (wb, wf, wo) = activeWeightTriple()
        val lockedP = _lockedPrimary.value.toList()
        val excludedP = _excludedPrimary.value.toList()
        val lockedS = _lockedSecondary.value.toList()
        val excludedS = _excludedSecondary.value.toList()

        val currentDestiny = _destiny.value ?: repository.getOfflineDestinyFromLunar(
            _lunarDate.value, _isMale.value, effectiveUserName, longitude = _birthLongitude.value
        )
        _recommendation.value = repository.getOfflineRecommendations(
            destiny = currentDestiny,
            lotteryType = targetType,
            count = _betCount.value,
            strategy = _selectedStrategy.value,
            userName = effectiveUserName,
            wBazi = wb,
            wFreq = wf,
            wOmission = wo,
            lockedPrimary = lockedP,
            excludedPrimary = excludedP,
            lockedSecondary = lockedS,
            excludedSecondary = excludedS
        )

        if (!isNetworkAvailable()) {
            _toastMessage.value = "当前处于离线模式，已启动离线纯算引擎极速推演"
            _isRecommending.value = false
            updateAggregateLoading()
            return
        }

        rerollJob?.cancel()
        rerollJob = viewModelScope.launch {
            _isRecommending.value = true
            updateAggregateLoading()
            try {
                withTimeout(28000L) {
                    val rec = repository.getRecommendations(
                        destiny = currentDestiny,
                        lotteryType = targetType,
                        count = _betCount.value,
                        strategy = _selectedStrategy.value,
                        userName = effectiveUserName,
                        lunarDate = _lunarDate.value,
                        isMale = _isMale.value,
                        longitude = _birthLongitude.value.toFloat(),
                        wBazi = wb,
                        wFreq = wf,
                        wOmission = wo,
                        lockedPrimary = lockedP,
                        excludedPrimary = excludedP,
                        lockedSecondary = lockedS,
                        excludedSecondary = excludedS
                    )
                    if (_selectedLottery.value == targetType) {
                        _recommendation.value = rec
                    }
                    if (repository.isOffline.value) {
                        _toastMessage.value = "云端响应离线，已启动离线纯算推演引擎为您选号"
                    }
                }
            } catch (_: TimeoutCancellationException) {
                _toastMessage.value = "云端响应超时，已为您无缝切换至离线纯算引擎极速推演"
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Exception) {
                _toastMessage.value = "网络连接异常，已启动离线易道推演引擎为您选号"
            } finally {
                _isRecommending.value = false
                updateAggregateLoading()
            }
        }
    }

    fun refreshStats(onComplete: ((Boolean) -> Unit)? = null) {
        val targetType = _selectedLottery.value
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            _isStatsLoading.value = true
            updateAggregateLoading()
            var success = false
            try {
                withTimeout(28000L) {
                    coroutineScope {
                        val sDef = async { repository.getStats(targetType) }
                        val hDef = async { repository.getHistoryDraws(targetType, 50) }
                        val s = sDef.await()
                        val remoteHist = hDef.await()
                        if (_selectedLottery.value == targetType) {
                            _stats.value = s
                        }
                        if (remoteHist.isNotEmpty()) {
                            updateDrawsCache(targetType, remoteHist)
                            if (_selectedLottery.value == targetType) {
                                _historyDraws.value = remoteHist
                            }
                        }
                        success = s.totalDraws > 0
                    }
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Exception) {
                if (_selectedLottery.value == targetType) {
                    _stats.value = repository.getOfflineStats(targetType)
                    _historyDraws.value = repository.getOfflineHistoryDraws(targetType, 50)
                }
                success = true
            } finally {
                _isStatsLoading.value = false
                updateAggregateLoading()
                onComplete?.invoke(success)
            }
        }
    }

    /**
     * 面向大众的云端最新开奖同步：并发拉取 4 大彩种最新官方开奖与统计缓存（由云端每日定时自动入库，无需客户端持有管理员写库密钥）。
     */
    fun triggerCloudSync(forceReload: Boolean = false) {
        if (!isNetworkAvailable()) {
            _toastMessage.value = "当前处于离线状态，已展示本地缓存开奖数据"
            return
        }
        viewModelScope.launch {
            _isSyncingCloud.value = true
            updateAggregateLoading()
            try {
                val targetType = _selectedLottery.value
                var updatedLotteries = 0
                withTimeout(28000L) {
                    coroutineScope {
                        val jobs = LotteryType.entries.map { type ->
                            async {
                                try {
                                    val draws = repository.getHistoryDraws(type, 50)
                                    if (draws.isNotEmpty()) {
                                        updateDrawsCache(type, draws)
                                        if (type == targetType) {
                                            _historyDraws.value = draws
                                        }
                                        true
                                    } else false
                                } catch (_: Exception) {
                                    false
                                }
                            }
                        }
                        val statsDef = async {
                            try {
                                val s = repository.getStats(targetType)
                                if (_selectedLottery.value == targetType) _stats.value = s
                            } catch (_: Exception) {
                            }
                        }
                        statsDef.await()
                        updatedLotteries = jobs.count { it.await() }
                    }
                }
                val latest = _historyDraws.value.firstOrNull()
                _toastMessage.value = if (updatedLotteries > 0 && latest != null) {
                    "已同步云端最新开奖（${targetType.title} 最新第 ${latest.issue} 期 · ${latest.drawDate}）"
                } else {
                    "云端开奖刷新完成"
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Exception) {
                _toastMessage.value = "云端连接波动，已保持本地最新缓存"
            } finally {
                _isSyncingCloud.value = false
                updateAggregateLoading()
            }
        }
    }

    fun testServerConnection(url: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (ok, msg) = withContext(Dispatchers.IO) {
                ApiClient.pingServer(url)
            }
            onResult(ok, msg)
        }
    }

    fun updateServerUrl(url: String): Boolean {
        val (valid, normalizedOrErr) = ApiClient.validateServerUrl(url)
        if (!valid) {
            _toastMessage.value = normalizedOrErr
            return false
        }
        ApiClient.setBaseUrl(normalizedOrErr)
        val current = ApiClient.getBaseUrl()
        _serverUrl.value = current
        try {
            prefs?.edit()?.putString(KEY_SERVER_URL, current)?.apply()
        } catch (_: Throwable) {
        }
        _toastMessage.value = "服务地址已更新: $current"
        refreshAll()
        return true
    }

    private fun refreshDestinyAndRecommend() {
        val targetType = _selectedLottery.value
        val curLunar = _lunarDate.value
        val curMale = _isMale.value
        val curName = effectiveUserName
        val curLon = _birthLongitude.value
        val (wb, wf, wo) = activeWeightTriple()

        val d = repository.getOfflineDestinyFromLunar(curLunar, curMale, curName, longitude = curLon)
        _destiny.value = d
        _todayFortune.value = BaziEngine.computeTodayFortuneOffline(d, curName)
        _recommendation.value = repository.getOfflineRecommendations(
            destiny = d,
            lotteryType = targetType,
            count = _betCount.value,
            strategy = _selectedStrategy.value,
            userName = curName,
            wBazi = wb,
            wFreq = wf,
            wOmission = wo,
            lockedPrimary = _lockedPrimary.value.toList(),
            excludedPrimary = _excludedPrimary.value.toList(),
            lockedSecondary = _lockedSecondary.value.toList(),
            excludedSecondary = _excludedSecondary.value.toList()
        )

        if (!isNetworkAvailable()) {
            _isDestinyLoading.value = false
            updateAggregateLoading()
            return
        }

        destinyJob?.cancel()
        destinyJob = viewModelScope.launch {
            _isDestinyLoading.value = true
            updateAggregateLoading()
            try {
                withTimeout(28000L) {
                    val remoteD = repository.getDestinyFromLunar(curLunar, curMale, curName, longitude = curLon)
                    _destiny.value = remoteD
                    coroutineScope {
                        val tfDef = async { repository.getTodayFortune(curLunar, remoteD, curMale, curName, curLon) }
                        val recDef = async {
                            repository.getRecommendations(
                                destiny = remoteD,
                                lotteryType = targetType,
                                count = _betCount.value,
                                strategy = _selectedStrategy.value,
                                userName = curName,
                                lunarDate = curLunar,
                                isMale = curMale,
                                longitude = curLon.toFloat(),
                                wBazi = wb,
                                wFreq = wf,
                                wOmission = wo,
                                lockedPrimary = _lockedPrimary.value.toList(),
                                excludedPrimary = _excludedPrimary.value.toList(),
                                lockedSecondary = _lockedSecondary.value.toList(),
                                excludedSecondary = _excludedSecondary.value.toList()
                            )
                        }
                        _todayFortune.value = tfDef.await()
                        val remoteRec = recDef.await()
                        if (_selectedLottery.value == targetType) {
                            _recommendation.value = remoteRec
                        }
                    }
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Exception) {
                // 保持端侧已算出的离线命盘
            } finally {
                _isDestinyLoading.value = false
                updateAggregateLoading()
            }
        }
    }

    private fun syncFromCloud() {
        if (!isNetworkAvailable()) return
        val targetType = _selectedLottery.value
        val curLunar = _lunarDate.value
        val curMale = _isMale.value
        val curName = effectiveUserName
        val curLon = _birthLongitude.value

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                // 先通过轻量 /api/health 唤醒可能缩容至 0 的 Cloud Run 单实例，再并发拉取业务接口避免队列拥堵
                repository.checkHealth()
                val d = repository.getDestinyFromLunar(curLunar, curMale, curName, longitude = curLon)
                _destiny.value = d
                coroutineScope {
                    val tfDef = async { repository.getTodayFortune(curLunar, d, curMale, curName, curLon) }
                    val statsDef = async { repository.getStats(targetType) }
                    val histDef = async { repository.getHistoryDraws(targetType, 50) }
                    val recDef = async {
                        repository.getRecommendations(
                            destiny = d,
                            lotteryType = targetType,
                            count = _betCount.value,
                            strategy = _selectedStrategy.value,
                            userName = curName,
                            lunarDate = curLunar,
                            isMale = curMale,
                            longitude = curLon.toFloat()
                        )
                    }
                    _todayFortune.value = tfDef.await()
                    val remoteStats = statsDef.await()
                    val remoteHist = histDef.await()
                    val remoteRec = recDef.await()
                    if (_selectedLottery.value == targetType) {
                        _stats.value = remoteStats
                    }
                    if (remoteHist.isNotEmpty()) {
                        updateDrawsCache(targetType, remoteHist)
                        if (_selectedLottery.value == targetType) {
                            _historyDraws.value = remoteHist
                        }
                    }
                    if (_selectedLottery.value == targetType) {
                        _recommendation.value = remoteRec
                    }
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Exception) {
                // 维持冷启动已预热的端侧数据
            }
        }
    }

    fun refreshAll() {
        val targetType = _selectedLottery.value
        val curLunar = _lunarDate.value
        val curMale = _isMale.value
        val curName = effectiveUserName
        val curLon = _birthLongitude.value
        val (wb, wf, wo) = activeWeightTriple()

        if (!isNetworkAvailable()) {
            val d = _destiny.value ?: repository.getOfflineDestinyFromLunar(curLunar, curMale, curName, curLon)
            _destiny.value = d
            _todayFortune.value = BaziEngine.computeTodayFortuneOffline(d, curName)
            if (_stats.value == null) _stats.value = repository.getOfflineStats(targetType)
            if (_historyDraws.value.isEmpty()) _historyDraws.value = repository.getOfflineHistoryDraws(targetType, 50)
            if (_recommendation.value == null) {
                _recommendation.value = repository.getOfflineRecommendations(
                    d, targetType, _betCount.value, _selectedStrategy.value, curName, wb, wf, wo
                )
            }
            _isDestinyLoading.value = false
            _isStatsLoading.value = false
            _isRecommending.value = false
            updateAggregateLoading()
            return
        }

        cancelPendingDataJobs()
        refreshJob = viewModelScope.launch {
            _isDestinyLoading.value = true
            _isStatsLoading.value = true
            _isRecommending.value = true
            updateAggregateLoading()
            try {
                withTimeout(30000L) {
                    repository.checkHealth()
                    val d = repository.getDestinyFromLunar(curLunar, curMale, curName, longitude = curLon)
                    _destiny.value = d
                    coroutineScope {
                        val tfDef = async { repository.getTodayFortune(curLunar, d, curMale, curName, curLon) }
                        val sDef = async { repository.getStats(targetType) }
                        val histDef = async { repository.getHistoryDraws(targetType, 50) }
                        val recDef = async {
                            repository.getRecommendations(
                                destiny = d,
                                lotteryType = targetType,
                                count = _betCount.value,
                                strategy = _selectedStrategy.value,
                                userName = curName,
                                lunarDate = curLunar,
                                isMale = curMale,
                                longitude = curLon.toFloat(),
                                wBazi = wb,
                                wFreq = wf,
                                wOmission = wo,
                                lockedPrimary = _lockedPrimary.value.toList(),
                                excludedPrimary = _excludedPrimary.value.toList(),
                                lockedSecondary = _lockedSecondary.value.toList(),
                                excludedSecondary = _excludedSecondary.value.toList()
                            )
                        }
                        _todayFortune.value = tfDef.await()
                        val s = sDef.await()
                        val remoteHist = histDef.await()
                        val rec = recDef.await()
                        if (_selectedLottery.value == targetType) _stats.value = s
                        if (remoteHist.isNotEmpty()) {
                            updateDrawsCache(targetType, remoteHist)
                            if (_selectedLottery.value == targetType) _historyDraws.value = remoteHist
                        }
                        if (_selectedLottery.value == targetType) _recommendation.value = rec
                    }
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Exception) {
                val d = _destiny.value ?: repository.getOfflineDestinyFromLunar(curLunar, curMale, curName, curLon)
                _destiny.value = d
                if (_stats.value == null) _stats.value = repository.getOfflineStats(targetType)
                if (_historyDraws.value.isEmpty()) _historyDraws.value = repository.getOfflineHistoryDraws(targetType, 50)
            } finally {
                _isDestinyLoading.value = false
                _isStatsLoading.value = false
                _isRecommending.value = false
                updateAggregateLoading()
            }
        }
    }

    // ============ AI 易道解盘控制 ============

    fun openDivinationSheet(betIndex: Int = 0) {
        val list = _recommendation.value?.recommendations.orEmpty()
        val targetIdx = if (list.isNotEmpty()) betIndex.coerceIn(0, list.lastIndex) else 0
        val indexChanged = targetIdx != _divinationBetIndex.value
        _divinationBetIndex.value = targetIdx
        _showDivinationSheet.value = true
        if (_aiDivinationText.value.isEmpty() || indexChanged) {
            startDivination(targetIdx)
        }
    }

    fun closeDivinationSheet() {
        // 关闭解盘面板时立即取消仍在传输中的流式请求，防止后台浪费付费 Token
        cancelDivination()
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
            val ctx = safeContext ?: try {
                getApplication<Application>()?.applicationContext ?: getApplication<Application>()
            } catch (_: Throwable) {
                null
            }
            KeyStoreManager.saveAiConfig(ctx, config)
            _aiConfig.value = config
            _toastMessage.value = "AI 解盘配置已加密保存"
        } catch (_: Throwable) {
            _aiConfig.value = config
        }
    }

    fun cancelDivination() {
        divinationJob?.cancel()
        divinationJob = null
        _isAiStreaming.value = false
    }

    fun startDivination(betIndex: Int = _divinationBetIndex.value) {
        cancelDivination()
        val list = _recommendation.value?.recommendations.orEmpty()
        val safeIndex = if (list.isNotEmpty()) betIndex.coerceIn(0, list.lastIndex) else 0
        _divinationBetIndex.value = safeIndex
        _aiDivinationText.value = ""
        _aiReasoningText.value = ""
        _aiError.value = null
        _isAiStreaming.value = true

        val rec = list.getOrNull(safeIndex)
        val primary = (rec?.primaryNumbers as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val secondary = (rec?.secondaryNumbers as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val currentDestiny = _destiny.value
        val dt = _birthDateTime.value
        val currentLottery = _selectedLottery.value
        val config = _aiConfig.value
        val effectiveName = effectiveUserName

        divinationJob = viewModelScope.launch {
            try {
                val ctx = safeContext ?: try {
                    getApplication<Application>()?.applicationContext ?: getApplication<Application>()
                } catch (_: Throwable) {
                    null
                }
                aiDivinationManager.streamDivination(
                    context = ctx,
                    lotteryType = currentLottery,
                    primaryNumbers = primary,
                    secondaryNumbers = secondary,
                    destiny = currentDestiny,
                    birthDateTime = dt,
                    userName = effectiveName,
                    configOverride = config,
                    onReasoningDelta = { reasoningDelta ->
                        _aiReasoningText.value += reasoningDelta
                    },
                    onFallback = { reason ->
                        _aiError.value = reason
                    }
                ).collect { delta ->
                    _aiDivinationText.value += delta
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (e: Exception) {
                _aiError.value = e.message ?: "AI 解盘发生未知异常"
            } finally {
                _isAiStreaming.value = false
            }
        }
    }

    // ============ 历史收藏与一键自动对奖复盘 ============

    fun checkTicketAgainstDraw(ticket: SavedTicket, draw: DrawItem): PrizeCheckResult {
        return PrizeChecker.checkTicket(
            lotteryType = ticket.lotteryType,
            ticketPrimary = ticket.primaryNumbers,
            ticketSecondary = ticket.secondaryNumbers,
            draw = draw
        )
    }

    fun checkTicketAgainstLatestDraw(ticket: SavedTicket): PrizeCheckResult? {
        val draws = latestDrawsByType[ticket.lotteryType]
            ?: repository.getOfflineHistoryDraws(ticket.lotteryType, 15).also {
                if (it.isNotEmpty()) updateDrawsCache(ticket.lotteryType, it)
            }
        val latest = draws.firstOrNull() ?: return null
        return checkTicketAgainstDraw(ticket, latest)
    }

    fun prefetchAllDrawsForPrizeCheck() {
        if (!isNetworkAvailable()) return
        viewModelScope.launch {
            coroutineScope {
                LotteryType.entries.forEach { type ->
                    async {
                        try {
                            val draws = repository.getHistoryDraws(type, 15)
                            if (draws.isNotEmpty()) {
                                updateDrawsCache(type, draws)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }
    }

    fun saveCurrentTicket(item: RecommendationItem, index: Int = 0) {
        val priInts = (item.primaryNumbers as? List<*>)?.mapNotNull {
            (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull()
        } ?: emptyList()
        val secInts = (item.secondaryNumbers as? List<*>)?.mapNotNull {
            (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull()
        } ?: emptyList()
        val ticket = SavedTicket(
            lotteryType = _selectedLottery.value,
            userName = effectiveUserName,
            primaryNumbers = priInts,
            secondaryNumbers = secInts,
            strategy = _selectedStrategy.value,
            rationale = item.scoreRationale
        )
        val isNew = historyStore.saveUniqueTicketWithCap(ticket)
        _savedTickets.value = historyStore.getAllTickets()
        val label = if (index < 26) ('A' + index).toString() else "${index + 1}"
        _toastMessage.value = if (isNew) {
            "已收藏第 [$label] 注灵感号码"
        } else {
            "第 [$label] 注已在收藏夹中，已刷新置顶"
        }
    }

    fun saveAllCurrentTickets() {
        val items = _recommendation.value?.recommendations ?: return
        if (items.isEmpty()) return
        val newTickets = items.mapNotNull { item ->
            val pri = (item.primaryNumbers as? List<*>)?.mapNotNull {
                (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull()
            } ?: emptyList()
            if (pri.isEmpty()) return@mapNotNull null
            val sec = (item.secondaryNumbers as? List<*>)?.mapNotNull {
                (it as? Number)?.toInt() ?: it?.toString()?.toIntOrNull()
            } ?: emptyList()
            SavedTicket(
                lotteryType = _selectedLottery.value,
                userName = effectiveUserName,
                primaryNumbers = pri,
                secondaryNumbers = sec,
                strategy = _selectedStrategy.value,
                rationale = item.scoreRationale
            )
        }
        if (newTickets.isNotEmpty()) {
            historyStore.saveUniqueTicketsWithCap(newTickets)
            _savedTickets.value = historyStore.getAllTickets()
            _toastMessage.value = "已保存本组 ${newTickets.size} 注至历史收藏（重复号自动合并）"
        }
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
