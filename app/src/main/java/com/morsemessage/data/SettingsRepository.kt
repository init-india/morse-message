package com.morsemessage.data

import android.content.Context
import com.morsemessage.morse.MorseSpeed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Outputs(
    val sound: Boolean = true,
    val vibrate: Boolean = false,
    val flashlight: Boolean = false,
    val screen: Boolean = false,
) {
    val any: Boolean get() = sound || vibrate || flashlight || screen
}

data class AppSettings(
    val charWpm: Int = 15,
    val farnsworth: Boolean = true,
    val effectiveWpm: Int = 10,
    val toneHz: Int = 650,
    val outputs: Outputs = Outputs(),
    val keyerWpm: Int = 12,
    /** One switch for the whole phone: contacts ring with their name, unknown numbers with the number. */
    val morseRingtones: Boolean = false,
    val ringtoneFirstNameOnly: Boolean = true,
    val unknownLastDigitsOnly: Boolean = false,
    val appendDecodeHint: Boolean = true,
    /**
     * The Morse / Normal toggle in Messages. Morse: messages are sent in Morse and every chat is
     * shown in Morse. Normal: sent as typed and shown in plain language. Stays until toggled.
     */
    val morseMode: Boolean = true,
    val kochLesson: Int = 1,
) {
    val speed: MorseSpeed
        get() = MorseSpeed(charWpm, if (farnsworth) minOf(effectiveWpm, charWpm) else charWpm)

    /** Ringtones aren't for practice, so they use the character speed without Farnsworth gaps. */
    val ringtoneSpeed: MorseSpeed
        get() = MorseSpeed(charWpm)

    companion object {
        const val MIN_WPM = 5
        const val MAX_WPM = 40
        const val MIN_TONE = 400
        const val MAX_TONE = 1000
        /** Phone flashlights take ~20-50 ms to switch; above this speed light Morse smears. */
        const val FLASHLIGHT_MAX_WPM = 12
    }
}

/** SharedPreferences-backed settings exposed as a StateFlow for Compose. */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val current: AppSettings get() = _settings.value

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        save(next)
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            charWpm = prefs.getInt("charWpm", d.charWpm),
            farnsworth = prefs.getBoolean("farnsworth", d.farnsworth),
            effectiveWpm = prefs.getInt("effectiveWpm", d.effectiveWpm),
            toneHz = prefs.getInt("toneHz", d.toneHz),
            outputs = Outputs(
                sound = prefs.getBoolean("outSound", d.outputs.sound),
                vibrate = prefs.getBoolean("outVibrate", d.outputs.vibrate),
                flashlight = prefs.getBoolean("outFlash", d.outputs.flashlight),
                screen = prefs.getBoolean("outScreen", d.outputs.screen),
            ),
            keyerWpm = prefs.getInt("keyerWpm", d.keyerWpm),
            morseRingtones = prefs.getBoolean("morseRingtones", d.morseRingtones),
            ringtoneFirstNameOnly = prefs.getBoolean("ringFirstName", d.ringtoneFirstNameOnly),
            unknownLastDigitsOnly = prefs.getBoolean("unknownLast4", d.unknownLastDigitsOnly),
            appendDecodeHint = prefs.getBoolean("decodeHint", d.appendDecodeHint),
            morseMode = prefs.getBoolean("morseMode", d.morseMode),
            kochLesson = prefs.getInt("kochLesson", d.kochLesson),
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putInt("charWpm", s.charWpm)
            .putBoolean("farnsworth", s.farnsworth)
            .putInt("effectiveWpm", s.effectiveWpm)
            .putInt("toneHz", s.toneHz)
            .putBoolean("outSound", s.outputs.sound)
            .putBoolean("outVibrate", s.outputs.vibrate)
            .putBoolean("outFlash", s.outputs.flashlight)
            .putBoolean("outScreen", s.outputs.screen)
            .putInt("keyerWpm", s.keyerWpm)
            .putBoolean("morseRingtones", s.morseRingtones)
            .putBoolean("ringFirstName", s.ringtoneFirstNameOnly)
            .putBoolean("unknownLast4", s.unknownLastDigitsOnly)
            .putBoolean("decodeHint", s.appendDecodeHint)
            .putBoolean("morseMode", s.morseMode)
            .putInt("kochLesson", s.kochLesson)
            .apply()
    }
}
