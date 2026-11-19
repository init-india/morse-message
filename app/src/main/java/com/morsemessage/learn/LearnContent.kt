package com.morsemessage.learn

/** Study material shown in the Learn tab. Plain data so it is easy to edit or translate. */
data class Article(val title: String, val paragraphs: List<String>)

object LearnContent {

    /** Koch method order (as used by G4FON / LCWO): learn at full speed, two characters at a time. */
    const val KOCH_ORDER = "KMURESNAPTLWI.JZ=FOY,VG5/Q92H38B?47C1D60X"

    val articles: List<Article> = listOf(
        Article(
            "How Morse code works",
            listOf(
                "Every character is a short pattern of dots (·) and dashes (−). Common letters have short " +
                    "codes: E is a single dot, T a single dash. Rarer letters like Q (−−·−) are longer.",
                "Timing is everything. Everything is measured in units, and a dot is one unit long.",
                "• Dash = 3 units\n• Gap between dots and dashes inside a letter = 1 unit\n" +
                    "• Gap between letters = 3 units\n• Gap between words = 7 units",
                "Speed is measured in words per minute (WPM) using the word PARIS, which is exactly 50 units " +
                    "long including the gap after it. At 20 WPM one unit is 60 ms. In general: unit (ms) = 1200 ÷ WPM.",
            ),
        ),
        Article(
            "Learn by sound, not by counting",
            listOf(
                "Don't memorise \"A is dot dash\" from a chart. Learn the sound \"di-dah\" instead. People who learn " +
                    "visually hit a wall around 10 WPM because they have to translate each dot and dash in their head.",
                "Koch method: start with just two characters (K and M) sent at full speed (about 20 WPM). When you copy " +
                    "90% correctly, add one more character. The Practice section in this tab follows this method.",
                "Farnsworth timing: letters are sent at full speed but with extra space between them, so you have time " +
                    "to recognise each one. Turn it on in Settings and close the gap as you improve.",
                "Ten to fifteen minutes a day works better than one long session a week.",
            ),
        ),
        Article(
            "A short history",
            listOf(
                "Samuel Morse and Alfred Vail developed the electric telegraph and its code in the late 1830s. On 24 May " +
                    "1844 Morse sent \"What hath God wrought\" from Washington to Baltimore.",
                "The original \"American Morse\" was adapted in Germany by Friedrich Gerke. His version became " +
                    "International Morse code, adopted at the International Telegraph Conference in Paris in 1865. " +
                    "That's the code this app uses.",
                "Radio made Morse the language of ships and aircraft. SOS (···−−−···) was adopted as the international " +
                    "distress signal in 1906 and came into force in 1908.",
                "In 1999 ships switched from Morse to the satellite-based GMDSS system for distress calls. In 2007 the US " +
                    "dropped the Morse test for amateur radio licences, but many radio amateurs still use Morse (\"CW\") every day.",
                "The @ sign (·−−·−·) was the most recent addition, in 2004.",
            ),
        ),
        Article(
            "Where Morse is used today",
            listOf(
                "• Amateur radio: Morse gets through weak, noisy signals that voice cannot, using simple equipment.",
                "• Aviation and navigation: radio beacons (VOR, NDB) identify themselves with a 2–3 letter Morse code that pilots check by ear.",
                "• Accessibility: people with very limited movement can type with one or two switches. Several phone " +
                    "keyboards include a Morse mode.",
                "• Emergencies: when nothing else works, SOS can be signalled with a flashlight, a mirror, a whistle or by tapping.",
                "• Discreet signalling: vibration Morse can be felt without anyone hearing or seeing it.",
                "• Fun: puzzles, escape rooms, and secret messages between friends.",
            ),
        ),
        Article(
            "Common abbreviations",
            listOf(
                "Radio operators shorten everything:",
                "CQ: calling anyone\nDE: from (\"this is\")\nK: over, go ahead\nR: received\nTNX / TU: thanks / thank you\n" +
                    "73: best regards\n88: love and kisses\nQTH: my location is\nQRS: please send slower\nQRZ: who is calling me?\n" +
                    "RST: signal report (readability, strength, tone)",
            ),
        ),
        Article(
            "About SOS",
            listOf(
                "SOS is sent as one continuous character, ···−−−···, with no gaps between the letters, and repeated.",
                "With a light: point it towards where help may be, and repeat with a pause in between.",
                "Only signal SOS in a real emergency. If you can, call your local emergency number first.",
            ),
        ),
    )
}
