package com.morsemessage.ui

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    var showHelp by remember { mutableStateOf(false) }
    var launchedAt by remember { mutableLongStateOf(0L) }

    fun refresh() {
        val now = repo.isDefaultSmsApp()
        if (now != isDefault) onChanged(now)
        isDefault = now
        if (now) showHelp = false
    }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
        if (!isDefault) {
            // Came back almost instantly = Android refused without showing its dialog
            // (restricted setting, or the request was declined before). Open the system
            // "Default apps" screen so the user can pick Morse Message there.
            if (SystemClock.elapsedRealtime() - launchedAt < 800) openDefaultApps(context)
            showHelp = true
        }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val intent = repo.requestDefaultIntent()
                if (intent != null) {
                    launchedAt = SystemClock.elapsedRealtime()
                    launcher.launch(intent)
                } else {
                    openDefaultApps(context)
                    showHelp = true
                }
            }) { Text("Set as default SMS app") }
            TextButton(onClick = { showHelp = true }) { Text("Help") }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Make Morse Message your SMS app") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Android didn't switch the SMS app. Try these steps:",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "1. Tap Default apps below → SMS app → choose Morse Message.\n\n" +
                            "2. If it's greyed out or says \"Restricted setting\": tap App info below → ⋮ menu " +
                            "(top right) → \"Allow restricted settings\" → confirm. Then do step 1 again. " +
                            "(Android asks this for apps installed from a file; it's not needed when installed " +
                            "from Google Play or F-Droid.)",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "If it still doesn't work, send this line to the developer:\n" + diagnostics(context),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            confirmButton = { Button(onClick = { openDefaultApps(context) }) { Text("Default apps") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { openAppInfo(context) }) { Text("App info") }
                    TextButton(onClick = { showHelp = false }) { Text("Close") }
                }
            },
        )
    }
}

/** Phone and role details, so a failure on a specific phone can be diagnosed. */
private fun diagnostics(context: Context): String {
    val rm = context.getSystemService(RoleManager::class.java)
    val available = runCatching { rm?.isRoleAvailable(RoleManager.ROLE_SMS) }.getOrNull()
    val held = runCatching { rm?.isRoleHeld(RoleManager.ROLE_SMS) }.getOrNull()
    val currentSms = runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
    return "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
        "role available=$available, held=$held, current SMS app=$currentSms, app=${context.packageName}"
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
