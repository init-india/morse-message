@file:OptIn(ExperimentalMaterial3Api::class)

package com.morsemessage

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morsemessage.ui.KeyerScreen
import com.morsemessage.ui.LearnScreen
import com.morsemessage.ui.MessagesNav
import com.morsemessage.ui.MessagesScreen
import com.morsemessage.ui.MorseToggle
import com.morsemessage.ui.MorseMessageTheme
import com.morsemessage.ui.SettingsScreen
import com.morsemessage.ui.TranslateScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    MESSAGES("Messages", Icons.Filled.Email),
    TRANSLATE("Translate", Icons.Filled.Home),
    KEY("Key", Icons.Filled.Edit),
    LEARN("Learn", Icons.Filled.Info),
    SETTINGS("Settings", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {

    private var sharedText by mutableStateOf("")
    private var tab by mutableStateOf(Tab.MESSAGES)
    private val nav = MessagesNav()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        else {
            tab = savedInstanceState.getString(STATE_TAB)?.let { runCatching { Tab.valueOf(it) }.getOrNull() } ?: Tab.MESSAGES
            savedInstanceState.getString(STATE_ADDRESS)?.let { nav.open(it) }
        }
        setContent {
            MorseMessageTheme {
                MainScaffold(tab, { tab = it }, sharedText, nav)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_TAB, tab.name)
        nav.openAddress?.let { outState.putString(STATE_ADDRESS, it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app (home button, switching apps, screen off) stops any Morse playing.
        app.player.stop()
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val scheme = intent.data?.scheme
        when {
            // Tapped a message notification
            intent.action == ACTION_OPEN_THREAD -> intent.getStringExtra(EXTRA_ADDRESS)?.let {
                tab = Tab.MESSAGES
                nav.open(it)
            }
            // "Send SMS to …" from another app (we are the default messaging app)
            (intent.action == Intent.ACTION_SENDTO || intent.action == Intent.ACTION_SEND) &&
                scheme in listOf("sms", "smsto", "mms", "mmsto") -> {
                val address = intent.data?.schemeSpecificPart.orEmpty().removePrefix("//").substringBefore('?').trim()
                val body = intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                tab = Tab.MESSAGES
                if (address.isNotEmpty()) nav.open(address, body) else nav.newMessage(body)
            }
            // Text shared from another app: show it in Morse
            intent.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                tab = Tab.TRANSLATE
            }
        }
    }

    companion object {
        const val ACTION_OPEN_THREAD = "com.morsemessage.OPEN_THREAD"
        const val EXTRA_ADDRESS = "address"
        private const val STATE_TAB = "tab"
        private const val STATE_ADDRESS = "address"
    }
}

@Composable
private fun MainScaffold(tab: Tab, onTab: (Tab) -> Unit, sharedText: String, nav: MessagesNav) {
    val app = LocalContext.current.app
    val playback by app.player.state.collectAsStateWithLifecycle()
    // An open chat gets the whole screen, like other messaging apps.
    val inChat = tab == Tab.MESSAGES && (nav.openAddress != null || nav.composingNew)
    // Switching tabs stops any Morse that is playing.
    LaunchedEffect(tab) { app.player.stop() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (!inChat) TopAppBar(
                    title = { Text(if (tab == Tab.MESSAGES) "Morse Message" else tab.label) },
                    actions = {
                        // Same toggle as in the chat: shows every chat in Morse or plain language.
                        if (tab == Tab.MESSAGES) {
                            val settings by app.settings.settings.collectAsStateWithLifecycle()
                            MorseToggle(
                                settings.morseMode,
                                { app.settings.update { it.copy(morseMode = !it.morseMode) } },
                                Modifier.padding(end = 12.dp),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (!inChat) NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { onTab(t) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    Tab.MESSAGES -> MessagesScreen(nav)
                    Tab.TRANSLATE -> TranslateScreen(sharedText)
                    Tab.KEY -> KeyerScreen()
                    Tab.LEARN -> LearnScreen()
                    Tab.SETTINGS -> SettingsScreen()
                }
            }
        }

        // Full-screen flash output: white for "on", black for "off". Tap anywhere to stop.
        if (playback.playing && playback.screenFlash) {
            val view = LocalView.current
            DisposableEffect(Unit) {
                view.keepScreenOn = true
                onDispose { view.keepScreenOn = false }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (playback.signalOn) Color.White else Color.Black)
                    .clickable { app.player.stop() },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    "Tap to stop",
                    color = Color.Gray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 48.dp),
                )
            }
        }
    }
}
