# ADR-0001: Stale Task Definition

**Status:** Accepted
**Date:** 2026-09-11
**Phase:** Phase 0 — Domain Modeling

## Context

`AgentEngine` needs a precise, implementable definition of "stale" to trigger `STALE_TASK` suggestions. Without a locked definition, different implementations could use different fields, thresholds, or status exclusions — leading to inconsistent behaviour across the app and tests.

## Decision

A Task is stale when **both** conditions are true:
1. `updated_at` is more than 7 days before the current timestamp
2. `status` is `OPEN` (not `IN_PROGRESS`, `DONE`, or `CANCELLED`)

`IN_PROGRESS` fully suppresses the stale nudge — an in-progress task is actively being worked on and is not stale by definition. `DONE` and `CANCELLED` are terminal states and are never stale.

The threshold is 7 days. No new columns are required — `updated_at` is already present on every Task row.

## Consequences

- `AgentEngine.computeSuggestions()` is a simple SQL predicate: `WHERE status = 'OPEN' AND updated_at < (now - 7 days)` — fast and testable.
- If a user edits any field on a task (including just changing priority), `updated_at` resets and the stale clock resets. This is the intended behaviour.
- Tasks where status is `IN_PROGRESS` will never surface a stale suggestion even if untouched for months. Acceptable trade-off — we trust the user's own status marking.

## Alternatives Considered

- **`last_viewed_at` column**: Rejected — requires tracking view events, adds write overhead on every navigation, and "viewed" is a weaker signal than "edited".
- **14-day threshold for IN_PROGRESS**: Rejected — adds complexity; suppression is simpler and clearer.
- **User-configurable threshold**: Rejected — YAGNI; add later if users ask.
