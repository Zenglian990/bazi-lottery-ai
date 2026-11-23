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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.zeng.lottery.ai.OpenAiClient
import com.zeng.lottery.ui.theme.FortuneGold
import com.zeng.lottery.ui.theme.LocalAccentGold

/**
 * AI 大模型通道配置对话框（支持后端安全中继、DeepSeek V3/R1、Gemini 2.5 与 OpenAI 兼容端点）。
 * 全面改用 rememberSaveable 保态，并对直连 API Key 非空与 HTTPS 端点做强校验。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsDialog(
    currentConfig: AiConfig = AiConfig(),
    onDismiss: () -> Unit,
    onSaveConfig: (AiConfig) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val accentGold = LocalAccentGold.current
    var selectedProviderName by rememberSaveable { mutableStateOf(currentConfig.provider.name) }
    val selectedProvider = AiProvider.fromName(selectedProviderName)

    var deepseekKey by rememberSaveable { mutableStateOf(currentConfig.deepseekApiKey) }
    var deepseekBaseUrl by rememberSaveable { mutableStateOf(currentConfig.deepseekBaseUrl.ifBlank { "https://api.deepseek.com/v1" }) }
    var deepseekModel by rememberSaveable { mutableStateOf(currentConfig.deepseekModel.ifBlank { "deepseek-chat" }) }

    var geminiKey by rememberSaveable { mutableStateOf(currentConfig.geminiApiKey) }
    var geminiModel by rememberSaveable { mutableStateOf(currentConfig.geminiModel.ifBlank { "gemini-2.5-flash" }) }

    var openaiKey by rememberSaveable { mutableStateOf(currentConfig.openaiApiKey) }
    var openaiBaseUrl by rememberSaveable { mutableStateOf(currentConfig.openaiBaseUrl.ifBlank { "https://api.openai.com/v1" }) }
    var openaiModel by rememberSaveable { mutableStateOf(currentConfig.openaiModel.ifBlank { "gpt-4o-mini" }) }

    var showDeepseekKey by rememberSaveable { mutableStateOf(false) }
    var showGeminiKey by rememberSaveable { mutableStateOf(false) }
    var showOpenAiKey by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

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
                    tint = accentGold,
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
                    color = accentGold
                )

                AiProvider.entries.forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedProviderName = provider.name
                                validationError = null
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedProvider == provider,
                            onClick = {
                                selectedProviderName = provider.name
                                validationError = null
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = accentGold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedProvider == provider) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val desc = when (provider) {
                                AiProvider.BACKEND_RELAY -> "免配置 Key，内置易道典籍实时推演引擎；如需深度大模型，可在下方填入个人 Key"
                                AiProvider.DIRECT_DEEPSEEK -> "国内直连，支持 DeepSeek-V3 (deepseek-chat) 与 R1 深度推理 (deepseek-reasoner)"
                                AiProvider.DIRECT_GEMINI -> "Google 直连，支持 Gemini 2.5 Flash / 2.5 Pro 极速流式解盘"
                                AiProvider.DIRECT_OPENAI -> "支持 OpenAI、硅基流动、Groq 等 HTTPS OpenAI 兼容接口"
                            }
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = if (provider == AiProvider.DIRECT_DEEPSEEK) accentGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!validationError.isNullOrBlank()) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                when (selectedProvider) {
                    AiProvider.BACKEND_RELAY -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = FortuneGold.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "✓ 已启用云端安全中继通道。内置 HMAC-SHA256 一次性签名与易道典籍 RAG 检索，无需在手机端存放明文密钥。",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    AiProvider.DIRECT_DEEPSEEK -> {
                        ApiKeyField(
                            value = deepseekKey,
                            onValueChange = {
                                deepseekKey = it
                                validationError = null
                            },
                            label = "DeepSeek API Key",
                            placeholder = "sk-...",
                            visible = showDeepseekKey,
                            onToggleVisible = { showDeepseekKey = !showDeepseekKey },
                            onPaste = {
                                val clip = clipboardManager.getText()?.text?.trim()?.replace("\r", "")?.replace("\n", "")
                                if (!clip.isNullOrBlank()) {
                                    deepseekKey = clip
                                    validationError = null
                                }
                            }
                        )

                        Text("选择模型规格：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Pair("DeepSeek-V3 (deepseek-chat)", "deepseek-chat"),
                                Pair("DeepSeek-R1 推理 (deepseek-reasoner)", "deepseek-reasoner")
                            ).forEach { (label, code) ->
                                SuggestionChip(
                                    onClick = { deepseekModel = code },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = if (deepseekModel == code) {
                                        SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = accentGold.copy(alpha = 0.18f),
                                            labelColor = accentGold
                                        )
                                    } else {
                                        SuggestionChipDefaults.suggestionChipColors()
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = deepseekModel,
                            onValueChange = { deepseekModel = it },
                            label = { Text("Model Name") },
                            placeholder = { Text("deepseek-chat") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Next)
                        )

                        OutlinedTextField(
                            value = deepseekBaseUrl,
                            onValueChange = {
                                deepseekBaseUrl = it
                                validationError = null
                            },
                            label = { Text("API 端点 (仅限 HTTPS)") },
                            placeholder = { Text("https://api.deepseek.com/v1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done)
                        )
                    }

                    AiProvider.DIRECT_GEMINI -> {
                        ApiKeyField(
                            value = geminiKey,
                            onValueChange = {
                                geminiKey = it
                                validationError = null
                            },
                            label = "Google Gemini API Key",
                            placeholder = "AIzaSy...",
                            visible = showGeminiKey,
                            onToggleVisible = { showGeminiKey = !showGeminiKey },
                            onPaste = {
                                val clip = clipboardManager.getText()?.text?.trim()?.replace("\r", "")?.replace("\n", "")
                                if (!clip.isNullOrBlank()) {
                                    geminiKey = clip
                                    validationError = null
                                }
                            }
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                Pair("Gemini 2.5 Flash (推荐)", "gemini-2.5-flash"),
                                Pair("Gemini 2.5 Pro (深度)", "gemini-2.5-pro")
                            ).forEach { (label, code) ->
                                SuggestionChip(
                                    onClick = { geminiModel = code },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = if (geminiModel == code) {
                                        SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = accentGold.copy(alpha = 0.18f),
                                            labelColor = accentGold
                                        )
                                    } else {
                                        SuggestionChipDefaults.suggestionChipColors()
                                    }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = geminiModel,
                            onValueChange = { geminiModel = it },
                            label = { Text("Gemini Model") },
                            placeholder = { Text("gemini-2.5-flash") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done)
                        )
                    }

                    AiProvider.DIRECT_OPENAI -> {
                        ApiKeyField(
                            value = openaiKey,
                            onValueChange = {
                                openaiKey = it
                                validationError = null
                            },
                            label = "API Key",
                            placeholder = "sk-...",
                            visible = showOpenAiKey,
                            onToggleVisible = { showOpenAiKey = !showOpenAiKey },
                            onPaste = {
                                val clip = clipboardManager.getText()?.text?.trim()?.replace("\r", "")?.replace("\n", "")
                                if (!clip.isNullOrBlank()) {
                                    openaiKey = clip
                                    validationError = null
                                }
                            }
                        )

                        OutlinedTextField(
                            value = openaiBaseUrl,
                            onValueChange = {
                                openaiBaseUrl = it
                                validationError = null
                            },
                            label = { Text("Base URL (仅限 HTTPS)") },
                            placeholder = { Text("https://api.openai.com/v1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next)
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            urlPresets.forEach { (name, url) ->
                                SuggestionChip(
                                    onClick = {
                                        openaiBaseUrl = url
                                        validationError = null
                                    },
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
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = ImeAction.Done)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanDeepseekKey = deepseekKey.trim().replace("\r", "").replace("\n", "")
                    val cleanDeepseekUrl = deepseekBaseUrl.trim().replace("\r", "").replace("\n", "").ifBlank { "https://api.deepseek.com/v1" }
                    val cleanDeepseekModel = deepseekModel.trim().ifBlank { "deepseek-chat" }

                    val cleanGeminiKey = geminiKey.trim().replace("\r", "").replace("\n", "")
                    val cleanGeminiModel = geminiModel.trim().ifBlank { "gemini-2.5-flash" }

                    val cleanOpenaiKey = openaiKey.trim().replace("\r", "").replace("\n", "")
                    val cleanOpenaiUrl = openaiBaseUrl.trim().replace("\r", "").replace("\n", "").ifBlank { "https://api.openai.com/v1" }
                    val cleanOpenaiModel = openaiModel.trim().ifBlank { "gpt-4o-mini" }

                    // 保存前严格校验直连参数与 HTTPS 安全
                    when (selectedProvider) {
                        AiProvider.DIRECT_DEEPSEEK -> {
                            if (cleanDeepseekKey.isBlank()) {
                                validationError = "请填写 DeepSeek API Key，或切换回云端安全中继"
                                return@Button
                            }
                            val (ok, msg) = OpenAiClient.validateDirectBaseUrl(cleanDeepseekUrl)
                            if (!ok) {
                                validationError = msg
                                return@Button
                            }
                        }
                        AiProvider.DIRECT_GEMINI -> {
                            if (cleanGeminiKey.isBlank()) {
                                validationError = "请填写 Google Gemini API Key，或切换回云端安全中继"
                                return@Button
                            }
                        }
                        AiProvider.DIRECT_OPENAI -> {
                            if (cleanOpenaiKey.isBlank()) {
                                validationError = "请填写 OpenAI 兼容 API Key，或切换回云端安全中继"
                                return@Button
                            }
                            val (ok, msg) = OpenAiClient.validateDirectBaseUrl(cleanOpenaiUrl)
                            if (!ok) {
                                validationError = msg
                                return@Button
                            }
                        }
                        AiProvider.BACKEND_RELAY -> Unit
                    }

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
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = FortuneGold,
                    contentColor = Color(0xFF1A1200)
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

@Composable
private fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    onPaste: () -> Unit
) {
    val accentGold = LocalAccentGold.current
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            onValueChange(input.replace("\r", "").replace("\n", ""))
        },
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done
        ),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPaste) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "粘贴剪贴板",
                        tint = accentGold
                    )
                }
                IconButton(onClick = onToggleVisible) {
                    Icon(
                        imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "切换可见性"
                    )
                }
            }
        }
    )
}
