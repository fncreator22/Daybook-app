# ADR-0004: LLM Inference Queue Policy — Drop with Message

**Status:** Accepted
**Date:** 2026-09-11
**Phase:** Phase 0 — Domain Modeling

## Context

The `LlmEngineHolder` enforces one inference at a time via a `Mutex`. When a second request arrives (e.g. from quick-capture spam), a policy decision is needed: queue the request, drop it, or cancel the first.

## Decision

**Drop the new request immediately** and surface a visible, actionable message to the user: "AI is processing, try again in a moment."

No queuing. No silent wait. No cancellation of the in-flight request.

## Consequences

- Implementation is minimal: the `Mutex.tryLock()` path returns immediately if the lock is held; the calling coroutine returns a `Result.failure(BusyException)` which the ViewModel converts to a `UserMessage`.
- The user always has immediate, honest feedback that their second request was not processed. They can re-submit after the first finishes.
- In practice, Gemma 270M inference takes 2–8 seconds. The window during which a second request arrives and conflicts is narrow — dropping is not a significant UX regression.
- No queue means no hidden backlog — the user is never left wondering whether an old request is about to fire.

## Alternatives Considered

- **Bounded queue (depth 1)**: Rejected — the user submits a second capture, sees no immediate feedback, then gets a result 8–16 seconds later that they may have forgotten about. Deceptive timing.
- **Cancel the first, run the second**: Rejected — the first request was user-initiated. Silently discarding it to run a newer one is worse than discarding the newer one: the user may not realise their first submission was lost.
- **Unbounded queue**: Rejected immediately — OOM risk and unbounded latency.
