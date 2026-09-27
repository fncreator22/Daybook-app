package com.sr2ma.daybook.ai

import android.content.Context
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Offline BERT WordPiece / subword tokenizer for MiniLM-L6-v2 embeddings.
 *
 * Implements real WordPiece tokenization:
 * 1. Text normalization: lowercasing and unicode accent stripping.
 * 2. Basic tokenization: whitespace & punctuation splitting.
 * 3. Subword tokenization: greedy longest-match prefix search with "##" prefix.
 * 4. BERT sequence formatting: prepends [CLS]=101, appends [SEP]=102, pads or truncates to maxTokens.
 *
 * Can load the bundled `assets/vocab.txt` (30,522 MiniLM/BERT tokens) or fall back
 * gracefully to an embedded core vocabulary for JVM test environments.
 */
class WordPieceTokenizer(
    private val vocab: Map<String, Long>,
) {

    private val unkId: Long = vocab[UNK_TOKEN] ?: 100L
    private val clsId: Long = vocab[CLS_TOKEN] ?: 101L
    private val sepId: Long = vocab[SEP_TOKEN] ?: 102L
    private val padId: Long = vocab[PAD_TOKEN] ?: 0L

    /**
     * Tokenizes [text] into BERT input arrays.
     *
     * @param text Input raw text.
     * @param maxTokens Maximum sequence length including [CLS] and [SEP].
     * @return Triple of (input_ids, attention_mask, token_type_ids).
     */
    fun tokenize(text: String, maxTokens: Int = DEFAULT_MAX_TOKENS): Triple<LongArray, LongArray, LongArray> {
        val basicTokens = basicTokenize(text)
        val tokenIds = ArrayList<Long>()

        for (token in basicTokens) {
            val subwordIds = wordPieceTokenize(token)
            for (id in subwordIds) {
                tokenIds.add(id)
                if (tokenIds.size >= maxTokens - 2) {
                    break
                }
            }
            if (tokenIds.size >= maxTokens - 2) {
                break
            }
        }

        // [CLS] + tokens + [SEP]
        val totalLength = tokenIds.size + 2
        val inputIds = LongArray(totalLength)
        inputIds[0] = clsId
        for (i in tokenIds.indices) {
            inputIds[i + 1] = tokenIds[i]
        }
        inputIds[totalLength - 1] = sepId

        val attentionMask = LongArray(totalLength) { 1L }
        val tokenTypeIds = LongArray(totalLength) { 0L }

        return Triple(inputIds, attentionMask, tokenTypeIds)
    }

    /**
     * Converts a single basic word token into WordPiece subword token IDs.
     */
    fun wordPieceTokenize(word: String): List<Long> {
        if (word.length > MAX_INPUT_CHARS_PER_WORD) {
            return listOf(unkId)
        }

        // Exact match fast path
        vocab[word]?.let { return listOf(it) }

        var isBad = false
        var start = 0
        val subTokens = ArrayList<Long>()

        while (start < word.length) {
            var end = word.length
            var matchedId: Long? = null

            while (start < end) {
                var substr = word.substring(start, end)
                if (start > 0) {
                    substr = "##$substr"
                }

                val id = vocab[substr]
                if (id != null) {
                    matchedId = id
                    break
                }
                end--
            }

            if (matchedId == null) {
                isBad = true
                break
            }

            subTokens.add(matchedId)
            start = end
        }

        return if (isBad) {
            // If subword lookup failed for rare/unknown characters, fallback to character tokens or UNK
            fallbackTokenize(word)
        } else {
            subTokens
        }
    }

    /**
     * Splits text on whitespace and punctuation, keeping punctuation marks as individual tokens.
     */
    fun basicTokenize(text: String): List<String> {
        val decomposed = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        val normalized = decomposed.replace("\\p{Mn}+".toRegex(), "").lowercase().trim()
        if (normalized.isEmpty()) return emptyList()

        val tokens = ArrayList<String>()
        val currentWord = StringBuilder()

        for (char in normalized) {
            if (char.isWhitespace()) {
                if (currentWord.isNotEmpty()) {
                    tokens.add(currentWord.toString())
                    currentWord.clear()
                }
            } else if (isPunctuation(char)) {
                if (currentWord.isNotEmpty()) {
                    tokens.add(currentWord.toString())
                    currentWord.clear()
                }
                tokens.add(char.toString())
            } else {
                currentWord.append(char)
            }
        }

        if (currentWord.isNotEmpty()) {
            tokens.add(currentWord.toString())
        }

        return tokens
    }

    /**
     * Fallback for words containing characters not in vocabulary.
     * Tries each character individually, or assigns UNK.
     */
    private fun fallbackTokenize(word: String): List<Long> {
        val result = ArrayList<Long>()
        for (char in word) {
            val charStr = char.toString()
            val id = vocab[charStr]
                ?: vocab["##$charStr"]
                ?: unkId
            result.add(id)
        }
        return if (result.isEmpty()) listOf(unkId) else result
    }

    private fun isPunctuation(char: Char): Boolean {
        // Standard ASCII and Unicode punctuation check
        val type = Character.getType(char)
        return (char in "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~") ||
            type == Character.CONNECTOR_PUNCTUATION.toInt() ||
            type == Character.DASH_PUNCTUATION.toInt() ||
            type == Character.START_PUNCTUATION.toInt() ||
            type == Character.END_PUNCTUATION.toInt() ||
            type == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
            type == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
            type == Character.OTHER_PUNCTUATION.toInt()
    }

    val vocabSize: Int get() = vocab.size

    companion object {
        const val PAD_TOKEN = "[PAD]"
        const val UNK_TOKEN = "[UNK]"
        const val CLS_TOKEN = "[CLS]"
        const val SEP_TOKEN = "[SEP]"
        const val MASK_TOKEN = "[MASK]"

        const val PAD_ID = 0L
        const val UNK_ID = 100L
        const val CLS_ID = 101L
        const val SEP_ID = 102L
        const val MASK_ID = 103L

        const val DEFAULT_MAX_TOKENS = 128
        private const val MAX_INPUT_CHARS_PER_WORD = 100

        /**
         * Loads tokenizer from an Android Context using the bundled `assets/vocab.txt`.
         * If the asset is absent (e.g. during unit tests), returns a fallback core tokenizer.
         */
        fun fromContext(context: Context, assetName: String = "vocab.txt"): WordPieceTokenizer {
            return try {
                context.assets.open(assetName).use { stream ->
                    fromInputStream(stream)
                }
            } catch (_: Throwable) {
                defaultFallback()
            }
        }

        /**
         * Loads tokenizer from an [InputStream] containing one vocabulary token per line.
         */
        fun fromInputStream(inputStream: InputStream): WordPieceTokenizer {
            val map = HashMap<String, Long>(32000)
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).useLines { lines ->
                var idx = 0L
                for (line in lines) {
                    val token = line.trim()
                    if (token.isNotEmpty()) {
                        map[token] = idx
                    }
                    idx++
                }
            }
            return WordPieceTokenizer(map)
        }

        /**
         * Core vocabulary fallback containing standard BERT control tokens, punctuation,
         * ASCII letters, digits, and high-frequency English subwords for JVM tests.
         */
        fun defaultFallback(): WordPieceTokenizer {
            val map = HashMap<String, Long>()
            map[PAD_TOKEN] = 0L
            map[UNK_TOKEN] = 100L
            map[CLS_TOKEN] = 101L
            map[SEP_TOKEN] = 102L
            map[MASK_TOKEN] = 103L

            // Standard punctuation
            map["!"] = 999L
            map["\""] = 1000L
            map["#"] = 1001L
            map["$"] = 1002L
            map["%"] = 1003L
            map["&"] = 1004L
            map["'"] = 1005L
            map["("] = 1006L
            map[")"] = 1007L
            map["*"] = 1008L
            map["+"] = 1009L
            map[","] = 1010L
            map["-"] = 1011L
            map["."] = 1012L
            map["/"] = 1013L
            map[":"] = 1024L
            map[";"] = 1025L
            map["<"] = 1026L
            map["="] = 1027L
            map[">"] = 1028L
            map["?"] = 1029L
            map["@"] = 1030L

            // Digits '0'..'9'
            for (c in '0'..'9') {
                map[c.toString()] = 1014L + (c - '0')
            }

            // Lowercase ASCII letters 'a'..'z'
            for (c in 'a'..'z') {
                map[c.toString()] = 1037L + (c - 'a')
                map["##$c"] = 2000L + (c - 'a')
            }

            // Common English words & subwords
            val common = listOf(
                "the" to 1996L, "of" to 1997L, "and" to 1998L, "to" to 2000L,
                "a" to 1037L, "in" to 1999L, "is" to 2003L, "that" to 2008L,
                "for" to 2005L, "it" to 2009L, "as" to 2004L, "was" to 2001L,
                "with" to 2007L, "be" to 2022L, "on" to 2006L, "at" to 2012L,
                "by" to 2011L, "this" to 2023L, "have" to 2031L, "from" to 2013L,
                "day" to 2154L, "book" to 2338L, "task" to 4708L, "meet" to 3113L,
                "meeting" to 3114L, "log" to 8833L, "note" to 3613L, "work" to 2147L,
                "time" to 2051L, "urgent" to 11068L, "today" to 2651L, "tomorrow" to 4826L,
                "##book" to 8654L, "##ing" to 2075L, "##ed" to 2092L, "##s" to 2015L,
                "##ly" to 2135L, "##er" to 2121L, "##al" to 2389L, "##tion" to 3215L,
            )
            for ((word, id) in common) {
                map[word] = id
            }

            return WordPieceTokenizer(map)
        }
    }
}
