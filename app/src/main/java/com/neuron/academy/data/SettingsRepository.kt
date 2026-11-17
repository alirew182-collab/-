package com.neuron.academy.data

import android.content.Context
import com.neuron.academy.ai.AiFeature
import com.neuron.academy.ai.KeySelectionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val title: String) { SYSTEM("Как в системе"), LIGHT("Светлая"), DARK("Тёмная") }

/** Обычные (несекретные) настройки приложения. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("neuron_settings", Context.MODE_PRIVATE)

    private fun <T> state(initial: T) = MutableStateFlow(initial)

    private val _themeMode = state(ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name)!!))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _sound = state(prefs.getBoolean("sound", true))
    val sound: StateFlow<Boolean> = _sound.asStateFlow()

    private val _vibration = state(prefs.getBoolean("vibration", true))
    val vibration: StateFlow<Boolean> = _vibration.asStateFlow()

    private val _notifications = state(prefs.getBoolean("notifications", false))
    val notifications: StateFlow<Boolean> = _notifications.asStateFlow()

    private val _notifyHour = state(prefs.getInt("notify_hour", 18))
    val notifyHour: StateFlow<Int> = _notifyHour.asStateFlow()

    private val _notifyMinute = state(prefs.getInt("notify_minute", 0))
    val notifyMinute: StateFlow<Int> = _notifyMinute.asStateFlow()

    /** Порог завершения класса (доля пройденных тем по каждому предмету). */
    private val _gradeThreshold = state(prefs.getFloat("grade_threshold", 0.8f))
    val gradeThreshold: StateFlow<Float> = _gradeThreshold.asStateFlow()

    private val _teacherMode = state(prefs.getBoolean("teacher_mode", false))
    val teacherMode: StateFlow<Boolean> = _teacherMode.asStateFlow()

    private val _keyMode = state(
        runCatching { KeySelectionMode.valueOf(prefs.getString("key_mode", KeySelectionMode.PRIORITY.name)!!) }
            .getOrDefault(KeySelectionMode.PRIORITY),
    )
    val keyMode: StateFlow<KeySelectionMode> = _keyMode.asStateFlow()

    private val _featureKeyMap = state(loadFeatureMap())
    val featureKeyMap: StateFlow<Map<AiFeature, String>> = _featureKeyMap.asStateFlow()

    private val _onboardingDone = state(prefs.getBoolean("onboarding_done", false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun setThemeMode(v: ThemeMode) { prefs.edit().putString("theme_mode", v.name).apply(); _themeMode.value = v }
    fun setSound(v: Boolean) { prefs.edit().putBoolean("sound", v).apply(); _sound.value = v }
    fun setVibration(v: Boolean) { prefs.edit().putBoolean("vibration", v).apply(); _vibration.value = v }
    fun setNotifications(v: Boolean) { prefs.edit().putBoolean("notifications", v).apply(); _notifications.value = v }
    fun setNotifyTime(h: Int, m: Int) {
        prefs.edit().putInt("notify_hour", h).putInt("notify_minute", m).apply()
        _notifyHour.value = h; _notifyMinute.value = m
    }
    fun setGradeThreshold(v: Float) { prefs.edit().putFloat("grade_threshold", v).apply(); _gradeThreshold.value = v }
    fun setTeacherMode(v: Boolean) { prefs.edit().putBoolean("teacher_mode", v).apply(); _teacherMode.value = v }
    fun setKeyMode(v: KeySelectionMode) { prefs.edit().putString("key_mode", v.name).apply(); _keyMode.value = v }
    fun setOnboardingDone() { prefs.edit().putBoolean("onboarding_done", true).apply(); _onboardingDone.value = true }

    fun setFeatureKey(feature: AiFeature, keyId: String?) {
        val map = _featureKeyMap.value.toMutableMap()
        if (keyId == null) map.remove(feature) else map[feature] = keyId
        prefs.edit().putString("feature_keys", map.entries.joinToString(";") { "${it.key.name}=${it.value}" }).apply()
        _featureKeyMap.value = map
    }

    private fun loadFeatureMap(): Map<AiFeature, String> =
        prefs.getString("feature_keys", "").orEmpty().split(';').mapNotNull { pair ->
            val parts = pair.split('=')
            if (parts.size != 2) return@mapNotNull null
            runCatching { AiFeature.valueOf(parts[0]) to parts[1] }.getOrNull()
        }.toMap()
}
