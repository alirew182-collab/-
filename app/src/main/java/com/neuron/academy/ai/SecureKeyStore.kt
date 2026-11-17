package com.neuron.academy.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

/**
 * Зашифрованное хранилище API-ключей (EncryptedSharedPreferences, мастер-ключ в Android Keystore).
 * Ключи никогда не попадают в Room, логи или код.
 */
class SecureKeyStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "neuron_secure_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _keys = MutableStateFlow(load())
    val keys: StateFlow<List<ApiKeyConfig>> = _keys.asStateFlow()

    private fun load(): List<ApiKeyConfig> = try {
        prefs.getString(KEY_LIST, null)?.let { json.decodeFromString(ListSerializer(ApiKeyConfig.serializer()), it) } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    private fun save(list: List<ApiKeyConfig>) {
        prefs.edit().putString(KEY_LIST, json.encodeToString(ListSerializer(ApiKeyConfig.serializer()), list)).apply()
        _keys.value = list
    }

    fun newId(): String = UUID.randomUUID().toString()

    fun upsert(key: ApiKeyConfig) {
        val list = _keys.value.toMutableList()
        val idx = list.indexOfFirst { it.id == key.id }
        if (idx >= 0) list[idx] = key else list.add(key)
        save(list)
    }

    fun delete(id: String) = save(_keys.value.filterNot { it.id == id })

    fun setEnabled(id: String, enabled: Boolean) =
        save(_keys.value.map { if (it.id == id) it.copy(enabled = enabled) else it })

    fun replaceAll(list: List<ApiKeyConfig>) = save(list)

    /** Слияние импортированных ключей: дубликаты (провайдер + ключ) пропускаются. */
    fun merge(imported: List<ApiKeyConfig>): Int {
        val existing = _keys.value
        val fresh = imported.filter { imp -> existing.none { it.provider == imp.provider && it.apiKey == imp.apiKey } }
            .map { it.copy(id = newId()) }
        save(existing + fresh)
        return fresh.size
    }

    // ---- PIN режима учителя (хранится как SHA-256 с солью) ----
    fun hasTeacherPin(): Boolean = prefs.contains(KEY_PIN)

    fun setTeacherPin(pin: String) {
        prefs.edit().putString(KEY_PIN, hash(pin)).apply()
    }

    fun checkTeacherPin(pin: String): Boolean = prefs.getString(KEY_PIN, null) == hash(pin)

    private fun hash(pin: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(("neuron-salt:" + pin).toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_LIST = "api_keys_v1"
        private const val KEY_PIN = "teacher_pin"
    }
}
