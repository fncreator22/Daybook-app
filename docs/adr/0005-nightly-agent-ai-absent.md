# ADR-0005: NightlyAgentWorker Behaviour When AI Model Absent

**Status:** Accepted
**Date:** 2026-09-11
**Phase:** Phase 0 — Domain Modeling

## Context

`NightlyAgentWorker` (WorkManager, nightly) runs both rule-engine steps (cadence task creation, stale nudges, Drive sync, Calendar sync) and AI steps (LLM-based meeting summarisation, embedding re-indexing). When the user has declined the Gemma model download, what should the worker do?

## Decision

`NightlyAgentWorker` **runs all rule-engine steps regardless** of whether the Gemma model is present. AI-only steps are silently skipped — no notification is sent to the user when they are skipped.

The worker checks `ModelState.isDownloaded()` at the start of each AI step. If false, it skips that step and logs internally. The worker still reports `Result.success()` to WorkManager so it reschedules normally.

## Consequences

- The nightly agent delivers its full deterministic value (cadence tasks, stale nudges, sync) even when the user has never enabled AI.
- This enforces the project's core invariant: the app is fully functional without any AI features active.
- Silent skip (no notification) prevents notification fatigue. The user explicitly declined the download; telling them nightly that AI was skipped would be annoying.
- If the user downloads the model later, the next nightly run automatically picks it up — no reconfiguration needed.
- Testability: two test paths — `workerRunsAllStepsWhenModelPresent` and `workerRunsRuleEngineOnlyWhenModelAbsent`. Both are pure JUnit tests with a faked `ModelState`.

## Alternatives Considered

- **Skip entirely when model absent**: Rejected — loses all deterministic value (cadence tasks, Drive sync) for users who haven't opted into AI.
- **Notify user that AI was skipped**: Rejected — notification fatigue; the user made a deliberate choice. Nightly reminders to download are spam.
- **Prompt download from the worker**: Rejected — a background worker should never prompt UI. Download flow is always user-initiated from Settings.
