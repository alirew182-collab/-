package com.neuron.academy.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.neuron.academy.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Звуки (отключаемые) и вибрация при ответах. */
class Feedback(context: Context, private val settings: SettingsRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tone: ToneGenerator? = try { ToneGenerator(AudioManager.STREAM_MUSIC, 60) } catch (e: Exception) { null }
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun correct() {
        if (settings.sound.value) play(ToneGenerator.TONE_PROP_ACK, 150)
        if (settings.vibration.value) vibrate(40)
    }

    fun wrong() {
        if (settings.sound.value) play(ToneGenerator.TONE_PROP_NACK, 200)
        if (settings.vibration.value) vibrate(longArrayOf(0, 30, 60, 30))
    }

    fun tap() {
        if (settings.sound.value) play(ToneGenerator.TONE_PROP_BEEP, 40)
    }

    fun levelUp() {
        if (settings.vibration.value) vibrate(longArrayOf(0, 60, 80, 60, 80, 120))
        if (!settings.sound.value) return
        scope.launch {
            listOf(ToneGenerator.TONE_DTMF_1, ToneGenerator.TONE_DTMF_5, ToneGenerator.TONE_DTMF_9, ToneGenerator.TONE_DTMF_D).forEach {
                play(it, 120); delay(140)
            }
        }
    }

    private fun play(t: Int, ms: Int) {
        try { tone?.startTone(t, ms) } catch (_: Exception) { }
    }

    private fun vibrate(ms: Long) {
        try { vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) { }
    }

    private fun vibrate(pattern: LongArray) {
        try { vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1)) } catch (_: Exception) { }
    }
}
