package com.morsemessage.morse

/** Keys of the in-app Morse keypad. */
enum class MorseKey { DOT, DASH, LETTER, WORD, BACKSPACE }

/** Result of a keypad press: the new text and where the cursor goes. */
data class Edit(val text: String, val cursor: Int)

/**
 * Morse keypad editing at the cursor, so the user can move the cursor back and fix a letter
 * anywhere in the message (not only at the end). A selection is replaced / deleted.
 */
object MorseEditing {

    fun apply(text: String, selStart: Int, selEnd: Int, key: MorseKey): Edit {
        val start = minOf(selStart, selEnd).coerceIn(0, text.length)
        val end = maxOf(selStart, selEnd).coerceIn(0, text.length)
        val before = text.substring(0, start)
        val after = text.substring(end)
        val hasSelection = start != end

        return when (key) {
            MorseKey.DOT -> insert(before, after, ".")
            MorseKey.DASH -> insert(before, after, "-")
            // Next letter: one space between letters (not doubled, not at the very start).
            MorseKey.LETTER ->
                if (before.isBlank() || before.endsWith(" ")) Edit(before + after, before.length)
                else insert(before, after, " ")
            // Next word: " / " between words.
            MorseKey.WORD ->
                if (before.isBlank() || before.trimEnd().endsWith("/")) Edit(before + after, before.length)
                else {
                    val b = before.trimEnd() + " / "
                    Edit(b + after.trimStart(), b.length)
                }
            MorseKey.BACKSPACE -> when {
                hasSelection -> Edit(before + after, before.length)
                before.isEmpty() -> Edit(text, 0)
                // Delete a whole word gap " / " in one press.
                before.endsWith(" / ") -> Edit(before.dropLast(3) + after, before.length - 3)
                else -> Edit(before.dropLast(1) + after, before.length - 1)
            }
        }
    }

    /** Paste at the cursor (replacing any selection). */
    fun paste(text: String, selStart: Int, selEnd: Int, pasted: String): Edit {
        val start = minOf(selStart, selEnd).coerceIn(0, text.length)
        val end = maxOf(selStart, selEnd).coerceIn(0, text.length)
        return insert(text.substring(0, start), text.substring(end), pasted)
    }

    private fun insert(before: String, after: String, s: String) = Edit(before + s + after, before.length + s.length)

    /**
     * Live translation of what is in the box, for the bar above it. The letter currently being
     * keyed (dots/dashes right before the end) is shown as what it would be so far, or "?" if
     * those symbols are not a letter (yet).
     */
    fun liveTranslation(text: String): String {
        val converted = MorseCode.toMorse(text)
        if (MorseCode.morseOnly(converted).isBlank()) return ""
        return MorseCode.decode(converted).replace(MorseCode.UNKNOWN, '?')
    }
}
