package com.morsemessage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morsemessage.BuildConfig
import com.morsemessage.app
import com.morsemessage.data.AppSettings
import com.morsemessage.data.Outputs

@Composable
fun SettingsScreen() {
    val app = LocalContext.current.app
    val settings by app.settings.settings.collectAsStateWithLifecycle()

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DefaultSmsCard()

        SectionCard("Speed") {
            SpeedControls(settings) { app.settings.update(it) }
            Text(
                "Character speed is how fast each letter is sent. With Farnsworth spacing on, the gaps between letters " +
                    "and words are stretched so the overall speed is slower. This is the recommended way to learn.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard("Tone") {
            LabeledSlider(
                "Pitch", settings.toneHz, AppSettings.MIN_TONE..AppSettings.MAX_TONE, step = 10,
                valueText = { "$it Hz" },
            ) { v -> app.settings.update { it.copy(toneHz = v) } }
            OutlinedButton(onClick = { app.player.playText("PARIS", app.settings.current, Outputs(sound = true)) }) {
                Text("Test: PARIS")
            }
        }

        SectionCard("Default outputs") {
            OutputChips(settings.outputs, app.player.torch.available) { o -> app.settings.update { it.copy(outputs = o) } }
            Text(
                "Flashlight output is limited to ${AppSettings.FLASHLIGHT_MAX_WPM} WPM because phone flashlights are slow to switch.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SectionCard("Messages") {
            SwitchRow(
                "Add a short decoding hint when sending Morse",
                settings.appendDecodeHint,
            ) { v -> app.settings.update { it.copy(appendDecodeHint = v) } }
        }

        RingtoneSettings(settings)

        SectionCard("About") {
            Text("Morse Message ${BuildConfig.VERSION_NAME} (${BuildConfig.DISTRIBUTION})")
            Text(
                "Text messaging with Morse code built in, plus tools to learn Morse. Free and open-source " +
                    "software under the GNU GPL v3. No ads, no tracking and no internet access.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DefaultSmsCard() {
    val repo = LocalContext.current.app.sms
    var isDefault by remember { mutableStateOf(repo.isDefaultSmsApp()) }
    LifecycleResumeEffect(Unit) {
        isDefault = repo.isDefaultSmsApp()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = repo.isDefaultSmsApp()
    }
    SectionCard("Messaging app") {
        if (isDefault) {
            Text("Morse Message is your default SMS app.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "To switch back: phone Settings > Apps > Default apps > SMS app.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            DefaultSmsSetup(onChanged = { isDefault = it })
        }
        Text(
            "Picture and group messages (MMS) are not supported yet.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange)
    }
}
