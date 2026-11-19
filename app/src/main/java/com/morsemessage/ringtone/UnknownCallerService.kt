package com.morsemessage.ringtone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse
import android.telephony.TelephonyManager
import com.morsemessage.app
import com.morsemessage.data.AppSettings
import com.morsemessage.morse.MorseTiming
import com.morsemessage.morse.ToneSynth

/**
 * Unknown callers: Android hands calls from numbers that are NOT in the contacts to the app holding
 * the "call screening" role. We let the call through but silence the normal ring, and play the
 * caller's number in Morse instead until the call is answered, rejected or stops ringing.
 *
 * If the feature is off, the phone is on silent / vibrate, or the number is hidden, the call is
 * left completely alone and rings as usual. Uninstalling the app removes this service, so unknown
 * callers simply get the phone's normal ringtone again.
 */
class UnknownCallerService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val settings = applicationContext.app.settings.current
        val number = details.handle?.schemeSpecificPart.orEmpty().filter { it.isDigit() }
        val digits = if (settings.unknownLastDigitsOnly) number.takeLast(4) else number
        val audio = getSystemService(AudioManager::class.java)

        val announce = details.callDirection == Call.Details.DIRECTION_INCOMING &&
            settings.morseRingtones &&
            digits.isNotEmpty() &&
            audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        // Must answer within 5 seconds; we never block or reject, only silence the normal ring.
        respondToCall(details, CallResponse.Builder().setSilenceCall(announce).build())
        if (announce) CallAnnouncer.start(applicationContext, digits, settings)
    }
}

/** Loops the number in Morse on the ringtone stream while the phone is ringing. */
object CallAnnouncer {
    private const val MAX_RING_MS = 60_000L
    @Volatile private var thread: Thread? = null

    fun start(context: Context, digits: String, settings: AppSettings) {
        stop()
        val rate = 22_050
        val pcm = ToneSynth.render(
            MorseTiming.fromText(digits, settings.ringtoneSpeed), settings.toneHz,
            sampleRate = rate, volume = 0.9, leadInMs = 100.0, trailingMs = 1500.0,
        )
        val telephony = context.getSystemService(TelephonyManager::class.java)
        thread = Thread({ play(pcm, rate, telephony) }, "call-announcer").apply { start() }
    }

    fun stop() {
        thread?.interrupt()
        thread = null
    }

    private fun play(pcm: ShortArray, rate: Int, telephony: TelephonyManager?) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE) // follows the ring volume
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        try {
            track.write(pcm, 0, pcm.size)
            track.setLoopPoints(0, pcm.size, -1)
            track.play()
            val start = SystemClock.elapsedRealtime()
            // Give Telecom a moment to enter the ringing state before we start checking it.
            Thread.sleep(500)
            while (SystemClock.elapsedRealtime() - start < MAX_RING_MS && isRinging(telephony)) {
                Thread.sleep(250)
            }
        } catch (_: InterruptedException) {
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }

    /** Ringing = not yet answered or rejected. Needs READ_PHONE_STATE (checked before start()). */
    @Suppress("MissingPermission", "DEPRECATION")
    private fun isRinging(telephony: TelephonyManager?): Boolean =
        try {
            telephony?.callState == TelephonyManager.CALL_STATE_RINGING
        } catch (_: SecurityException) {
            false
        }
}
