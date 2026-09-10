# Daybook — Domain Glossary & Context

> Canonical reference for all agents and humans. Use these terms verbatim. Do not invent synonyms.
> Updated: 2026-09-11 after Phase 0 grilling session.

---

## Core Entities

**Task** — A discrete unit of work with a title, optional due date, priority (`LOW`, `MEDIUM`, `HIGH`, `URGENT`), and status (`OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED`). A task belongs to at most one project.

**Meeting** — A scheduled or past interaction with one or more attendees. Has a date, optional time, optional notes, and an optional follow-up date. Not the same as a calendar event — a meeting is a first-class Daybook record; a calendar event is a system-level concept that may be linked via `calendar_event_id`.

**Log Entry** — A timestamped freeform note classified by kind: `NOTE`, `DECISION`, `BLOCKER`, or `WIN`. Immutable once saved.

**Pass** — A digitised physical card or ticket that has a **machine-readable barcode**. Stored in the internal Wallet. Has a barcode value + format, a category, and optional expiry/balance fields. A Pass always has a barcode — if there is no barcode, it is not a Pass.

**Document** — A captured image or photo of a physical paper (insurance certificate, receipt, warranty card) that has **no machine-readable barcode**. Distinct from a Pass. Stored separately. Phase 2+ feature.

**Agent Suggestion** — A proactive nudge produced by `AgentEngine` after each data refresh. Displayed as dismissible cards on the Today screen. Never modifies data autonomously.

---

## Key Distinctions

**Pass vs Document** — The boundary is the barcode. If ML Kit can decode a barcode from the captured image, it is a **Pass**. If not, it is a **Document**. These go into separate tables. (ADR-0002)

**Calendar Event vs Meeting** — A calendar event is read from the device's `CalendarContract` (may be Google Calendar, Outlook, Exchange). A Daybook Meeting is a record created inside Daybook. They are separate concepts that may be linked via `calendar_event_id`.

**Voice Input vs LLM** — Voice input (`SpeechRecognizer`) converts audio to text. The LLM (`Gemma 270M`) understands and classifies text. These are entirely separate pipeline stages. Voice input does not imply LLM use; LLM use does not imply voice input.

**Rule Engine vs LLM** — The rule engine (`AgentEngine`, `NaturalLanguageParser`, `CadenceEngine`, `BriefingWriter`) makes routing and scheduling decisions deterministically. The LLM is called only for unstructured→structured transformation. The rule engine always runs; the LLM runs only if the model is downloaded.

---

## Stale Task (Locked — ADR-0001)

A Task is **stale** when `updated_at` is more than **7 days ago** AND status is not `IN_PROGRESS`.

- `IN_PROGRESS` **suppresses** the stale nudge entirely — an in-progress task is not stale.
- `DONE` and `CANCELLED` tasks are never stale (they are terminal states).
- Only `OPEN` tasks older than 7 days (by `updated_at`) trigger a `STALE_TASK` AgentSuggestion.
- No new columns needed — `updated_at` already exists.

---

## Cadence (Locked — ADR-0003)

The `cadence` field on a Task controls automatic recurrence: `NONE`, `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`.

**When a cadenced task is marked DONE**, `CadenceEngine.onTaskCompleted()` creates a new Task instance with:
- Same: `title`, `notes`, `priority`, `project`, `cadence`
- Reset: `status` → `OPEN`, `completed_at` → null
- Advanced: `due_date` → original `due_date` + cadence interval (e.g. +7 days for WEEKLY). If the task was completed early, the next due date is still anchored to the original — not the completion date.
- New: `id` (auto-generated), `cadence_parent_id` → the completed task's id, `created_at` → now

Notes carry forward. The user clears them manually if unwanted.

---

## Inference Queue Policy (Locked — ADR-0004)

When a second LLM inference request arrives while one is in-flight: **drop** the new request immediately and show a visible "AI is processing, try again in a moment" message.

No queuing. No silent wait. The user always knows why their request wasn't processed.

---

## NightlyAgentWorker + AI Absent (Locked — ADR-0005)

When the Gemma model has not been downloaded, `NightlyAgentWorker`:
- **Runs** all rule-engine steps (cadence task creation, staleness suggestions, Drive sync, Calendar sync)
- **Silently skips** AI-only steps (LLM summarisation, capture-and-route classification)
- Does **not** notify the user that AI was skipped

The nightly agent's core value is deterministic — it does not require the model.

---

## Architecture

```
Presentation  →  Domain  →  Data
                               └── SQLite (SQLCipher, encrypted)
                               └── sqlite-vec (vector extension, ARM64 .so)

AI Services (independent, opt-in):
  LlmEngine       (LiteRT-LM, Gemma 270M INT4, lazy-loaded, idle-released)
  EmbeddingEngine (ONNX Runtime, MiniLM-L6-v2 INT8, ~22 MB, always on)

Background (WorkManager):
  NightlyAgentWorker    (00:00, charging preferred)
  DriveSyncWorker       (WiFi, 6h)
  CalendarSyncWorker    (network, 1h)
  EmbeddingIndexWorker  (30s after any save)
  ModelDownloadWorker   (one-time, user-consented)
```

---

## ADR Index

| # | Title | Status |
|---|-------|--------|
| ADR-0001 | Stale task = 7 days by updated_at, IN_PROGRESS suppresses | Accepted |
| ADR-0002 | Pass vs Document boundary is the barcode | Accepted |
| ADR-0003 | Cadence propagation: notes forward, due date from original | Accepted |
| ADR-0004 | Inference queue: drop with message, no queuing | Accepted |
| ADR-0005 | NightlyAgentWorker runs rule-engine steps regardless of AI | Accepted |

---

## Open Questions

*(None — Phase 0 grilling complete.)*
