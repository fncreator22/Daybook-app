package com.sr2ma.daybook.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class WordPieceTokenizerTest {

    private val tokenizer: WordPieceTokenizer by lazy {
        val vocabFile = File("src/main/assets/vocab.txt")
        if (vocabFile.exists()) {
            FileInputStream(vocabFile).use { WordPieceTokenizer.fromInputStream(it) }
        } else {
            WordPieceTokenizer.defaultFallback()
        }
    }

    @Test
    fun `basic tokenization splits words and preserves punctuation`() {
        val tokens = tokenizer.basicTokenize("Hello, world! Daybook 2026.")
        assertEquals(listOf("hello", ",", "world", "!", "daybook", "2026", "."), tokens)
    }

    @Test
    fun `tokenizes subwords with double hash prefix`() {
        // "daybook" splits into "day" and "##book"
        val subTokens = tokenizer.wordPieceTokenize("daybook")
        assertTrue(subTokens.size >= 2)
        // With real MiniLM/BERT vocab, day=2154, ##book=8654
        assertEquals(2154L, subTokens[0])
        assertEquals(8654L, subTokens[1])
    }

    @Test
    fun `formats sequence with CLS and SEP tokens and valid attention mask`() {
        val (inputIds, attentionMask, tokenTypeIds) = tokenizer.tokenize("urgent task", maxTokens = 10)

        assertEquals(WordPieceTokenizer.CLS_ID, inputIds[0])
        assertEquals(WordPieceTokenizer.SEP_ID, inputIds[inputIds.size - 1])
        assertEquals(inputIds.size, attentionMask.size)
        assertEquals(inputIds.size, tokenTypeIds.size)

        // All attention mask entries must be 1L
        assertTrue(attentionMask.all { it == 1L })
        // All token type IDs must be 0L
        assertTrue(tokenTypeIds.all { it == 0L })
    }

    @Test
    fun `truncates long text without exceeding maxTokens`() {
        val longText = (1..50).joinToString(" ") { "task number $it" }
        val maxLen = 16
        val (inputIds, attentionMask, tokenTypeIds) = tokenizer.tokenize(longText, maxTokens = maxLen)

        assertTrue(inputIds.size <= maxLen)
        assertEquals(WordPieceTokenizer.CLS_ID, inputIds[0])
        assertEquals(WordPieceTokenizer.SEP_ID, inputIds[inputIds.size - 1])
        assertEquals(inputIds.size, attentionMask.size)
        assertEquals(inputIds.size, tokenTypeIds.size)
    }

    @Test
    fun `handles empty and whitespace input gracefully`() {
        val (inputIds, mask, _) = tokenizer.tokenize("    ")
        assertEquals(2, inputIds.size)
        assertEquals(WordPieceTokenizer.CLS_ID, inputIds[0])
        assertEquals(WordPieceTokenizer.SEP_ID, inputIds[1])
        assertEquals(2, mask.size)
    }

    @Test
    fun `default fallback tokenizer operates offline without vocab file`() {
        val fallback = WordPieceTokenizer.defaultFallback()
        val (ids, mask, types) = fallback.tokenize("daybook meeting", maxTokens = 10)

        assertEquals(WordPieceTokenizer.CLS_ID, ids[0])
        assertEquals(WordPieceTokenizer.SEP_ID, ids[ids.size - 1])
        assertTrue(ids.size > 2)
        assertTrue(mask.all { it == 1L })
        assertTrue(types.all { it == 0L })
    }

    @Test
    fun `strips unicode accents during basic tokenization`() {
        val tokens = tokenizer.basicTokenize("Café résumé naïve")
        assertEquals(listOf("cafe", "resume", "naive"), tokens)
    }
}
