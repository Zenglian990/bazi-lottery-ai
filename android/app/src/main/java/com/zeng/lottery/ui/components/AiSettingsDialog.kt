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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeng.lottery.ai.AiConfig
import com.zeng.lottery.ai.AiProvider
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.ImperialRed

/**
 * AI Configuration Dialog for selecting AI provider (Backend Relay / Direct Gemini / Direct OpenAI),
 * configuring endpoints, and securely managing API keys.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsDialog(
    currentConfig: AiConfig,
    onDismiss: () -> Unit,
    onSaveConfig: (AiConfig) -> Unit
) {
    var selectedProvider by remember { mutableStateOf(currentConfig.provider) }
    var geminiKey by remember { mutableStateOf(currentConfig.geminiApiKey) }
    var geminiModel by remember { mutableStateOf(currentConfig.geminiModel) }
    var openaiKey by remember { mutableStateOf(currentConfig.openaiApiKey) }
    var openaiBaseUrl by remember { mutableStateOf(currentConfig.openaiBaseUrl) }
    var openaiModel by remember { mutableStateOf(currentConfig.openaiModel) }

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
                            if (provider == AiProvider.BACKEND_RELAY) {
                                Text(
                                    text = "免配置 API Key，经由云端安全中继与真实命理推演",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Provider specific input forms
                when (selectedProvider) {
                    AiProvider.BACKEND_RELAY -> {
                        Text(
                            text = "✓ 已启用后端安全中继通道。内置智能容灾与易道彩经大师五行推演，开箱即用。",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AiProvider.DIRECT_GEMINI -> {
                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = { geminiKey = it },
                            label = { Text("Google Gemini API Key") },
                            placeholder = { Text("AIzaSy...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                    Icon(
                                        imageVector = if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "切换可见性"
                                    )
                                }
                            }
                        )

                        OutlinedTextField(
                            value = geminiModel,
                            onValueChange = { geminiModel = it },
                            label = { Text("Gemini Model") },
                            placeholder = { Text("gemini-1.5-flash") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    AiProvider.DIRECT_OPENAI -> {
                        OutlinedTextField(
                            value = openaiKey,
                            onValueChange = { openaiKey = it },
                            label = { Text("API Key") },
                            placeholder = { Text("sk-...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showOpenAiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showOpenAiKey = !showOpenAiKey }) {
                                    Icon(
                                        imageVector = if (showOpenAiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "切换可见性"
                                    )
                                }
                            }
                        )

                        OutlinedTextField(
                            value = openaiBaseUrl,
                            onValueChange = { openaiBaseUrl = it },
                            label = { Text("Base URL") },
                            placeholder = { Text("https://api.openai.com/v1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
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
                            onValueChange = { openaiModel = it },
                            label = { Text("Model Name") },
                            placeholder = { Text("gpt-4o-mini / deepseek-chat") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = AiConfig(
                        provider = selectedProvider,
                        geminiApiKey = geminiKey.trim(),
                        geminiModel = geminiModel.trim().ifBlank { "gemini-1.5-flash" },
                        openaiApiKey = openaiKey.trim(),
                        openaiBaseUrl = openaiBaseUrl.trim().ifBlank { "https://api.openai.com/v1" },
                        openaiModel = openaiModel.trim().ifBlank { "gpt-4o-mini" }
                    )
                    onSaveConfig(updated)
                    onDismiss()
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
