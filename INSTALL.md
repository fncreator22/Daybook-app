# Getting Daybook onto your phone

Read this first: **there is no APK in this folder.** The app's source is complete
and statically checked, but an APK has to be compiled by the Android toolchain,
and the environment this project was written in has no Java compiler, no Kotlin
compiler, no Gradle and no Android SDK, and no outbound network access. I could
not run a build, so I have not claimed one. What follows are three real ways to
produce the APK, ranked by how long they take and how much you have to install.

Route A is the fastest if you have a GitHub account. Route B is the most useful
long term. Route C is for a machine that already has Android tooling.

---

## Route A — build it in the cloud, install nothing (~10 minutes)

Nothing is installed on your PC. GitHub builds the APK and you download it.

1. Create a new **private** repository on github.com. Do not tick "add a README".
2. Upload this whole folder to it. Either drag the files into GitHub's web
   uploader, or from a terminal in this folder:

   ```
   git init
   git add .
   git commit -m "Daybook"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```

3. Open the repository's **Actions** tab. The workflow is called
   **Build debug APK**. It starts on its own after the push; if it does not,
   click it and press **Run workflow**.
4. Wait for the green tick — the first run takes about 5-8 minutes because it
   downloads the Android SDK.
5. Open the finished run and download the **daybook-debug-apk** artifact from
   the Artifacts section at the bottom. It arrives as a .zip; the .apk is inside.

The workflow is `.github/workflows/build-apk.yml`. It assembles the APK *before*
running the unit tests on purpose, so even a failing test still leaves you an
installable file to look at.

## Route B — Android Studio (~30 minutes, mostly downloading)

Worth it if you intend to keep changing the app, and it is the same tool you will
need later for the Play Store build.

1. Install Android Studio: https://developer.android.com/studio
2. **File > Open**, select this folder. Say yes to "Trust project".
3. Wait for "Gradle sync" to finish in the status bar. Studio downloads Gradle
   8.13 and the Android SDK itself — you do not need to fetch anything by hand.
4. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. When the notification appears, click **locate**. The file is
   `app/build/outputs/apk/debug/app-debug.apk`.

You can also plug the phone in over USB, enable USB debugging on it, and press
the green Run arrow to install directly — that skips the file-copying entirely.

## Route C — one command, if you already have the tools

Needs a JDK 17 or newer and an Android SDK already on the machine.

- **Windows:** double-click `build-apk.bat`, or run it from a terminal.
- **macOS / Linux:** `./scripts/build-apk.sh`

The script finds your JDK and SDK, writes `local.properties` to point at the SDK,
downloads Gradle 8.13 into `~/.gradle/daybook-tools` if `gradle` is not already
on your PATH, runs the build, and copies the result to `Daybook-debug.apk` in
this folder. It tells you exactly what is missing rather than dumping a Gradle
stack trace at you.

The Gradle download is checked against the SHA-256 that Gradle publishes next to
the zip, and the script deletes the file and stops if they do not match. Being
straight with you about the limit of that: both the zip and its checksum come
from the same host, so this catches a corrupt or truncated download but is weaker
than a checksum pinned from somewhere else. If that matters to you, install
Gradle 8.13 yourself and the script will use it instead of downloading anything.

### Why there is no `gradlew`

Normally you would run `./gradlew assembleDebug`. That needs
`gradle/wrapper/gradle-wrapper.jar`, which is a compiled binary — I have no Java
compiler here, so I cannot produce it. Shipping `gradlew` without its jar is
worse than shipping neither, because the build fails with a confusing error
instead of an obvious one. So: `gradle-wrapper.properties` is present (Android
Studio reads it and provisions the right Gradle version from it), and the three
routes above each provide Gradle another way.

If you want the wrapper back, run this once in this folder after any successful
build and it will generate itself: `gradle wrapper --gradle-version 8.13`

---

## Installing the APK on the phone

1. Get the file onto the phone: USB cable, or upload it to your own Google Drive
   or OneDrive and download it there.
2. Tap the file on the phone. Android will say it cannot install from this
   source — tap **Settings** in that prompt and allow it for whichever app you
   opened the file from (Files, Chrome, Drive), then go back and tap **Install**.
3. It installs as **Daybook (debug)**.

The debug build uses application id `com.sr2ma.daybook.debug`, so it will sit
alongside a future Play Store copy rather than conflicting with it. It is signed
with the standard Android debug key, which is fine for your own phone and is not
sufficient to publish. Play Store signing is set up separately, later, from
`keystore.properties` — see `keystore.properties.example`.

The app asks for no permissions at all and declares no internet access, so
nothing you type leaves the phone. Data lives in the app's private SQLite
database. **Uninstalling deletes it.** Before you uninstall or reset anything,
use Settings > Export backup, which writes a JSON file wherever you choose.

## What to check while you use it

- Add a task from the Today tab's quick-add field, then from the + button.
- Tick something off; check it moves and the count updates.
- Make a meeting, add two action items inside its sheet, close it, then find
  those items in the Tasks tab.
- Write a log entry; check it groups under today's date.
- Set a follow-up date on a meeting in the past and confirm it appears under
  "Follow-ups due" on Today.
- Rotate the phone with a sheet open and half-typed text in it.
- Settings > Export backup, then Settings > Import and merge the same file — the
  counts should not double, because rows keep their ids.
- Turn the system font size up to maximum in Android settings and look at the
  list rows; chips should wrap rather than clip.

---

## Honest note on the first build

Every Kotlin file has been checked by `scripts/verify_kotlin.py`, which
cross-references imports, symbols and every `R.*` resource id, and the database
schema and all DAO SQL have been executed for real against SQLite by
`scripts/verify_sql.py` (23/23 checks pass). That catches missing imports, typos,
unbalanced braces and broken resource references.

What it cannot catch is a wrong *external* API signature, because there is no
compiler here and documentation was unreachable from this environment. If the
first build reports errors, these are the places I could not verify and would
look at first:

- `ui/components/Fields.kt` — the Material 3 time picker
  (`rememberTimePickerState`, `TimeInput`, `TimePickerState.hour` / `.minute`).
  This is the newest and least-verified code in the project. If it fails, the
  parameter names are the likely cause.
- `ui/components/Fields.kt` — the `Modifier.clickable` overload taking
  `interactionSource`, `indication`, `role` and `onClickLabel` together.
- `ui/editors/TaskEditor.kt` — `SuggestionChip(onClick =, label =)`.
- `ui/components/Rows.kt` — `FlowRow`, which needs
  `@OptIn(ExperimentalLayoutApi::class)`; that annotation is present.

Anything Gradle reports will name the file and line. Send me the error text and
it is a quick fix — these are signature mismatches, not design problems.
