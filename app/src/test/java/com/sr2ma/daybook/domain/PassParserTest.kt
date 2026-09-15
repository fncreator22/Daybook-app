package com.sr2ma.daybook.domain

import com.sr2ma.daybook.domain.model.PassCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PassParserTest {

    // ── Title inference from OCR ──────────────────────────────────────────────

    @Test
    fun `first non-blank OCR line becomes suggested title`() {
        val result = PassParser.parse(
            barcodeValue = "ABC123",
            barcodeFormat = "QR_CODE",
            ocrText = "\n  \nCoffee Rewards\nMember since 2023",
        )
        assertEquals("Coffee Rewards", result.suggestedTitle)
    }

    @Test
    fun `blank OCR produces null suggested title`() {
        val result = PassParser.parse("ABC123", "QR_CODE", ocrText = "   \n  ")
        assertNull(result.suggestedTitle)
    }

    @Test
    fun `empty OCR produces null suggested title`() {
        val result = PassParser.parse("ABC123", "QR_CODE", ocrText = "")
        assertNull(result.suggestedTitle)
    }

    // ── Category heuristics ───────────────────────────────────────────────────

    @Test
    fun `PDF_417 suggests TRANSPORT`() {
        val result = PassParser.parse("BOARDING", "PDF_417")
        assertEquals(PassCategory.TRANSPORT, result.suggestedCategory)
    }

    @Test
    fun `AZTEC suggests TRANSPORT`() {
        val result = PassParser.parse("TICKET123", "AZTEC")
        assertEquals(PassCategory.TRANSPORT, result.suggestedCategory)
    }

    @Test
    fun `EAN_13 suggests LOYALTY_CARD`() {
        val result = PassParser.parse("1234567890123", "EAN_13")
        assertEquals(PassCategory.LOYALTY_CARD, result.suggestedCategory)
    }

    @Test
    fun `UPC_A suggests LOYALTY_CARD`() {
        val result = PassParser.parse("012345678905", "UPC_A")
        assertEquals(PassCategory.LOYALTY_CARD, result.suggestedCategory)
    }

    @Test
    fun `QR_CODE suggests OTHER`() {
        val result = PassParser.parse("https://example.com", "QR_CODE")
        assertEquals(PassCategory.OTHER, result.suggestedCategory)
    }

    @Test
    fun `unknown format suggests OTHER`() {
        val result = PassParser.parse("XYZ", "DATA_MATRIX")
        assertEquals(PassCategory.OTHER, result.suggestedCategory)
    }

    @Test
    fun `format matching is case-insensitive`() {
        val result = PassParser.parse("BOARDING", "pdf_417")
        assertEquals(PassCategory.TRANSPORT, result.suggestedCategory)
    }

    // ── Raw fields pass through unchanged ─────────────────────────────────────

    @Test
    fun `barcode value and format pass through unchanged`() {
        val result = PassParser.parse("RAW_VALUE", "QR_CODE", "some text")
        assertEquals("RAW_VALUE", result.barcodeValue)
        assertEquals("QR_CODE", result.barcodeFormat)
        assertEquals("some text", result.ocrText)
    }

    // ── Multi-barcode: pickLargest ────────────────────────────────────────────

    @Test
    fun `pickLargest returns index of barcode with biggest area`() {
        val barcodes = listOf(
            Triple("A", "QR_CODE", 400),
            Triple("B", "EAN_13", 900),
            Triple("C", "AZTEC", 200),
        )
        assertEquals(1, PassParser.pickLargest(barcodes))
    }

    @Test
    fun `pickLargest on empty list returns 0`() {
        assertEquals(0, PassParser.pickLargest(emptyList()))
    }

    @Test
    fun `pickLargest on single item returns 0`() {
        assertEquals(0, PassParser.pickLargest(listOf(Triple("X", "QR_CODE", 500))))
    }
}
