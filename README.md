# Daybook

A lightweight, modern, and privacy-first personal productivity and journaling application for Android, built with **Jetpack Compose** and **Material 3**.

---

## Overview

Daybook combines your daily agenda, action items, meeting notes, and daily reflections into a single unified workspace:

- **Today Dashboard:** Unified view of overdue tasks, today's schedule, immediate meeting touchpoints, and quick notes.
- **Tasks:** Prioritized task management with due dates, cadences, projects, and instant search/filtering.
- **Meetings:** Meeting tracking with attendees, discussion summaries, action items, and next touchpoints.
- **Log:** Daily micro-journaling and reflection entries with timeline navigation.
- **Backup & Restore:** Full offline JSON export and import for user data sovereignty.

---

## Key Highlights

- **100% Offline & Private:** Zero network permissions requested in AndroidManifest.xml. All data stays strictly on your local device.
- **Pure Jetpack Compose & Material 3:** Modern declarative UI with edge-to-edge styling, dynamic theming, and light-theme system bar management.
- **Robust Local Storage:** Direct SQLite integration with foreign key constraints, cascade handling, and 6 optimized indexes.
- **Clean Architecture:** Strict separation between Data (DaybookDatabase, DAOs, DaybookRepository), Domain (TodayBuilder, Queries, Dates), and UI (DaybookViewModel, StateFlow, Composables).
- **Automated CI/CD:** GitHub Actions workflow (.github/workflows/build-apk.yml) that builds debug APKs in the cloud and runs unit tests.
- **Standalone Build Scripts:** Build without Android Studio via uild-apk.bat (Windows) or scripts/build-apk.sh (macOS/Linux).
- **Custom Verification Suites:** Includes offline Kotlin static verification (scripts/verify_kotlin.py) and SQLite schema validation (scripts/verify_sql.py).

---

## Getting Started & Installation

Refer to **[INSTALL.md](INSTALL.md)** for detailed instructions on:
1. Building the APK in the cloud via GitHub Actions (install nothing locally).
2. Building and running with Android Studio.
3. Building locally via command-line scripts.
4. Installing the APK on your Android device.

---

## Tech Stack & Toolchain

- **Language:** Kotlin 2.2.20
- **Android Gradle Plugin (AGP):** 8.13.0
- **Min SDK:** 26 (Android 8.0 Oreo) | **Target & Compile SDK:** 36 (Android 16)
- **UI:** Jetpack Compose (BOM 2025.09.00), Material 3
- **Async:** Kotlinx Coroutines 1.9.0
- **Database:** Android SQLite OpenHelper with custom DAOs
- **Testing:** JUnit 4, org.json, Coroutines Test
