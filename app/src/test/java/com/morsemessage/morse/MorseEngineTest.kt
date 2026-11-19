package com.morsemessage.morse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MorseEngineTest {

    @Test fun encodesWordsAndLetters() {
        assertEquals("... --- ...", MorseCode.encode("SOS"))
        assertEquals(".... .. / - .... . .-. .", MorseCode.encode("hi  there"))
    }

    @Test fun convertsOnlyEnglish() {
        assertEquals(".- -...", MorseCode.encode("A😀B"))
        assertEquals(".... .. / வணக்கம் நண்பா / .....", MorseCode.encode("Hi வணக்கம் நண்பா 5"))
        assertEquals("नमस्ते", MorseCode.encode("नमस्ते"))
        assertEquals("你好 / .... ..", MorseCode.encode("你好 hi"))
        assertEquals("José", MorseCode.encode("José"))           // accented word = not English
        assertEquals("HI வணக்கம் நண்பா 5", MorseCode.decode(MorseCode.encode("Hi வணக்கம் நண்பா 5")))
    }

    @Test fun mixedMessagesAreRecognised() {
        val m = MorseCode.extractMorse(".... .. / வணக்கம்")!!
        assertEquals("HI வணக்கம்", MorseCode.decode(m))
        assertEquals(null, MorseCode.extractMorse("வணக்கம் ... சரி"))       // Tamil with an ellipsis
        assertEquals(null, MorseCode.extractMorse("मैं 5 बजे / ... आऊँगा"))  // has English digits
        assertEquals("·· / नमस्ते", MorseCode.prettyMixed(".. / नमस्ते"))
        assertEquals(".. / ...", MorseCode.morseOnly(".. / नमस्ते / ..."))
    }

    @Test fun ringtoneTimelineKeepsAccentedNames() {
        val s = MorseSpeed(20)
        assertTrue(MorseTiming.fromText("José", s).isEmpty())
        assertEquals(MorseTiming.fromText("JOSE", s), MorseTiming.fromText("José", s, englishOnly = false))
    }

    @Test fun decodesWithAlternativeSymbols() {
        assertEquals("SOS", MorseCode.decode("··· −−− ···"))
        assertEquals("HI THERE", MorseCode.decode(".... .. / - .... . .-. ."))
        assertEquals("HI THERE", MorseCode.decode(".... ..   - .... . .-. ."))
        assertEquals("A#", MorseCode.decode(".- ........."))
    }

    @Test fun roundTripsEveryCharacter() {
        val all = MorseCode.table.keys.joinToString("")
        assertEquals(all, MorseCode.decode(MorseCode.encode(all)))
    }

    @Test fun detectsMorseInput() {
        assertTrue(MorseCode.looksLikeMorse("... --- ..."))
        assertTrue(MorseCode.looksLikeMorse(".- / -..."))
        assertFalse(MorseCode.looksLikeMorse("hello"))
        assertFalse(MorseCode.looksLikeMorse("   "))
    }

    @Test fun parisAtTwentyWpmIsThreeSeconds() {
        // PARIS + one word gap = 50 units; at 20 WPM a unit is 60 ms.
        val speed = MorseSpeed(20)
        val t = MorseTiming.fromText("PARIS PARIS", speed)
        val oneWordPlusGap = MorseTiming.totalMs(t) - MorseTiming.totalMs(MorseTiming.fromText("PARIS", speed))
        assertEquals(3000.0, oneWordPlusGap, 0.001)
    }

    @Test fun farnsworthStretchesOnlyGaps() {
        val speed = MorseSpeed(charWpm = 20, effectiveWpm = 10)
        val g = MorseTiming.gaps(speed)
        assertEquals(60.0, g.dot, 0.001)
        assertEquals(180.0, g.dash, 0.001)
        assertTrue(g.interChar > 180.0)
        // PARIS + gap should now take 60 / 10 = 6 seconds.
        val t1 = MorseTiming.totalMs(MorseTiming.fromText("PARIS", speed))
        val t2 = MorseTiming.totalMs(MorseTiming.fromText("PARIS PARIS", speed))
        assertTrue(abs((t2 - t1) - 6000.0) < 1.0)
    }

    @Test fun morseTimelineMatchesTextTimeline() {
        val speed = MorseSpeed(15)
        val fromText = MorseTiming.fromText("HI YOU", speed).map { it.on to it.durationMs }
        val fromMorse = MorseTiming.fromMorse(MorseCode.encode("HI YOU"), speed).map { it.on to it.durationMs }
        assertEquals(fromText, fromMorse)
    }

    @Test fun wavHasValidHeaderAndLength() {
        val t = MorseTiming.fromText("E", MorseSpeed(20))   // one 60 ms dot
        val pcm = ToneSynth.render(t, 600, sampleRate = 8000, trailingMs = 40.0)
        assertEquals(800, pcm.size)                          // 100 ms @ 8 kHz
        val wav = ToneSynth.toWav(pcm, 8000)
        assertEquals(44 + 1600, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        // silence after the dot
        assertTrue(pcm.copyOfRange(480, 800).all { it.toInt() == 0 })
    }

    @Test fun extractsMorseFromMessages() {
        val sms = "... --- ... / .... ..\n\n" + MorseCode.DECODE_HINT
        assertEquals("SOS HI", MorseCode.decode(MorseCode.extractMorse(sms)!!))
        assertEquals("SOS", MorseCode.decode(MorseCode.extractMorse("··· −−− ···")!!))
        assertEquals(null, MorseCode.extractMorse("See you at 5..."))
        assertEquals(null, MorseCode.extractMorse("..."))
        assertEquals(null, MorseCode.extractMorse("-"))
        assertEquals(null, MorseCode.extractMorse("hello"))
    }

    @Test fun toggleDisplayAndSending() {
        val morseSms = ".... .. / வணக்கம்\n\n" + MorseCode.DECODE_HINT
        // Normal mode: Morse decoded, ordinary text unchanged
        assertEquals("HI வணக்கம்", MessageText.display(morseSms, morseMode = false))
        assertEquals("See you", MessageText.display("See you", morseMode = false))
        // Morse mode: Morse shown pretty, English converted, other languages untouched
        assertEquals("···· ·· / வணக்கம்", MessageText.display(morseSms, morseMode = true))
        assertEquals("··· / −·−− −−− ··−", MessageText.display("S you", morseMode = true))
        assertEquals("नमस्ते", MessageText.display("नमस्ते", morseMode = true))
        // Sending
        assertEquals("Hi", MessageText.outgoing(" Hi ", morseMode = false, appendHint = true))
        assertEquals(".... ..", MessageText.outgoing("Hi", morseMode = true, appendHint = false))
        assertEquals("வணக்கம்", MessageText.outgoing("வணக்கம்", morseMode = true, appendHint = true))
        assertEquals("... ---", MessageText.outgoing("... ---", morseMode = true, appendHint = false))
    }

    @Test fun toggleConvertsDraftWithoutDoubleEncoding() {
        assertEquals(".... .. / - .... . .-. . / வணக்கம்", MorseCode.toMorse(".... .. there வணக்கம்"))
        assertEquals(".... .. / .-", MorseCode.toMorse("··· ·· / .-".replace("···", "....")))
        assertEquals(".... .. / வணக்கம்", MessageText.draftToMorse("Hi வணக்கம்"))
        assertEquals(".... .. / வணக்கம்", MessageText.draftToMorse(".... .. / வணக்கம்"))   // already Morse: unchanged
        assertEquals("HI வணக்கம்", MessageText.draftToText(".... .. / வணக்கம்"))
        assertEquals("வணக்கம்", MessageText.draftToMorse("வணக்கம்"))
        assertEquals(".... .. / வணக்கம்", MessageText.outgoing(".... .. / வணக்கம்", morseMode = true, appendHint = false))
    }

    @Test fun typingEnglishConvertsEachWordOnSpace() {
        assertEquals(".... .. / ", MessageText.convertOnSpace("hi", "hi "))
        assertEquals(".... .. / -.-- --- ..- / ", MessageText.convertOnSpace(".... .. / you", ".... .. / you "))
        assertEquals("hi", MessageText.convertOnSpace("h", "hi"))            // mid-word: unchanged
        assertEquals("நண்பா ", MessageText.convertOnSpace("நண்பா", "நண்பா "))  // other language: unchanged
        assertEquals(".- ", MessageText.convertOnSpace(".-", ".- "))          // typed Morse: unchanged
        assertEquals("hello wor", MessageText.convertOnSpace("hello word", "hello wor")) // deleting
        assertEquals("HI YOU", MorseCode.decode(MorseCode.toMorse(".... .. / you")))  // Send converts the last word
    }
}
