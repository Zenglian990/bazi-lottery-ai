package com.zeng.lottery.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 安全的 AES-256-GCM 密钥存储管理器，用于端侧加密保存用户直连大模型 API Key。
 *
 * 1. Android 真机环境：严格依赖硬件/系统级 AndroidKeyStore 生成与保管主密钥，
 *    不再回退任何硬编码静态密钥种子。
 * 2. 纯 JVM 单元测试环境：无 AndroidKeyStore 时使用进程内随机生成的临时 256-bit 会话密钥，
 *    既满足全套单元测试闭环，又确保 APK 字节码中零静态密钥硬编码。
 */
object KeyStoreManager {

    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "bazi_lottery_ai_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val PREFS_NAME = "lottery_ai_secure_prefs"

    // 仅限纯 JVM 单元测试使用的内存存储与进程内随机临时密钥（不落盘、无硬编码）
    private val jvmTestPrefs = ConcurrentHashMap<String, String>()
    private val jvmEphemeralTestKey: SecretKey by lazy {
        val randomBytes = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        SecretKeySpec(randomBytes, "AES")
    }

    private val isAndroidKeyStoreAvailable: Boolean by lazy {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER)
            ks.load(null)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun generateAndroidKeyStoreKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE_PROVIDER)
        val specClass = Class.forName("android.security.keystore.KeyGenParameterSpec\$Builder")
        val constructor = specClass.getConstructor(String::class.java, Int::class.javaPrimitiveType)
        val purposes = 1 or 2 // PURPOSE_ENCRYPT | PURPOSE_DECRYPT
        val builder = constructor.newInstance(KEY_ALIAS, purposes)

        val setBlockModes = specClass.getMethod("setBlockModes", Array<String>::class.java)
        setBlockModes.invoke(builder, arrayOf("GCM"))

        val setEncryptionPaddings = specClass.getMethod("setEncryptionPaddings", Array<String>::class.java)
        setEncryptionPaddings.invoke(builder, arrayOf("NoPadding"))

        val setKeySize = specClass.getMethod("setKeySize", Int::class.javaPrimitiveType)
        setKeySize.invoke(builder, 256)

        val buildMethod = specClass.getMethod("build")
        val keyGenSpec = buildMethod.invoke(builder)

        val initMethod = KeyGenerator::class.java.getMethod(
            "init",
            java.security.spec.AlgorithmParameterSpec::class.java
        )
        initMethod.invoke(keyGenerator, keyGenSpec)
        return keyGenerator.generateKey()
    }

    private fun getSecretKey(): SecretKey? {
        if (!isAndroidKeyStoreAvailable) {
            return jvmEphemeralTestKey
        }
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateAndroidKeyStoreKey()
            } else {
                val entry = keyStore.getEntry(KEY_ALIAS, null)
                if (entry is KeyStore.SecretKeyEntry) {
                    entry.secretKey
                } else {
                    // 清理异常条目并重新生成硬件密钥，绝不回退弱密钥
                    keyStore.deleteEntry(KEY_ALIAS)
                    generateAndroidKeyStoreKey()
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 使用 AES-256-GCM 加密明文，输出 "base64(iv):base64(ciphertext)"。
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val secretKey = getSecretKey() ?: return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            try {
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            } catch (_: Throwable) {
                val randomIv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, randomIv))
            }
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            if (iv == null || iv.isEmpty()) {
                ""
            } else {
                "${encodeBase64(iv)}:${encodeBase64(cipherBytes)}"
            }
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * 解密 AES-256-GCM 密文载荷 ("base64(iv):base64(ciphertext)")。
     * 为兼容旧版本升级，保留对无冒号历史明文字符串的安全读取。
     */
    fun decrypt(encryptedPayload: String): String {
        if (encryptedPayload.isBlank()) return ""
        val trimmed = encryptedPayload.trim()
        if (!trimmed.contains(":")) {
            return trimmed
        }
        val secretKey = getSecretKey() ?: return ""
        return try {
            val parts = trimmed.split(":", limit = 2)
            if (parts.size != 2) return ""
            val iv = decodeBase64(parts[0])
            val cipherBytes = decodeBase64(parts[1])
            if (iv.isEmpty() || cipherBytes.isEmpty()) {
                return ""
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            ""
        }
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Throwable) {
            try {
                java.util.Base64.getEncoder().encodeToString(bytes)
            } catch (_: Throwable) {
                ""
            }
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            Base64.decode(str, Base64.NO_WRAP)
        } catch (_: Throwable) {
            try {
                java.util.Base64.getDecoder().decode(str)
            } catch (_: Throwable) {
                ByteArray(0)
            }
        }
    }

    /**
     * 脱敏展示 API Key（如 sk-••••••••1234）。
     */
    fun maskApiKey(key: String?): String {
        if (key.isNullOrBlank()) return ""
        val trimmed = key.trim()
        if (trimmed.length <= 8) return "••••••••"
        return when {
            trimmed.startsWith("sk-") -> "sk-••••••••${trimmed.takeLast(4)}"
            trimmed.startsWith("AIza") -> "AIza••••••••${trimmed.takeLast(4)}"
            else -> "${trimmed.take(3)}••••••••${trimmed.takeLast(4)}"
        }
    }

    private fun getPrefs(context: Context?): SharedPreferences? {
        return try {
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (_: Throwable) {
            null
        }
    }

    fun saveApiKey(context: Context?, keyName: String, apiKey: String) {
        try {
            val encrypted = encrypt(apiKey)
            val prefs = getPrefs(context)
            if (prefs != null) {
                prefs.edit().putString("key_$keyName", encrypted).apply()
            } else {
                jvmTestPrefs["key_$keyName"] = encrypted
            }
        } catch (_: Throwable) {
            // 防御性捕获，不中断主界面
        }
    }

    fun getApiKey(context: Context?, keyName: String): String {
        return try {
            val prefs = getPrefs(context)
            val enc = if (prefs != null) {
                prefs.getString("key_$keyName", "") ?: ""
            } else {
                jvmTestPrefs["key_$keyName"] ?: ""
            }
            decrypt(enc)
        } catch (_: Throwable) {
            ""
        }
    }

    fun saveAiConfig(context: Context?, config: AiConfig) {
        try {
            val prefs = getPrefs(context)
            val encryptedDeepSeek = encrypt(config.deepseekApiKey)
            val encryptedGemini = encrypt(config.geminiApiKey)
            val encryptedOpenAi = encrypt(config.openaiApiKey)

            if (prefs != null) {
                prefs.edit()
                    .putString("provider", config.provider.name)
                    .putString("deepseek_key", encryptedDeepSeek)
                    .putString("deepseek_base_url", config.deepseekBaseUrl)
                    .putString("deepseek_model", config.deepseekModel)
                    .putString("gemini_key", encryptedGemini)
                    .putString("gemini_model", config.geminiModel)
                    .putString("openai_key", encryptedOpenAi)
                    .putString("openai_base_url", config.openaiBaseUrl)
                    .putString("openai_model", config.openaiModel)
                    .apply()
            } else {
                jvmTestPrefs["provider"] = config.provider.name
                jvmTestPrefs["deepseek_key"] = encryptedDeepSeek
                jvmTestPrefs["deepseek_base_url"] = config.deepseekBaseUrl
                jvmTestPrefs["deepseek_model"] = config.deepseekModel
                jvmTestPrefs["gemini_key"] = encryptedGemini
                jvmTestPrefs["gemini_model"] = config.geminiModel
                jvmTestPrefs["openai_key"] = encryptedOpenAi
                jvmTestPrefs["openai_base_url"] = config.openaiBaseUrl
                jvmTestPrefs["openai_model"] = config.openaiModel
            }
        } catch (_: Throwable) {
            // 防御性捕获
        }
    }

    fun loadAiConfig(context: Context?): AiConfig {
        return try {
            val prefs = getPrefs(context)
            if (prefs != null) {
                val providerStr = prefs.getString("provider", AiProvider.BACKEND_RELAY.name) ?: AiProvider.BACKEND_RELAY.name
                val encDeepSeek = prefs.getString("deepseek_key", "") ?: ""
                val deepseekBaseUrl = prefs.getString("deepseek_base_url", "https://api.deepseek.com/v1") ?: "https://api.deepseek.com/v1"
                val deepseekModel = prefs.getString("deepseek_model", "deepseek-chat") ?: "deepseek-chat"
                val encGemini = prefs.getString("gemini_key", "") ?: ""
                val geminiModel = prefs.getString("gemini_model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
                val encOpenAi = prefs.getString("openai_key", "") ?: ""
                val openaiBaseUrl = prefs.getString("openai_base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
                val openaiModel = prefs.getString("openai_model", "gpt-4o-mini") ?: "gpt-4o-mini"

                AiConfig(
                    provider = AiProvider.fromName(providerStr),
                    deepseekApiKey = decrypt(encDeepSeek),
                    deepseekBaseUrl = deepseekBaseUrl,
                    deepseekModel = deepseekModel,
                    geminiApiKey = decrypt(encGemini),
                    geminiModel = geminiModel,
                    openaiApiKey = decrypt(encOpenAi),
                    openaiBaseUrl = openaiBaseUrl,
                    openaiModel = openaiModel
                )
            } else {
                val providerStr = jvmTestPrefs["provider"] ?: AiProvider.BACKEND_RELAY.name
                val encDeepSeek = jvmTestPrefs["deepseek_key"] ?: ""
                val deepseekBaseUrl = jvmTestPrefs["deepseek_base_url"] ?: "https://api.deepseek.com/v1"
                val deepseekModel = jvmTestPrefs["deepseek_model"] ?: "deepseek-chat"
                val encGemini = jvmTestPrefs["gemini_key"] ?: ""
                val geminiModel = jvmTestPrefs["gemini_model"] ?: "gemini-2.5-flash"
                val encOpenAi = jvmTestPrefs["openai_key"] ?: ""
                val openaiBaseUrl = jvmTestPrefs["openai_base_url"] ?: "https://api.openai.com/v1"
                val openaiModel = jvmTestPrefs["openai_model"] ?: "gpt-4o-mini"

                AiConfig(
                    provider = AiProvider.fromName(providerStr),
                    deepseekApiKey = decrypt(encDeepSeek),
                    deepseekBaseUrl = deepseekBaseUrl,
                    deepseekModel = deepseekModel,
                    geminiApiKey = decrypt(encGemini),
                    geminiModel = geminiModel,
                    openaiApiKey = decrypt(encOpenAi),
                    openaiBaseUrl = openaiBaseUrl,
                    openaiModel = openaiModel
                )
            }
        } catch (_: Throwable) {
            AiConfig()
        }
    }
}
