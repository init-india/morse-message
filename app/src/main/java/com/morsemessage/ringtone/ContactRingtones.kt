package com.morsemessage.ringtone

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import com.morsemessage.data.AppSettings
import com.morsemessage.morse.MorseCode
import com.morsemessage.morse.MorseTiming
import com.morsemessage.morse.ToneSynth
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class ContactItem(val id: Long, val name: String, val customRingtone: String?)

/**
 * Caller-name ringtones for every contact, controlled by one switch in Settings.
 *
 * Android has no hook to replace the ringtone while a call comes in from a contact, but every
 * contact can have its own ringtone. So each contact's name is rendered as Morse to a WAV in the
 * app's private storage (served by [RingtoneProvider]) and set as that contact's CUSTOM_RINGTONE.
 * The phone then rings with the name even when this app isn't running.
 *
 * - Contacts sharing a name share one file.
 * - A contact's own ringtone from before is remembered and put back when the feature is turned off.
 * - If the user later picks a different ringtone for a contact by hand, that choice is kept.
 * - New or renamed contacts are picked up by [ContactsChangedJob].
 */
class ContactRingtones(private val context: Context) {

    private val resolver get() = context.contentResolver
    /** contactId -> "<our uri>\n<previous ringtone or empty>" */
    private val store = context.getSharedPreferences("contact_ringtones", Context.MODE_PRIVATE)
    private val meta = context.getSharedPreferences("ringtone_meta", Context.MODE_PRIVATE)
    private val salt: String by lazy {
        meta.getString("salt", null) ?: UUID.randomUUID().toString().also { meta.edit().putString("salt", it).apply() }
    }
    private val lock = Any()

    fun loadContacts(): List<ContactItem> {
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.CUSTOM_RINGTONE,
        )
        val out = mutableListOf<ContactItem>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI, projection,
            "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                out += ContactItem(c.getLong(0), name, c.getString(2))
            }
        }
        return out
    }

    /** What will be keyed: first name by default, characters Morse can't send removed. */
    fun spokenName(fullName: String, firstNameOnly: Boolean): String {
        val words = fullName.trim().split(Regex("\\s+"))
            .map { w -> w.filter { MorseCode.codeFor(it) != null } }
            .filter { it.isNotEmpty() }
        return (if (firstNameOnly) words.take(1) else words).joinToString(" ").uppercase()
    }

    /** Number of contacts currently ringing with a Morse ringtone. */
    fun activeCount(): Int = store.all.size

    /**
     * Makes every contact ring with their name in Morse. Safe to call repeatedly: contacts that
     * are already up to date are not touched, so this doesn't trigger itself via [ContactsChangedJob].
     */
    fun syncAll(settings: AppSettings, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }) = synchronized(lock) {
        val contacts = loadContacts()
        val usedFiles = mutableSetOf<String>()
        val edit = store.edit()

        contacts.forEachIndexed { i, contact ->
            onProgress(i, contacts.size)
            val key = contact.id.toString()
            val saved = store.getString(key, null)
            val ourPrevUri = saved?.substringBefore('\n')

            // The user chose another ringtone for this contact by hand: respect it and forget ours.
            if (saved != null && contact.customRingtone != ourPrevUri) {
                edit.remove(key)
                return@forEachIndexed
            }

            val spoken = spokenName(contact.name, settings.ringtoneFirstNameOnly)
            if (spoken.isBlank()) return@forEachIndexed
            val fileName = fileNameFor("name|$spoken", settings)
            usedFiles += fileName
            val file = File(RingtoneProvider.dir(context), fileName)
            if (!file.exists()) file.writeBytes(render(spoken, settings))

            val uri = RingtoneProvider.uriFor(context, fileName).toString()
            if (contact.customRingtone == uri) return@forEachIndexed   // already up to date

            setContactRingtone(contact.id, uri)
            val previous = if (saved != null) saved.substringAfter('\n') else contact.customRingtone.orEmpty()
            edit.putString(key, "$uri\n$previous")
        }

        // Forget contacts that were deleted.
        val ids = contacts.map { it.id.toString() }.toSet()
        store.all.keys.filter { it !in ids }.forEach { edit.remove(it) }
        edit.apply()

        // Delete audio no contact uses any more (old speed/tone, renamed contacts).
        RingtoneProvider.dir(context).listFiles()?.forEach { if (it.name !in usedFiles) it.delete() }
        onProgress(contacts.size, contacts.size)
    }

    /** Turns the feature off: every contact gets back the ringtone it had before. */
    fun removeAll() = synchronized(lock) {
        val contacts = loadContacts().associateBy { it.id.toString() }
        for ((key, value) in store.all) {
            val saved = value as? String ?: continue
            val contact = contacts[key] ?: continue
            // Only undo it if the contact still has our ringtone.
            if (contact.customRingtone == saved.substringBefore('\n')) {
                setContactRingtone(contact.id, saved.substringAfter('\n').ifEmpty { null })
            }
        }
        store.edit().clear().apply()
        RingtoneProvider.dir(context).listFiles()?.forEach { it.delete() }
    }

    private fun setContactRingtone(contactId: Long, uri: String?) {
        val values = ContentValues().apply {
            if (uri == null) putNull(ContactsContract.Contacts.CUSTOM_RINGTONE)
            else put(ContactsContract.Contacts.CUSTOM_RINGTONE, uri)
        }
        resolver.update(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), values, null, null)
    }

    /** Ringtone audio: Morse, then a pause, because the phone loops the file. */
    private fun render(text: String, settings: AppSettings): ByteArray {
        val timeline = MorseTiming.fromText(text, settings.ringtoneSpeed, englishOnly = false)
        val pcm = ToneSynth.render(
            timeline, settings.toneHz, sampleRate = RINGTONE_RATE, volume = 0.9,
            leadInMs = 200.0, trailingMs = 1500.0,
        )
        return ToneSynth.toWav(pcm, RINGTONE_RATE)
    }

    /** sha1(salt | content | speed | tone): unguessable, and changes when the sound would change. */
    private fun fileNameFor(content: String, settings: AppSettings): String {
        val input = "$salt|$content|${settings.charWpm}|${settings.toneHz}"
        val hash = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) } + ".wav"
    }

    companion object {
        /** 8 kHz is plenty for a pure 400-1000 Hz tone and keeps a few hundred contacts to ~20 MB. */
        const val RINGTONE_RATE = 8_000
    }
}
