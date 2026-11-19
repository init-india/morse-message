package com.morsemessage.sms

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.morsemessage.app

/**
 * Incoming SMS. Android delivers SMS_DELIVER only to the default messaging app, which must save
 * the message itself; then we notify.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, -1).takeIf { it >= 0 }
        val pending = goAsync()
        Thread {
            try {
                val repo = context.app.sms
                // A long SMS arrives in several parts; join them per sender.
                parts.groupBy { it.displayOriginatingAddress.orEmpty() }.forEach { (address, msgs) ->
                    val body = msgs.joinToString("") { it.displayMessageBody.orEmpty() }
                    repo.saveIncoming(address, body, msgs.first().timestampMillis, subId)
                    MessageNotifier.notifyIncoming(context, address, repo.nameFor(address), body)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/**
 * MMS (picture / group messages) is not supported yet. The default app must still declare this
 * receiver; for now it tells the user that an MMS arrived so it isn't silently missed.
 */
class MmsWapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        MessageNotifier.notifyMmsUnsupported(context)
    }
}

/** Result of sending each SMS part: marks the message Sent or Failed. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        val repo = context.app.sms
        when {
            resultCode != Activity.RESULT_OK -> repo.setType(uri, Telephony.Sms.MESSAGE_TYPE_FAILED)
            intent.getBooleanExtra(EXTRA_LAST_PART, true) -> repo.setType(uri, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
    }

    companion object {
        const val EXTRA_LAST_PART = "last_part"
    }
}

/**
 * "Reply with a message" from the incoming-call screen (e.g. "Can't talk now"). Required for
 * the default messaging app; sends the text without opening any UI.
 */
class HeadlessSmsSendService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val recipients = intent.data?.schemeSpecificPart?.removePrefix("//")
                ?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            if (!text.isNullOrBlank()) {
                Thread {
                    recipients.forEach { app.sms.send(it, text) }
                    stopSelf(startId)
                }.start()
                return START_NOT_STICKY
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
