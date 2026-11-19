package com.morsemessage.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.LruCache
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

data class Conversation(
    val threadId: Long,
    val address: String,
    val name: String?,
    val snippet: String,
    val date: Long,
    val unread: Boolean,
) {
    val title: String get() = name ?: address
}

data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
) {
    val incoming: Boolean get() = type == Telephony.Sms.MESSAGE_TYPE_INBOX
    val failed: Boolean get() = type == Telephony.Sms.MESSAGE_TYPE_FAILED
    val pending: Boolean get() = type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED
}

/**
 * Reads and writes the phone's SMS database (the same one every messaging app uses).
 * Android only lets the *default* messaging app write to it, so most writes require
 * [isDefaultSmsApp]; reading needs READ_SMS, which Android grants with the default role.
 */
class SmsRepository(private val context: Context) {

    private val resolver get() = context.contentResolver
    private val names = LruCache<String, String>(500)
    private val noName = "\u0000"

    // ---------------------------------------------------------------- role & permissions

    fun isDefaultSmsApp(): Boolean = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    fun canRead(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Intent for the system dialog "Make Morse Message your default SMS app?". */
    fun requestDefaultIntent(): Intent? {
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        return if (rm.isRoleAvailable(RoleManager.ROLE_SMS) && !rm.isRoleHeld(RoleManager.ROLE_SMS))
            rm.createRequestRoleIntent(RoleManager.ROLE_SMS) else null
    }

    // ---------------------------------------------------------------- reading

    fun conversations(): List<Conversation> {
        if (!canRead()) return emptyList()
        val recipients = canonicalAddresses()
        val out = mutableListOf<Conversation>()
        resolver.query(
            CONVERSATIONS_URI,
            arrayOf("_id", "date", "recipient_ids", "snippet", "read"),
            null, null, "date DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val ids = c.getString(2).orEmpty().split(' ').filter { it.isNotBlank() }
                if (ids.isEmpty()) continue
                val address = ids.mapNotNull { recipients[it] }.joinToString(", ")
                if (address.isEmpty()) continue
                out += Conversation(
                    threadId = c.getLong(0),
                    address = address,
                    name = if (ids.size == 1) nameFor(address) else null,
                    snippet = c.getString(3).orEmpty(),
                    date = c.getLong(1),
                    unread = c.getInt(4) == 0,
                )
            }
        }
        return out
    }

    fun messages(threadId: Long): List<SmsMessage> {
        if (!canRead()) return emptyList()
        val out = mutableListOf<SmsMessage>()
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
            "${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += SmsMessage(c.getLong(0), c.getLong(1), c.getString(2).orEmpty(), c.getString(3).orEmpty(), c.getLong(4), c.getInt(5))
            }
        }
        return out
    }

    fun threadIdFor(address: String): Long = Telephony.Threads.getOrCreateThreadId(context, address)

    private fun canonicalAddresses(): Map<String, String> {
        val map = HashMap<String, String>()
        resolver.query(CANONICAL_URI, arrayOf("_id", "address"), null, null, null)?.use { c ->
            while (c.moveToNext()) map[c.getString(0)] = c.getString(1).orEmpty()
        }
        return map
    }

    /** Contact name for a number, if contacts may be read. Cached. */
    fun nameFor(address: String): String? {
        names.get(address)?.let { return if (it == noName) null else it }
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val name = runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
            resolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        names.put(address, name ?: noName)
        return name
    }

    /** Emits whenever any SMS changes (new message, sent status, read state…). */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        resolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    // ---------------------------------------------------------------- writing (default app only)

    fun markRead(threadId: Long) {
        if (!isDefaultSmsApp()) return
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        runCatching {
            resolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0", arrayOf(threadId.toString()))
        }
    }

    /** Saves an incoming SMS. As the default app this is our job; nobody else writes it. */
    fun saveIncoming(address: String, body: String, sentAt: Long, subscriptionId: Int?): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, sentAt)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            subscriptionId?.let { put(Telephony.Sms.SUBSCRIPTION_ID, it) }
        }
        return runCatching { resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }.getOrNull()
    }

    /**
     * Sends an SMS (long ones in several parts) and records it in the Sent box.
     * The message starts as "sending" and [SmsSentReceiver] marks it sent or failed.
     * SEND_SMS is granted together with the default-SMS-app role.
     */
    @SuppressLint("MissingPermission")
    fun send(address: String, body: String) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
            put(Telephony.Sms.THREAD_ID, threadIdFor(address))
        }
        val uri = runCatching { resolver.insert(Telephony.Sms.CONTENT_URI, values) }.getOrNull()
        val sms = smsManager()
        val parts = sms.divideMessage(body)
        val sentIntents = ArrayList(parts.indices.map { i ->
            val intent = Intent(context, SmsSentReceiver::class.java)
                .setData(uri)
                .putExtra(SmsSentReceiver.EXTRA_LAST_PART, i == parts.lastIndex)
            PendingIntent.getBroadcast(
                context, nextRequestCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
            )
        })
        try {
            sms.sendMultipartTextMessage(address, null, parts, sentIntents, null)
        } catch (e: Exception) {
            uri?.let { setType(it, Telephony.Sms.MESSAGE_TYPE_FAILED) }
        }
    }

    /** Sends a failed message again. */
    fun retry(message: SmsMessage) {
        runCatching { resolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, message.id.toString()), null, null) }
        send(message.address, message.body)
    }

    fun deleteMessage(id: Long) {
        runCatching { resolver.delete(Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString()), null, null) }
    }

    fun setType(uri: Uri, type: Int) {
        runCatching { resolver.update(uri, ContentValues().apply { put(Telephony.Sms.TYPE, type) }, null, null) }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager =
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java) else null)
            ?: SmsManager.getDefault()

    companion object {
        private val CONVERSATIONS_URI = Uri.parse("content://mms-sms/conversations?simple=true")
        private val CANONICAL_URI = Uri.parse("content://mms-sms/canonical-addresses")
        private var requestCode = (System.currentTimeMillis() and 0xFFFFFF).toInt()

        @Synchronized
        private fun nextRequestCode(): Int = ++requestCode
    }
}
