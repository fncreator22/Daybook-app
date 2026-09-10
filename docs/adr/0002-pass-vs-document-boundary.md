# ADR-0002: Pass vs Document Boundary

**Status:** Accepted
**Date:** 2026-09-11
**Phase:** Phase 0 — Domain Modeling

## Context

The Barcode Wallet (Phase 2) will store digitised physical items. Two categories emerged during design: items with machine-readable barcodes (loyalty cards, event tickets, transit passes) and items without (insurance certificates, receipts, warranty cards). The original `passes` table design required `barcode_value` and `barcode_format` as `NOT NULL`, implicitly encoding this boundary — but it was never made explicit in the domain model.

## Decision

**A Pass has a barcode. A Document does not.**

- A **Pass** is any digitised physical card or ticket from which ML Kit successfully decodes at least one barcode. The decoded barcode value and format are mandatory fields. Passes live in the `passes` table.
- A **Document** is any captured image or photo of physical paper that has no machine-readable barcode. Documents live in a separate `documents` table (Phase 2+ addition).
- The classification is made at capture time by `BarcodeAnalyser`. If ML Kit returns ≥1 barcode → Pass. If ML Kit returns 0 barcodes → Document (or user is warned and asked to confirm).

## Consequences

- The `passes` table keeps `barcode_value TEXT NOT NULL` and `barcode_format TEXT NOT NULL` — no nullable workaround needed.
- A separate `documents` table is out of scope for Phase 2 (barcode wallet) but must be reserved in the schema versioning plan so migration 7→8 doesn't conflict.
- The UI will show two separate sections in the Wallet tab: "Passes" (with barcode) and "Documents" (without).
- If ML Kit finds ambiguous results (partial decode, low confidence), the item is treated as a Document and the user is shown the raw OCR text to review.

## Alternatives Considered

- **Single table with `has_barcode` flag**: Rejected — optional `barcode_value` / `barcode_format` columns make the schema ambiguous and every query must guard the nulls.
- **Passes include everything scannable**: Rejected — a photographed prescription label might have a barcode but is semantically a document; the barcode is the classification signal, not semantics.
