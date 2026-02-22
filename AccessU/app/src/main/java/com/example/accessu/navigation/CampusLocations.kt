package com.example.accessu.navigation

/**
 * Prototype: valid campus locations for navigation.
 * - CCIS, ETLC, NREF: user spells each letter ("C C I S", "E T L C", "N R E F")
 * - CAB, SAB, SUB: user says as words
 * - Others: full phrases (Tory building, bus stop near SUB, etc.)
 */
data class LocationInfo(
    val fullName: String,
    val abbreviation: String
)

object CampusLocations {

    // Spelled-letter buildings: remove spaces from "c c i s" -> "ccis"
    private val spelledAbbrevs = listOf("ccis", "etlc", "nref")

    private val spelledToInfo = mapOf(
        "ccis" to LocationInfo("Centennial Centre for Interdisciplinary Sciences", "CCIS"),
        "etlc" to LocationInfo("Engineering Teaching and Learning Complex", "ETLC"),
        "nref" to LocationInfo("Natural Resources Engineering Facility", "NREF")
    )

    // Word-form only: CAB, SAB, SUB, GSB
    private val wordAbbrevs = mapOf(
        "cab" to LocationInfo("Central Academic Building", "CAB"),
        "sab" to LocationInfo("South Academic Building", "SAB"),
        "sub" to LocationInfo("Students' Union Building", "SUB"),
        "gsb" to LocationInfo("General Services Building", "GSB")
    )

    // Full phrase buildings - expanded variants for common STT mishearings
    private val phraseLocations = mapOf(
        LocationInfo("Centennial Centre for Interdisciplinary Sciences", "CCIS") to listOf(
            "ccis", "centennial", "centennial centre", "centennial center", "c c i s", "cecis", "cc is", "sis", "csis", "ccis building"
        ),
        LocationInfo("Engineering Teaching and Learning Complex", "ETLC") to listOf(
            "etlc", "engineering teaching", "e t l c", "etlc building", "engineering building", "e t el c", "e t l see", "itlc"
        ),
        LocationInfo("Natural Resources Engineering Facility", "NREF") to listOf(
            "nref", "natural resources", "n r e f", "natural resources engineering"
        ),
        LocationInfo("Tory Building", "Tory") to listOf("tory", "tory building", "tory building"),
        LocationInfo("Bus stop near Students' Union Building", "Bus stop near SUB") to listOf(
            "bus stop near sub", "bus stop near the sub", "bus stop sub", "bus stop at sub",
            "bus stop by sub", "sub bus stop", "the bus stop near sub"
        ),
        LocationInfo("University bus stop", "University bus stop") to listOf(
            "university bus stop", "uni bus stop", "main bus stop", "the university bus stop"
        ),
        LocationInfo("University Commons Building", "University Commons") to listOf(
            "university commons", "uni commons", "commons building", "commons", "the commons"
        ),
        LocationInfo("Cameron Library", "Cameron Library") to listOf(
            "cameron", "cameron library", "cameron library building", "the cameron library"
        )
    )

    /** List of supported building names for display/documentation. */
    fun getSupportedLocations(): List<String> = listOf(
        "CCIS (Centennial Centre for Interdisciplinary Sciences)",
        "ETLC (Engineering Teaching and Learning Complex)",
        "NREF (Natural Resources Engineering Facility)",
        "CAB (Central Academic Building)",
        "SAB (South Academic Building)",
        "SUB (Students' Union Building)",
        "Cameron Library",
        "GSB (General Services Building)",
        "Tory Building",
        "Bus stop near SUB",
        "University bus stop",
        "University Commons"
    )

    /** Match yes/no/affirmative for verification. Returns true=yes, false=no, null=unclear. */
    fun parseYesNo(text: String): Boolean? {
        val t = text.trim().lowercase().replace(Regex("\\s+"), " ")
        if (t.isBlank()) return null
        val words = t.split(Regex("\\s+"))
        val yesWords = setOf("yes", "yeah", "yep", "yup", "correct", "right", "ok", "okay", "sure", "confirm")
        val noWords = setOf("no", "nope", "wrong", "incorrect", "change", "redo", "different")
        if (words.any { it in yesWords }) return true
        if (words.any { it in noWords }) return false
        if (t.contains("that's right") || t.contains("that is correct")) return true
        return null
    }

    /** First clear yes/no from STT alternatives, or null if none parse. */
    fun firstYesNoFromAlternatives(alternatives: List<String>?): Boolean? {
        if (alternatives.isNullOrEmpty()) return null
        return alternatives.firstNotNullOfOrNull { parseYesNo(it) }
    }

    /**
     * Given STT alternatives (ordered by confidence), return the first raw string that matches a location.
     * Enables efficient match-from-multiple-hypotheses when recognizer returns several options.
     */
    fun firstMatchingAlternative(alternatives: List<String>?): String? {
        if (alternatives.isNullOrEmpty()) return null
        return alternatives.firstNotNullOfOrNull { s ->
            val t = s.trim()
            if (t.isNotBlank() && matchLocation(t) != null) t else null
        }
    }

    // Spoken-letter forms STT often returns: "el"->l, "see"->c, "why"->y, "you"->u, etc.
    private fun normalizeSpelledInput(s: String): String {
        var t = s.trim().lowercase().replace(Regex("\\s+"), " ")
        val spokenToLetter = mapOf(
            "el" to "l", "ell" to "l", "l" to "l",
            "see" to "c", "sea" to "c", "c" to "c",
            "why" to "y", "y" to "y",
            "you" to "u", "u" to "u",
            "are" to "r", "r" to "r",
            "bee" to "b", "be" to "b", "b" to "b",
            "tee" to "t", "t" to "t",
            "eye" to "i", "ay" to "a", "eh" to "e", "e" to "e"
        )
        spokenToLetter.forEach { (spoken, letter) ->
            if (spoken.length > 1) t = t.replace(Regex("\\b$spoken\\b"), letter)
        }
        return t.replace(Regex("[^a-z]"), "")
    }

    /**
     * Match user speech to a location.
     * - Spelled: "c c i s", "e t l c" -> CCIS, ETLC
     * - Words: "cab", "sab", "sub"
     * - Phrases: "tory building", "bus stop near sub", etc.
     * - Extracts location from wrapper phrases ("I want to go to CCIS")
     */
    fun matchLocation(userInput: String): LocationInfo? {
        val normalized = userInput.trim().lowercase().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return null

        val noSpaces = normalized.replace(Regex("\\s|[.\\-]"), "")

        // Spelled abbrevs: exact match
        for (abbrev in spelledAbbrevs) {
            if (noSpaces == abbrev) return spelledToInfo[abbrev]
        }

        // Spelled abbrevs: extract letters from "c c i s" or "cc is" style input
        val extractedLetters = normalized.replace(Regex("[^a-z]"), "")
        for (abbrev in spelledAbbrevs) {
            if (extractedLetters == abbrev) return spelledToInfo[abbrev]
        }

        // Spelled with spoken forms: "e t el c" -> etlc, "e t l see" -> etlc
        val normalizedSpelled = normalizeSpelledInput(userInput)
        for (abbrev in spelledAbbrevs) {
            if (normalizedSpelled == abbrev) return spelledToInfo[abbrev]
        }

        // Word abbrevs: cab, sab, sub
        if (noSpaces.length <= 4 && wordAbbrevs.containsKey(noSpaces)) {
            return wordAbbrevs[noSpaces]
        }

        // Phrase match: check variants (including embedded in longer utterance)
        for ((info, variants) in phraseLocations) {
            if (variants.any { v -> normalized.contains(v) || v.contains(normalized) }) return info
        }

        // Extract location from wrapper phrases: "I want to go to ccis", "take me to the sub"
        val tokens = normalized.split(Regex("\\s+"))
        for (token in tokens) {
            val t = token.replace(Regex("[^a-z]"), "")
            if (t in spelledToInfo) return spelledToInfo[t]
            if (t in wordAbbrevs) return wordAbbrevs[t]
        }

        // Multi-token: "the sub", "to ccis", "go to etlc"
        if (normalized.contains(" to ") || normalized.contains(" the ")) {
            val afterTo = normalized.substringAfterLast(" to ").trim().replace(Regex("^the\\s+"), "")
            val cleaned = afterTo.replace(Regex("[^a-z]"), "")
            if (cleaned in spelledToInfo) return spelledToInfo[cleaned]
            if (cleaned in wordAbbrevs) return wordAbbrevs[cleaned]
        }

        return null
    }
}
