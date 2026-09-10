# ADR-0003: Cadence Task Propagation on Completion

**Status:** Accepted
**Date:** 2026-09-11
**Phase:** Phase 0 — Domain Modeling

## Context

`CadenceEngine.onTaskCompleted()` must create the next instance of a recurring task when the current one is marked DONE. The exact set of fields to carry forward, and how to compute the next due date, were unspecified.

## Decision

When a cadenced task (cadence ≠ NONE) is marked DONE, `CadenceEngine` creates a new Task with:

**Fields carried forward:**
- `title`
- `notes` (standing context often applies to every recurrence)
- `priority`
- `project`
- `cadence`

**Fields reset:**
- `status` → `OPEN`
- `completed_at` → null

**Fields computed:**
- `due_date` → original task's `due_date` + cadence interval. The next due date is anchored to the **original due date**, not the completion date. If completed early (e.g. a weekly task due Friday, completed Tuesday), the next instance is still due the following Friday — not 7 days from Tuesday.
- `id` → new auto-generated id
- `cadence_parent_id` → the completed task's id (forms a chain)
- `created_at` → now, `updated_at` → now

**Not carried forward:**
- `completed_at` (reset to null)
- `status` (reset to OPEN)

## Consequences

- Notes carry forward — this is intentional. Recurring tasks often have standing notes ("always include Q-report", "bring the invoice"). The user clears notes manually when they don't apply.
- Due dates stay on-schedule even when tasks are completed early. This is the correct behaviour for time-anchored recurrences (weekly review, monthly report).
- `cadence_parent_id` creates a singly-linked chain. A full history chain can be reconstructed by following parent IDs. This is sufficient; a join table is not needed.
- If the completed task had no `due_date`, the next instance also has no `due_date` (the interval cannot be computed without an anchor).

## Alternatives Considered

- **Due date advances from completion date**: Rejected — breaks time-anchored cadences. A "weekly Monday review" completed on Saturday should still be due next Monday, not the following Saturday.
- **Only title + cadence carry forward (fresh start)**: Rejected — loses standing context that recurring tasks accumulate; user-visible regression.
- **Attachments carry forward**: Deferred — attachments are a Phase 2+ feature; revisit when implemented.
