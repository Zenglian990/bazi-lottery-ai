package com.zeng.lottery

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeng.lottery.data.api.ApiClient
import com.zeng.lottery.ui.components.AiDivinationSheet
import com.zeng.lottery.ui.components.AiSettingsDialog
import com.zeng.lottery.ui.screens.BirthInputScreen
import com.zeng.lottery.ui.screens.DestinyChartScreen
import com.zeng.lottery.ui.screens.LotterySelectScreen
import com.zeng.lottery.ui.screens.RecommendScreen
import com.zeng.lottery.ui.screens.StatsScreen
import com.zeng.lottery.ui.theme.ElementWood
import com.zeng.lottery.ui.theme.LocalAccentGold
import com.zeng.lottery.ui.viewmodel.LotteryViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LotteryApp(
    viewModel: LotteryViewModel = viewModel()
) {
    val context = LocalContext.current
    val accentGold = LocalAccentGold.current
    val selectedLottery by viewModel.selectedLottery.collectAsState()
    val lunarDate by viewModel.lunarDate.collectAsState()
    val isMale by viewModel.isMale.collectAsState()
    val birthLongitude by viewModel.birthLongitude.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val birthProfiles by viewModel.birthProfiles.collectAsState()
    val destiny by viewModel.destiny.collectAsState()
    val todayFortune by viewModel.todayFortune.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val recommendation by viewModel.recommendation.collectAsState()
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val isRecommending by viewModel.isRecommending.collectAsState()
    val isStatsLoading by viewModel.isStatsLoading.collectAsState()
    val isSyncingCloud by viewModel.isSyncingCloud.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()

    val useCustomWeights by viewModel.useCustomWeights.collectAsState()
    val customWBazi by viewModel.customWBazi.collectAsState()
    val customWFreq by viewModel.customWFreq.collectAsState()
    val customWOmission by viewModel.customWOmission.collectAsState()
    val lockedPrimary by viewModel.lockedPrimary.collectAsState()
    val excludedPrimary by viewModel.excludedPrimary.collectAsState()
    val lockedSecondary by viewModel.lockedSecondary.collectAsState()
    val excludedSecondary by viewModel.excludedSecondary.collectAsState()

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToastMessage()
        }
    }

    val aiDivinationText by viewModel.aiDivinationText.collectAsState()
    val aiReasoningText by viewModel.aiReasoningText.collectAsState()
    val aiError by viewModel.aiError.collectAsState()
    val divinationBetIndex by viewModel.divinationBetIndex.collectAsState()
    val isAiStreaming by viewModel.isAiStreaming.collectAsState()
    val aiConfig by viewModel.aiConfig.collectAsState()
    val showDivinationSheet by viewModel.showDivinationSheet.collectAsState()
    val showAiSettingsDialog by viewModel.showAiSettingsDialog.collectAsState()

    val userName by viewModel.userName.collectAsState()
    val betCount by viewModel.betCount.collectAsState()
    val historyDraws by viewModel.historyDraws.collectAsState()
    val drawsByType by viewModel.drawsByType.collectAsState()
    val savedTickets by viewModel.savedTickets.collectAsState()

    // 使用 rememberSaveable 防止旋转/后台重建丢弃导航页签
    var selectedNavIndex by rememberSaveable { mutableIntStateOf(0) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }

    // 4 个 Tab 各自持有独立滚动位置，切换页签互不干扰
    val recommendScroll = rememberScrollState()
    val destinyScroll = rememberScrollState()
    val birthScroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo_round),
                            contentDescription = "应用图标",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "曾练彩票助手",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentGold
                        )
                        if (isOffline) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(accentGold.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "端侧离线",
                                    tint = accentGold,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "离线引擎",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = accentGold
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleTheme() }) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = if (isDarkTheme) "切换宣纸浅色主题" else "切换玄墨暗金主题",
                            tint = accentGold
                        )
                    }
                    IconButton(onClick = { viewModel.openAiSettings() }) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI 模型配置",
                            tint = accentGold
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "服务器与网络设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = accentGold
            ) {
                val navItems = listOf(
                    Triple("智能机选", Icons.Default.Casino, 0),
                    Triple("命盘格局", Icons.Default.AutoAwesome, 1),
                    Triple("冷热走势", Icons.AutoMirrored.Filled.TrendingUp, 2),
                    Triple("生辰排盘", Icons.Default.CalendarMonth, 3)
                )

                navItems.forEach { (label, icon, index) ->
                    val isSelected = selectedNavIndex == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedNavIndex = index },
                        icon = { Icon(imageVector = icon, contentDescription = label) },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.secondary,
                            selectedTextColor = accentGold,
                            indicatorColor = accentGold.copy(alpha = 0.2f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedNavIndex) {
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(recommendScroll)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        RecommendScreen(
                            lotteryType = selectedLottery,
                            recommendationResponse = recommendation,
                            selectedStrategy = selectedStrategy,
                            betCount = betCount,
                            userName = userName,
                            isLoading = isRecommending,
                            useCustomWeights = useCustomWeights,
                            customWBazi = customWBazi,
                            customWFreq = customWFreq,
                            customWOmission = customWOmission,
                            lockedPrimary = lockedPrimary,
                            excludedPrimary = excludedPrimary,
                            lockedSecondary = lockedSecondary,
                            excludedSecondary = excludedSecondary,
                            onStrategySelected = { viewModel.selectStrategy(it) },
                            onBetCountSelected = { viewModel.setBetCount(it) },
                            onToggleCustomWeights = { viewModel.setUseCustomWeights(it) },
                            onUpdateCustomWeights = { b, f, o -> viewModel.updateCustomWeights(b, f, o) },
                            onTogglePrimaryConstraint = { viewModel.togglePrimaryNumberConstraint(it) },
                            onToggleSecondaryConstraint = { viewModel.toggleSecondaryNumberConstraint(it) },
                            onClearConstraints = { viewModel.clearNumberConstraints() },
                            onReroll = { viewModel.reroll() },
                            onOpenDivination = { betIdx -> viewModel.openDivinationSheet(betIdx) },
                            savedTickets = savedTickets,
                            drawsByType = drawsByType,
                            onSaveTicket = { item, idx -> viewModel.saveCurrentTicket(item, idx) },
                            onSaveAllTickets = { viewModel.saveAllCurrentTickets() },
                            onDeleteSavedTicket = { viewModel.deleteSavedTicket(it) },
                            onClearAllSavedTickets = { viewModel.clearAllSavedTickets() },
                            onCheckTicketPrize = { ticket -> viewModel.checkTicketAgainstLatestDraw(ticket) },
                            onCheckTicketAgainstDraw = { ticket, draw -> viewModel.checkTicketAgainstDraw(ticket, draw) },
                            onOpenHistory = { viewModel.prefetchAllDrawsForPrizeCheck() }
                        )

                        LotterySelectScreen(
                            selectedType = selectedLottery,
                            onTypeSelected = { viewModel.selectLottery(it) }
                        )

                        BirthInputScreen(
                            lunarDate = lunarDate,
                            isMale = isMale,
                            destiny = destiny,
                            todayFortune = todayFortune,
                            userName = userName,
                            birthLongitude = birthLongitude,
                            birthProfiles = birthProfiles,
                            onUserNameChanged = { viewModel.setUserName(it) },
                            onLunarDateChanged = { viewModel.setLunarDate(it) },
                            onResetCurrentTime = { viewModel.resetToCurrentTime() },
                            onGenderChanged = { viewModel.setGender(it) },
                            onLongitudeChanged = { viewModel.setBirthLongitude(it) },
                            onSaveProfile = { viewModel.saveCurrentAsProfile(it) },
                            onApplyProfile = { viewModel.applyBirthProfile(it) },
                            onDeleteProfile = { viewModel.deleteBirthProfile(it) },
                            showTodayLiuRi = false
                        )
                    }
                }

                1 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(destinyScroll)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DestinyChartScreen(
                            destiny = destiny,
                            userName = userName
                        )
                    }
                }

                2 -> {
                    // StatsScreen 自带 LazyColumn 懒加载，不再嵌套外层 verticalScroll
                    StatsScreen(
                        stats = stats,
                        historyDraws = historyDraws,
                        lotteryType = selectedLottery,
                        isLoading = isStatsLoading,
                        isSyncingCloud = isSyncingCloud,
                        onLotterySelected = { viewModel.selectLottery(it) },
                        onRefreshStats = { viewModel.refreshStats() },
                        onTriggerCloudSync = { viewModel.triggerCloudSync() }
                    )
                }

                3 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(birthScroll)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BirthInputScreen(
                            lunarDate = lunarDate,
                            isMale = isMale,
                            destiny = destiny,
                            todayFortune = todayFortune,
                            userName = userName,
                            birthLongitude = birthLongitude,
                            birthProfiles = birthProfiles,
                            onUserNameChanged = { viewModel.setUserName(it) },
                            onLunarDateChanged = { viewModel.setLunarDate(it) },
                            onResetCurrentTime = { viewModel.resetToCurrentTime() },
                            onGenderChanged = { viewModel.setGender(it) },
                            onLongitudeChanged = { viewModel.setBirthLongitude(it) },
                            onSaveProfile = { viewModel.saveCurrentAsProfile(it) },
                            onApplyProfile = { viewModel.applyBirthProfile(it) },
                            onDeleteProfile = { viewModel.deleteBirthProfile(it) },
                            showTodayLiuRi = true
                        )
                    }
                }
            }
        }
    }

    // 后端服务与网络连通性测速设置弹窗
    if (showSettingsDialog) {
        var inputUrl by rememberSaveable { mutableStateOf(serverUrl) }
        var urlError by rememberSaveable { mutableStateOf<String?>(null) }
        var isTestingPing by rememberSaveable { mutableStateOf(false) }
        var pingResultText by rememberSaveable { mutableStateOf<String?>(null) }
        var pingOk by rememberSaveable { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "后端服务与网络测速设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "默认连接谷歌云官方生产集群。断网时自动启用 100% 离线纯 Kotlin 引擎并保留已缓存的最新官方开奖期号。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "快捷服务节点：",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentGold
                    )

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val isCloudSelected = inputUrl.trimEnd('/') == ApiClient.PRODUCTION_CLOUD_URL.trimEnd('/')
                        SuggestionChip(
                            onClick = {
                                inputUrl = ApiClient.PRODUCTION_CLOUD_URL
                                urlError = null
                                pingResultText = null
                            },
                            label = { Text("⚡ 谷歌云官方云端", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = if (isCloudSelected) {
                                SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = accentGold.copy(alpha = 0.25f),
                                    labelColor = accentGold
                                )
                            } else {
                                SuggestionChipDefaults.suggestionChipColors()
                            }
                        )
                        SuggestionChip(
                            onClick = {
                                inputUrl = "http://10.0.2.2:8000/"
                                urlError = null
                                pingResultText = null
                            },
                            label = { Text("💻 本地模拟器 (10.0.2.2)", fontSize = 11.sp) }
                        )
                    }

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { raw ->
                            inputUrl = raw.filter { c -> c != '\r' && c != '\n' }
                            urlError = null
                            pingResultText = null
                        },
                        label = { Text("API Base URL") },
                        isError = urlError != null,
                        supportingText = urlError?.let { err -> { Text(err) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val (valid, msg) = ApiClient.validateServerUrl(inputUrl)
                                if (!valid) {
                                    urlError = msg
                                    return@OutlinedButton
                                }
                                isTestingPing = true
                                pingResultText = null
                                viewModel.testServerConnection(inputUrl) { ok, resMsg ->
                                    isTestingPing = false
                                    pingOk = ok
                                    pingResultText = resMsg
                                }
                            },
                            enabled = !isTestingPing
                        ) {
                            if (isTestingPing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = accentGold)
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp), tint = accentGold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTestingPing) "测速中..." else "测试连接", fontSize = 12.sp, color = accentGold)
                        }

                        if (!pingResultText.isNullOrBlank()) {
                            Text(
                                text = pingResultText!!,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 11.sp,
                                color = if (pingOk) ElementWood else MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val (valid, msg) = ApiClient.validateServerUrl(inputUrl)
                        if (!valid) {
                            urlError = msg
                            return@TextButton
                        }
                        if (viewModel.updateServerUrl(inputUrl)) {
                            showSettingsDialog = false
                        }
                    }
                ) {
                    Text("保存生效", color = accentGold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    if (showDivinationSheet) {
        AiDivinationSheet(
            text = aiDivinationText,
            reasoningText = aiReasoningText,
            errorMessage = aiError,
            isStreaming = isAiStreaming,
            aiConfig = aiConfig,
            totalBets = recommendation?.recommendations?.size ?: 1,
            selectedBetIndex = divinationBetIndex,
            onSelectBet = { idx -> viewModel.startDivination(idx) },
            onStopStreaming = { viewModel.cancelDivination() },
            onDismiss = { viewModel.closeDivinationSheet() },
            onRedivine = { viewModel.startDivination(divinationBetIndex) },
            onOpenSettings = { viewModel.openAiSettings() }
        )
    }

    if (showAiSettingsDialog) {
        AiSettingsDialog(
            currentConfig = aiConfig,
            onDismiss = { viewModel.closeAiSettings() },
            onSaveConfig = { viewModel.updateAiConfig(it) }
        )
    }
}
