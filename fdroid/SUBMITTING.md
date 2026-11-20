# Submitting Morse Message to F-Droid

App ID: `io.github.init_india.morsemessage` · Recipe: [`io.github.init_india.morsemessage.yml`](io.github.init_india.morsemessage.yml)

F-Droid builds the app itself from this GitHub repository (the `fdroid` flavour, release build, tag `v0.9.2`),
signs it with F-Droid's key and publishes it. Nothing is uploaded by us except the recipe.

## Already done in this repository
- [x] GPL-3.0 licence, public source, only free libraries (AndroidX / Jetpack Compose; no Google Play Services, Firebase, ads or trackers)
- [x] Store text and images: `fastlane/metadata/android/en-US/` (title, descriptions, changelogs, 512 px icon,
      1024×500 feature graphic, 5 phone screenshots; the chat screenshots use made-up demo conversations). The same folder layout is used for Google Play.
- [x] Version tag `v0.9.2` (versionCode 11) and a release build that GitHub Actions checks on every push
- [x] Build recipe `fdroid/io.github.init_india.morsemessage.yml`

## Still to do (needs your accounts)

### 1. GitLab account
Create a free account at https://gitlab.com (F-Droid's data lives there).

### 2. Request for Packaging
Open a new issue at https://gitlab.com/fdroid/rfp/-/issues using its template. Suggested text:

> **Morse Message**: an SMS app with Morse code built in.
> Source: https://github.com/init-india/morse-message (GPL-3.0-or-later)
> Default SMS app with a morse / abc mode switch: messages can be sent and shown in Morse (English letters and numbers),
> a Morse keypad, playback as beep / flash / vibration / flashlight, Morse lessons, and caller-name ringtones in Morse.
> No proprietary dependencies. Fastlane metadata included. I am the developer (init-india).
> Not yet supported: MMS (stated in the description).

### 3. Merge request with the recipe
1. Open https://gitlab.com/fdroid/fdroiddata and click **Fork**.
2. In your fork, create a branch named `io.github.init_india.morsemessage`.
3. Add the file `metadata/io.github.init_india.morsemessage.yml` with the content of this repo's
   `fdroid/io.github.init_india.morsemessage.yml` (GitLab web editor: **+ → New file**).
4. Commit with the message `New App: io.github.init_india.morsemessage`.
5. Wait until the fork's CI pipeline is green (it runs `fdroid lint` and a test build).
6. Open a **merge request** to `fdroid/fdroiddata`, and link the RFP issue from step 2.

### 4. Review
F-Droid volunteers review licence, build and anti-features and may ask questions in the merge request.
After the merge, the app usually appears in F-Droid within a couple of days.

Note: F-Droid wants the full commit hash in `commit:` (not the tag name). v0.9.2 = `55f41b01cab7e5650ed12b7f35855bd3addf8628`.
Reproducible builds are not enabled yet, so F-Droid signs the app with its own key.

## Future releases
1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Push to `main`. GitHub Actions sees the new version and creates the release and tag `v<versionName>` itself.
   F-Droid's auto-update (`UpdateCheckMode: Tags`) picks the tag up; no new merge request needed.
