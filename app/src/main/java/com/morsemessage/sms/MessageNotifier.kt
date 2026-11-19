package com.morsemessage.sms

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.morsemessage.MainActivity
import com.morsemessage.R
import com.morsemessage.app
import com.morsemessage.morse.MessageText

object MessageNotifier {
    private const val CHANNEL_MESSAGES = "messages"

    private fun manager(context: Context): NotificationManager? {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return null
        val nm = context.getSystemService(NotificationManager::class.java) ?: return null
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH)
        )
        return nm
    }

    fun notificationId(address: String): Int = address.hashCode()

    /** Shown in Morse or plain language according to the toggle; the sender's name is never converted. */
    fun notifyIncoming(context: Context, address: String, name: String?, body: String) {
        val nm = manager(context) ?: return
        // Follows the Morse / Normal toggle, like the chats.
        val text = MessageText.display(body, context.app.settings.current.morseMode)
        val open = PendingIntent.getActivity(
            context, notificationId(address),
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_THREAD)
                .putExtra(MainActivity.EXTRA_ADDRESS, address)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(name ?: address)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(notificationId(address), n)
    }

    fun cancel(context: Context, address: String) {
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId(address))
    }

    fun notifyMmsUnsupported(context: Context) {
        val nm = manager(context) ?: return
        val n = Notification.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Picture or group message received")
            .setContentText("Morse Message can't download MMS yet. Ask the sender to send it as a text.")
            .setStyle(Notification.BigTextStyle().bigText(
                "Morse Message can't download picture or group messages (MMS) yet, so this one can't be shown. " +
                    "Ask the sender to send it as a normal text message."
            ))
            .setAutoCancel(true)
            .build()
        nm.notify(MMS_ID, n)
    }

    private const val MMS_ID = 77_001
}
