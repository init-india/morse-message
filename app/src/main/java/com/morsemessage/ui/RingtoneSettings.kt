package com.morsemessage.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.morsemessage.app
import com.morsemessage.data.AppSettings
import com.morsemessage.data.Outputs
import com.morsemessage.ringtone.ContactsChangedJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One switch for the whole phone: when on, every contact rings with their name in Morse and
 * unknown numbers ring with the number in Morse. When off (or the app is uninstalled) the phone
 * rings with its normal ringtone again.
 */
@Composable
fun RingtoneSettings(settings: AppSettings) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }

    fun permissionsGranted() = REQUIRED_PERMISSIONS.all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }
    fun screeningHeld() = roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var activeCount by remember { mutableIntStateOf(app.ringtones.activeCount()) }
    var roleHeld by remember { mutableStateOf(screeningHeld()) }
    var sampleName by remember { mutableStateOf("ANNA") }

    suspend fun syncNow() {
        busy = true
        withContext(Dispatchers.IO) {
            app.ringtones.syncAll(app.settings.current) { done, total ->
                progress = if (total == 0) 1f else done.toFloat() / total
            }
        }
        activeCount = app.ringtones.activeCount()
        busy = false
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        roleHeld = screeningHeld()
    }

    fun requestScreeningRole() {
        val rm = roleManager ?: return
        if (rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    fun turnOn() {
        app.settings.update { it.copy(morseRingtones = true) }
        ContactsChangedJob.schedule(context)
        scope.launch {
            syncNow()
            requestScreeningRole()
        }
    }

    fun turnOff() {
        app.settings.update { it.copy(morseRingtones = false) }
        ContactsChangedJob.cancel(context)
        scope.launch {
            busy = true
            withContext(Dispatchers.IO) { app.ringtones.removeAll() }
            activeCount = 0
            busy = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (permissionsGranted()) turnOn()
    }

    // Speed, pitch or "first name only" changed: rebuild the ringtones (after the slider settles).
    LaunchedEffect(settings.charWpm, settings.toneHz, settings.ringtoneFirstNameOnly) {
        if (settings.morseRingtones && permissionsGranted() && !busy) {
            delay(1500)
            syncNow()
        }
    }

    // A first contact name for the preview button.
    LaunchedEffect(settings.morseRingtones, settings.ringtoneFirstNameOnly) {
        if (permissionsGranted()) {
            val first = withContext(Dispatchers.IO) { runCatching { app.ringtones.loadContacts().firstOrNull() }.getOrNull() }
            first?.let { c ->
                app.ringtones.spokenName(c.name, settings.ringtoneFirstNameOnly).takeIf { it.isNotBlank() }?.let { sampleName = it }
            }
        }
    }

    SectionCard("Morse ringtone for calls") {
        Text(
            "Instead of your normal ringtone, incoming calls tap out who is calling in Morse: the contact's " +
                "name, or the phone number if the caller isn't in your contacts. This applies to all calls.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SwitchRow("Ring in Morse", settings.morseRingtones) { on ->
            when {
                !on -> turnOff()
                permissionsGranted() -> turnOn()
                else -> permissionLauncher.launch(REQUIRED_PERMISSIONS)
            }
        }

        if (busy) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("Updating ringtones…", style = MaterialTheme.typography.bodySmall)
        }

        if (settings.morseRingtones) {
            StatusRow(true, "Contacts: $activeCount ring with their name")
            if (roleHeld) {
                StatusRow(true, "Unknown numbers: ring with the number")
            } else {
                Text(
                    "To play the number for callers not in your contacts, allow Morse Message as your call " +
                        "screening app. It never blocks calls.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { requestScreeningRole() }) { Text("Allow for unknown numbers") }
            }

            SwitchRow("Contacts: first name only (shorter)", settings.ringtoneFirstNameOnly) { v ->
                app.settings.update { it.copy(ringtoneFirstNameOnly = v) }
            }
            SwitchRow("Unknown numbers: last 4 digits only (shorter)", settings.unknownLastDigitsOnly) { v ->
                app.settings.update { it.copy(unknownLastDigitsOnly = v) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    app.player.playText(sampleName, app.settings.current, Outputs(sound = true))
                }) { Text("Hear: $sampleName") }
                OutlinedButton(onClick = {
                    val n = if (settings.unknownLastDigitsOnly) "4567" else "91234567"
                    app.player.playText(n, app.settings.current, Outputs(sound = true))
                }) { Text("Hear a number") }
            }
        }

        Text(
            "Uses your speed and pitch from above. On silent or vibrate, the phone behaves as usual. " +
                "Turn this off, or uninstall the app, and your phone's own ringtone comes back.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatusRow(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (ok) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.WRITE_CONTACTS,
    Manifest.permission.READ_PHONE_STATE,
)
