package com.morsemessage

import android.app.Application
import android.content.Context
import com.morsemessage.data.SettingsRepository
import com.morsemessage.output.MorsePlayer
import com.morsemessage.ringtone.ContactRingtones
import com.morsemessage.ringtone.ContactsChangedJob
import com.morsemessage.sms.SmsRepository

/** Tiny service locator: one settings store, one player, shared by every screen. */
class MorseMessageApp : Application() {
    val settings by lazy { SettingsRepository(this) }
    val player by lazy { MorsePlayer(this) }
    val ringtones by lazy { ContactRingtones(this) }
    val sms by lazy { SmsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Keep the "contacts changed" watcher armed while Morse ringtones are on.
        if (settings.current.morseRingtones && ContactsChangedJob.hasContactsPermission(this)) {
            ContactsChangedJob.ensureScheduled(this)
        }
    }
}

val Context.app: MorseMessageApp get() = applicationContext as MorseMessageApp
