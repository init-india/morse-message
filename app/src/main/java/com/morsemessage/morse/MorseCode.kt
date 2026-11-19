package com.morsemessage.morse

import java.text.Normalizer

/**
 * International Morse code (ITU-R M.1677-1) plus common prosigns.
 *
 * Text form used throughout the app: symbols are '.' and '-', letters are separated
 * by a single space and words by " / ".  Example: "SOS HI" -> "... --- ... / .... ..".
 */
object MorseCode {

    val letters: Map<Char, String> = linkedMapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
        'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
        'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
        'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
        'Y' to "-.--", 'Z' to "--..",
    )

    val digits: Map<Char, String> = linkedMapOf(
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
    )

    val punctuation: Map<Char, String> = linkedMapOf(
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '\'' to ".----.", '!' to "-.-.--",
        '/' to "-..-.", '(' to "-.--.", ')' to "-.--.-", '&' to ".-...", ':' to "---...",
        ';' to "-.-.-.", '=' to "-...-", '+' to ".-.-.", '-' to "-....-", '_' to "..--.-",
        '"' to ".-..-.", '$' to "...-..-", '@' to ".--.-.",
    )

    /** Procedural signals: letters sent run together without the usual letter gap. */
    val prosigns: Map<String, Pair<String, String>> = linkedMapOf(
        "SOS" to ("...---..." to "Distress call"),
        "AR" to (".-.-." to "End of message"),
        "SK" to ("...-.-" to "End of contact"),
        "BT" to ("-...-" to "Break / new paragraph"),
        "KN" to ("-.--." to "Go ahead, named station only"),
        "AS" to (".-..." to "Wait"),
        "CT" to ("-.-.-" to "Start of transmission"),
        "HH" to ("........" to "Error / correction"),
    )

    val table: Map<Char, String> = letters + digits + punctuation

    private val reverse: Map<String, Char> = table.entries.associate { (k, v) -> v to k }

    /** Code for one character, after upper-casing and stripping accents (é -> E). */
    fun codeFor(c: Char): String? = table[baseChar(c)]

    fun isEncodable(c: Char): Boolean = c.isWhitespace() || codeFor(c) != null

    /** Upper-case and strip diacritics so names like "José" or "Zoë" still encode. */
    fun baseChar(c: Char): Char {
        val upper = c.uppercaseChar()
        if (upper in table) return upper
        val stripped = Normalizer.normalize(upper.toString(), Normalizer.Form.NFD)
            .firstOrNull { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }
        return when (upper) {
            'ß' -> 'S'
            else -> stripped ?: upper
        }
    }

    /** Morse for an English character (A–Z, 0–9, punctuation) only; null for anything else. */
    fun englishCode(c: Char): String? = if (c.code < 128) table[c.uppercaseChar()] else null

    /**
     * Text -> Morse, converting **only English** letters, numbers and punctuation.
     *
     * Words in any other language or script (Tamil, Hindi, Chinese, accented words…) are kept
     * exactly as typed. Each English word becomes one Morse word; consecutive non-English words
     * stay together as one phrase. All parts are separated by " / ".
     *   "Hi 5"            -> ".... .. / ....."
     *   "Hi வணக்கம் நண்பா" -> ".... .. / வணக்கம் நண்பா"
     * Characters with no Morse inside an English word (e.g. emoji) are left out.
     */
    fun encode(text: String): String {
        val segments = mutableListOf<String>()
        val verbatim = mutableListOf<String>()
        fun flushVerbatim() {
            if (verbatim.isNotEmpty()) segments += verbatim.joinToString(" ")
            verbatim.clear()
        }
        for (word in text.trim().split(WHITESPACE)) {
            if (word.isEmpty()) continue
            if (isForeignWord(word)) {
                verbatim += word
            } else {
                val code = word.mapNotNull { englishCode(it) }.joinToString(" ")
                if (code.isNotEmpty()) {
                    flushVerbatim()
                    segments += code
                }
            }
        }
        flushVerbatim()
        return segments.joinToString(" / ")
    }

    /**
     * Like [encode], but Morse that is already in the text stays as it is. Used for the chat box,
     * where the user may mix typed Morse, English and other languages:
     *   ".... .. there வணக்கம்" -> ".... .. / - .... . .-. . / வணக்கம்"
     * Tokens made only of dots and dashes count as Morse letters; "/" separates words.
     */
    fun toMorse(text: String): String {
        val segments = mutableListOf<String>()
        val morseWord = mutableListOf<String>()
        val verbatim = mutableListOf<String>()
        fun flushMorse() {
            if (morseWord.isNotEmpty()) segments += morseWord.joinToString(" ")
            morseWord.clear()
        }
        fun flushVerbatim() {
            if (verbatim.isNotEmpty()) segments += verbatim.joinToString(" ")
            verbatim.clear()
        }
        for (token in text.trim().split(WHITESPACE)) {
            if (token.isEmpty()) continue
            when {
                looksLikeMorse(token) || token == "/" || token == "|" -> {
                    flushVerbatim()
                    // A token like "..-/.-" contains word breaks of its own.
                    normalizeSymbols(token).replace('|', '/').split('/').forEachIndexed { i, part ->
                        if (i > 0) flushMorse()
                        if (part.isNotEmpty()) morseWord += part
                    }
                }
                isForeignWord(token) -> {
                    flushMorse()
                    verbatim += token
                }
                else -> {
                    flushMorse()
                    flushVerbatim()
                    val code = token.mapNotNull { englishCode(it) }.joinToString(" ")
                    if (code.isNotEmpty()) segments += code
                }
            }
        }
        flushMorse()
        flushVerbatim()
        return segments.joinToString(" / ")
    }

    /** A word containing any letter or digit that isn't plain English (a–z, 0–9). */
    fun isForeignWord(word: String): Boolean = word.any { it.isLetterOrDigit() && it.code >= 128 }

    /**
     * Morse -> text.  Accepts '.', '·', '•', '*' as dot and '-', '_', '−', '–', '—' as dash;
     * '/' or '|' (or 3+ spaces) as word separator.  Parts that aren't Morse (words in another
     * language) are passed through unchanged.  Unknown codes are shown as [UNKNOWN] ('#'),
     * because '?' is itself a valid Morse character.
     */
    fun decode(morse: String): String =
        morse.replace(Regex(" {3,}"), " / ")
            .split('/', '|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { segment ->
                if (!looksLikeMorse(segment)) segment
                else normalizeSymbols(segment).trim().split(WHITESPACE)
                    .filter { it.isNotEmpty() }
                    .joinToString("") { (reverse[it] ?: UNKNOWN).toString() }
            }

    /** True if the string is made only of Morse symbols and separators. */
    fun looksLikeMorse(s: String): Boolean {
        val t = s.trim()
        return t.isNotEmpty() && t.any { it in DOTS || it in DASHES } &&
            t.all { it in DOTS || it in DASHES || it == '/' || it == '|' || it.isWhitespace() }
    }

    fun normalizeSymbols(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                in DOTS -> '.'
                in DASHES -> '-'
                '\n', '\t', '\r' -> ' '
                else -> c
            }
        )
    }

    /**
     * Finds the Morse in a received message, e.g. an SMS sent by Morse Message
     * ("... --- ... / வணக்கம்\n\n(Morse code: …)"). Returns the message's Morse form (Morse words
     * normalised, other-language parts kept), or null if it is an ordinary message.
     *
     * Rules, so normal texts are never mistaken for Morse:
     * - every part between " / " is either Morse, or contains no English letters/digits;
     * - at least two Morse letters in total (a lone "..." is just an ellipsis).
     */
    fun extractMorse(message: String): String? {
        val lines = message.lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(DECODE_HINT_PREFIX) }
        if (lines.isEmpty()) return null
        var morseLetters = 0
        val parts = lines.joinToString(" / ").split('/', '|').map { it.trim() }.filter { it.isNotEmpty() }
        val out = parts.map { part ->
            when {
                looksLikeMorse(part) -> normalizeSymbols(part).trim().also { m ->
                    morseLetters += m.split(WHITESPACE).count { it.isNotEmpty() }
                }
                part.any { it.code < 128 && it.isLetterOrDigit() } -> return null
                else -> part
            }
        }
        return if (morseLetters >= 2) out.joinToString(" / ") else null
    }

    /** Pretty form for display: · and − read better than . and - on screen. */
    fun pretty(morse: String): String = morse.replace('.', '·').replace('-', '−')

    /** Like [pretty], but leaves other-language parts of a mixed message untouched. */
    fun prettyMixed(mixed: String): String =
        mixed.split(" / ").joinToString(" / ") { if (looksLikeMorse(it)) pretty(it) else it }

    /** Only the Morse parts of a mixed message, for playing as sound / light. */
    fun morseOnly(mixed: String): String =
        mixed.split('/', '|').map { it.trim() }.filter { looksLikeMorse(it) }.joinToString(" / ")

    const val UNKNOWN = '#'
    const val DECODE_HINT_PREFIX = "(Morse code"
    const val DECODE_HINT = "(Morse code: dots and dashes, letters split by spaces, words by /)"
    private const val DOTS = ".·•*"
    private const val DASHES = "-_−–—"
    private val WHITESPACE = Regex("\\s+")
}
