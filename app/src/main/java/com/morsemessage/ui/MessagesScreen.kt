@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.morsemessage.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import com.morsemessage.morse.MorseEditing
import com.morsemessage.morse.MorseKey
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morsemessage.app
import com.morsemessage.morse.MorseCode
import com.morsemessage.morse.MessageText
import com.morsemessage.sms.Conversation
import com.morsemessage.sms.MessageNotifier
import com.morsemessage.sms.SmsMessage
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which chat is open in the Messages tab. Held by MainActivity so notifications and links can open a chat. */
class MessagesNav {
    var openAddress by mutableStateOf<String?>(null)
    var draft by mutableStateOf("")
    var composingNew by mutableStateOf(false)

    fun open(address: String, draft: String = "") {
        this.draft = draft
        composingNew = false
        openAddress = address
    }

    fun newMessage(draft: String = "") {
        this.draft = draft
        openAddress = null
        composingNew = true
    }

    fun close() {
        openAddress = null
        composingNew = false
        draft = ""
    }
}

@Composable
fun MessagesScreen(nav: MessagesNav) {
    val repo = LocalContext.current.app.sms
    var isDefault by remember { mutableStateOf(repo.isDefaultSmsApp()) }
    LifecycleResumeEffect(Unit) {
        isDefault = repo.isDefaultSmsApp()
        onPauseOrDispose { }
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = repo.isDefaultSmsApp()
    }
    val context = LocalContext.current
    val askDefault: () -> Unit = {
        repo.requestDefaultIntent()?.let { roleLauncher.launch(it) } ?: openDefaultApps(context)
    }

    val address = nav.openAddress
    when {
        address != null -> ThreadScreen(address, nav.draft, isDefault, askDefault, onBack = { nav.close() })
        nav.composingNew -> NewMessageScreen(onStart = { nav.open(it, nav.draft) }, onBack = { nav.close() })
        else -> ConversationList(isDefault, { isDefault = it }, onOpen = { nav.open(it) }, onNew = { nav.newMessage() })
    }
}

// ------------------------------------------------------------------ conversation list

@Composable
private fun ConversationList(isDefault: Boolean, onDefaultChanged: (Boolean) -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app.sms
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var contactsAllowed by remember { mutableStateOf(hasContacts(context)) }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        contactsAllowed = it
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(isDefault, contactsAllowed) {
        if (isDefault && android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        repo.changes().collect { conversations = withContext(Dispatchers.IO) { repo.conversations() } }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            if (!isDefault) item {
                SectionCard("Make Morse Message your messaging app", Modifier.padding(16.dp)) {
                    DefaultSmsSetup(onChanged = onDefaultChanged)
                }
            }
            if (isDefault && !contactsAllowed) item {
                TextButton(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }, Modifier.padding(horizontal = 8.dp)) {
                    Text("Show contact names")
                }
            }
            if (isDefault && conversations.isEmpty()) item {
                Text("No messages yet. Tap New message to start.", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium)
            }
            items(conversations, key = { it.threadId }) { c ->
                ConversationRow(c) { onOpen(c.address) }
                HorizontalDivider()
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNew,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("New message") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun ConversationRow(c: Conversation, onClick: () -> Unit) {
    val context = LocalContext.current
    val morseMode = LocalContext.current.app.settings.settings.collectAsStateWithLifecycle().value.morseMode
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(c.title)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                c.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (c.unread) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                MessageText.display(c.snippet, morseMode),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (c.unread) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(formatTime(context, c.date), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Avatar(title: String) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val letter = title.firstOrNull { it.isLetter() }?.uppercaseChar()
        if (letter != null) Text(letter.toString(), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleMedium)
        else Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

// ------------------------------------------------------------------ chat thread

@Composable
private fun ThreadScreen(address: String, initialDraft: String, isDefault: Boolean, askDefault: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.app
    val repo = app.sms
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    BackHandler {
        app.player.stop()
        onBack()
    }

    var messages by remember(address) { mutableStateOf<List<SmsMessage>>(emptyList()) }
    // Names always come from the contact list as saved; they are never converted.
    val name = remember(address) { repo.nameFor(address) }
    var text by rememberSaveable(address) { mutableStateOf(initialDraft) }
    val playback by app.player.state.collectAsStateWithLifecycle()
    // Leaving the chat (back button or anything else) stops any Morse that is playing.
    DisposableEffect(address) { onDispose { app.player.stop() } }
    var lastConversion by remember(address) { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(address) {
        val threadId = withContext(Dispatchers.IO) { repo.threadIdFor(address) }
        repo.changes().collect {
            messages = withContext(Dispatchers.IO) {
                repo.markRead(threadId)
                repo.messages(threadId)
            }
            MessageNotifier.cancel(context, address)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Column(Modifier.weight(1f)) {
                Text(name ?: address, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (name != null) Text(address, style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()

        // Messages, newest at the bottom; shown in Morse or plain language per the toggle.
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages.asReversed(), key = { it.id }) { m ->
                Bubble(
                    message = m,
                    morseMode = settings.morseMode,
                    playState = when {
                        playback.playing && playback.source == MessageText.playable(m.body) -> PlayState.PLAYING
                        playback.paused && playback.source == MessageText.playable(m.body) -> PlayState.PAUSED
                        else -> PlayState.IDLE
                    },
                    onPlay = { morse -> app.player.toggle(morse, isMorse = true, settings = app.settings.current) },
                    onRetry = { scope.launch(Dispatchers.IO) { repo.retry(m) } },
                    onDelete = { scope.launch(Dispatchers.IO) { repo.deleteMessage(m.id) } },
                )
            }
        }

        HorizontalDivider()
        Composer(
            text = text,
            onText = { text = it },
            morseMode = settings.morseMode,
            onToggle = {
                val toMorse = !settings.morseMode
                if (toMorse) {
                    // Convert what's typed so far to Morse; remember it to give back the exact words.
                    val converted = MessageText.draftToMorse(text)
                    lastConversion = text to converted
                    text = converted
                } else {
                    val (plain, morse) = lastConversion ?: ("" to "")
                    text = if (text == morse && plain.isNotEmpty()) plain else MessageText.draftToText(text)
                    lastConversion = null
                }
                app.settings.update { it.copy(morseMode = toMorse) }
            },
            enabled = isDefault,
            askDefault = askDefault,
            onSend = {
                val body = MessageText.outgoing(text, settings.morseMode, settings.appendDecodeHint)
                text = ""
                scope.launch(Dispatchers.IO) { repo.send(address, body) }
            },
        )
    }
}

@Composable
private fun Bubble(
    message: SmsMessage,
    morseMode: Boolean,
    playState: PlayState,
    onPlay: (String) -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val shown = remember(message.body, morseMode) { MessageText.display(message.body, morseMode) }
    val playable = remember(message.body) { MessageText.playable(message.body) }
    val isMorseMessage = remember(message.body) { MorseCode.extractMorse(message.body) != null }
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.incoming) Arrangement.Start else Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (message.incoming) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .combinedClickable(onClick = { menu = false }, onLongClick = { menu = !menu }),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    shown,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = if (morseMode && playable != null) FontFamily.Monospace else null,
                )
                // Play: always for messages that arrived / went out in Morse; in Morse mode for any English text.
                if (playable != null && (morseMode || isMorseMessage)) {
                    // One button: ▶ Play → ⏸ Pause → ▶ Resume
                    TextButton(onClick = { onPlay(playable) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(
                            when (playState) {
                                PlayState.IDLE -> "▶ Play"
                                PlayState.PLAYING -> "⏸ Pause"
                                PlayState.PAUSED -> "▶ Resume"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Text(
                    formatTime(context, message.date) + when {
                        message.failed -> " · Not sent"
                        message.pending -> " · Sending…"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (message.failed) TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) { Text("Retry") }
                if (menu) TextButton(onClick = { menu = false; onDelete() }, contentPadding = PaddingValues(0.dp)) {
                    Text("Delete message", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/**
 * Text box, then the morse / abc button, then Send, like a normal messaging app.
 *
 * - abc mode: the phone's normal keyboard.
 * - morse mode: the phone keyboard is kept away and the Morse keypad below types into the box.
 *   The box stays fully editable: tap to move the cursor, long-press to select / paste, scroll.
 *   "Type English" brings back the phone keyboard (words turn into Morse as you type) and the
 *   "Morse keypad" button returns to the keypad.
 * - In morse mode a live translation bar above the box shows what the Morse says while typing.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    morseMode: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean,
    askDefault: () -> Unit,
    onSend: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current
    val focus = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    var userSwitched by remember { mutableStateOf(false) }
    var typingEnglish by remember { mutableStateOf(false) }
    LaunchedEffect(morseMode) { if (!morseMode) typingEnglish = false }

    // Text + cursor/selection of the box.
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    LaunchedEffect(text) {
        if (text != field.text) field = TextFieldValue(text, TextRange(text.length))
    }
    fun setField(v: TextFieldValue) {
        field = v
        if (v.text != text) onText(v.text)
    }

    // Keep the phone keyboard away while the Morse keypad is in use, without making the box
    // read-only (so the cursor, selection, scrolling and paste still work).
    val keypadActive = morseMode && !typingEnglish
    val blockKeyboard by rememberUpdatedState(keypadActive)
    val interceptor = remember {
        object : PlatformTextInputInterceptor {
            override suspend fun interceptStartInputMethod(
                request: PlatformTextInputMethodRequest,
                nextHandler: PlatformTextInputSession,
            ): Nothing {
                if (blockKeyboard) awaitCancellation()
                nextHandler.startInputMethod(request)
            }
        }
    }
    // When the user switches keyboards, restart the text input so the right one appears.
    LaunchedEffect(keypadActive) {
        if (!userSwitched) return@LaunchedEffect
        focusManager.clearFocus()
        delay(50)
        focus.requestFocus()
        if (keypadActive) keyboard?.hide() else keyboard?.show()
    }

    val live = remember(text) { MorseEditing.liveTranslation(text) }
    val hasForeign = text.split(Regex("\\s+")).any { MorseCode.isForeignWord(it) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Set Morse Message as your SMS app to send.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = askDefault) { Text("Set default") }
            }
        }
        // Live translation bar: what the Morse in the box says, updated with every key.
        if (morseMode) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Reads:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        live.ifEmpty { "your Morse appears here in English as you type" },
                        style = if (live.isEmpty()) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                        fontWeight = if (live.isEmpty()) FontWeight.Normal else FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (hasForeign) {
                Text(
                    "Only English letters and numbers become Morse. Other languages are sent as typed.",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
              InterceptPlatformTextInput(interceptor) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { v ->
                        if (morseMode && typingEnglish) {
                            val newText = MessageText.convertOnSpace(field.text, v.text)
                            setField(if (newText != v.text) TextFieldValue(newText, TextRange(newText.length)) else v)
                        } else setField(v)
                    },
                    placeholder = {
                        Text(
                            when {
                                !morseMode -> "Message"
                                typingEnglish -> "Type English: each word becomes Morse"
                                else -> "Key Morse below"
                            }
                        )
                    },
                    maxLines = 5,
                    textStyle = if (morseMode) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyLarge,
                    visualTransformation = if (morseMode) MorseSymbols else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
              }
            }
            MorseToggle(
                morseMode,
                { userSwitched = true; onToggle() },
                Modifier.padding(start = 6.dp),
            )
            IconButton(onClick = onSend, enabled = enabled && text.isNotBlank()) {
                Icon(Icons.Filled.Send, contentDescription = "Send")
            }
        }
        if (morseMode && typingEnglish) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Typing English. Each word turns into Morse after a space.",
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = {
                    // Convert any last word that hasn't been followed by a space yet.
                    val converted = MessageText.draftToMorse(text)
                    setField(TextFieldValue(converted, TextRange(converted.length)))
                    userSwitched = true
                    typingEnglish = false
                }) { Text("·− Morse keypad") }
            }
        } else if (morseMode) {
            MorseKeypad(
                onKey = { key ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val e = MorseEditing.apply(field.text, field.selection.start, field.selection.end, key)
                    setField(TextFieldValue(e.text, TextRange(e.cursor)))
                },
                onTypeEnglish = {
                    userSwitched = true
                    typingEnglish = true
                },
                onPaste = {
                    clipboard.getText()?.text?.takeIf { it.isNotEmpty() }?.let { pasted ->
                        val e = MorseEditing.paste(field.text, field.selection.start, field.selection.end, pasted)
                        setField(TextFieldValue(e.text, TextRange(e.cursor)))
                    }
                },
            )
        }
    }
}

@Composable
private fun MorseKeypad(onKey: (MorseKey) -> Unit, onTypeEnglish: () -> Unit, onPaste: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeypadButton("·", Modifier.weight(1f), big = true) { onKey(MorseKey.DOT) }
            KeypadButton("−", Modifier.weight(1f), big = true) { onKey(MorseKey.DASH) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeypadButton("next letter", Modifier.weight(1.2f)) { onKey(MorseKey.LETTER) }
            KeypadButton("next word /", Modifier.weight(1.2f)) { onKey(MorseKey.WORD) }
            KeypadButton("⌫", Modifier.weight(0.8f)) { onKey(MorseKey.BACKSPACE) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeypadButton("⌨ Type English", Modifier.weight(1.3f), onClick = onTypeEnglish)
            KeypadButton("📋 Paste", Modifier.weight(1f), onClick = onPaste)
        }
        Text(
            "Tip: tap in the box to move the cursor and fix any letter; ⌫ deletes before the cursor.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun KeypadButton(label: String, modifier: Modifier, big: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.height(if (big) 56.dp else 44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = if (big) 32.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Shows '.' and '-' as · and − in the box (one character each, so the cursor still lines up). */
private object MorseSymbols : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(AnnotatedString(text.text.replace('.', '·').replace('-', '−')), OffsetMapping.Identity)
}

private enum class PlayState { IDLE, PLAYING, PAUSED }

// ------------------------------------------------------------------ new message

@Composable
private fun NewMessageScreen(onStart: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var number by rememberSaveable { mutableStateOf("") }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        context.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0)?.let { onStart(it) } }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("New message", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = number,
            onValueChange = { v -> number = v.filter { it.isDigit() || it in "+ -()" } },
            label = { Text("To: phone number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = number.count { it.isDigit() } >= 3, onClick = { onStart(number.trim()) }) { Text("Start chat") }
            TextButton(onClick = {
                pickLauncher.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
            }) {
                Icon(Icons.Filled.Person, contentDescription = null)
                Spacer(Modifier.size(4.dp))
                Text("Choose contact")
            }
        }
    }
}

// ------------------------------------------------------------------ helpers

private fun hasContacts(context: Context) =
    context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

fun formatTime(context: Context, timestamp: Long): String {
    val date = Date(timestamp)
    return if (DateUtils.isToday(timestamp)) DateFormat.getTimeFormat(context).format(date)
    else DateFormat.getDateFormat(context).format(date)
}
