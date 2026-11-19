@file:OptIn(ExperimentalMaterial3Api::class)

package com.morsemessage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morsemessage.app
import com.morsemessage.data.AppSettings
import com.morsemessage.data.Outputs
import com.morsemessage.morse.MorseCode

@Composable
fun TranslateScreen(initialText: String) {
    val context = LocalContext.current
    val app = context.app
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val playback by app.player.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    var input by rememberSaveable(initialText) { mutableStateOf(initialText) }
    var morseInput by rememberSaveable { mutableStateOf(false) }
    var showSend by remember { mutableStateOf(false) }

    // In Morse mode the user types dots/dashes and we show the decoded text, and vice versa.
    val plainText = if (morseInput) MorseCode.decode(input) else input
    val morseText = if (morseInput) MorseCode.normalizeSymbols(input).trim() else MorseCode.encode(input)
    val suggestMorseMode = !morseInput && MorseCode.looksLikeMorse(input)

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!morseInput, { morseInput = false }, label = { Text("Text → Morse") })
            FilterChip(morseInput, { morseInput = true }, label = { Text("Morse → Text") })
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(if (morseInput) "Morse (· − space between letters, / between words)" else "Type a message") },
            textStyle = if (morseInput) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
            else MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )

        if (suggestMorseMode) {
            TextButton(onClick = { morseInput = true }) { Text("This looks like Morse. Decode it instead?") }
        }

        SectionCard(if (morseInput) "Text" else "Morse code") {
            SelectionContainer {
                if (morseInput) {
                    Text(plainText.ifEmpty { "—" }, style = MaterialTheme.typography.headlineSmall)
                } else {
                    Text(
                        highlightedMorse(input, playback.sourceIndex.takeIf { playback.playing && !playback.sourceIsMorse }),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                    )
                }
            }
            if (playback.playing && playback.sourceIndex >= 0 && playback.sourceIndex < playback.source.length) {
                NowPlaying(playback.source, playback.sourceIndex, playback.sourceIsMorse)
            }
        }

        OutputChips(settings.outputs, app.player.torch.available) { o -> app.settings.update { it.copy(outputs = o) } }
        if (settings.outputs.flashlight && app.player.torch.available && settings.charWpm > AppSettings.FLASHLIGHT_MAX_WPM) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, Modifier.padding(end = 6.dp))
                Text(
                    "Flashlight plays at up to ${AppSettings.FLASHLIGHT_MAX_WPM} WPM so the light stays readable.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (playback.playing) {
                Button(
                    onClick = { app.player.stop() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Stop") }
            } else {
                Button(
                    enabled = morseText.isNotBlank() && settings.outputs.any,
                    onClick = {
                        if (morseInput) app.player.playMorse(input, settings) else app.player.playText(input, settings)
                    },
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Play")
                }
            }
            OutlinedButton(
                enabled = plainText.isNotBlank(),
                onClick = { showSend = true },
            ) {
                Icon(Icons.Filled.Send, contentDescription = null)
                Text(" Send")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(enabled = morseText.isNotBlank(), onClick = { clipboard.setText(AnnotatedString(morseText)) }) { Text("Copy Morse") }
            TextButton(enabled = plainText.isNotBlank(), onClick = { clipboard.setText(AnnotatedString(plainText)) }) { Text("Copy text") }
            TextButton(onClick = { input = "" }) { Text("Clear") }
        }

        SectionCard("Speed") {
            SpeedControls(settings) { app.settings.update(it) }
        }
    }

    if (showSend) SendDialog(plainText) { showSend = false }
}

/** Morse for [text], with the letter currently being played in bold colour. */
@Composable
private fun highlightedMorse(text: String, playingIndex: Int?): AnnotatedString {
    val highlight = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    // Same rules as MorseCode.encode: English becomes Morse, other languages stay as typed.
    return buildAnnotatedString {
        var lastWasForeign = false
        Regex("\\S+").findAll(text).forEach { match ->
            val word = match.value
            if (MorseCode.isForeignWord(word)) {
                if (length > 0) append(if (lastWasForeign) " " else " / ")
                append(word)
                lastWasForeign = true
                return@forEach
            }
            var firstLetter = true
            word.forEachIndexed { k, ch ->
                val code = MorseCode.englishCode(ch) ?: return@forEachIndexed
                if (length > 0) append(if (firstLetter) " / " else " ")
                firstLetter = false
                if (match.range.first + k == playingIndex) withStyle(highlight) { append(MorseCode.pretty(code)) }
                else append(MorseCode.pretty(code))
            }
            if (!firstLetter) lastWasForeign = false
        }
        if (length == 0) append("—")
    }
}

@Composable
private fun NowPlaying(source: String, index: Int, isMorse: Boolean) {
    val (letter, code) = if (isMorse) {
        val end = source.indexOfAny(charArrayOf(' ', '/', '|'), index).let { if (it < 0) source.length else it }
        val c = source.substring(index, end)
        MorseCode.decode(c) to c
    } else {
        val ch = source[index]
        ch.uppercaseChar().toString() to (MorseCode.englishCode(ch) ?: "")
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(letter, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        Text(MorseCode.pretty(code), fontSize = 32.sp, fontFamily = FontFamily.Monospace)
    }
}
