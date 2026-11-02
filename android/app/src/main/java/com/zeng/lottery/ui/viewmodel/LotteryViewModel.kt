package com.zeng.lottery.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeng.lottery.ai.AiConfig
import com.zeng.lottery.ai.AiDivinationManager
import com.zeng.lottery.ai.KeyStoreManager
import com.zeng.lottery.data.api.ApiClient
import com.zeng.lottery.data.local.BaziEngine
import com.zeng.lottery.data.model.BaziDestiny
import com.zeng.lottery.data.model.BaziRecommendationResponse
import com.zeng.lottery.data.model.LotteryStats
import com.zeng.lottery.data.model.LotteryType
import com.zeng.lottery.data.repository.LotteryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class LotteryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LotteryRepository(application.applicationContext)
    private val aiDivinationManager = AiDivinationManager()
    private var divinationJob: Job? = null

    private val _selectedLottery = MutableStateFlow(LotteryType.SSQ)
    val selectedLottery: StateFlow<LotteryType> = _selectedLottery.asStateFlow()

    private val _birthDateTime = MutableStateFlow(LocalDateTime.of(1990, 8, 15, 8, 30))
    val birthDateTime: StateFlow<LocalDateTime> = _birthDateTime.asStateFlow()

    private val _isLunar = MutableStateFlow(false)
    val isLunar: StateFlow<Boolean> = _isLunar.asStateFlow()

    private val _isMale = MutableStateFlow(true)
    val isMale: StateFlow<Boolean> = _isMale.asStateFlow()

    private val _destiny = MutableStateFlow<BaziDestiny?>(null)
    val destiny: StateFlow<BaziDestiny?> = _destiny.asStateFlow()

    private val _stats = MutableStateFlow<LotteryStats?>(null)
    val stats: StateFlow<LotteryStats?> = _stats.asStateFlow()

    private val _recommendation = MutableStateFlow<BaziRecommendationResponse?>(null)
    val recommendation: StateFlow<BaziRecommendationResponse?> = _recommendation.asStateFlow()

    private val _selectedStrategy = MutableStateFlow("命理吉数优先")
    val selectedStrategy: StateFlow<String> = _selectedStrategy.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val isOffline: StateFlow<Boolean> = repository.isOffline

    private val _serverUrl = MutableStateFlow(ApiClient.getBaseUrl())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

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
        // Initial compute
        refreshAll()
    }

    fun setBirthDateTime(dt: LocalDateTime) {
        _birthDateTime.value = dt
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
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _stats.value = repository.getStats(type)
                val currentDestiny = _destiny.value ?: repository.getDestiny(_birthDateTime.value, _isMale.value)
                _recommendation.value = repository.getRecommendations(
                    destiny = currentDestiny,
                    lotteryType = type,
                    strategy = _selectedStrategy.value
                )
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
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentDestiny = _destiny.value ?: repository.getDestiny(_birthDateTime.value, _isMale.value)
                _recommendation.value = repository.getRecommendations(
                    destiny = currentDestiny,
                    lotteryType = _selectedLottery.value,
                    strategy = _selectedStrategy.value
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _stats.value = repository.getStats(_selectedLottery.value)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateServerUrl(url: String) {
        ApiClient.setBaseUrl(url)
        _serverUrl.value = ApiClient.getBaseUrl()
        refreshAll()
    }

    private fun refreshDestinyAndRecommend() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val d = repository.getDestiny(_birthDateTime.value, _isMale.value)
                _destiny.value = d
                _recommendation.value = repository.getRecommendations(
                    destiny = d,
                    lotteryType = _selectedLottery.value,
                    strategy = _selectedStrategy.value
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.checkHealth()
                val d = repository.getDestiny(_birthDateTime.value, _isMale.value)
                _destiny.value = d
                _stats.value = repository.getStats(_selectedLottery.value)
                _recommendation.value = repository.getRecommendations(
                    destiny = d,
                    lotteryType = _selectedLottery.value,
                    strategy = _selectedStrategy.value
                )
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
        KeyStoreManager.saveAiConfig(getApplication<Application>().applicationContext, config)
        _aiConfig.value = config
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

        divinationJob = viewModelScope.launch {
            try {
                aiDivinationManager.streamDivination(
                    context = getApplication<Application>().applicationContext,
                    lotteryType = currentLottery,
                    primaryNumbers = primary,
                    secondaryNumbers = secondary,
                    destiny = currentDestiny,
                    birthDateTime = dt,
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
