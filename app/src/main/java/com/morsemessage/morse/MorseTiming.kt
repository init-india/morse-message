package com.morsemessage.morse

/**
 * Sending speed.
 *
 * @param charWpm speed of each character (PARIS standard: 1 unit = 1200 / wpm ms).
 * @param effectiveWpm overall speed.  When lower than [charWpm] the extra time goes into the
 *   gaps between letters and words (Farnsworth timing), so letters keep their "sound".
 */
data class MorseSpeed(val charWpm: Int, val effectiveWpm: Int = charWpm) {
    init {
        require(charWpm in 1..100) { "charWpm out of range: $charWpm" }
        require(effectiveWpm in 1..100) { "effectiveWpm out of range: $effectiveWpm" }
    }

    /** Never faster than [maxWpm]; used for the flashlight, which cannot toggle quickly. */
    fun cappedAt(maxWpm: Int): MorseSpeed =
        MorseSpeed(minOf(charWpm, maxWpm), minOf(effectiveWpm, maxWpm))
}

/** One stretch of signal on (tone / light / vibration) or off. */
data class Segment(
    val on: Boolean,
    val durationMs: Double,
    /** Index in the source string of the character being sent, or -1 for word gaps. */
    val sourceIndex: Int,
)

data class Gaps(
    val dot: Double,
    val dash: Double,
    val intraChar: Double,
    val interChar: Double,
    val word: Double,
)

object MorseTiming {

    fun unitMs(wpm: Int): Double = 1200.0 / wpm

    fun gaps(speed: MorseSpeed): Gaps {
        val c = speed.charWpm.toDouble()
        val s = speed.effectiveWpm.toDouble()
        val u = unitMs(speed.charWpm)
        if (s >= c) return Gaps(u, 3 * u, u, 3 * u, 7 * u)
        // ARRL / Farnsworth: total extra delay per PARIS word, spread over 19 gap units.
        val taMs = (60.0 * c - 37.2 * s) / (s * c) * 1000.0
        return Gaps(u, 3 * u, u, 3 * taMs / 19.0, 7 * taMs / 19.0)
    }

    private sealed interface Token {
        data class Letter(val code: String, val sourceIndex: Int) : Token
        data object WordBreak : Token
    }

    /**
     * Timeline for plain text ("HELLO WORLD"). Unsupported characters are skipped.
     *
     * @param englishOnly true (default): only English is sent, words in other languages are
     *   skipped, matching [MorseCode.encode]. false: accented Latin letters are sent as their
     *   base letter (é -> E), used for contact-name ringtones such as "José".
     */
    fun fromText(text: String, speed: MorseSpeed, englishOnly: Boolean = true): List<Segment> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < text.length) {
            if (text[i].isWhitespace()) {
                tokens += Token.WordBreak
                i++
                continue
            }
            var end = i
            while (end < text.length && !text[end].isWhitespace()) end++
            val word = text.substring(i, end)
            if (!(englishOnly && MorseCode.isForeignWord(word))) {
                for (k in i until end) {
                    val code = if (englishOnly) MorseCode.englishCode(text[k]) else MorseCode.codeFor(text[k])
                    code?.let { tokens += Token.Letter(it, k) }
                }
            }
            i = end
        }
        return build(tokens, speed)
    }

    /** Timeline for Morse written out (".... .. / - .-"); sourceIndex points into [morse]. */
    fun fromMorse(morse: String, speed: MorseSpeed): List<Segment> {
        val m = MorseCode.normalizeSymbols(morse)
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < m.length) {
            val ch = m[i]
            when {
                ch == '.' || ch == '-' -> {
                    val start = i
                    while (i < m.length && (m[i] == '.' || m[i] == '-')) i++
                    tokens += Token.Letter(m.substring(start, i), start)
                    continue
                }
                ch == '/' || ch == '|' -> tokens += Token.WordBreak
            }
            i++
        }
        return build(tokens, speed)
    }

    private fun build(tokens: List<Token>, speed: MorseSpeed): List<Segment> {
        val g = gaps(speed)
        val out = mutableListOf<Segment>()
        var started = false
        var wordBreakPending = false
        for (t in tokens) {
            when (t) {
                Token.WordBreak -> if (started) wordBreakPending = true
                is Token.Letter -> {
                    if (started) {
                        out += if (wordBreakPending) Segment(false, g.word, -1)
                        else Segment(false, g.interChar, t.sourceIndex)
                    }
                    wordBreakPending = false
                    t.code.forEachIndexed { k, sym ->
                        if (k > 0) out += Segment(false, g.intraChar, t.sourceIndex)
                        out += Segment(true, if (sym == '.') g.dot else g.dash, t.sourceIndex)
                    }
                    started = true
                }
            }
        }
        return out
    }

    fun totalMs(timeline: List<Segment>): Double = timeline.sumOf { it.durationMs }
}
