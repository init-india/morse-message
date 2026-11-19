package com.morsemessage

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morsemessage.data.Outputs
import com.morsemessage.morse.MorseCode
import com.morsemessage.ui.MorseMessageTheme

/**
 * Adds "Morse" to the text-selection menu (Copy / Paste / …) of every app: WhatsApp, Gmail,
 * Messages and so on. Nothing happens in those apps unless the user taps it.
 *
 * - Text the user is typing (editable): replaced in place. Plain text becomes Morse; selected
 *   Morse is turned back into text.
 * - Text the user can only read (e.g. a received message): a small window shows the
 *   translation, with Play and Copy.
 */
class ProcessTextActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selected = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)

        val morse = MorseCode.extractMorse(selected)
            ?: MorseCode.normalizeSymbols(selected).trim().takeIf { MorseCode.looksLikeMorse(selected) }
        val isMorse = morse != null
        val translated = if (isMorse) MorseCode.decode(morse!!) else MorseCode.encode(selected)

        if (!readOnly && translated.isNotBlank()) {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, translated))
            finish()
            return
        }

        setContent {
            MorseMessageTheme {
                val playback by app.player.state.collectAsStateWithLifecycle()
                val clipboard = LocalClipboardManager.current
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(if (isMorse) "Morse → text" else "Text → Morse") },
                    text = {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SelectionContainer {
                                Text(
                                    if (isMorse) translated.ifBlank { "(no Morse found)" } else MorseCode.pretty(translated),
                                    style = if (isMorse) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                                    fontFamily = if (isMorse) null else FontFamily.Monospace,
                                    fontSize = if (isMorse) 24.sp else 18.sp,
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Row {
                            TextButton(onClick = {
                                if (playback.playing) app.player.stop()
                                else if (isMorse) app.player.playMorse(morse!!, app.settings.current, Outputs(sound = true))
                                else app.player.playText(selected, app.settings.current, Outputs(sound = true))
                            }) { Text(if (playback.playing) "Stop" else "Play") }
                            TextButton(onClick = {
                                clipboard.setText(AnnotatedString(translated))
                                finish()
                            }) { Text("Copy") }
                        }
                    },
                    dismissButton = { TextButton(onClick = { app.player.stop(); finish() }) { Text("Close") } },
                )
            }
        }
    }
}
