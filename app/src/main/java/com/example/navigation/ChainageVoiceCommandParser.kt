package com.example.navigation

import com.example.model.Chainage
import java.util.Locale

/**
 * Structured intent recognized from spoken input inside the Navigate to Chainage popup.
 */
sealed class ChainageVoiceIntent {
    data class SetDestination(
        val rawTranscript: String,
        val chainage: Chainage,
        val formattedChainage: String,
        val confidence: Float,
        val alternateCandidates: List<Chainage> = emptyList(),
        val requiresDisambiguation: Boolean = false
    ) : ChainageVoiceIntent()

    data class AmbiguousDestination(
        val rawTranscript: String,
        val candidates: List<Chainage>,
        val explanation: String
    ) : ChainageVoiceIntent()

    data object ConfirmDestination : ChainageVoiceIntent()
    data object StartNavigation : ChainageVoiceIntent()
    data object RepeatDestination : ChainageVoiceIntent()
    data object ChangeDestination : ChainageVoiceIntent()
    data object ClearDestination : ChainageVoiceIntent()
    data object Cancel : ChainageVoiceIntent()

    data class Unrecognized(
        val rawTranscript: String,
        val reason: String
    ) : ChainageVoiceIntent()
}

/**
 * Pure, deterministic spoken-chainage and voice-command parser supporting:
 * - Natural English & Indian English number pronunciation ("one ninety six plus four hundred ninety four" -> 196+494)
 * - Individual spoken digits ("one nine eight plus zero zero zero" -> 198+000)
 * - Direct ASR digits ("201 plus 500", "196+494", "go to chainage 201+500")
 * - Hindi (Devanagari & common phonetic tokens) and Telugu (Telugu script & common phonetic tokens)
 * - Explicit navigation assistant commands (set, confirm, start navigation, repeat, change, clear, cancel)
 * - Confidence & multi-hypothesis ambiguity detection so it NEVER silently chooses an incorrect destination.
 */
object ChainageVoiceCommandParser {

    private val DEVANAGARI_DIGITS = mapOf(
        '०' to '0', '१' to '1', '२' to '2', '३' to '3', '४' to '4',
        '५' to '5', '६' to '6', '७' to '7', '८' to '8', '९' to '9'
    )

    private val TELUGU_DIGITS = mapOf(
        '౦' to '0', '౧' to '1', '౨' to '2', '౩' to '3', '౪' to '4',
        '౫' to '5', '౬' to '6', '౭' to '7', '౮' to '8', '౯' to '9'
    )

    private val PLUS_SEPARATOR_TOKENS = setOf(
        "+", "plus", "add", "and", "slash",
        "प्लस", "जमा", "धन",
        "ప్లస్", "కూడిక"
    )

    private val FILLER_PREFIX_WORDS = setOf(
        "go", "to", "navigate", "navigation", "set", "destination", "target", "chainage",
        "km", "kilometre", "kilometer", "kilometres", "kilometers", "stone", "marker",
        "at", "the", "my", "is", "please", "hey", "ok", "okay", "ctecs", "number", "no",
        "चेनेज", "गंतव्य", "किलोमीटर", "जाओ", "पर", "को", "सेट", "करें",
        "చైనేజ్", "గమ్యం", "కిలోమీటర్", "వెళ్ళు", "కి"
    )

    private val SINGLE_DIGITS = mapOf(
        // English
        "zero" to 0, "oh" to 0, "o" to 0, "nil" to 0, "naught" to 0,
        "one" to 1, "won" to 1,
        "two" to 2, "too" to 2, "tu" to 2,
        "three" to 3, "tree" to 3,
        "four" to 4, "for" to 4, "fore" to 4,
        "five" to 5,
        "six" to 6,
        "seven" to 7,
        "eight" to 8, "ate" to 8,
        "nine" to 9,
        // Hindi (Devanagari & common phonetic)
        "शून्य" to 0, "जीरो" to 0, "सिफर" to 0, "shunya" to 0,
        "एक" to 1, "ek" to 1,
        "दो" to 2, "do" to 2,
        "तीन" to 3, "teen" to 3,
        "चार" to 4, "chaar" to 4, "char" to 4,
        "पांच" to 5, "पाँच" to 5, "paanch" to 5, "panch" to 5,
        "छह" to 6, "छः" to 6, "छे" to 6, "chhe" to 6,
        "सात" to 7, "saat" to 7,
        "आठ" to 8, "aath" to 8,
        "नौ" to 9, "nau" to 9,
        // Telugu (Telugu script & common phonetic)
        "సున్నా" to 0, "సున్న" to 0, "sunna" to 0,
        "ఒకటి" to 1, "ఒక" to 1, "okati" to 1,
        "రెండు" to 2, "rendu" to 2,
        "మూడు" to 3, "moodu" to 3, "mudu" to 3,
        "నాలుగు" to 4, "nalugu" to 4,
        "ఐదు" to 5, "అయిదు" to 5, "aidu" to 5,
        "ఆరు" to 6, "aaru" to 6,
        "ఏడు" to 7, "edu" to 7,
        "ఎనిమిది" to 8, "enimidi" to 8,
        "తొమ్మిది" to 9, "tommidi" to 9
    )

    private val TEENS_AND_TENS = mapOf(
        // English
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fourty" to 40,
        "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
        // Hindi key tens/compound words
        "दस" to 10, "ग्यारह" to 11, "बारह" to 12, "तेरह" to 13, "चौदह" to 14,
        "पंद्रह" to 15, "सोलह" to 16, "सत्रह" to 17, "अठारह" to 18, "उन्नीस" to 19,
        "बीस" to 20, "तीस" to 30, "चालीस" to 40, "पचास" to 50, "साठ" to 60,
        "सत्तर" to 70, "अस्सी" to 80, "नब्बे" to 90,
        "चौरानवे" to 94, "छियानवे" to 96, "अट्ठानवे" to 98, "निन्यानवे" to 99,
        // Telugu key tens/compound words
        "పది" to 10, "ఇరవై" to 20, "ముప్పై" to 30, "నలభై" to 40, "యాభై" to 50,
        "అరవై" to 60, "డెబ్బై" to 70, "ఎనభై" to 80, "తొంభై" to 90,
        "పదకొండు" to 11, "పన్నెండు" to 12, "పదిహేను" to 15, "తొంభైఆరు" to 96, "తొంభైఎనిమిది" to 98
    )

    private val HUNDRED_WORDS = setOf(
        "hundred", "hundreds",
        "सौ", "sau",
        "వంద", "వందల", "వందలు", "vanda", "vandala", "vandalu"
    )

    private val THOUSAND_WORDS = setOf(
        "thousand", "हजार", "हज़ार", "hazaar", "వెయ్యి", "వేల"
    )

    /**
     * Live typing sanitizer & gentle auto-formatter for the Destination Chainage input box.
     * - Strips invalid characters (keeps digits and at most one '+')
     * - Prevents malformed sequences like "++", leading "+", or more than 3 metre digits after '+'
     */
    fun sanitizeTypingInput(raw: String): String {
        if (raw.isEmpty()) return ""
        val ascii = normalizeLocalizedDigits(raw)
        val sb = StringBuilder()
        var seenPlus = false
        var digitsAfterPlus = 0
        var digitsBeforePlus = 0

        for (ch in ascii) {
            when {
                ch.isDigit() -> {
                    if (!seenPlus) {
                        if (digitsBeforePlus < 5) {
                            sb.append(ch)
                            digitsBeforePlus++
                        }
                    } else {
                        if (digitsAfterPlus < 3) {
                            sb.append(ch)
                            digitsAfterPlus++
                        }
                    }
                }
                (ch == '+' || ch == '/' || ch == '.') && !seenPlus && digitsBeforePlus > 0 -> {
                    sb.append('+')
                    seenPlus = true
                }
            }
        }
        return sb.toString()
    }

    /**
     * Parses one or more Android SpeechRecognizer recognition hypotheses + optional confidence scores
     * into a safe [ChainageVoiceIntent].
     */
    fun parseVoiceHypotheses(
        hypotheses: List<String>,
        confidences: FloatArray? = null,
        awaitingConfirmation: Boolean = false
    ): ChainageVoiceIntent {
        val cleanedList = hypotheses.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleanedList.isEmpty()) {
            return ChainageVoiceIntent.Unrecognized("", "No speech detected. Please speak the destination chainage.")
        }

        val primaryRaw = cleanedList.first()
        val primaryConfidence = confidences?.firstOrNull()?.takeIf { !it.isNaN() && it >= 0f } ?: 0.85f

        // 1. Check explicit assistant commands first (highest clarity when awaiting confirmation or explicit phrases)
        val cmd = matchExplicitCommand(primaryRaw, awaitingConfirmation)
        if (cmd != null) {
            return cmd
        }

        // 2. Try parsing chainage across all returned ASR hypotheses to detect ambiguity safely
        val parsedByHypothesis = mutableListOf<Pair<Chainage, Float>>()
        cleanedList.forEachIndexed { idx, text ->
            val conf = confidences?.getOrNull(idx)?.takeIf { !it.isNaN() && it >= 0f }
                ?: (primaryConfidence - idx * 0.08f).coerceAtLeast(0.35f)
            val parsed = parseSpokenChainage(text)
            if (parsed != null) {
                parsedByHypothesis.add(parsed to conf)
            }
        }

        if (parsedByHypothesis.isEmpty()) {
            return ChainageVoiceIntent.Unrecognized(
                rawTranscript = primaryRaw,
                reason = "Could not recognize a valid chainage from \"$primaryRaw\". Try saying e.g. \"201 plus 500\"."
            )
        }

        val distinctCandidates = parsedByHypothesis
            .map { it.first }
            .distinctBy { it.metres }

        val topChainage = parsedByHypothesis.first().first
        val topConf = parsedByHypothesis.first().second

        // Detect ambiguity:
        // - Low recognition confidence (< 0.55)
        // - Multiple closely scored conflicting chainages in top hypotheses
        val secondDistinctConf = parsedByHypothesis
            .firstOrNull { it.first.metres != topChainage.metres }
            ?.second
        val hasCloseCompetingHypothesis = secondDistinctConf != null &&
            (topConf - secondDistinctConf) < 0.12f &&
            distinctCandidates.size > 1 &&
            topConf < 0.80f

        if (topConf < 0.55f || hasCloseCompetingHypothesis) {
            return ChainageVoiceIntent.AmbiguousDestination(
                rawTranscript = primaryRaw,
                candidates = distinctCandidates.take(3),
                explanation = if (distinctCandidates.size > 1) {
                    "Multiple possible chainages were heard. Please select or repeat the destination."
                } else {
                    "Low speech confidence for ${topChainage.format()}. Please confirm or repeat."
                }
            )
        }

        return ChainageVoiceIntent.SetDestination(
            rawTranscript = primaryRaw,
            chainage = topChainage,
            formattedChainage = topChainage.format(),
            confidence = topConf,
            alternateCandidates = distinctCandidates.drop(1).take(2),
            requiresDisambiguation = false
        )
    }

    /**
     * Parses a single natural spoken phrase into a [Chainage] (or null if no valid chainage is present).
     */
    fun parseSpokenChainage(rawText: String): Chainage? {
        if (rawText.isBlank()) return null

        val normalized = normalizeTranscript(rawText)
        if (normalized.isBlank()) return null

        // Fast-path 1: Explicit numeric KM+MMM inside the phrase (e.g. "set destination to 201+500")
        val directPlusRegex = Regex("""\b(\d{1,5})\s*\+\s*(\d{1,3})\b""")
        directPlusRegex.find(normalized)?.let { m ->
            val km = m.groupValues[1].toLongOrNull() ?: return@let
            val mStr = m.groupValues[2]
            val metres = mStr.toLongOrNull() ?: return@let
            if (metres in 0L..999L) {
                return Chainage(km * 1000L + metres)
            }
        }

        // Tokenize and look for explicit spoken "plus" separator (English / Hindi / Telugu)
        val tokens = tokenize(normalized)
        if (tokens.isEmpty()) return null

        val plusIdx = tokens.indexOfFirst { it in PLUS_SEPARATOR_TOKENS && it != "and" }
        if (plusIdx > 0 && plusIdx < tokens.lastIndex) {
            val leftTokens = stripLeadingFiller(tokens.subList(0, plusIdx))
            val rightTokens = stripTrailingFiller(tokens.subList(plusIdx + 1, tokens.size))
            val kmVal = parseNumberSegment(leftTokens, isMetreSegment = false)
            val mVal = parseNumberSegment(rightTokens, isMetreSegment = true)
            if (kmVal != null && mVal != null && kmVal in 0L..9999L && mVal in 0L..999L) {
                return Chainage(kmVal * 1000L + mVal)
            }
        }

        // Handle "201 point 500" or "201.500" decimal kilometre format
        val decimalRegex = Regex("""\b(\d{1,5})\.(\d{1,3})\b""")
        decimalRegex.find(normalized)?.let { m ->
            val km = m.groupValues[1].toLongOrNull() ?: return@let
            val mPart = m.groupValues[2].padEnd(3, '0').take(3).toLongOrNull() ?: return@let
            if (km in 0L..9999L && mPart in 0L..999L) {
                return Chainage(km * 1000L + mPart)
            }
        }

        // Handle "kilometre/km <X> <Y> metres" spoken phrasing without "plus"
        val kmWordIdx = tokens.indexOfFirst { it in setOf("km", "kilometer", "kilometre", "kilometers", "kilometres") }
        if (kmWordIdx in 1 until tokens.lastIndex) {
            val leftTokens = stripLeadingFiller(tokens.subList(0, kmWordIdx))
            val rightTokens = stripTrailingFiller(tokens.subList(kmWordIdx + 1, tokens.size))
            val kmVal = parseNumberSegment(leftTokens, isMetreSegment = false)
            val mVal = parseNumberSegment(rightTokens, isMetreSegment = true)
            if (kmVal != null && mVal != null && kmVal in 0L..9999L && mVal in 0L..999L) {
                return Chainage(kmVal * 1000L + mVal)
            }
        }

        // Handle exact 6-digit metre chainage spoken as a whole number (e.g. "196494" -> 196+494)
        val stripped = stripTrailingFiller(stripLeadingFiller(tokens))
        if (stripped.size == 1 && stripped[0].all { it.isDigit() }) {
            val digits = stripped[0]
            return when {
                digits.length in 1..3 -> {
                    val km = digits.toLongOrNull() ?: return null
                    Chainage(km * 1000L)
                }
                digits.length in 4..7 -> {
                    val totalM = digits.toLongOrNull() ?: return null
                    Chainage(totalM)
                }
                else -> null
            }
        }

        // Handle two standalone numeric tokens like "201 500" (when right side is 3 digits 000..999)
        if (stripped.size == 2 && stripped[0].all { it.isDigit() } && stripped[1].all { it.isDigit() } && stripped[1].length == 3) {
            val km = stripped[0].toLongOrNull()
            val m = stripped[1].toLongOrNull()
            if (km != null && m != null && km in 0L..9999L && m in 0L..999L) {
                return Chainage(km * 1000L + m)
            }
        }

        // Handle whole-kilometre spoken words like "chainage two hundred" -> 200+000
        if (normalized.contains("chainage") || normalized.contains("km") || normalized.contains("kilometre") ||
            normalized.contains("kilometer") || normalized.contains("चेनेज") || normalized.contains("చైనేజ్")
        ) {
            val kmOnly = parseNumberSegment(stripped, isMetreSegment = false)
            if (kmOnly != null && kmOnly in 0L..9999L) {
                return Chainage(kmOnly * 1000L)
            }
        }

        return null
    }

    private fun matchExplicitCommand(raw: String, awaitingConfirmation: Boolean): ChainageVoiceIntent? {
        val lower = normalizeTranscript(raw)

        // Cancel / Close commands
        if (lower in setOf("cancel", "close", "stop", "exit", "dismiss", "abort", "रद्द", "रद्द करें", "बंद", "రద్దు", "రద్దు చేయి", "ఆపు") ||
            lower.startsWith("cancel ") || lower.contains("no cancel") || lower.contains("cancel navigation")
        ) {
            return ChainageVoiceIntent.Cancel
        }

        // Clear destination commands
        if (lower.contains("clear destination") || lower == "clear" || lower.contains("reset destination") ||
            lower.contains("हटाएं") || lower.contains("తొలగించు")
        ) {
            return ChainageVoiceIntent.ClearDestination
        }

        // Change destination commands (e.g. "No, change destination")
        if (lower.contains("change destination") || lower.contains("edit destination") ||
            lower.contains("new destination") || lower.contains("wrong destination") ||
            lower.contains("बदलें") || lower.contains("మార్చు") ||
            (awaitingConfirmation && lower in setOf("no", "nope", "change", "incorrect", "wrong", "नहीं", "కాదు"))
        ) {
            return ChainageVoiceIntent.ChangeDestination
        }

        // Repeat destination commands
        if (lower.contains("repeat destination") || lower == "repeat" || lower.contains("say again") ||
            lower.contains("दोहराएं") || lower.contains("మళ్ళీ చెప్పు")
        ) {
            return ChainageVoiceIntent.RepeatDestination
        }

        // Start navigation commands (only when no chainage numbers are being set in the same phrase)
        val hasStartNavPhrase = lower.contains("start navigation") ||
            lower.contains("begin navigation") ||
            lower.contains("launch navigation") ||
            lower.contains("नेविगेशन शुरू") ||
            lower.contains("నావిగేషన్ ప్రారంభించు") ||
            (awaitingConfirmation && lower in setOf(
                "yes", "yeah", "yep", "start", "go", "proceed", "ok start", "okay start",
                "yes start", "yes please", "confirm and start", "हां", "हाँ", "शुरू करें", "అవును", "ప్రారంభించు"
            ))
        if (hasStartNavPhrase && parseSpokenChainage(raw) == null) {
            return ChainageVoiceIntent.StartNavigation
        }

        // Confirm destination command
        if (lower.contains("confirm destination") ||
            (awaitingConfirmation && lower in setOf("confirm", "confirmed", "correct", "right", "सही", "నిర్ధారించు"))
        ) {
            return ChainageVoiceIntent.ConfirmDestination
        }

        return null
    }

    /**
     * Parses a spoken number segment (either KM side or Metre side).
     * Supports:
     * - Direct digits: "196", "000", "500"
     * - Individual digits: "one nine six" -> 196, "zero zero zero" -> 0, "zero five zero" -> 50
     * - Natural hundreds: "one hundred ninety eight" -> 198, "four hundred ninety four" -> 494, "two hundred one" -> 201
     * - Indian English shorthand hundreds: "one ninety six" -> 196, "four ninety four" -> 494, "two zero one" -> 201
     * - Multipliers: "double zero" -> 00, "triple zero" -> 000
     * - Hindi & Telugu words ("दो सौ एक" -> 201, "पांच सौ" -> 500, "రెండు వందల ఒకటి" -> 201, "ఐదు వందలు" -> 500)
     */
    internal fun parseNumberSegment(rawTokens: List<String>, isMetreSegment: Boolean): Long? {
        val tokens = expandRepeatMultipliers(
            rawTokens.filter { it !in setOf("and", "metres", "meters", "metre", "meter", "m", "मीटर", "మీటర్లు") }
        )
        if (tokens.isEmpty()) return null

        // Direct single numeric token
        if (tokens.size == 1 && tokens[0].all { it.isDigit() }) {
            return tokens[0].toLongOrNull()
        }

        // Pure sequence of digits or single-digit words (e.g. "zero zero zero", "one nine six", "2 0 1")
        val allSingleDigits = tokens.all { tok ->
            (tok.length == 1 && tok[0].isDigit()) || SINGLE_DIGITS.containsKey(tok)
        }
        if (allSingleDigits && tokens.size in 1..4) {
            val digitString = tokens.joinToString("") { tok ->
                if (tok.length == 1 && tok[0].isDigit()) tok else SINGLE_DIGITS[tok].toString()
            }
            return digitString.toLongOrNull()
        }

        // Mixed numeric tokens from Android ASR like ["1", "96"] -> 196, ["4", "94"] -> 494, ["200", "1"] -> 201
        if (tokens.all { it.all { ch -> ch.isDigit() } }) {
            if (tokens.size == 2) {
                val a = tokens[0].toLongOrNull() ?: return null
                val b = tokens[1].toLongOrNull() ?: return null
                // "1 96" -> 196; "4 94" -> 494
                if (a in 1L..9L && tokens[1].length == 2 && b in 10L..99L) {
                    return a * 100L + b
                }
                // "200 1" -> 201; "400 94" -> 494
                if (a >= 100L && a % 100L == 0L && b in 0L..99L) {
                    return a + b
                }
                // "0 50" -> 50
                if (a == 0L && b in 0L..99L) {
                    return b
                }
            }
            val joined = tokens.joinToString("")
            if (joined.length <= 5) {
                return joined.toLongOrNull()
            }
        }

        // Natural spoken English / Hindi / Telugu number composition
        // Example: "one ninety six" -> [1, 90, 6] => 1 * 100 + 96 = 196
        // Example: "one hundred ninety eight" -> [1, hundred, 90, 8] => 198
        // Example: "two hundred one" -> [2, hundred, 1] => 201
        // Example: "four hundred ninety four" -> [4, hundred, 90, 4] => 494
        // Example: "five hundred" -> [5, hundred] => 500
        var total = 0L
        var current = 0L
        var recognizedAny = false
        var seenHundred = false
        var idx = 0

        while (idx < tokens.size) {
            val tok = tokens[idx]
            val numericVal = tok.toLongOrNull()
            val singleVal = SINGLE_DIGITS[tok]?.toLong()
            val teenTenVal = TEENS_AND_TENS[tok]?.toLong()

            when {
                tok in THOUSAND_WORDS -> {
                    val mult = if (current == 0L) 1L else current
                    total += mult * 1000L
                    current = 0L
                    recognizedAny = true
                }
                tok in HUNDRED_WORDS -> {
                    val mult = if (current == 0L) 1L else current
                    current = mult * 100L
                    seenHundred = true
                    recognizedAny = true
                }
                numericVal != null -> {
                    if (current in 1L..9L && numericVal in 10L..99L && !seenHundred) {
                        // Shorthand like "1 96" -> 196
                        current = current * 100L + numericVal
                        seenHundred = true
                    } else if (current >= 100L && current % 100L == 0L && numericVal in 0L..99L) {
                        current += numericVal
                    } else {
                        current += numericVal
                    }
                    recognizedAny = true
                }
                teenTenVal != null -> {
                    if (current in 1L..9L && !seenHundred) {
                        // Indian English natural phrasing: "one ninety six" -> 100 + 90
                        current = current * 100L + teenTenVal
                        seenHundred = true
                    } else {
                        current += teenTenVal
                    }
                    recognizedAny = true
                }
                singleVal != null -> {
                    if (current in 1L..9L && !seenHundred && idx + 1 < tokens.size) {
                        // e.g. "two zero one" handled by allSingleDigits above, or "one zero five"
                        val nextTok = tokens[idx + 1]
                        if (SINGLE_DIGITS.containsKey(nextTok)) {
                            current = current * 10L + singleVal
                        } else if (nextTok in HUNDRED_WORDS || nextTok in THOUSAND_WORDS) {
                            current += singleVal
                        } else {
                            current = current * 10L + singleVal
                        }
                    } else {
                        current += singleVal
                    }
                    recognizedAny = true
                }
                else -> {
                    // Unknown non-number token inside segment
                    if (isMetreSegment && !recognizedAny) return null
                }
            }
            idx++
        }

        if (!recognizedAny) return null
        return total + current
    }

    private fun expandRepeatMultipliers(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if ((t == "double" || t == "डबल") && i + 1 < tokens.size) {
                val next = tokens[i + 1]
                out.add(next)
                out.add(next)
                i += 2
            } else if ((t == "triple" || t == "ट्रिपल") && i + 1 < tokens.size) {
                val next = tokens[i + 1]
                out.add(next)
                out.add(next)
                out.add(next)
                i += 2
            } else {
                out.add(t)
                i++
            }
        }
        return out
    }

    private fun stripLeadingFiller(tokens: List<String>): List<String> {
        var start = 0
        while (start < tokens.size && tokens[start] in FILLER_PREFIX_WORDS) {
            start++
        }
        return tokens.subList(start, tokens.size)
    }

    private fun stripTrailingFiller(tokens: List<String>): List<String> {
        var end = tokens.size
        while (end > 0 && tokens[end - 1] in setOf("metres", "meters", "metre", "meter", "m", "please", "now", "only", "मीटर", "మీటర్లు")) {
            end--
        }
        return tokens.subList(0, end)
    }

    private fun normalizeTranscript(raw: String): String {
        val withAsciiDigits = normalizeLocalizedDigits(raw)
        return withAsciiDigits
            .lowercase(Locale.ROOT)
            .replace("+", " + ")
            .replace(",", " ")
            .replace("-", " ")
            .replace("?", " ")
            .replace("!", " ")
            .replace(";", " ")
            .replace(":", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeLocalizedDigits(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            sb.append(DEVANAGARI_DIGITS[ch] ?: TELUGU_DIGITS[ch] ?: ch)
        }
        return sb.toString()
    }

    private fun tokenize(normalized: String): List<String> {
        return normalized
            .split(' ')
            .map { it.trim().trimEnd('.') }
            .filter { it.isNotEmpty() }
    }
}
