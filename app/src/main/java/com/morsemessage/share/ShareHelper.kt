package com.morsemessage.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.morsemessage.data.AppSettings
import com.morsemessage.morse.MorseCode
import com.morsemessage.morse.MorseTiming
import com.morsemessage.morse.ToneSynth
import java.io.File

enum class SendFormat(val label: String, val description: String) {
    TEXT("Normal text", "Send the message as you typed it"),
    MORSE("Morse code", "Send dots and dashes as text"),
    AUDIO("Morse audio", "Send a sound file of the Morse beeps"),
}

enum class SendChannel(val label: String) {
    SMS("SMS"),
    WHATSAPP("WhatsApp"),
    EMAIL("Email"),
    OTHER("Other apps"),
}

/**
 * Android does not let apps send messages silently: every option opens the target app with
 * the message filled in, and the user taps Send there.
 */
object ShareHelper {

    private const val WHATSAPP = "com.whatsapp"
    private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    fun send(context: Context, plainText: String, format: SendFormat, channel: SendChannel, settings: AppSettings) {
        val intent = when (format) {
            SendFormat.AUDIO -> audioIntent(context, plainText, channel, settings)
            else -> textIntent(context, body(plainText, format, settings), channel)
        }
        launch(context, intent)
    }

    fun body(plainText: String, format: SendFormat, settings: AppSettings): String = when (format) {
        SendFormat.TEXT, SendFormat.AUDIO -> plainText
        SendFormat.MORSE -> buildString {
            append(MorseCode.encode(plainText))
            if (settings.appendDecodeHint) append("\n\n").append(MorseCode.DECODE_HINT)
        }
    }

    private fun textIntent(context: Context, body: String, channel: SendChannel): Intent = when (channel) {
        SendChannel.SMS -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", body)
        SendChannel.EMAIL -> Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "A message in Morse code")
            putExtra(Intent.EXTRA_TEXT, body)
            selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")) // email apps only
        }
        SendChannel.WHATSAPP -> Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
            whatsAppPackage(context)?.let { setPackage(it) }
        }
        SendChannel.OTHER -> Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), "Send Morse"
        )
    }

    private fun audioIntent(context: Context, plainText: String, channel: SendChannel, settings: AppSettings): Intent {
        val uri = writeAudio(context, plainText, settings)
        val base = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Morse audio message")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return when (channel) {
            SendChannel.WHATSAPP -> base.apply { whatsAppPackage(context)?.let { setPackage(it) } }
            SendChannel.EMAIL -> base.apply {
                putExtra(Intent.EXTRA_SUBJECT, "A message in Morse code")
                selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            }
            // SMS apps cannot be targeted for attachments reliably: let the user pick (MMS app etc.)
            SendChannel.SMS, SendChannel.OTHER -> Intent.createChooser(base, "Send Morse audio")
        }
    }

    /** Renders the message as a WAV in the app cache and returns a shareable content:// URI. */
    fun writeAudio(context: Context, plainText: String, settings: AppSettings): Uri {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // keep only the latest share
        val file = File(dir, "morse-${System.currentTimeMillis()}.wav")
        val timeline = MorseTiming.fromText(plainText, settings.speed)
        val pcm = ToneSynth.render(timeline, settings.toneHz, leadInMs = 300.0, trailingMs = 500.0)
        file.writeBytes(ToneSynth.toWav(pcm))
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun whatsAppPackage(context: Context): String? =
        listOf(WHATSAPP, WHATSAPP_BUSINESS).firstOrNull { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }

    private fun launch(context: Context, intent: Intent) {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app found for this option", Toast.LENGTH_SHORT).show()
        }
    }
}
