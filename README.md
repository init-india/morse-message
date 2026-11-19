# Morse Message (Android)

A text-messaging app with Morse code built in. Set it as the phone's **default SMS app** and chat as
usual. One **morse / abc button** next to Send switches the keyboard, converts what you typed, and changes how every chat is shown. It also has everything for playing and learning Morse: beep, screen flash, flashlight,
vibration, a Morse key, lessons, and caller-name ringtones in Morse.

Free and open source (GPL-3.0-or-later), with no Google Play Services, ads, trackers or internet permission.
The same code ships on **Google Play** and **F-Droid**.

## Download

The latest test build is on the [Releases page](https://github.com/init-india/morse-message/releases/tag/latest-build).
Install `MorseMessage-*-fdroid-debug.apk` on an Android 10+ phone (allow installing from unknown sources).
GitHub Actions rebuilds it on every push to `main` (see `.github/workflows/build.yml`).

## Features (v0.8)

| Tab | What it does |
|---|---|
| **Messages** | Full SMS app: conversation list, chats, new message (type a number or pick a contact), notifications, unread state, retry failed sends, delete. Composer: text box → **·− / abc button** → **Send**. One button per Morse message: **▶ Play → ⏸ Pause → ▶ Resume** (beep / flash / vibration / flashlight). Playback stops on Back, leaving the chat, switching tabs or leaving the app. |
| **Translate** | Text → Morse and Morse → text. Plays as **sound, vibration, flashlight, full-screen flash** (any mix), highlighting each letter. Copy or send to other apps (SMS, WhatsApp, email…). |
| **Key** | Type in Morse with **dot/dash buttons** or a **straight key** (hold = dash, tap = dot). Live sidetone, decoded text, play back, send. |
| **Learn** | Tap-to-hear alphabet, numbers, punctuation, prosigns. **Koch-method practice** with scoring. Articles: timing, how to learn, history, uses, abbreviations, SOS. |
| **Settings** | Default SMS app status, speed 5–40 WPM with **Farnsworth** spacing, pitch, default outputs, decode hint on/off, and the **Morse ringtone for calls** switch. |

**In other apps (WhatsApp, Gmail, Telegram…):** select text and tap **Morse** in the text menu (Copy / Paste /
⋮). Text you're typing is replaced with Morse, or selected Morse is turned back into text. On a received
message, a small window shows the translation with Play. If you don't tap it, nothing changes in those apps.
Android doesn't allow any app to pop up inside WhatsApp or Gmail when you press their Send button.

### The morse / abc button
A single button, just before Send, showing **·−** above **abc** with the active one highlighted. Tapping it switches mode.
The same button is in the Messages top bar. The mode is saved until it is tapped again.

| | Keyboard | What's in the box when you tap | Sending | Showing chats, chat list, notifications |
|---|---|---|---|---|
| **·− morse** | Phone keyboard hidden; **Morse keypad**: `·` `−` `next letter` `next word /` `⌫` `⌨ Type English` `📋 Paste` | Converted to Morse | Delivered as Morse (English letters/numbers only; other languages as typed) | All messages in Morse (English parts) |
| **abc** | Phone's normal keyboard | Turned back into text (your exact words if you didn't edit) | Sent exactly as typed, any language | All messages in normal language |

While keying Morse, a line above the box shows what it says (e.g. `→ HI`).

**Live translation bar:** in morse mode a bar above the box shows what your Morse says while you key it,
including the letter in progress ("?" while the dots/dashes aren't a letter yet).

**Fixing mistakes:** the box stays editable in morse mode. Tap anywhere to move the cursor; the keypad types and
⌫ deletes at the cursor. Long-press to select or paste; the keypad also has a **📋 Paste** key.

**Typing English in morse mode:** tap **⌨ English** on the Morse keypad. The phone keyboard opens while you stay
in morse mode, and chats stay in Morse. Each English word turns into Morse as soon as you type a space; other languages
stay as typed. Tap the **·− Morse keypad** button to go back to keying, or just press Send: any last word is converted too.

Contact names always appear as saved in the contact list. They are never converted.

**Only English becomes Morse.** International Morse has no Tamil, Hindi or Chinese letters, so a word containing
any non-English letter is kept as typed (accented words like "José" too). Example in Morse mode:
`Hi வணக்கம் 5` → `.... .. / வணக்கம் / .....`, which the receiver's Normal mode shows as `HI வணக்கம் 5`.

### How a Morse SMS looks
```
... --- ... / .... ..

(Morse code: dots and dashes, letters split by spaces, words by /)
```
The hint line is optional (Settings → Messages). Morse Message recognises Morse in any incoming SMS, including the dot styles · • and dash styles − – —
and messages that mix Morse with other languages. A message counts as Morse only if each part between " / " is either
Morse or has no English letters/digits, with at least two Morse letters, so "..." or "1/2" are never mistaken for Morse.

### Default SMS app: what it means
- **If tapping "Set as default" does nothing**, Android refused without showing its dialog. The app then opens the
  phone's Default apps screen and shows a Help dialog (with a diagnostics line to send to the developer).
- **If Android refuses** (common for an APK installed from a file on Android 13+, "Restricted setting"): the app shows
  App info and Default apps buttons. Fix: App info → ⋮ → **Allow restricted settings**, then set it as default again.
  Installs from Google Play or F-Droid don't need this.
- Android grants SMS permissions together with the "SMS app" role, which the user approves once (Messages tab or Settings).
- Morse Message then receives every SMS, saves it in the phone's shared SMS database and notifies. It sends and saves
  sent messages, marks them failed if sending fails, and handles "reply with message" from the incoming-call screen.
- Conversations are the phone's normal SMS history, so switching back to another SMS app keeps everything.
- **Not supported yet:** MMS (picture and group messages). When one arrives, a notification says it can't be shown.
  The user should not rely on Morse Message as their default app for MMS until that lands.
- **Not possible for any third-party app:** RCS ("chat features" in Google Messages).

### Morse ringtone for calls (Settings)
One switch applies to all calls:

- **Contacts** ring with their name in Morse.
- **Unknown numbers** ring with the number in Morse. This needs the "call screening" permission; the app never blocks calls.
- **Off**, or **app uninstalled** → the phone's own ringtone.

Contact ringtones live in the app's private storage, served by `RingtoneProvider`, so they disappear on uninstall
and Android falls back to the default ringtone. The app never changes the phone's default ringtone.

## Test on real phones before release
1. **SMS:** send and receive (short and long messages), dual SIM, airplane mode → failed → retry,
   quick reply from the call screen, notifications on Android 13+.
2. **Switching default SMS app** back and forth: history intact.
3. **MMS arriving** while default: the notification appears.
4. **Ringtones:** uninstall fallback, unknown-caller playback on Samsung / Xiaomi / Pixel, dialers that ignore per-contact ringtones.
5. **Text menu "Morse"** in WhatsApp, Gmail and Messages, editable and read-only.

## Build

Requirements: a recent Android Studio (2025 or newer) or the Android SDK command-line tools, plus JDK 17+.

```bash
./gradlew testFdroidDebugUnitTest        # Morse engine unit tests
./gradlew assembleFdroidDebug            # debug APK  -> app/build/outputs/apk/fdroid/debug/
./gradlew assembleFdroidRelease          # unsigned release APK for F-Droid
./gradlew bundlePlayRelease              # .aab for Google Play (sign with your upload key)
```

**Build variants:** `fdroid` and `play` share all code. The only difference is the `BuildConfig.DISTRIBUTION` label in About.

## Releasing

### Google Play
1. Sign `bundlePlayRelease` with your upload key.
2. Play Console → **Permissions declaration**: SMS permissions are allowed because the app works as the
   **default SMS handler** (core functionality). Call-log access is not used.
3. A **privacy policy URL** and the **Data safety** form are required: messages and contacts stay on the device, nothing is collected or shared.
4. Play reviews default-SMS apps closely. Expect a review video request showing the app set as default and sending/receiving.

### F-Droid
1. Publish the source (GitHub, GitLab or Codeberg) and tag the release (e.g. `v0.8.0`).
2. `fastlane/metadata/android/en-US/` holds the title, descriptions and changelogs. Add screenshots to `images/phoneScreenshots/`.
3. Fill in the placeholders in `fdroid/com.morsemessage.yml` and submit it to [fdroiddata](https://gitlab.com/fdroid/fdroiddata).

## Project layout

```
app/src/main/java/com/morsemessage/
  sms/        SmsRepository (read/write SMS database, send), SmsReceivers (deliver, MMS notice, sent status,
              quick reply), MessageNotifier
  morse/      MorseCode (English-only codec, mixed-language messages), MessageText (toggle rules),
              MorseTiming, ToneSynth
  output/     MorsePlayer (sound/vibration/torch/screen in sync), Sidetone, Torch & Haptics
  ringtone/   ContactRingtones, RingtoneProvider, UnknownCallerService, ContactsChangedJob
  share/      ShareHelper (send to other apps)
  learn/      LearnContent
  data/       SettingsRepository
  ui/         Compose screens (MessagesScreen, TranslateScreen, KeyerScreen, LearnScreen, SettingsScreen…)
  ProcessTextActivity  "Morse" in other apps' text menu
```

## Roadmap
- **MMS** (pictures, group chats): next milestone before recommending it as an everyday default app
- Direct reply from the notification, a search box
- Morse decoder from microphone / camera, system-wide Morse keyboard, Wear OS vibration
- Translations
