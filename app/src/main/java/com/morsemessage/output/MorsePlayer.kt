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
    /** Stopped part-way by pause(); resume() continues from the same letter. */
    val paused: Boolean = false,
    /** What is being played, so screens can highlight the current character / message. */
    val source: String = "",
    val sourceIsMorse: Boolean = false,
    val sourceIndex: Int = -1,
    /** True while a dot or dash is "on": drives the full-screen flash. */
    val signalOn: Boolean = false,
    val screenFlash: Boolean = false,
) {
    /** True if [source] is playing or paused (used for the per-message Play / Pause button). */
    fun isActive(source: String): Boolean = (playing || paused) && this.source == source
}

/**
 * Plays Morse through any combination of speaker, vibration, flashlight and screen.
 *
 * Audio is rendered to PCM up-front and streamed (sample-accurate). Vibration is handed to the
 * vibrator as one waveform. Torch, screen and highlighting follow the same timeline, scheduled
 * against the monotonic clock so they never drift.
 *
 * Pause stops at the current dot/dash and remembers where; resume continues from there.
 */
class MorsePlayer(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    @Volatile private var generation = 0L

    /** Where playback is, so pause() knows where resume() must continue. */
    @Volatile private var position = 0
    @Volatile private var current: Playback? = null

    private data class Playback(
        val source: String,
        val isMorse: Boolean,
        val timeline: List<Segment>,
        val toneHz: Int,
        val outputs: Outputs,
        val useTorch: Boolean,
    )

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
        current = Playback(source, isMorse, timeline, toneHz, outputs, useTorch)
        start(from = 0)
    }

    /** Play / Pause / Resume for one message: the single button in each chat bubble. */
    fun toggle(source: String, isMorse: Boolean, settings: AppSettings) {
        val s = _state.value
        when {
            s.playing && s.source == source -> pause()
            s.paused && s.source == source -> resume()
            isMorse -> playMorse(source, settings)
            else -> playText(source, settings)
        }
    }

    fun pause() {
        val p = current ?: return
        if (!_state.value.playing) return
        val at = position
        generation++              // the cancelled job must not reset the state
        job?.cancel()
        job = null
        _state.value = PlaybackState(
            paused = true, source = p.source, sourceIsMorse = p.isMorse,
            sourceIndex = _state.value.sourceIndex, screenFlash = false,
        )
        position = at
    }

    fun resume() {
        if (!_state.value.paused || current == null) return
        start(from = position)
    }

    /** Stops completely (also clears a paused message). */
    fun stop() {
        generation++
        job?.cancel()
        job = null
        current = null
        position = 0
        if (_state.value.playing || _state.value.paused) _state.value = PlaybackState()
    }

    private fun start(from: Int) {
        val p = current ?: return
        val timeline = p.timeline.subList(from.coerceIn(0, p.timeline.size), p.timeline.size)
        if (timeline.isEmpty()) {
            stop()
            return
        }
        val pcm = if (p.outputs.sound) ToneSynth.render(timeline, p.toneHz) else null
        val vibration = if (p.outputs.vibrate) vibrationPattern(timeline) else null

        val myGeneration = ++generation
        _state.value = PlaybackState(
            playing = true, source = p.source, sourceIsMorse = p.isMorse, screenFlash = p.outputs.screen,
        )

        job = scope.launch {
            try {
                val audioJob = pcm?.let { samples -> launch(Dispatchers.IO) { streamAudio(samples) } }
                vibration?.let { haptics.playPattern(it) }
                runTimeline(timeline, from, p.useTorch)
                audioJob?.join()
                if (generation == myGeneration) {
                    current = null
                    position = 0
                }
            } finally {
                if (p.useTorch) torch.set(false)
                if (vibration != null) haptics.cancel()
                // A newer play()/pause()/stop() may already have set the state; only reset if still current.
                if (generation == myGeneration) _state.value = PlaybackState()
            }
        }
    }

    private suspend fun runTimeline(timeline: List<Segment>, offset: Int, useTorch: Boolean) {
        val t0 = SystemClock.elapsedRealtime()
        var elapsed = 0.0
        timeline.forEachIndexed { i, seg ->
            position = offset + i
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
