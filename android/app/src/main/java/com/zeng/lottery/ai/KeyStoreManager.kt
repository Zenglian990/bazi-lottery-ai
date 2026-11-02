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
 * Includes automatic pure JVM unit test fallback when Android KeyStore is not available.
 */
object KeyStoreManager {

    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "bazi_lottery_ai_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val IV_LENGTH = 12
    private const val PREFS_NAME = "lottery_ai_secure_prefs"

    // In-memory fallback storage for JVM unit test execution
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
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    /**
     * Encrypt plain text using AES-256 GCM.
     */
    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val ivBase64 = encodeBase64(iv)
        val cipherBase64 = encodeBase64(cipherBytes)
        return "$ivBase64:$cipherBase64"
    }

    /**
     * Decrypt AES-256 GCM encrypted string.
     */
    fun decrypt(encryptedPayload: String): String {
        if (encryptedPayload.isEmpty() || !encryptedPayload.contains(":")) return ""
        return try {
            val parts = encryptedPayload.split(":", limit = 2)
            val iv = decodeBase64(parts[0])
            val cipherBytes = decodeBase64(parts[1])

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    private fun encodeBase64(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun decodeBase64(str: String): ByteArray {
        return try {
            Base64.decode(str, Base64.NO_WRAP)
        } catch (e: Throwable) {
            java.util.Base64.getDecoder().decode(str)
        }
    }

    /**
     * Mask API key for secure display in UI (e.g. sk-••••••••1234).
     */
    fun maskApiKey(key: String): String {
        if (key.isBlank()) return ""
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

    fun saveAiConfig(context: Context?, config: AiConfig) {
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
    }

    fun loadAiConfig(context: Context?): AiConfig {
        val prefs = getPrefs(context)
        return if (prefs != null) {
            val providerStr = prefs.getString("provider", AiProvider.BACKEND_RELAY.name)
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
    }
}
