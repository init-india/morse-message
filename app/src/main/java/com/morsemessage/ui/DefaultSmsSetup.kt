package com.morsemessage.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.morsemessage.app

/**
 * "Set as default SMS app" with help when Android refuses.
 *
 * Common reason: on Android 13+, an app installed from a downloaded file is not allowed to
 * become the SMS app until the user opens App info → ⋮ → "Allow restricted settings".
 * Apps installed from Google Play or F-Droid don't have this step.
 */
@Composable
fun DefaultSmsSetup(onChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val repo = context.app.sms
    var isDefault by remember { mutableStateOf(repo.isDefaultSmsApp()) }
    var triedAndFailed by remember { mutableStateOf(false) }

    fun refresh() {
        val now = repo.isDefaultSmsApp()
        if (now != isDefault) onChanged(now)
        isDefault = now
    }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
        if (!isDefault) triedAndFailed = true
    }

    if (isDefault) {
        Text("Morse Message is your default SMS app.", style = MaterialTheme.typography.bodyMedium)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "To send and receive text messages here, set Morse Message as your default SMS app. " +
                "You can switch back any time; your messages stay on the phone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = {
            val intent = repo.requestDefaultIntent()
            if (intent != null) launcher.launch(intent)
            else openDefaultApps(context).also { triedAndFailed = true }
        }) { Text("Set as default SMS app") }

        if (triedAndFailed) {
            Text("Didn't work?", style = MaterialTheme.typography.titleSmall)
            Text(
                "1. If Android said \"Restricted setting\" or nothing happened: tap App info below, then the ⋮ menu " +
                    "(top right) → \"Allow restricted settings\", and confirm.\n" +
                    "2. Come back and tap \"Set as default SMS app\" again.\n" +
                    "3. Or choose it yourself: Default apps → SMS app → Morse Message.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openAppInfo(context) }) { Text("App info") }
                OutlinedButton(onClick = { openDefaultApps(context) }) { Text("Default apps") }
            }
        }
    }
}

fun openAppInfo(context: Context) {
    start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}

fun openDefaultApps(context: Context) {
    if (!start(context, Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))) {
        start(context, Intent(Settings.ACTION_SETTINGS))
    }
}

private fun start(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}
