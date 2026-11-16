package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.ApiKeys
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface KeyStorage {
    fun loadKeys(): ApiKeys
    fun saveKeys(keys: ApiKeys)
    fun clearKeys()
}

/**
 * Stores API keys encrypted via Android Keystore (AES-256-GCM).
 * Never stores plaintext secrets in code or SharedPreferences.
 */
class SecureKeyStorage(context: Context) : KeyStorage {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun loadKeys(): ApiKeys {
        return ApiKeys(
            geminiKey = decrypt(prefs.getString(KEY_GEMINI, null)),
            openAiKey = decrypt(prefs.getString(KEY_OPENAI, null)),
            perplexityKey = decrypt(prefs.getString(KEY_PERPLEXITY, null))
        )
    }

    override fun saveKeys(keys: ApiKeys) {
        prefs.edit().apply {
            putEncryptedOrRemove(KEY_GEMINI, keys.geminiKey.trim())
            putEncryptedOrRemove(KEY_OPENAI, keys.openAiKey.trim())
            putEncryptedOrRemove(KEY_PERPLEXITY, keys.perplexityKey.trim())
            remove("enc_claude")
            remove("enc_grok")
            apply()
        }
    }

    override fun clearKeys() {
        prefs.edit().clear().apply()
    }

    private fun SharedPreferences.Editor.putEncryptedOrRemove(prefKey: String, value: String) {
        if (value.isEmpty()) {
            remove(prefKey)
        } else {
            putString(prefKey, encrypt(value))
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) {
            return existing.secretKey
        }
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        return "$ivB64:$ctB64"
    }

    private fun decrypt(encoded: String?): String {
        if (encoded.isNullOrBlank()) return ""
        val parts = encoded.split(":", limit = 2)
        if (parts.size != 2) return ""
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val PREFS_NAME = "vroum_secure_keys_prefs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "vroum_api_keys_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        private const val KEY_GEMINI = "enc_gemini"
        private const val KEY_OPENAI = "enc_openai"
        private const val KEY_PERPLEXITY = "enc_perplexity"
    }
}
