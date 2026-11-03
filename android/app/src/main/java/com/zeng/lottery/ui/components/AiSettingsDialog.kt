package com.zeng.lottery.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.ai.AiConfig
import com.zeng.lottery.ai.AiProvider
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed

/**
 * AI Configuration Dialog for selecting AI provider (Backend Relay / DeepSeek / Direct Gemini / Direct OpenAI),
 * configuring endpoints, and securely managing API keys.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsDialog(
    currentConfig: AiConfig = AiConfig(),
    onDismiss: () -> Unit,
    onSaveConfig: (AiConfig) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var selectedProvider by remember { mutableStateOf(currentConfig.provider) }
    var deepseekKey by remember { mutableStateOf(currentConfig.deepseekApiKey ?: "") }
    var deepseekBaseUrl by remember { mutableStateOf(currentConfig.deepseekBaseUrl ?: "https://api.deepseek.com/v1") }
    var deepseekModel by remember { mutableStateOf(currentConfig.deepseekModel ?: "deepseek-flash") }

    var geminiKey by remember { mutableStateOf(currentConfig.geminiApiKey ?: "") }
    var geminiModel by remember { mutableStateOf(currentConfig.geminiModel ?: "gemini-3.8-flash") }

    var openaiKey by remember { mutableStateOf(currentConfig.openaiApiKey ?: "") }
    var openaiBaseUrl by remember { mutableStateOf(currentConfig.openaiBaseUrl ?: "https://api.openai.com/v1") }
    var openaiModel by remember { mutableStateOf(currentConfig.openaiModel ?: "gpt-4o-mini") }

    var showDeepseekKey by remember { mutableStateOf(false) }
    var showGeminiKey by remember { mutableStateOf(false) }
    var showOpenAiKey by remember { mutableStateOf(false) }

    val urlPresets = listOf(
        Pair("OpenAI", "https://api.openai.com/v1"),
        Pair("DeepSeek", "https://api.deepseek.com/v1"),
        Pair("SiliconFlow", "https://api.siliconflow.cn/v1"),
        Pair("Groq", "https://api.groq.com/openai/v1")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = FortuneGold,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "AI 易道解盘配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "选择大模型推理通道：",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = FortuneGold
                )

                // Provider Radio Options
                AiProvider.entries.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedProvider = provider }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedProvider == provider,
                            onClick = { selectedProvider = provider },
                            colors = RadioButtonDefaults.colors(selectedColor = FortuneGold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedProvider == provider) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            when (provider) {
                                AiProvider.BACKEND_RELAY -> {
                                    Text(
                                        text = "免配置 Key，云端中继与易道大师五行推演",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AiProvider.DIRECT_DEEPSEEK -> {
                                    Text(
                                        text = "国内最强纯中文推理，支持 Flash 极速与 V4-Pro 旗舰深度推演 (1M上下文)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = FortuneGold.copy(alpha = 0.85f)
                                    )
                                }
                                AiProvider.DIRECT_GEMINI -> {
                                    Text(
                                        text = "Google 前沿旗舰，支持 Gemini 3.8/2.5 Flash 极速推演",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AiProvider.DIRECT_OPENAI -> {
                                    Text(
                                        text = "支持 OpenAI、硅基流动、Groq 等兼容端点",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Provider specific input forms
                when (selectedProvider) {
                    AiProvider.BACKEND_RELAY -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = FortuneGold.copy(alpha = 0.08f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "✓ 已启用后端安全中继通道。内置智能容灾与易道彩经大师五行推演，开箱即用，无需配置 API Key。",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    AiProvider.DIRECT_DEEPSEEK -> {
                        OutlinedTextField(
                            value = deepseekKey,
                            onValueChange = { input ->
                                try {
                                    val clean = if (input.contains('\r') || input.contains('\n')) {
                                        input.replace("\r", "").replace("\n", "")
                                    } else {
                                        input
                                    }
                                    deepseekKey = clean
                                } catch (_: Throwable) {}
                            },
                            label = { Text("DeepSeek API Key") },
                            placeholder = { Text("sk-...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showDeepseekKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                autoCorrect = false,
                                imeAction = ImeAction.Done
                            ),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                val clip = clipboardManager.getText()?.text
                                                if (!clip.isNullOrBlank()) {
                                                    deepseekKey = clip.trim().replace("\r", "").replace("\n", "")
                                                }
                                            } catch (_: Throwable) {}
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "粘贴剪贴板",
                                            tint = FortuneGold
                                        )
                                    }
                                    IconButton(onClick = { showDeepseekKey = !showDeepseekKey }) {
                                        Icon(
                                            imageVector = if (showDeepseekKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "切换可见性"
                                        )
                                    }
                                }
                            }
                        )

                        Text(
                            text = "选择模型规格：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val deepseekModels = listOf(
                                Pair("deepseek-flash (极速/思考/1M)", "deepseek-flash"),
                                Pair("deepseek-v4-pro (旗舰深度推理/1M)", "deepseek-v4-pro")
                            )
                            deepseekModels.forEach { (label, modelCode) ->
                                SuggestionChip(
                                    onClick = { deepseekModel = modelCode },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = if (deepseekModel == modelCode) {
                                        SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = FortuneGold.copy(alpha = 0.2f),
                                            labelColor = FortuneGold
                                        )
                                    } else {
                                        SuggestionChipDefaults.suggestionChipColors()
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = deepseekModel,
                            onValueChange = { input ->
                                try { deepseekModel = input } catch (_: Throwable) {}
                            },
                            label = { Text("Model Name") },
                            placeholder = { Text("deepseek-flash") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                autoCorrect = false,
                                imeAction = ImeAction.Next
                            )
                        )

                        OutlinedTextField(
                            value = deepseekBaseUrl,
                            onValueChange = { input ->
                                try { deepseekBaseUrl = input } catch (_: Throwable) {}
                            },
                            label = { Text("官方 API 端点") },
                            placeholder = { Text("https://api.deepseek.com/v1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                autoCorrect = false,
                                imeAction = ImeAction.Done
                            )
                        )
                    }

                    AiProvider.DIRECT_GEMINI -> {
                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = { input ->
                                try {
                                    val clean = if (input.contains('\r') || input.contains('\n')) {
                                        input.replace("\r", "").replace("\n", "")
                                    } else {
                                        input
                                    }
                                    geminiKey = clean
                                } catch (_: Throwable) {}
                            },
                            label = { Text("Google Gemini API Key") },
                            placeholder = { Text("AIzaSy...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                autoCorrect = false,
                                imeAction = ImeAction.Done
                            ),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                val clip = clipboardManager.getText()?.text
                                                if (!clip.isNullOrBlank()) {
                                                    geminiKey = clip.trim().replace("\r", "").replace("\n", "")
                                                }
                                            } catch (_: Throwable) {}
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "粘贴剪贴板",
                                            tint = FortuneGold
                                        )
                                    }
                                    IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                        Icon(
                                            imageVector = if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "切换可见性"
                                        )
                                    }
                                }
                            }
                        )

                        Text(
                            text = "前沿模型快捷选择：",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val geminiModels = listOf(
                                Pair("3.8 Flash (最新旗舰推荐)", "gemini-3.8-flash"),
                                Pair("2.5 Flash (极速推演)", "gemini-2.5-flash"),
                                Pair("2.5 Pro (深度逻辑)", "gemini-2.5-pro")
                            )
                            geminiModels.forEach { (label, modelCode) ->
                                SuggestionChip(
                                    onClick = { geminiModel = modelCode },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = if (geminiModel == modelCode) {
                                        SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = FortuneGold.copy(alpha = 0.2f),
                                            labelColor = FortuneGold
                                        )
                                    } else {
                                        SuggestionChipDefaults.suggestionChipColors()
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = geminiModel,
                            onValueChange = { input ->
                                try { geminiModel = input } catch (_: Throwable) {}
                            },
                            label = { Text("Gemini Model") },
                            placeholder = { Text("gemini-3.8-flash") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                autoCorrect = false,
                                imeAction = ImeAction.Done
                            )
                        )
                    }

                    AiProvider.DIRECT_OPENAI -> {
                        OutlinedTextField(
                            value = openaiKey,
                            onValueChange = { input ->
                                try {
                                    val clean = if (input.contains('\r') || input.contains('\n')) {
                                        input.replace("\r", "").replace("\n", "")
                                    } else {
                                        input
                                    }
                                    openaiKey = clean
                                } catch (_: Throwable) {}
                            },
                            label = { Text("API Key") },
                            placeholder = { Text("sk-...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showOpenAiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                autoCorrect = false,
                                imeAction = ImeAction.Done
                            ),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                val clip = clipboardManager.getText()?.text
                                                if (!clip.isNullOrBlank()) {
                                                    openaiKey = clip.trim().replace("\r", "").replace("\n", "")
                                                }
                                            } catch (_: Throwable) {}
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "粘贴剪贴板",
                                            tint = FortuneGold
                                        )
                                    }
                                    IconButton(onClick = { showOpenAiKey = !showOpenAiKey }) {
                                        Icon(
                                            imageVector = if (showOpenAiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = "切换可见性"
                                        )
                                    }
                                }
                            }
                        )

                        OutlinedTextField(
                            value = openaiBaseUrl,
                            onValueChange = { input ->
                                try { openaiBaseUrl = input } catch (_: Throwable) {}
                            },
                            label = { Text("Base URL") },
                            placeholder = { Text("https://api.openai.com/v1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                autoCorrect = false,
                                imeAction = ImeAction.Next
                            )
                        )

                        // Base URL Presets
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            urlPresets.forEach { (name, url) ->
                                SuggestionChip(
                                    onClick = { openaiBaseUrl = url },
                                    label = { Text(name, fontSize = 11.sp) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = openaiModel,
                            onValueChange = { input ->
                                try { openaiModel = input } catch (_: Throwable) {}
                            },
                            label = { Text("Model Name") },
                            placeholder = { Text("gpt-4o-mini / deepseek-flash") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                autoCorrect = false,
                                imeAction = ImeAction.Done
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val cleanDeepseekKey = (deepseekKey ?: "").trim().replace("\r", "").replace("\n", "")
                        val cleanDeepseekUrl = (deepseekBaseUrl ?: "").trim().replace("\r", "").replace("\n", "").ifBlank { "https://api.deepseek.com/v1" }
                        val cleanDeepseekModel = (deepseekModel ?: "").trim().replace("\r", "").replace("\n", "").ifBlank { "deepseek-flash" }

                        val cleanGeminiKey = (geminiKey ?: "").trim().replace("\r", "").replace("\n", "")
                        val cleanGeminiModel = (geminiModel ?: "").trim().replace("\r", "").replace("\n", "").ifBlank { "gemini-3.8-flash" }

                        val cleanOpenaiKey = (openaiKey ?: "").trim().replace("\r", "").replace("\n", "")
                        val cleanOpenaiUrl = (openaiBaseUrl ?: "").trim().replace("\r", "").replace("\n", "").ifBlank { "https://api.openai.com/v1" }
                        val cleanOpenaiModel = (openaiModel ?: "").trim().replace("\r", "").replace("\n", "").ifBlank { "gpt-4o-mini" }

                        val updated = AiConfig(
                            provider = selectedProvider,
                            deepseekApiKey = cleanDeepseekKey,
                            deepseekBaseUrl = cleanDeepseekUrl,
                            deepseekModel = cleanDeepseekModel,
                            geminiApiKey = cleanGeminiKey,
                            geminiModel = cleanGeminiModel,
                            openaiApiKey = cleanOpenaiKey,
                            openaiBaseUrl = cleanOpenaiUrl,
                            openaiModel = cleanOpenaiModel
                        )
                        onSaveConfig(updated)
                        onDismiss()
                    } catch (e: Throwable) {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = FortuneGold,
                    contentColor = Color.Black
                )
            ) {
                Text("保存配置", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
