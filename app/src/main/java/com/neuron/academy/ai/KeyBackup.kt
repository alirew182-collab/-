package com.neuron.academy.ai

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Экспорт/импорт списка ключей в зашифрованный файл.
 * Шифрование: PBKDF2-HMAC-SHA256 (120 000 итераций) + AES-256-GCM. Без пароля файл не прочитать.
 */
object KeyBackup {
    private const val ITERATIONS = 120_000
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Envelope(val format: String, val version: Int, val salt: String, val iv: String, val data: String)

    fun export(keys: List<ApiKeyConfig>, password: String): String {
        val plain = json.encodeToString(ListSerializer(ApiKeyConfig.serializer()), keys).toByteArray()
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt), GCMParameterSpec(128, iv))
        val enc = cipher.doFinal(plain)
        val env = Envelope("neuron-keys", 1, b64(salt), b64(iv), b64(enc))
        return json.encodeToString(Envelope.serializer(), env)
    }

    /** @throws IllegalArgumentException при неверном пароле или повреждённом файле */
    fun import(content: String, password: String): List<ApiKeyConfig> {
        val env = try {
            json.decodeFromString(Envelope.serializer(), content)
        } catch (e: Exception) {
            throw IllegalArgumentException("Это не файл ключей «Нейрона»")
        }
        require(env.format == "neuron-keys") { "Неизвестный формат файла" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val plain = try {
            cipher.init(Cipher.DECRYPT_MODE, derive(password, unb64(env.salt)), GCMParameterSpec(128, unb64(env.iv)))
            cipher.doFinal(unb64(env.data))
        } catch (e: Exception) {
            throw IllegalArgumentException("Неверный пароль")
        }
        return json.decodeFromString(ListSerializer(ApiKeyConfig.serializer()), String(plain))
    }

    private fun derive(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}
