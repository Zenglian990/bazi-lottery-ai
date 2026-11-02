package com.zeng.lottery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeng.lottery.ui.components.AiDivinationSheet
import com.zeng.lottery.ui.components.AiSettingsDialog
import com.zeng.lottery.ui.screens.BirthInputScreen
import com.zeng.lottery.ui.screens.DestinyChartScreen
import com.zeng.lottery.ui.screens.LotterySelectScreen
import com.zeng.lottery.ui.screens.RecommendScreen
import com.zeng.lottery.ui.screens.StatsScreen
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed
import com.zeng.lottery.ui.viewmodel.LotteryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryApp(
    viewModel: LotteryViewModel = viewModel()
) {
    val selectedLottery by viewModel.selectedLottery.collectAsState()
    val birthDateTime by viewModel.birthDateTime.collectAsState()
    val isLunar by viewModel.isLunar.collectAsState()
    val isMale by viewModel.isMale.collectAsState()
    val destiny by viewModel.destiny.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val recommendation by viewModel.recommendation.collectAsState()
    val selectedStrategy by viewModel.selectedStrategy.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isOffline by viewModel.isOffline.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()

    val aiDivinationText by viewModel.aiDivinationText.collectAsState()
    val isAiStreaming by viewModel.isAiStreaming.collectAsState()
    val aiConfig by viewModel.aiConfig.collectAsState()
    val showDivinationSheet by viewModel.showDivinationSheet.collectAsState()
    val showAiSettingsDialog by viewModel.showAiSettingsDialog.collectAsState()

    var selectedNavIndex by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "天工八字彩票机选",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = FortuneGold
                        )
                        if (isOffline) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(
                                        color = FortuneGold.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "端侧离线",
                                    tint = FortuneGold,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "离线引擎",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = FortuneGold
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.openAiSettings() }) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI 模型配置",
                            tint = FortuneGold
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
                contentColor = FortuneGold
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
                            selectedIconColor = ImperialRed,
                            selectedTextColor = FortuneGold,
                            indicatorColor = FortuneGold.copy(alpha = 0.2f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedNavIndex) {
                0 -> {
                    // 1. 机选 Screen (Machine Pick + Lottery Selector + Birth Quick Bar)
                    RecommendScreen(
                        lotteryType = selectedLottery,
                        recommendationResponse = recommendation,
                        selectedStrategy = selectedStrategy,
                        isLoading = isLoading,
                        onStrategySelected = { viewModel.selectStrategy(it) },
                        onReroll = { viewModel.reroll() },
                        onOpenDivination = { viewModel.openDivinationSheet() }
                    )

                    LotterySelectScreen(
                        selectedType = selectedLottery,
                        onTypeSelected = { viewModel.selectLottery(it) }
                    )

                    BirthInputScreen(
                        selectedDateTime = birthDateTime,
                        isLunar = isLunar,
                        isMale = isMale,
                        destiny = destiny,
                        onDateTimeChanged = { viewModel.setBirthDateTime(it) },
                        onLunarChanged = { viewModel.setLunar(it) },
                        onGenderChanged = { viewModel.setGender(it) }
                    )
                }
                1 -> {
                    // 2. 命盘 Screen (Wuxing Radar + Day Master Strength)
                    DestinyChartScreen(destiny = destiny)

                    BirthInputScreen(
                        selectedDateTime = birthDateTime,
                        isLunar = isLunar,
                        isMale = isMale,
                        destiny = destiny,
                        onDateTimeChanged = { viewModel.setBirthDateTime(it) },
                        onLunarChanged = { viewModel.setLunar(it) },
                        onGenderChanged = { viewModel.setGender(it) }
                    )
                }
                2 -> {
                    // 3. 走势 Screen (Hot/Cold/Omission + Lottery Selector)
                    LotterySelectScreen(
                        selectedType = selectedLottery,
                        onTypeSelected = { viewModel.selectLottery(it) }
                    )

                    StatsScreen(
                        stats = stats,
                        lotteryType = selectedLottery,
                        onRefreshStats = { viewModel.refreshStats() }
                    )
                }
                3 -> {
                    // 4. 生辰排盘 Screen
                    BirthInputScreen(
                        selectedDateTime = birthDateTime,
                        isLunar = isLunar,
                        isMale = isMale,
                        destiny = destiny,
                        onDateTimeChanged = { viewModel.setBirthDateTime(it) },
                        onLunarChanged = { viewModel.setLunar(it) },
                        onGenderChanged = { viewModel.setGender(it) }
                    )

                    DestinyChartScreen(destiny = destiny)
                }
            }
        }
    }

    // Dynamic Server Base URL Settings Dialog
    if (showSettingsDialog) {
        var inputUrl by remember { mutableStateOf(serverUrl) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "后端服务与网络设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "支持 Android 模拟器 (http://10.0.2.2:8000/)、局域网真机 IP 或 Cloud Run 域名。网络不可用时自动启动 100% 离线纯 Kotlin 八字与本地种子库兜底。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("API Base URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateServerUrl(inputUrl)
                        showSettingsDialog = false
                    }
                ) {
                    Text("保存生效", color = FortuneGold, fontWeight = FontWeight.Bold)
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
            isStreaming = isAiStreaming,
            aiConfig = aiConfig,
            onDismiss = { viewModel.closeDivinationSheet() },
            onRedivine = { viewModel.startDivination() },
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
