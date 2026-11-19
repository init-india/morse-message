package com.morsemessage.output

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Low-latency tone that follows a key being held down (like a real telegraph key's sidetone).
 * A small background thread streams 5 ms chunks; [keyDown] just flips a flag, and the
 * amplitude ramps to avoid clicks.
 */
class Sidetone(private val frequencyHz: Int, private val volume: Double = 0.7) {

    @Volatile var keyDown: Boolean = false
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "sidetone").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        keyDown = false
        thread?.join(200)
        thread = null
    }

    private fun loop() {
        val rate = 44_100
        val chunk = rate / 200 // 5 ms
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME) // lowest-latency usage commonly available
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
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(minBuf)
            .build()

        val buf = ShortArray(chunk)
        val omega = 2.0 * PI * frequencyHz / rate
        val rampStep = 1.0 / (rate * 0.004) // 4 ms ramp
        var phase = 0.0
        var env = 0.0
        try {
            track.play()
            while (running) {
                val target = if (keyDown) 1.0 else 0.0
                for (i in 0 until chunk) {
                    env = when {
                        env < target -> minOf(target, env + rampStep)
                        env > target -> maxOf(target, env - rampStep)
                        else -> env
                    }
                    buf[i] = (Short.MAX_VALUE * volume * env * sin(phase)).toInt().toShort()
                    phase += omega
                    if (phase > 2 * PI) phase -= 2 * PI
                }
                track.write(buf, 0, chunk)
            }
        } finally {
            runCatching { track.pause(); track.flush() }
            track.release()
        }
    }
}
