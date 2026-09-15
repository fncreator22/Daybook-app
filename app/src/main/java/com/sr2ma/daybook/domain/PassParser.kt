package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.PassCategory

/**
 * The result of analysing a scan — either a decoded barcode or nothing.
 *
 * [barcodeValue] and [barcodeFormat] come directly from ML Kit.
 * [ocrText] is the raw OCR output from the same frame (may be empty).
 * [suggestedTitle] is the first non-blank OCR line, or null if OCR was empty.
 * [suggestedCategory] is a heuristic based on barcode format (ADR-0004 grilling Q4).
 */
data class ScanResult(
    val barcodeValue: String,
    val barcodeFormat: String,
    val ocrText: String = "",
    val suggestedTitle: String? = null,
    val suggestedCategory: PassCategory = PassCategory.OTHER,
)

/**
 * Pure parser: turns raw ML Kit barcode + OCR output into a [ScanResult].
 *
 * No Android deps. No I/O. [BarcodeFormat] strings match ML Kit's
 * Barcode.FORMAT_* constant names (e.g. "QR_CODE", "PDF_417", "AZTEC").
 *
 * Category heuristic (per Phase 2 grilling — Q4 recommendation B):
 *   PDF_417 → TRANSPORT or ID (shown in picker, pre-selected)
 *   AZTEC   → TRANSPORT
 *   QR_CODE → OTHER (widest use)
 *   EAN_13 / UPC_A → LOYALTY_CARD (retail)
 *   everything else → OTHER
 *
 * The UI always shows the category picker so the user confirms or overrides.
 */
object PassParser {

    /**
     * @param barcodeValue  raw decoded barcode string from ML Kit
     * @param barcodeFormat ML Kit format constant name (e.g. "QR_CODE")
     * @param ocrText       full OCR text from the same camera frame; may be blank
     */
    fun parse(
        barcodeValue: String,
        barcodeFormat: String,
        ocrText: String = "",
    ): ScanResult {
        val title = firstOcrLine(ocrText)
        val category = inferCategory(barcodeFormat)
        return ScanResult(
            barcodeValue = barcodeValue,
            barcodeFormat = barcodeFormat,
            ocrText = ocrText,
            suggestedTitle = title,
            suggestedCategory = category,
        )
    }

    /**
     * When the camera finds multiple barcodes, pick the one with the largest
     * bounding-box area — that is the barcode the user pointed the lens at.
     * [areas] is a list of (barcodeValue, barcodeFormat, pixelArea) triples.
     * Returns the index of the winner, or 0 if the list is empty.
     */
    fun pickLargest(areas: List<Triple<String, String, Int>>): Int {
        if (areas.isEmpty()) return 0
        return areas.indexOfFirst { it.third == areas.maxOf { t -> t.third } }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun firstOcrLine(ocrText: String): String? =
        ocrText.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }

    private fun inferCategory(format: String): PassCategory = when (format.uppercase()) {
        "PDF_417"        -> PassCategory.TRANSPORT  // boarding passes, transit
        "AZTEC"          -> PassCategory.TRANSPORT  // transit tickets
        "EAN_13", "UPC_A" -> PassCategory.LOYALTY_CARD
        "EAN_8", "UPC_E" -> PassCategory.LOYALTY_CARD
        else             -> PassCategory.OTHER
    }
}
