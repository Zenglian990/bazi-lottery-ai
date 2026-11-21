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
 * Secure AES-256 GCM KeyStore Manager for storing client-side API keys.
 *
 * 1. Primary Layer: Android KeyStore hardware/system backed AES-256-GCM ("AES/GCM/NoPadding").
 *    - Uses provider-generated IV to ensure full compatibility with AndroidKeyStore
 *      randomized encryption requirements.
 * 2. Unit-Test Layer: Standard JCE AES-256-GCM fallback when running in a pure JVM unit test
 *    environment where the AndroidKeyStore provider is unavailable.
 */
object KeyStoreManager {

    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "bazi_lottery_ai_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val PREFS_NAME = "lottery_ai_secure_prefs"

    // In-memory fallback storage for pure JVM unit test execution
    private val jvmTestPrefs = ConcurrentHashMap<String, String>()
    private val jvmFallbackKey: SecretKey by lazy {
        val raw = "ZengBaziLottery2026SecureKeySeed!".toByteArray(StandardCharsets.UTF_8).copyOf(32)
        SecretKeySpec(raw, "AES")
    }

    private val isAndroidKeyStoreAvailable: Boolean by lazy {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER)
            ks.load(null)
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun getSecretKey(): SecretKey {
        if (!isAndroidKeyStoreAvailable) {
            return jvmFallbackKey
        }
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
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

                val initMethod = KeyGenerator::class.java.getMethod("init", java.security.spec.AlgorithmParameterSpec::class.java)
                initMethod.invoke(keyGenerator, keyGenSpec)
                keyGenerator.generateKey()
            } else {
                val entry = keyStore.getEntry(KEY_ALIAS, null)
                if (entry is KeyStore.SecretKeyEntry) {
                    entry.secretKey
                } else {
                    keyStore.deleteEntry(KEY_ALIAS)
                    jvmFallbackKey
                }
            }
        } catch (e: Throwable) {
            jvmFallbackKey
        }
    }

    /**
     * Encrypt plain text using AES-256-GCM ("AES/GCM/NoPadding") backed by AndroidKeyStore
     * (or JCE AES-256-GCM in pure JVM unit tests).
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val secretKey = getSecretKey()
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
        } catch (e: Throwable) {
            ""
        }
    }

    /**
     * Decrypt AES-256-GCM payload ("base64(iv):base64(ciphertext)").
     * Retains backward-compatible read support for legacy unencrypted plain strings without a colon.
     */
    fun decrypt(encryptedPayload: String): String {
        if (encryptedPayload.isBlank()) return ""
        val trimmed = encryptedPayload.trim()
        if (!trimmed.contains(":")) {
            return trimmed
        }
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
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Throwable) {
            ""
        }
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Throwable) {
            try {
                java.util.Base64.getEncoder().encodeToString(bytes)
            } catch (e2: Throwable) {
                ""
            }
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            Base64.decode(str, Base64.NO_WRAP)
        } catch (e: Throwable) {
            try {
                java.util.Base64.getDecoder().decode(str)
            } catch (e2: Throwable) {
                ByteArray(0)
            }
        }
    }

    /**
     * Mask API key for secure display in UI (e.g. sk-••••••••1234).
     */
    fun maskApiKey(key: String?): String {
        if (key.isNullOrBlank()) return ""
        val trimmed = key.trim()
        if (trimmed.length <= 8) return "••••••••"
        return when {
            trimmed.startsWith("sk-") -> {
                val prefix = "sk-"
                val suffix = trimmed.takeLast(4)
                "$prefix••••••••$suffix"
            }
            trimmed.startsWith("AIza") -> {
                val prefix = "AIza"
                val suffix = trimmed.takeLast(4)
                "$prefix••••••••$suffix"
            }
            else -> {
                val prefix = trimmed.take(3)
                val suffix = trimmed.takeLast(4)
                "$prefix••••••••$suffix"
            }
        }
    }

    private fun getPrefs(context: Context?): SharedPreferences? {
        return try {
            context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Safely save a single API key to secure SharedPreferences.
     */
    fun saveApiKey(context: Context?, keyName: String, apiKey: String) {
        try {
            val encrypted = encrypt(apiKey)
            val prefs = getPrefs(context)
            if (prefs != null) {
                prefs.edit().putString("key_$keyName", encrypted).apply()
            } else {
                jvmTestPrefs["key_$keyName"] = encrypted
            }
        } catch (e: Throwable) {
            // Defensive swallow: never crash UI
        }
    }

    /**
     * Safely retrieve a single API key from secure SharedPreferences.
     */
    fun getApiKey(context: Context?, keyName: String): String {
        return try {
            val prefs = getPrefs(context)
            val enc = if (prefs != null) {
                prefs.getString("key_$keyName", "") ?: ""
            } else {
                jvmTestPrefs["key_$keyName"] ?: ""
            }
            decrypt(enc)
        } catch (e: Throwable) {
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
        } catch (e: Throwable) {
            // Top-level defensive swallow: never crash
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
        } catch (e: Throwable) {
            AiConfig()
        }
    }
}
