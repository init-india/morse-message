package com.morsemessage.morse

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Renders a Morse timeline to 16-bit mono PCM, and wraps PCM as a WAV file. */
object ToneSynth {

    const val SAMPLE_RATE = 44_100

    /**
     * @param rampMs raised-cosine fade at each edge so tones don't click.
     * @param leadInMs / trailingMs silence before / after (a ringtone needs a pause before it loops).
     */
    fun render(
        timeline: List<Segment>,
        frequencyHz: Int,
        sampleRate: Int = SAMPLE_RATE,
        volume: Double = 0.8,
        rampMs: Double = 5.0,
        leadInMs: Double = 0.0,
        trailingMs: Double = 0.0,
    ): ShortArray {
        val totalMs = leadInMs + MorseTiming.totalMs(timeline) + trailingMs
        val pcm = ShortArray(msToSamples(totalMs, sampleRate))
        val amp = volume.coerceIn(0.0, 1.0) * Short.MAX_VALUE
        val omega = 2.0 * PI * frequencyHz / sampleRate
        val rampSamples = msToSamples(rampMs, sampleRate)

        var t = leadInMs
        for (seg in timeline) {
            // Boundaries come from accumulated time, so rounding never drifts.
            val start = msToSamples(t, sampleRate)
            t += seg.durationMs
            val end = minOf(msToSamples(t, sampleRate), pcm.size)
            if (!seg.on) continue
            val len = end - start
            val ramp = minOf(rampSamples, len / 2)
            for (n in 0 until len) {
                val env = when {
                    ramp == 0 -> 1.0
                    n < ramp -> 0.5 - 0.5 * cos(PI * n / ramp)
                    n >= len - ramp -> 0.5 - 0.5 * cos(PI * (len - 1 - n) / ramp)
                    else -> 1.0
                }
                pcm[start + n] = (amp * env * sin(omega * n)).roundToInt().toShort()
            }
        }
        return pcm
    }

    fun msToSamples(ms: Double, sampleRate: Int): Int = (ms * sampleRate / 1000.0).roundToInt()

    /** Standard 44-byte RIFF/WAVE header + little-endian 16-bit mono samples. */
    fun toWav(pcm: ShortArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val dataBytes = pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataBytes)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)                // PCM fmt chunk size
        buf.putShort(1)               // audio format = PCM
        buf.putShort(1)               // channels = mono
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2)    // byte rate
        buf.putShort(2)               // block align
        buf.putShort(16)              // bits per sample
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataBytes)
        for (s in pcm) buf.putShort(s)
        return buf.array()
    }
}
