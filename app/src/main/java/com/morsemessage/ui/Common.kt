@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.morsemessage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.morsemessage.app
import com.morsemessage.data.AppSettings
import com.morsemessage.data.Outputs
import com.morsemessage.share.SendChannel
import com.morsemessage.share.SendFormat
import com.morsemessage.share.ShareHelper
import kotlin.math.roundToInt

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Sound / Vibrate / Flashlight / Screen toggles. */
@Composable
fun OutputChips(outputs: Outputs, flashlightAvailable: Boolean, onChange: (Outputs) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(outputs.sound, { onChange(outputs.copy(sound = !outputs.sound)) }, label = { Text("🔊 Sound") })
        FilterChip(outputs.vibrate, { onChange(outputs.copy(vibrate = !outputs.vibrate)) }, label = { Text("📳 Vibrate") })
        FilterChip(
            outputs.flashlight && flashlightAvailable,
            { onChange(outputs.copy(flashlight = !outputs.flashlight)) },
            label = { Text(if (flashlightAvailable) "🔦 Flashlight" else "🔦 No flashlight") },
            enabled = flashlightAvailable,
        )
        FilterChip(outputs.screen, { onChange(outputs.copy(screen = !outputs.screen)) }, label = { Text("💡 Screen flash") })
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    valueText: (Int) -> String = { it.toString() },
    enabled: Boolean = true,
    onChange: (Int) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueText(value), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((it / step).roundToInt() * step) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            enabled = enabled,
        )
    }
}

/** Speed control shared by the Translate screen and Settings. */
@Composable
fun SpeedControls(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    LabeledSlider("Character speed", settings.charWpm, AppSettings.MIN_WPM..AppSettings.MAX_WPM, valueText = { "$it WPM" }) { v ->
        onChange { it.copy(charWpm = v) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilterChip(settings.farnsworth, { onChange { it.copy(farnsworth = !it.farnsworth) } }, label = { Text("Farnsworth spacing") })
    }
    if (settings.farnsworth) {
        LabeledSlider(
            "Overall speed (extra space between letters)",
            minOf(settings.effectiveWpm, settings.charWpm),
            AppSettings.MIN_WPM..maxOf(AppSettings.MIN_WPM + 1, settings.charWpm),
            valueText = { "$it WPM" },
        ) { v -> onChange { it.copy(effectiveWpm = v) } }
    }
}

/**
 * Step 1: "Send as normal text or Morse code?" (plus Morse audio). Step 2: which app.
 */
@Composable
fun SendDialog(plainText: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = context.app.settings.current
    var format by remember { mutableStateOf(SendFormat.MORSE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send as normal text or Morse code?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SendFormat.entries.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().selectable(f == format) { format = f },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(f == format, onClick = { format = f })
                        Column {
                            Text(f.label, style = MaterialTheme.typography.bodyLarge)
                            Text(f.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (format != SendFormat.AUDIO) {
                    Text("Preview:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                    Text(
                        ShareHelper.body(plainText, format, settings).take(200),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text("Send with:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SendChannel.entries.forEach { ch ->
                        OutlinedButton(onClick = {
                            ShareHelper.send(context, plainText, format, ch, settings)
                            onDismiss()
                        }) { Text(ch.label) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The single Morse / abc button. Tapping it switches mode:
 * - morse (·−): messages are sent in Morse, the Morse keypad replaces the phone keyboard,
 *   and every chat is shown in Morse;
 * - abc: messages are sent as typed with the phone's normal keyboard, and every chat is shown
 *   in normal language.
 * The active half is highlighted.
 */
@Composable
fun MorseToggle(morseMode: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.semantics {
            contentDescription = if (morseMode) "Morse mode. Tap for abc" else "abc mode. Tap for Morse"
        },
    ) {
        Column(Modifier.padding(3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ToggleSegment("·−", selected = morseMode)
            ToggleSegment("abc", selected = !morseMode)
        }
    }
}

@Composable
private fun ToggleSegment(label: String, selected: Boolean) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
