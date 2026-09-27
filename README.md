# Daybook

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Android Gradle Plugin](https://img.shields.io/badge/AGP-8.13.0-3DDC84.svg?logo=android)](https://developer.android.com/studio/releases/gradle-plugin)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202025.09.00-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-orange.svg)](https://developer.android.com/about/dashboards)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36-green.svg)](https://developer.android.com/about/versions/16)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-177%20Passed-brightgreen.svg)]()

> **100% Offline-First Personal Productivity & On-Device Intelligence for Android.**  
> A private, zero-cloud personal workspace built with **Jetpack Compose**, **Material 3**, **SQLCipher**, and on-device machine intelligence.

---

## Table of Contents

- [Vision & Architectural Invariant](#vision--architectural-invariant)
- [Key Features](#key-features)
  - [Today Dashboard & Liquid Glass UI](#today-dashboard--liquid-glass-ui)
  - [Task & Meeting Management with CadenceEngine](#task--meeting-management-with-cadenceengine)
  - [Work Journal / Daily Log](#work-journal--daily-log)
  - [Digital Wallet, Passes & OCR Document Upload](#digital-wallet-passes--ocr-document-upload)
  - [In-App Morning Briefing & Audio TTS](#in-app-morning-briefing--audio-tts)
  - [Natural Language Parser & On-Device AI](#natural-language-parser--on-device-ai)
  - [Alarms, Notifications & Reboot Resilience](#alarms-notifications--reboot-resilience)
  - [WhatsApp Notification Reader](#whatsapp-notification-reader)
  - [Encrypted Backup & Storage Access Framework (SAF)](#encrypted-backup--storage-access-framework-saf)
- [Architecture & Tech Stack](#architecture--tech-stack)
- [App-Scoped Storage & Directory Architecture](#app-scoped-storage--directory-architecture)
- [Getting Started & Build Instructions](#getting-started--build-instructions)
- [Verification & Automated Test Suite](#verification--automated-test-suite)
- [License & Model Attribution](#license--model-attribution)

---

## Vision & Architectural Invariant

Daybook is engineered around a core, non-negotiable principle:

> **The application must be 100% functional with zero network connectivity and zero AI features active.**

All AI capabilities (Gemma 3 270M IT via LiteRT-LM, vector embeddings via ONNX, and cloud integrations) are **strictly opt-in**. The deterministic rule engine (`NaturalLanguageParser`, `BriefingWriter`, `CadenceEngine`, and `AgentEngine`) executes locally with zero network requests and zero battery-draining background loops.

All personal information—tasks, confidential notes, client meetings, health cards, driver's licenses, and chat messages—is stored in a **hardware-encrypted SQLCipher database** with encryption keys guarded by the **Android Keystore**.

---

## Key Features

### Today Dashboard & Liquid Glass UI
- **Liquid Frosted Glass (`GlassCard`):** Leverages Android 12+ (API 31+) hardware GPU shader blur (`RenderEffect.createBlurEffect`) with high-density multi-stop acrylic gradient fallbacks on API 26–30.
- **Physical Specular Rims:** Static linear gradient refraction borders replace continuous animation loops, eliminating battery drain and maintaining a steady 60–120 FPS.
- **Spring Touch Physics:** Interactive cards scale dynamically to `0.97f` on touch press using damped spring physics (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessLow`).
- **Tabular Figures (`tnum`):** Typography configured with font feature settings (`tnum`) across all counters, badges, and times to eliminate digit-jumping jitter.
- **Completed Today Shelf:** Collapsible shelf with spring-animated rotating chevron allowing instant review and single-tap undo for accidental completions.
- **4-Tab Navigation & Unified Pill FAB:** Material 3 compliant 4-tab bottom navigation (`Today`, `Tasks`, `Meetings`, `Log`) with a single expandable contextual action pill FAB.

### Task & Meeting Management with CadenceEngine
- **Prioritized Tasks:** Status tracking (`OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED`), priority levels (`LOW`, `MEDIUM`, `HIGH`, `URGENT`), and project tags.
- **CadenceEngine Auto-Spawning:** Deterministic recurrence calculations (`DAILY`, `WEEKDAYS`, `WEEKLY`, `MONTHLY`). Marking a recurring task done automatically calculates the next due date, clears calendar sync IDs, registers a new database occurrence, and schedules an advance reminder alert.
- **Meeting Tracker:** Tracks date, time, attendees, preparation notes, and action items.

### Work Journal / Daily Log
- Reverse-chronological timeline of daily reflections, accomplishments, decisions, and blockers.
- Categorized into structured entry types: `NOTE`, `WIN`, `BLOCKER`, and `DECISION`.

### Digital Wallet, Passes & OCR Document Upload
- **CameraX Barcode Scanner:** Real-time scanning for 1D and 2D barcodes (QR, PDF417, Aztec, Code 128, EAN, etc.).
- **ML Kit OCR Document Upload:** When selecting an image or document from the device gallery that lacks a barcode, ML Kit Text Recognition automatically extracts text and creates a searchable `DOCUMENT` card.
- **Persistent Favorites & Archives:** Favorites (`is_favorited`) and archives (`is_archived`) are persisted directly to SQLite, surviving device reboots and configuration changes.

### In-App Morning Briefing & Audio TTS
- **On-Demand Synthesis:** `BriefingWriter` computes an agenda summary combining overdue tasks, today's schedule, meetings, and follow-ups.
- **`BriefingSheet` Bottom Sheet:** Interactive bottom sheet on the Today dashboard providing the briefing text and spoken audio narration using Android's native `TextToSpeech` engine.
- **Scheduled 7:30 AM Notification:** Optional morning briefing notification via `BriefingNotificationWorker`.

### Natural Language Parser & On-Device AI
- **3-Stage Compound Clause Splitter:** `NaturalLanguageParser.splitClauses()` decomposes compound sentences (e.g., *"two meetings tomorrow with Acme and three tasks for day after tomorrow"*) into individual discrete actions offline.
- **Conversational Intelligence:** Conversational queries and greetings (`"hi how are you"`) are classified as `ParsedIntent.CONVERSATION` with helpful responses, eliminating awkward fallback task prompts.
- **Gemma 3 270M IT via LiteRT-LM:** Supports on-device inference using Google DeepMind's Gemma 3 270M IT model. Gated model downloads are authenticated with user-provided Hugging Face Bearer tokens with presigned AWS S3 redirect handling and SHA-256 verification.
- **Open Knowledge Format (OKF) Knowledge Graph:** `KnowledgeGraphEngine` maintains entity-relationship subgraphs (`kg_nodes`, `kg_edges`) and injects contextual triples into prompt contexts.
- **Offline BERT WordPiece Tokenizer:** Mathematically valid WordPiece tokenizer for MiniLM-L6-v2 embeddings with greedy subword prefix search and unicode accent normalization.
- **Resilient KNN Vector Search:** Pure Kotlin KNN cosine similarity fallback over SQLite float BLOBs in `embeddings_fallback`, ensuring search never fails even if native extensions are disabled by the OS.

### Alarms, Notifications & Reboot Resilience
- **`ReminderNotificationManager`:** Schedules high-priority reminder alerts using Android `AlarmManager`:
  - Meetings: **15 minutes in advance**.
  - Tasks: **9:00 AM on the due date**.
- **Reboot Resilience (`BootReceiver`):** Listens for `ACTION_BOOT_COMPLETED` and vendor fast-boot intents (`QUICKBOOT_POWERON`) to automatically reschedule all pending alarms after device restarts.
- **Smart Alarm Cancellation:** Alarms automatically cancel when a task is completed or deleted and reschedule if reopened.

### WhatsApp Notification Reader
- Privileged `NotificationListenerService` (`WhatsAppListenerService`) with `android:exported="true"` reading incoming notification text locally.
- **Android 13+ Restricted Settings Flow:** Guides users through Special App Access → Allow Restricted Settings on sideloaded APKs.
- Automatically extracts actionable tasks and meetings from incoming messages with quick-action suggestion chips (`"+ Add Task"`, `"+ Add Meeting"`).

### Encrypted Backup & Storage Access Framework (SAF)
- **`LocalBackupManager`:** Hardware-grade AES-256-GCM authenticated encryption with 128-bit authentication tags.
- Direct integration with Android's Storage Access Framework (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`) allowing exports to user-selected cloud or local folders.
- Automatic rollback and staging to prevent database corruption during import.
- **Rolling File Logger (`DaybookLogger`):** Rolling on-device logging (`daybook-app.log`) with 1 MB rotation and automatic credential redaction (Hugging Face tokens, Bearer headers, passphrases, passport numbers).

---

## Architecture & Tech Stack

```
app/src/main/java/com/sr2ma/daybook/
├── ai/             # LiteRT-LM engine, ONNX embedding, WordPiece tokenizer, ModelDownloader, VectorStore
├── data/           # DaybookDatabase (SQLCipher), DatabaseKeyManager (Keystore), DAOs, DaybookRepository
├── domain/         # NaturalLanguageParser, TodayBuilder, BriefingWriter, CadenceEngine, AgentEngine, KnowledgeGraphEngine
├── logging/        # DaybookLogger (1 MB rolling log with privacy redaction)
├── notifications/  # ReminderNotificationManager, ReminderReceiver, BootReceiver
├── sync/           # LocalBackupManager (SAF), GmailSyncEngine, DriveBackupWorker, CalendarSyncWorker, GoogleAuthClient
├── ui/             # DaybookApp, DaybookViewModel, theme, Glassmorphism, screens (Today, Tasks, Meetings, Log, Wallet, Settings)
└── whatsapp/       # WhatsAppListenerService, WhatsAppSettingsSection, WhatsAppReplyHelper
```

| Layer | Technology | Details |
| :--- | :--- | :--- |
| **Language** | Kotlin 2.2.20 | Modern, idiomatic coroutines & StateFlow |
| **UI Framework** | Jetpack Compose (BOM 2025.09.00) | Declarative UI, Material 3, Custom Glassmorphic design system |
| **Database & Security** | SQLCipher 4.5.6 | 256-bit AES database encryption, Android Keystore StrongBox key storage |
| **On-Device LLM** | Gemma 3 270M IT via LiteRT-LM | INT8 QAT, ~250–300 MB post-install download (never bundled in APK) |
| **Embeddings & Search** | ONNX MiniLM-L6-v2 + WordPiece | 384-dimensional embeddings, KNN cosine distance fallback |
| **Vision & Scanning** | Google ML Kit (Bundled) | `barcode-scanning:17.3.0`, `text-recognition:16.0.1` (100% offline) |
| **Camera** | CameraX 1.4.1 | Custom preview and frame analysis pipeline |
| **Background Work** | Android WorkManager 2.10.0 | Daily briefing notifications, nightly graph pruning |
| **Target SDK** | SDK 36 (Android 16) | Min SDK 26 (Android 8.0 Oreo) |

---

## App-Scoped Storage & Directory Architecture

All application data is isolated inside private app-scoped internal storage (`context.filesDir` and `context.getDatabasePath()`):

| Path | Description | Encryption / Format |
| :--- | :--- | :--- |
| `context.getDatabasePath("daybook.db")` | Core SQLite database storing tasks, meetings, logs, passes, chats, knowledge graph, and vector embeddings. | SQLCipher 256-bit AES encrypted |
| `context.getDatabasePath("daybook.db-wal")` | SQLite Write-Ahead Log journal. | Ephemeral database journal |
| `context.filesDir/daybook_db_passphrase.enc` | 256-bit database encryption passphrase. | Android Keystore AES-256-GCM |
| `context.filesDir/logs/daybook-app.log` | Active rolling execution log (auto-sanitized, no secrets). | Plaintext rolling log |
| `context.filesDir/logs/daybook-app.log.1` | Rotated execution log (created when active log exceeds 1 MB). | Plaintext rolling log |
| `context.filesDir/gemma-270m-it-q8-v1.litertlm` | On-device Gemma 3 270M IT model weights. | LiteRT-LM binary format |
| `context.filesDir/daybook_restore.tmp` | Atomic temporary staging file used during database restoration. | Ephemeral staging file |

---

## Getting Started & Build Instructions

### Prerequisites
- JDK 17 or higher
- Android SDK (API 36, Build Tools 36.0.0)

### 1. Build via Command Line
```bash
# Clone the repository
git clone https://github.com/fncreator22/Daybook-app.git
cd Daybook-app

# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Run Automated Unit Tests
```bash
# Windows
gradlew.bat testDebugUnitTest

# macOS / Linux
./gradlew testDebugUnitTest
```

### 3. Install on a Connected Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Verification & Automated Test Suite

Daybook maintains a test-driven development (TDD) discipline. The unit test suite covers domain logic, clause splitting, alarm receivers, database codecs, vector mathematics, and SAF encryption:

- **177 Unit Tests across 18 Test Suites (100% Pass Rate)**
- Test Suites include:
  - `NaturalLanguageParserTest`: Compound clause splitting, relative date resolution, time parsing, and conversational intent detection.
  - `TodayBuilderTest`: Bucket aggregation, timezone boundary handling, and active vs. archived pass segregation.
  - `CadenceEngineTest`: Recurrence scheduling, calendar ID clearance, and next occurrence calculation.
  - `BootReceiverTest`: Boot action filtering and pending alarm recalculation.
  - `DatabaseKeyManagerTest`: Key generation, test isolation, and SQLite header validation.
  - `VectorStoreTest`: Vector serialization, deserialization, and cosine distance math.
  - `WordPieceTokenizerTest`: Basic tokenization, greedy subword matching, and accent normalization.
  - `LocalBackupManagerTest`: AES-256-GCM export, restore, tampered ciphertext rejection, and magic header validation.
  - `DaybookLoggerTest`: Log persistence, 1 MB file rotation, and credential sanitization.

---

## License & Model Attribution

Daybook is licensed under the [Apache License, Version 2.0](LICENSE).

### Model Attribution
- **Gemma 3 270M IT** by Google DeepMind is governed by the [Gemma Terms of Use](https://ai.google.dev/gemma/terms).
- Daybook does not redistribute model binaries. Model downloads occur strictly on-device with explicit user consent.
