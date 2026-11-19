package com.morsemessage.morse

/**
 * How a chat message is sent and shown under the Morse / Normal toggle.
 * Only English letters, numbers and punctuation ever become Morse; other languages stay as typed.
 */
object MessageText {

    /** Text shown in a bubble, the chat list or a notification. */
    fun display(body: String, morseMode: Boolean): String {
        val morse = MorseCode.extractMorse(body)
        return if (morseMode) {
            // Morse messages as dots and dashes; ordinary messages converted (English parts only).
            MorseCode.prettyMixed(morse ?: MorseCode.encode(body).ifEmpty { body })
        } else {
            // Morse messages decoded into plain language; ordinary messages unchanged.
            morse?.let { MorseCode.decode(it) } ?: body
        }
    }

    /** True if [display] in Morse mode shows any dots and dashes for this message. */
    fun hasMorse(body: String): Boolean = playable(body) != null

    /** The Morse parts of a message, for playing as beep / flash / vibration; null if none. */
    fun playable(body: String): String? =
        MorseCode.morseOnly(MorseCode.extractMorse(body) ?: MorseCode.encode(body)).ifBlank { null }

    /**
     * What is actually sent. Morse mode: English parts become Morse, Morse already in the box
     * stays as is, other languages stay as typed, plus the optional decoding hint. Text with no
     * Morse at all (e.g. only Tamil) is sent as typed. Normal mode: sent exactly as typed.
     */
    fun outgoing(typed: String, morseMode: Boolean, appendHint: Boolean): String {
        val text = typed.trim()
        if (!morseMode) return text
        val converted = MorseCode.toMorse(text)
        if (MorseCode.morseOnly(converted).isBlank()) return text
        return if (appendHint) "$converted\n\n${MorseCode.DECODE_HINT}" else converted
    }

    /** Pressing ·− : the draft in the box is converted to Morse (other languages stay). */
    fun draftToMorse(draft: String): String {
        val converted = MorseCode.toMorse(draft)
        return if (MorseCode.morseOnly(converted).isBlank()) draft else converted
    }

    /**
     * Typing English with the phone keyboard while in morse mode: when a space has just been
     * typed at the end, the finished word becomes Morse and a word gap (" / ") follows.
     * Anything else (typing inside a word, deleting, pasting) is left as it is.
     */
    fun convertOnSpace(previous: String, current: String): String {
        if (current.length != previous.length + 1 || !current.startsWith(previous) || !current.endsWith(" ")) return current
        // Only an English word just finished is converted; other languages and typed Morse stay as they are.
        val lastWord = current.trimEnd().substringAfterLast(' ')
        if (lastWord.isEmpty() || MorseCode.isForeignWord(lastWord) || MorseCode.looksLikeMorse(lastWord)) return current
        val converted = MorseCode.toMorse(current)
        return if (converted.isEmpty()) current else "$converted / "
    }

    /** Pressing abc : Morse in the draft is turned back into normal text. */
    fun draftToText(draft: String): String {
        val converted = MorseCode.toMorse(draft)
        return if (MorseCode.morseOnly(converted).isBlank()) draft else MorseCode.decode(converted)
    }
}
