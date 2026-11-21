package com.zeng.lottery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeng.lottery.ui.theme.BaziLotteryTheme
import com.zeng.lottery.ui.viewmodel.LotteryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: LotteryViewModel = viewModel()
            val isDarkTheme by vm.isDarkTheme.collectAsState()
            BaziLotteryTheme(darkTheme = isDarkTheme) {
                LotteryApp(viewModel = vm)
            }
        }
    }
}
