# Domain Docs — Daybook

## Layout: Single-Context

One `CONTEXT.md` at the repo root. ADRs in `docs/adr/`.

## Consumer Rules

Every agent session:

1. **Read `CONTEXT.md` first.** It is the canonical domain glossary and decision log. Use its terms verbatim — do not invent synonyms.
2. **Read relevant ADRs before touching their area.** Check `docs/adr/` for any record that covers the component you are modifying. An ADR overrides your defaults.
3. **Update `CONTEXT.md` when a grilling session or domain-modeling session produces new terms or changes existing ones.** Terms should be defined once, precisely, and referenced consistently everywhere else.
4. **Write a new ADR for every non-trivial architectural decision** made during implementation. Template: `docs/adr/TEMPLATE.md`.

## What Belongs in CONTEXT.md vs ADRs

| Content | Location |
|---------|----------|
| Domain terms, glossary, canonical names | `CONTEXT.md` |
| "We chose X over Y because Z" with context and consequences | `docs/adr/NNN-title.md` |
| Open design questions (not yet decided) | `CONTEXT.md` under `## Open Questions` |
| Phase-level backlog and task tracking | GitHub Issues |
