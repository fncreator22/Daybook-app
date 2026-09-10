# Daybook — Domain Glossary & Context

> Maintained by agents and humans together. Use these terms verbatim throughout the codebase, specs, and issues. Do not invent synonyms. Update this file when grilling sessions produce new or changed definitions.

---

## Core Entities

**Task** — A discrete unit of work with a title, optional due date, priority (`LOW`, `MEDIUM`, `HIGH`, `URGENT`), and status (`OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED`). A task belongs to at most one project.

**Meeting** — A scheduled or past interaction with one or more attendees. Has a date, optional time, optional notes, and an optional follow-up date. Not the same as a calendar event — a meeting is a first-class Daybook record; a calendar event is a system-level concept that may be linked.

**Log Entry** — A timestamped freeform note classified by kind: `NOTE`, `DECISION`, `BLOCKER`, or `WIN`. Immutable once saved (edit creates a new entry; delete is a hard delete).

**Pass** — A digitised physical card or ticket stored in Daybook's internal wallet. Has a barcode value + format, a category, and optional expiry/balance fields. Not the same as a Google Wallet pass (a Google Wallet pass is a push to the system wallet; a Daybook pass is always stored locally regardless of whether a Google Wallet push has occurred).

**Agent Suggestion** — A proactive nudge produced by `AgentEngine` after each data refresh. Displayed as dismissible cards on the Today screen. Never modifies data autonomously — always presents a suggestion for the user to act on.

---

## Key Distinctions

**Pass vs Document** — *To be resolved in Phase 0 domain-modeling grilling session.*

**Calendar Event vs Meeting** — A calendar event is read from the device's `CalendarContract` (may be Google Calendar, Outlook, Exchange). A Daybook meeting is a record created inside Daybook. They can be linked (a meeting may have a `calendar_event_id`), but they are separate concepts.

**Voice Input vs LLM** — Voice input (`SpeechRecognizer`) converts audio to text. The LLM (`Gemma 270M`) understands and classifies text. These are separate pipeline stages. Voice input does not imply LLM use; LLM use does not imply voice input.

**Rule Engine vs LLM** — The rule engine (`AgentEngine`, `NaturalLanguageParser`, `CadenceEngine`, `BriefingWriter`) makes routing and scheduling decisions deterministically. The LLM is called only for unstructured→structured transformation steps. The rule engine always runs; the LLM only runs when the model is downloaded and the user has opted in.

---

## Cadence (Open — Phase 0)

The `cadence` field on a Task controls automatic recurrence: `NONE`, `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`. When a cadenced task is completed, a new instance is created automatically.

**Open questions (resolve in Phase 0 grilling):**
- Which fields carry forward to the new instance?
- Does completing a future-dated cadenced task early trigger the next instance from the original due date or from the completion date?

---

## Stale (Open — Phase 0)

A task is "stale" when it has not been updated in N days. Triggers an `AgentSuggestion` of type `STALE_TASK`.

**Open questions (resolve in Phase 0 grilling):**
- What is N? (candidate: 7 days)
- Does updating the task's status reset the clock?
- Does `IN_PROGRESS` status suppress or delay the stale nudge?

---

## Inference Queue Policy (Open — Phase 0)

When a second capture-and-route request arrives while an inference is in-flight:

**Open question (resolve in Phase 0 grilling):**
- Does the new request wait in queue (bounded queue — pick max depth), or is the new request dropped with a "busy" message to the user?

---

## Nightly Agent + AI Declined (Open — Phase 0)

When the user has not downloaded the Gemma model:

**Open question (resolve in Phase 0 grilling):**
- Does `NightlyAgentWorker` run rule-engine steps only (silently skipping AI steps), or does it skip entirely when the model is absent?

---

## Architecture

```
Presentation  →  Domain  →  Data
                               └── SQLite (SQLCipher)
                               └── sqlite-vec (vector extension)

AI Services (independent, opt-in):
  LlmEngine  (LiteRT-LM, Gemma 270M, lazy-loaded)
  EmbeddingEngine  (ONNX Runtime, MiniLM-L6-v2, always-loaded if ONNX present)

Background:
  WorkManager workers: NightlyAgentWorker, DriveSyncWorker, CalendarSyncWorker,
                       EmbeddingIndexWorker, ModelDownloadWorker
```

---

## ADRs

| # | Title | Status |
|---|-------|--------|
| — | *(none yet — to be created in Phase 0)* | — |

---

## Open Questions

*(Answered questions are moved to the relevant ADR or entity definition above.)*

1. Pass vs Document boundary — what is the canonical definition?
2. Cadence field propagation on task completion
3. Stale threshold and clock-reset rules
4. Inference queue policy (wait vs drop)
5. NightlyAgentWorker behavior when AI model absent
