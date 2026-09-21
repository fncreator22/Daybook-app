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

---

## 9. Session Log

| Date | Session | Summary |
|---|---|---|
| 2026-09-17 | 1 | Phases 0-3: domain model, NLP rule engine (52 tests), ML Kit wallet, LiteRT-LM |
| 2026-09-17 | 2 | Phase 4 (Google sync), Phase 7 (WhatsApp), autonomy levels, voice FAB |
| 2026-09-18 | 3 | Security audit: network_security_config, Log removal, backup exclusions, versionCode 1->2 |
| 2026-09-18 | 4 | Mic fixes (API guard, fallback), Google auth placeholder, privacy string |
| 2026-09-19/21 | 5 | DONE: BUG-001 mojibake, BUG-002 mic immediate stop, FEAT-001 wallet image upload (commit 210d352) |

---

## 10. How to Use This Playbook

1. Check section 7 (done phases)
2. Check section 8 (open bugs + status)
3. Check section 9 (last session summary)
4. Check section 5 (DB version -- never regress, always add migration)
5. Build with section 2 command to confirm baseline green before changes
6. Update sections 8 and 9 after every task

