@file:OptIn(ExperimentalMaterial3Api::class)

package com.morsemessage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morsemessage.app
import com.morsemessage.data.AppSettings
import com.morsemessage.morse.MorseCode
import com.morsemessage.morse.MorseTiming
import com.morsemessage.output.Sidetone
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Keyed Morse kept as text: letters separated by ' ', words by "/ ". */
private class KeyedText(var value: String = "") {
    fun symbol(s: Char) { value += s }
    fun endLetter() { if (value.isNotEmpty() && !value.endsWith(' ')) value += " " }
    fun endWord() {
        endLetter()
        if (value.isNotEmpty() && !value.endsWith("/ ")) value += "/ "
    }
    fun backspace() {
        value = when {
            value.endsWith("/ ") -> value.dropLast(2)
            value.endsWith(' ') -> value.dropLast(1)
            else -> value.dropLast(1)
        }
    }
}

private class GapTimer { var job: Job? = null }

@Composable
fun KeyerScreen() {
    val context = LocalContext.current
    val app = context.app
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val playback by app.player.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var keyed by rememberSaveable { mutableStateOf("") }
    var straightKey by rememberSaveable { mutableStateOf(false) }
    var showSend by remember { mutableStateOf(false) }
    var keyIsDown by remember { mutableStateOf(false) }
    val gapTimer = remember { GapTimer() }

    val unit = MorseTiming.unitMs(settings.keyerWpm)
    val decoded = MorseCode.decode(keyed)
    val currentLetter = keyed.substringAfterLast(' ').substringAfterLast('/')

    fun edit(block: KeyedText.() -> Unit) {
        keyed = KeyedText(keyed).apply(block).value
    }

    val sidetone = remember(settings.toneHz) { Sidetone(settings.toneHz) }
    DisposableEffect(sidetone) {
        sidetone.start()
        onDispose { sidetone.stop() }
    }

    fun beep(ms: Double) {
        scope.launch {
            sidetone.keyDown = true
            delay(ms.toLong())
            sidetone.keyDown = false
        }
        if (settings.outputs.vibrate) app.player.haptics.pulse(ms.toLong())
    }

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!straightKey, { straightKey = false }, label = { Text("Dot / Dash buttons") })
            FilterChip(straightKey, { straightKey = true }, label = { Text("Straight key") })
        }

        SectionCard("Your message") {
            Text(decoded.ifEmpty { "Start keying…" }, style = MaterialTheme.typography.headlineSmall)
            Text(
                MorseCode.pretty(keyed).ifEmpty { " " },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current letter:", style = MaterialTheme.typography.labelMedium)
                Text(MorseCode.pretty(currentLetter).ifEmpty { "–" }, fontFamily = FontFamily.Monospace, fontSize = 22.sp)
                if (currentLetter.isNotEmpty()) Text("→ ${MorseCode.decode(currentLetter)}", fontSize = 22.sp)
            }
        }

        if (straightKey) {
            Text(
                "Hold for a dash, tap for a dot. Pause ~3 dots to finish a letter, ~7 for a new word.",
                style = MaterialTheme.typography.bodySmall,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (keyIsDown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                    .pointerInput(unit, sidetone) {
                        awaitEachGesture {
                            awaitFirstDown()
                            gapTimer.job?.cancel()
                            val pressedAt = System.nanoTime()
                            keyIsDown = true
                            sidetone.keyDown = true
                            waitForUpOrCancellation()
                            sidetone.keyDown = false
                            keyIsDown = false
                            val heldMs = (System.nanoTime() - pressedAt) / 1_000_000.0
                            // Threshold halfway between a dot (1 unit) and a dash (3 units).
                            edit { symbol(if (heldMs < 2 * unit) '.' else '-') }
                            gapTimer.job = scope.launch {
                                delay((3 * unit).toLong())
                                edit { endLetter() }
                                delay((4 * unit).toLong())
                                edit { endWord() }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "KEY",
                    style = MaterialTheme.typography.displaySmall,
                    color = if (keyIsDown) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { edit { symbol('.') }; beep(unit) },
                    modifier = Modifier.weight(1f).height(96.dp),
                ) { Text("·", fontSize = 48.sp) }
                Button(
                    onClick = { edit { symbol('-') }; beep(3 * unit) },
                    modifier = Modifier.weight(1f).height(96.dp),
                ) { Text("−", fontSize = 48.sp) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { edit { endLetter() } }, modifier = Modifier.weight(1f)) { Text("Next letter") }
                FilledTonalButton(onClick = { edit { endWord() } }, modifier = Modifier.weight(1f)) { Text("Next word") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { gapTimer.job?.cancel(); edit { backspace() } }) { Text("⌫ Undo") }
            OutlinedButton(onClick = { gapTimer.job?.cancel(); keyed = "" }) { Text("Clear") }
            OutlinedButton(
                enabled = keyed.isNotBlank() && !playback.playing,
                onClick = { app.player.playMorse(keyed, settings) },
            ) { Text("Play back") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = decoded.isNotBlank(), onClick = { showSend = true }) { Text("Send…") }
            TextButton(enabled = decoded.isNotBlank(), onClick = { clipboard.setText(AnnotatedString(decoded)) }) { Text("Copy text") }
            TextButton(enabled = keyed.isNotBlank(), onClick = { clipboard.setText(AnnotatedString(keyed.trim())) }) { Text("Copy Morse") }
        }

        SectionCard("Keying speed") {
            Text(
                "Sets how long a dot is when you key. Start slow; raise it as you get faster.",
                style = MaterialTheme.typography.bodySmall,
            )
            LabeledSlider("Speed", settings.keyerWpm, AppSettings.MIN_WPM..30, valueText = { "$it WPM" }) { v ->
                app.settings.update { it.copy(keyerWpm = v) }
            }
        }
    }

    if (showSend) SendDialog(decoded) { showSend = false }
}
