# Daybook — Agent Operating Manual

> Read this entire file before touching any code. It is short on purpose.

---

## 1. What Daybook Is

A 100% offline-first personal productivity app (tasks, meetings, daily log) being upgraded with
on-device agentic AI features. The non-negotiable invariant: **the app must be fully functional
with zero AI features active.** All AI, network, and cloud features are opt-in.

Current state: shipping, green CI, clean MVVM / clean-architecture Kotlin / Compose codebase.
No DI framework. No Room (deliberate — hand-written `SQLiteOpenHelper` DAOs). No network
permissions in the current manifest (network will be added, gated behind opt-in flows).

---

## 2. Locked Stack

| Layer | Choice | Real size | Notes |
|-------|--------|-----------|-------|
| LLM | Gemma 3 270M IT, INT4 QAT, via LiteRT-LM | ~250–300 MB | Optional post-install download only — never bundled in APK. Verify actual `.litertlm` artifact size before committing any headline figure. Source: `litert-community/gemma-3-270m-it` on Hugging Face. |
| Embeddings | ONNX MiniLM-L6-v2, INT8 | ~22 MB | Bundled in APK assets. Offline. |
| Vector store | sqlite-vec | ~1 MB | Pre-compiled ARM64 `.so` in `jniLibs/`. KNN SQL queries. |
| Barcode/OCR | ML Kit bundled: `barcode-scanning:17.3.0`, `text-recognition:16.0.1` | ~3.6 MB APK | Fully offline. Bundled model — not the Play Services unbundled variant. |
| DB encryption | SQLCipher 4.5.6 | — | All tables encrypted. Key in Android Keystore. |
| Rule engine | Plain Kotlin: `NaturalLanguageParser`, `BriefingWriter`, `CadenceEngine`, `AgentEngine` | 0 MB | Deterministic. LLM never routes. |
| Voice input | `SpeechRecognizer.createOnDeviceSpeechRecognizer()` (Android 13+) | 0 MB | OS-native. Offline. Converts audio → text only. Unrelated to the LLM. |
| Voice output | `TextToSpeech` (Android built-in) | 0 MB | OS-native. Offline. |

**Not doing:** LoRA fine-tuning, bundling Whisper, Room, Hilt, any network feature that isn't
behind an explicit user opt-in.

---

## 3. The LLM Is Called Exactly Here — Nowhere Else

```
User action / background trigger
        │
        ▼
AgentEngine / NaturalLanguageParser   ← always runs, deterministic, zero cost
        │
        ├── Handled by rules alone → output (no LLM call)
        │
        └── Needs language understanding:
              capture-and-route classification
              unstructured note → structured fields
              meeting note summarisation
              search relevance re-ranking
                        │
                        ▼
                 LlmEngine.infer()   ← called ONLY here
```

The rule engine decides **what to do**. The LLM generates **unstructured→structured output**.

---

## 4. Mandatory Self-Review Checklist

Run this after generating **any** non-trivial piece of code — not just at end of a phase:

1. **Is this actually required?** Could the feature be satisfied by existing rule-engine logic,
   an OS API, or simply not building it? If yes — don't write it.

2. **Does a well-maintained library already solve this?** Before hand-rolling logic (parsing,
   vector math, JSON schema validation, etc.), check for an existing, maintained package.
   Prefer the library unless there is a concrete, stated reason not to (size, license, staleness).

3. **Is this the simplest correct implementation?** Not the cleverest — the one a reviewer can
   verify fastest. If a simpler version passes the same tests, prefer it.

---

## 5. LLM Engine Non-Negotiables

- **Lazy-load:** create `Engine` on first AI-feature use, never at app start.
- **Idle release:** close and null the engine after 3 minutes of no inference calls.
- **Single-inference queue:** one `Mutex` guards all inference; concurrent calls queue, never run parallel.
- **Hard timeout:** 30-second `withTimeout` on every `infer()` call.
- **Storage pre-check:** require >250 MB free before offering the download. If check fails, surface an actionable error — do not silently skip.
- **Download safety:** download to `model.litertlm.tmp`, SHA-256 verify against published hash, then atomic `rename()`. Never load a partial file.
- **Model versioning:** new model version → user-prompted update (not silent). Versioned filenames (`gemma-270m-it-q4-v{N}.litertlm`) so old and new can coexist during transition.

---

## 6. Security

- **SQLCipher** replaces `android.database.sqlite.*` everywhere. Drop-in API, zero DAO changes.
- Encryption key = 256-bit random, generated once, stored in **Android Keystore** only.
- The `passes` table holds health IDs, driver's licences, passport barcodes. Plaintext is not acceptable.
- OAuth tokens → `EncryptedSharedPreferences` backed by Android Keystore.
- No `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`. Everything in app-scoped internal storage.

---

## 7. Model Licensing

Gemma Terms of Use apply (not Apache 2.0). Required:
- Attribution in Settings → About: `"Gemma 270M by Google DeepMind — gemma.google.com/terms"`
- Do not redistribute the model file itself; distribute only the download URL.

If a newer/smaller model is proposed as a swap (e.g. a Qwen3 or LFM2 build): report real file
size, license terms, and a sample accuracy comparison before switching. Do not swap silently.

---

## 8. Phased Backlog & Skill Map

All skills are installed in `.agents/skills/`.

### Before anything (run once)

```
/setup-matt-pocock-skills
```

Sets up issue tracker config (`docs/agents/issue-tracker.md`), triage label vocabulary, and
domain-doc layout. Required before `triage`, `to-spec`, `to-tickets` will work correctly.

### Phase 0 — Domain modeling (before any schema or entity work)

**Skills: `domain-modeling` + `grill-with-docs`**

Must resolve before writing code:

| Question | Why it matters |
|----------|---------------|
| What exactly counts as "stale" for a task suggestion? (days since last edit? last view? does IN_PROGRESS reset the clock?) | Gates `AgentEngine` staleness rule |
| What is the canonical definition of a "pass" vs a "document"? | Gates `passes` table category enum |
| When a recurring task completes, which fields copy to the next instance? | Gates `CadenceEngine.onTaskCompleted()` |
| "One inference at a time" — what happens when a second capture-and-route fires mid-flight? Wait in queue or drop? | Gates `LlmEngineHolder` queue policy |
| When AI download is declined, does `NightlyAgentWorker` skip AI steps silently (rule-engine steps only) or skip entirely? | Gates `NightlyAgentWorker` design |

Answers become ADRs in `docs/adr/`. Update `CONTEXT.md` as terms land.

### Phase 1 — Intelligence (pure logic, zero new dependencies)

**Skills: `to-spec` → `tdd`**

Features: `NaturalLanguageParser`, `BriefingWriter`, `CadenceEngine`, `AgentEngine`

These are pure functions (input → output, no I/O, no Android) — ideal TDD candidates.
Generate spec with `to-spec` first. Write failing test before implementation. Do not start
Phase 2 until Phase 1 is green and merged.

### Phase 2 — Barcode Wallet (ML Kit + CameraX)

**Skills: `grill-with-docs` → `to-spec` → `tdd`**

Grill first. Key open questions:
- What happens when ML Kit finds two barcodes in one frame?
- Can the user scan from a screenshot (static image from Gallery)?
- Is `title` mandatory or auto-inferred from OCR?
- What category shows when classification is ambiguous?

TDD the pure parsing logic (`PassParser`, `BarcodeAnalyser`). Wire camera last.

### Phase 3 — On-device AI (LiteRT-LM + ONNX)

**Skills: `diagnosing-bugs` (OOM, tokenizer mismatch, ANR, corrupted model) + `prototype` (prompt tuning)**

Do not start until Phase 1 is green. Use `diagnosing-bugs` for every model-integration failure
mode — do not improvise fixes. Use `prototype` to tune capture-and-route classification prompts
before the real wiring lands.

### Phase 4 — Google Drive / Calendar

**Skills: `wizard` (OAuth console steps — human-only) + `to-spec` (sync conflict policy)**

The OAuth console steps cannot be automated. Use `wizard` to produce a step-by-step script for
the human to follow (create Android client ID, Web client ID, enable APIs, register in Wallet
console). `to-spec` for the offline-first sync conflict policy (last-write-wins is the default
decision — confirm or change it via grilling before implementing).

### Phase 5 — Google Wallet (official passes)

**Skills: `grill-with-docs`**

Requires a backend server to sign JWTs securely. Grill first: confirm backend budget/plan exists
before writing a single line. If no backend plan, defer Phase 5 and ship the internal barcode
wallet (Phase 2) only.

### Phase 6 — Capture-and-route + voice input

**Skills: `prototype` → `tdd` → `to-spec`**

Voice input is `SpeechRecognizer` only — audio→text. The LLM classification of what to do with
the text is Phase 3 work. These two pipeline stages must be designed and tested independently.
Prototype the classification prompt first. TDD the JSON-response parser. Wire up last.

### Ongoing — Backlog management

**Skill: `triage`**

After each phase: run `triage` to move completed issues, re-evaluate blocked ones, and create
`ready-for-agent` briefs for the next phase's tasks.

---

## 9. Do-Not List

- Do not let the LLM make routing or decision logic — rule engine only.
- Do not store passes / health IDs / passport data unencrypted (SQLCipher is mandatory).
- Do not skip the >250 MB free-storage check before offering the model download.
- Do not bundle the LLM in the APK — download-only, post-install, user-consented.
- Do not build a LoRA adapter — explicitly descoped.
- Do not use `play-services-mlkit-barcode-scanning` (unbundled) — use `com.google.mlkit:barcode-scanning` (bundled).
- Do not start Phase 2 until Phase 1 is green.
- Do not start Phase 3 (LLM integration) until Phase 1 is green.
- Do not add `fallbackToDestructiveMigration()` to any database builder — ever.
- Do not write to external storage — all files in app-scoped `context.filesDir` or `context.getDatabasePath()`.

---

## Agent skills

### Issue tracker

GitHub Issues on `fncreator22/Daybook-app`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: `CONTEXT.md` at repo root, ADRs in `docs/adr/`. See `docs/agents/domain.md`.
