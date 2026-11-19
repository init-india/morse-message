package com.morsemessage.output

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.morsemessage.data.AppSettings
import com.morsemessage.data.Outputs
import com.morsemessage.morse.MorseSpeed
import com.morsemessage.morse.MorseTiming
import com.morsemessage.morse.Segment
import com.morsemessage.morse.ToneSynth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

data class PlaybackState(
    val playing: Boolean = false,
    /** What is being played, so screens can highlight the current character. */
    val source: String = "",
    val sourceIsMorse: Boolean = false,
    val sourceIndex: Int = -1,
    /** True while a dot or dash is "on": drives the full-screen flash. */
    val signalOn: Boolean = false,
    val screenFlash: Boolean = false,
)

/**
 * Plays Morse through any combination of speaker, vibration, flashlight and screen.
 *
 * Audio is rendered to PCM up-front and streamed (sample-accurate). Vibration is handed to the
 * vibrator as one waveform. Torch, screen and highlighting follow the same timeline, scheduled
 * against the monotonic clock so they never drift.
 */
class MorsePlayer(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var generation = 0L

    val torch = Torch(context)
    val haptics = Haptics(context)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun playText(text: String, settings: AppSettings, outputs: Outputs = settings.outputs) =
        play(text, isMorse = false, settings.speed, settings.toneHz, outputs)

    fun playMorse(morse: String, settings: AppSettings, outputs: Outputs = settings.outputs) =
        play(morse, isMorse = true, settings.speed, settings.toneHz, outputs)

    fun play(
        source: String,
        isMorse: Boolean,
        requestedSpeed: MorseSpeed,
        toneHz: Int,
        outputs: Outputs,
    ) {
        stop()
        val useTorch = outputs.flashlight && torch.available
        // Keep every output in sync: if the torch is on, the whole message slows to its limit.
        val speed = if (useTorch) requestedSpeed.cappedAt(AppSettings.FLASHLIGHT_MAX_WPM) else requestedSpeed
        val timeline = if (isMorse) MorseTiming.fromMorse(source, speed) else MorseTiming.fromText(source, speed)
        if (timeline.isEmpty()) return

        val pcm = if (outputs.sound) ToneSynth.render(timeline, toneHz) else null
        val vibration = if (outputs.vibrate) vibrationPattern(timeline) else null

        val myGeneration = ++generation
        _state.value = PlaybackState(
            playing = true, source = source, sourceIsMorse = isMorse, screenFlash = outputs.screen,
        )

        job = scope.launch {
            try {
                val audioJob = pcm?.let { samples -> launch(Dispatchers.IO) { streamAudio(samples) } }
                vibration?.let { haptics.playPattern(it) }
                runTimeline(timeline, useTorch)
                audioJob?.join()
            } finally {
                if (useTorch) torch.set(false)
                if (vibration != null) haptics.cancel()
                // A newer play() may already be running; only reset state if we are still current.
                if (generation == myGeneration) _state.value = PlaybackState()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runTimeline(timeline: List<Segment>, useTorch: Boolean) {
        val t0 = SystemClock.elapsedRealtime()
        var elapsed = 0.0
        for (seg in timeline) {
            _state.update {
                it.copy(
                    signalOn = seg.on,
                    sourceIndex = if (seg.sourceIndex >= 0) seg.sourceIndex else it.sourceIndex,
                )
            }
            if (useTorch) torch.set(seg.on)
            elapsed += seg.durationMs
            val wait = t0 + elapsed.roundToLong() - SystemClock.elapsedRealtime()
            if (wait > 0) delay(wait)
        }
        if (useTorch) torch.set(false)
        _state.update { it.copy(signalOn = false) }
    }

    private suspend fun streamAudio(pcm: ShortArray) {
        val rate = ToneSynth.SAMPLE_RATE
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
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
            .setBufferSizeInBytes(maxOf(minBuf, 8192))
            .build()
        try {
            track.play()
            var offset = 0
            // Small chunks so cancellation (Stop) is noticed quickly.
            while (offset < pcm.size && currentCoroutineContext().isActive) {
                val n = track.write(pcm, offset, minOf(2048, pcm.size - offset))
                if (n <= 0) break
                offset += n
            }
            // Let the buffered tail finish playing.
            while (currentCoroutineContext().isActive && track.playbackHeadPosition < pcm.size) {
                delay(20)
            }
        } finally {
            runCatching { track.pause(); track.flush() }
            track.release()
        }
    }

    /** VibrationEffect waveform: alternating off/on durations, starting with an "off" delay. */
    private fun vibrationPattern(timeline: List<Segment>): LongArray {
        val out = ArrayList<Long>(timeline.size + 1)
        out += 0L
        var expectOn = true
        for (seg in timeline) {
            val ms = seg.durationMs.roundToLong()
            if (seg.on == expectOn) {
                out += ms
                expectOn = !expectOn
            } else {
                out[out.lastIndex] = out.last() + ms   // merge consecutive same-state segments
            }
        }
        return out.toLongArray()
    }
}
