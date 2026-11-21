package com.zeng.lottery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zeng.lottery.ui.theme.BaziLotteryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BaziLotteryTheme {
                LotteryApp()
            }
        }
    }
}
