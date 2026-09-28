# Daybook — Agent Playbook

> **Purpose**: Read at the start of every session. Update at end of every task.
> Never orphan files without recording them here.

---

## 1. Project Snapshot

| Field | Value |
|---|---|
| Repo | https://github.com/fncreator22/Daybook-app.git |
| Branch | main |
| versionCode | **2** |
| DB version | **7** |
| Desktop APK | C:\Users\sr2ma\Desktop\Daybook-debug.apk |
| Last green build | cd52bc4 |
| APK size | ~141.6 MB |

---

## 2. Build Environment

```powershell
$env:JAVA_HOME = "C:\Users\sr2ma\.gradle\daybook-tools\jdk17\jdk-17.0.13+11"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
& "C:\Users\sr2ma\.gradle\daybook-tools\gradle-8.13\bin\gradle.bat" assembleDebug --no-daemon 2>&1
```
CWD: c:\Users\sr2ma\AppData\Local\Claude-3p\local-agent-mode-sessions\1c832108\00000000\204ea497\outputs\Daybook-source\daybook

---

## 3. Stack (LOCKED)

- LLM: Gemma 3 270M IT via LiteRT-LM (reflection). Lazy-load, idle-release 3 min, single-inference Mutex, 30s timeout.
- Embeddings: ONNX MiniLM-L6-v2 INT8, bundled APK assets
- Vector store: sqlite-vec precompiled .so in jniLibs/
- Barcode/OCR: ML Kit bundled (barcode-scanning:17.3.0, text-recognition:16.0.1)
- DB: SQLCipher 4.5.6, hand-written DAOs (NO Room)
- DI: None (NO Hilt)
- Voice: SpeechRecognizer (on-device API 31+ with fallback to network)
- DO NOT: add Room, Hilt, fallbackToDestructiveMigration(), external storage writes, bundle LLM in APK

---

## 4. Architecture Map

```
ui/
  DaybookApp.kt          -- root composable, voice FAB, ListeningSheet, VoiceResultSheet
  DaybookViewModel.kt    -- single ViewModel; startListening, onVoiceResult, onVoiceNoMatch, onVoiceError
  DaybookUiState.kt      -- all state incl. isListening, voiceRetried, voiceRetryMessage, voiceResult
  VoiceCaptureManager.kt -- wraps SpeechRecognizer as Flow; VoiceResult sealed interface
  VoiceAgentResult.kt    -- data class (spokenText, parseResult)
  screens/
    WalletScreen.kt      -- WalletAddMenu: Scan / Manual / [SESSION-5 TODO: Upload Image]
    TodayScreen.kt, TasksScreen.kt, MeetingsScreen.kt -- PendingSyncBanner
domain/
  NaturalLanguageParser, AgentEngine, CadenceEngine, BriefingWriter
  model/Enums.kt  -- Priority,TaskStatus,LogKind,Cadence,SyncStatus,AutonomyLevel,ToolCategory
data/
  DaybookDatabase.kt (v7), DaybookRepository.kt (internal val database)
  dao/ -- WhatsAppDao, PassDao, etc.
  BackupCodec.kt
ai/
  LlmEngine.kt, ModelDownloader.kt, EmbeddingEngine.kt, VectorStore.kt, NightlyAgentWorker.kt
sync/
  GoogleAuthClient.kt  -- CredentialManager, zero Log calls
  SyncPreferences.kt   -- EncryptedSharedPreferences; getAutonomy/setAutonomy
  SyncViewModel.kt     -- SyncUiState with autonomyLevels map
  SyncSettingsSection.kt -- google_auth_placeholder guard, AutonomyRow chips
whatsapp/
  WhatsAppListenerService.kt, WhatsAppReplyHelper.kt, WhatsAppSettingsSection.kt
  WhatsAppDao.kt, model/WhatsAppMessage.kt
```

---

## 5. DB Migration History

| Version | Change |
|---|---|
| 1->2 | tasks cadence columns |
| 2->3 | meetings ai_summary |
| 3->4 | meetings calendar_event_id |
| 4->5 | passes table |
| 5->6 | gcal_event_id, sync_status, calendar_sync_enabled |
| 6->7 | whatsapp_messages table |
| **Current: 7** | |

---

## 6. Security Invariants

- Zero Log.* calls anywhere in .kt files
- network_security_config.xml: cleartextTrafficPermitted="false"
- data_extraction_rules.xml + backup_rules.xml: exclude database + sharedpref
- android:usesCleartextTraffic="false" in manifest
- google_auth_placeholder=true -> sign-in button disabled until real OAuth client ID set
- EncryptedSharedPreferences for sync tokens + autonomy prefs
- SQLCipher for all DB tables

---

## 7. Completed Phases

| Phase | Status | Commits |
|---|---|---|
| 0 Domain modeling | DONE | 6a3ef1f |
| 1 Intelligence (52 tests) | DONE | bca1e2b |
| 2 Barcode Wallet ML Kit | DONE | 207df6d |
| 3 On-device AI LiteRT-LM | DONE | cb90725 |
| 4 Google Drive/Calendar sync | DONE (code); OAuth human step pending | e386007 |
| 6 Voice capture + mic FAB | DONE | c719cc4 f58162d 42efc4e |
| 7 WhatsApp notification reader | DONE | 1037181 496397a |
| 8 Per-tool autonomy levels | DONE | 94a3c2d |
| Security hardening | DONE | 29baa28 cd52bc4 |

---

## 8. Known Issues / Backlog (session 5 target)

### BUG-001 -- Mojibake in strings.xml [HIGH]
Symptom: "Add a task for todayAEa,Not..." / "ListeningAEa,Not..." on device
Root cause: UTF-8 multi-byte chars (ellipsis, curly quotes, em-dash) double-encoded on Windows
Fix: Replace all non-ASCII chars in strings.xml with XML entities (&#8230; for ..., etc.) or plain ASCII
Files: app/src/main/res/values/strings.xml
Status: DONE

### BUG-002 -- Mic stops immediately ~1s, shows "Couldn't catch that" [HIGH]
Symptom: ListeningSheet appears then closes in ~1s; user cannot speak
Root cause A: LaunchedEffect(state.isListening, state.voiceRetried) -- when voiceRetried
  flips false->true (first ERROR_NO_MATCH from on-device recognizer), the effect
  restarts, fires a second immediate recognition attempt, also fails, onVoiceNoMatch()
  second call closes the sheet. Two failures happen before user can speak.
Root cause B: createOnDeviceSpeechRecognizer() fails immediately on devices where
  the on-device model isn't properly loaded, even when isOnDeviceRecognitionAvailable()=true
Fix:
  1. Remove voiceRetried from LaunchedEffect key -- handle retry INSIDE the coroutine with 500ms delay
  2. Switch primary recognizer to createSpeechRecognizer(context) (network-based/system default)
     EXTRA_PREFER_OFFLINE=true hint is sufficient; on-device will be used if available
  3. On retry: delay(500ms) before starting next recognition attempt
Files: ui/DaybookApp.kt (LaunchedEffect), ui/VoiceCaptureManager.kt
Status: DONE

### FEAT-001 -- Wallet: Upload image from gallery for barcode parsing [MEDIUM]
Request: Third option "Upload image" in WalletAddMenu
Pipeline: image picker -> ML Kit barcode scanner -> ML Kit OCR -> pre-fill PassSheet
If fields missing: PassSheet shows with empty fields, user fills manually
Files: ui/screens/WalletScreen.kt, ui/editors/PassConfirmSheet.kt, domain/PassParser.kt
Status: DONE

### BUG-003 -- Email Sync Data Loss: Parsed fields discarded [HIGH]
Symptom: Email action conversion created bare tasks with empty notes, discarding sender, snippet, topics, and meeting locations.
Root cause: `convertGmailAction` created `Task(title, ...)` with default empty `notes = ""`, discarding snippet and metadata.
Fix: Added companion extractors `extractTopics`, `extractLocation`, and `buildTaskNotes` in `GmailSyncEngine.kt` to populate `notes`, `project`, and `location` in `Task` and `Meeting`.
Files: sync/GmailSyncEngine.kt, ui/DaybookViewModel.kt
Status: DONE

### BUG-004 -- Actioned Dashboard Items Non-Removable [HIGH]
Symptom: Once an email card action was converted, action buttons disappeared leaving no way to remove it from the Today board. WhatsApp cards lacked a dismiss button.
Root cause: `TodayScreen.kt` checked `if (msg.suggestedAction != null && msg.actionedAt == null)` and rendered nothing when actioned; `WhatsAppMessageCard` had no dismiss action.
Fix: Added "Added" chip and "Remove" / "Dismiss" buttons for all email states; added "Dismiss" to `WhatsAppMessageCard` wired to `dismissWhatsAppMessage` in `DaybookViewModel`.
Files: ui/screens/TodayScreen.kt, ui/DaybookViewModel.kt
Status: DONE

### BUG-005 -- Tapping items opens edit form directly instead of Details viewer [MEDIUM]
Symptom: Tapping task or meeting rows opened the editable form sheet directly, risking accidental edits and hiding detailed parsed context.
Root cause: `onClick` on task and meeting rows called `viewModel.editTask` / `viewModel.editMeeting` directly.
Fix: Tapping task/meeting rows in Today, Tasks, and Meetings screens opens read-only `TaskCardSheet` or `MeetingCardSheet` displaying all parsed fields (notes, topics, location, time, cadence) and a 3-dot overflow menu for Edit and Delete.
Files: ui/screens/TaskCardSheet.kt, ui/screens/MeetingCardSheet.kt, ui/screens/TodayScreen.kt, ui/screens/TasksScreen.kt, ui/screens/MeetingsScreen.kt
Status: DONE

### BUG-006 -- Mobile Reminders & Notifications Not Firing [HIGH]
Symptom: App never displayed reminder or sync notifications in the system status shade.
Root cause: `POST_NOTIFICATIONS` was in manifest but never requested at runtime on Android 13+ (API 33+); notification channels lacked high-importance visibility; exact alarm scheduling lacked fallback for same-day overdue reminders.
Fix: Added runtime `POST_NOTIFICATIONS` permission launcher in `MainActivity.kt`; initialized high-importance channels with public lockscreen visibility; scheduled same-day overdue fallback reminders (`now + 15m`).
Files: MainActivity.kt, notifications/ReminderNotificationManager.kt
Status: DONE

### BUG-007 -- AI Agent Fails on Schedule & Agenda Queries [HIGH]
Symptom: Asking conversational questions like "what do I have to do today?" created a new task titled "what do I have to do today" instead of answering with today's schedule.
Root cause: Rule engine had no pattern for schedule inspection queries, falling through to default `CREATE_TASK`.
Fix: Added `ParsedIntent.QUERY_SCHEDULE` and regex query matching in `NaturalLanguageParser.kt`; added `buildScheduleSummary(today)` in `DaybookViewModel.kt` formatting tasks, overdue items, meetings, and completed tallies.
Files: domain/NaturalLanguageParser.kt, ui/DaybookApp.kt, ui/DaybookViewModel.kt
Status: DONE

### BUG-008 -- Battery Drain & Continuous Network Activity Icon [HIGH]
Symptom: Continuous network transfer icon in Android status bar and excessive battery drain.
Root cause: `NetworkTracker` registered an active `NetworkRequest` with `NET_CAPABILITY_INTERNET`, holding physical modem radios awake; HTTP connections in `GmailSyncEngine` lacked `finally { disconnect() }`.
Fix: Switched `NetworkTracker` to passive `registerDefaultNetworkCallback`; wrapped HTTP requests in `try/finally { conn.disconnect() }`; added `requiresBatteryNotLow` constraint to `DriveBackupWorker`.
Files: sync/NetworkTracker.kt, sync/GmailSyncEngine.kt, sync/DriveBackupWorker.kt
Status: DONE

### FEAT-002 -- Periodic Background Gmail Sync & Dynamic WhatsApp Permission Check [MEDIUM]
Request: Background email sync every 15 minutes without battery drain; dynamic re-check of WhatsApp listener permission after visiting Android Settings.
Fix: Implemented `GmailSyncWorker` via WorkManager with network and `requiresBatteryNotLow` constraints and sync diff logging in `SyncPreferences`; added `Lifecycle.Event.ON_RESUME` observer in `WhatsAppSettingsSection`.
Files: sync/GmailSyncWorker.kt, sync/SyncManager.kt, sync/SyncPreferences.kt, whatsapp/WhatsAppSettingsSection.kt
Status: DONE

### BUG-009 -- Read-Only Detail Presentation Sheets for Emails & WhatsApp Messages [MEDIUM]
Symptom: Tapping email or WhatsApp message cards toggled raw card expansion instead of opening a comprehensive read-only detail presentation sheet with overflow actions.
Root cause: `TodayScreen.kt` lacked modal sheet viewers for Gmail and WhatsApp messages.
Fix: Created `GmailCardSheet` and `WhatsAppCardSheet` displaying all parsed topics, subtopics, detected schedule, location, sender, full body/snippet, and overflow menus (Edit, Delete, + Task, + Meeting, Reply).
Files: ui/screens/GmailCardSheet.kt, ui/screens/WhatsAppCardSheet.kt, ui/screens/TodayScreen.kt
Status: DONE

### BUG-010 -- Completed Task Tap Regressed to Status Toggle [MEDIUM]
Symptom: Tapping a task in the "Completed Today" list toggled the task back to incomplete rather than opening its details sheet.
Root cause: `completedTodaySection` ignored `onTaskClick` parameter and passed `onClick = { viewModel.toggleTaskDone(task) }`.
Fix: Wired `onClick` to `onTaskClick?.invoke(task) ?: viewModel.toggleTaskDone(task)` so tapping opens `TaskCardSheet`.
Files: ui/screens/TodayScreen.kt
Status: DONE

### BUG-011 -- Dashboard Suggestions Missing Auto-Expiration [HIGH]
Symptom: Once an email was converted or time elapsed, the suggestion card stayed on the dashboard indefinitely without auto-expiring.
Root cause: `TodayScreen.kt` passed all `state.recentGmailMessages` without checking if associated tasks completed, meetings elapsed, or 24 hours passed.
Fix: Filtered `visibleGmailMessages` to auto-expire actioned items whose task is DONE, whose meeting is in the past, or that are >24 hours old; filter out unactioned emails whose detected date is in the past.
Files: ui/screens/TodayScreen.kt
Status: DONE

### BUG-012 -- Voice Utterances Forced Single Add Action [MEDIUM]
Symptom: Voice capture confirmation only allowed a single button, forcing user into unintended actions when intent was ambiguous.
Root cause: `VoiceResultSheet` had a single Confirm button for non-query results.
Fix: Enhanced `VoiceResultSheet` with multi-choice options ("+ Task", "+ Meeting", "+ Log", "Ask Assistant", "Dismiss") for ambiguous voice results.
Files: ui/DaybookApp.kt
Status: DONE

---

## 9. Session Log

| Date | Session | Summary |
|---|---|---|
| 2026-09-17 | 1 | Phases 0-3: domain model, NLP rule engine (52 tests), ML Kit wallet, LiteRT-LM |
| 2026-09-17 | 2 | Phase 4 (Google sync), Phase 7 (WhatsApp), autonomy levels, voice FAB |
| 2026-09-18 | 3 | Security audit: network_security_config, Log removal, backup exclusions, versionCode 1->2 |
| 2026-09-18 | 4 | Mic fixes (API guard, fallback), Google auth placeholder, privacy string |
| 2026-09-19/21 | 5 | DONE: BUG-001 mojibake, BUG-002 mic immediate stop, FEAT-001 wallet image upload (commit 210d352) |
| 2026-09-28 | 6 | DONE: BUG-003 to BUG-012: rich metadata extraction, read-only sheets with overflow menus (GmailCardSheet, WhatsAppCardSheet), auto-expiration on dashboard, notifications runtime permission & high importance channels, schedule query AI routing, passive network tracker & background WorkManager sync |

---

## 10. How to Use This Playbook

1. Check section 7 (done phases)
2. Check section 8 (open bugs + status)
3. Check section 9 (last session summary)
4. Check section 5 (DB version -- never regress, always add migration)
5. Build with section 2 command to confirm baseline green before changes
6. Update sections 8 and 9 after every task


