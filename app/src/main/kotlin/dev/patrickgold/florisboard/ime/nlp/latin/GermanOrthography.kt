/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import kotlin.math.ln

/**
 * Generates the German orthographic forms users commonly omit while typing quickly. Every generated form
 * still has to be accepted by the active dictionary before it can become a suggestion or correction.
 */
internal object GermanOrthography {
    private const val MAX_TRANSFORMATIONS = 4
    private const val MAX_VARIANTS = 64

    // Omitting an umlaut/ß is intentional keyboard shorthand, so it is less surprising than a generic typo.
    private const val TRANSFORMATION_LOG_PENALTY = -0.25
    private const val KNOWN_WORD_AUTOCORRECT_MARGIN = 0.25

    fun appliesTo(language: String): Boolean = language.equals("de", ignoreCase = true)

    fun allowsSharpS(country: String): Boolean = !country.equals("CH", ignoreCase = true)

    /** Standard German dictionary spelling rendered for Swiss German, which always writes ss instead of ß. */
    fun toSwissSpelling(word: String): String = word.replace("ß", "ss").replace("ẞ", "SS")

    /**
     * ASCII/digraph variants for German: a/o/u -> ä/ö/ü, ae/oe/ue -> ä/ö/ü and, when allowed, ss -> ß.
     * The value is the minimum number of orthographic transformations used to produce the candidate.
     */
    fun variants(word: String, allowSharpS: Boolean): Map<String, Int> =
        variants(word, restoreUmlauts = true, restoreSharpS = allowSharpS)

    fun logLikelihood(transformations: Int): Double = TRANSFORMATION_LOG_PENALTY * transformations

    /**
     * A real typed word may only be replaced when bigram context positively favors the variant and the
     * complete noisy-channel score clears a conservative margin. This keeps schon/dass/muss intact unless
     * context supplies actual evidence for alternatives such as schön.
     */
    fun shouldAutoCommitKnownVariant(
        typedFrequency: Int,
        candidateFrequency: Int,
        typedContextScore: Double,
        candidateContextScore: Double,
        transformations: Int,
    ): Boolean {
        if (candidateContextScore <= typedContextScore) return false
        val typedScore = ln((typedFrequency + 1).toDouble()) + typedContextScore
        val candidateScore = ln((candidateFrequency + 1).toDouble()) +
            logLikelihood(transformations) + candidateContextScore
        return candidateScore >= typedScore + KNOWN_WORD_AUTOCORRECT_MARGIN
    }

    private fun variants(
        word: String,
        restoreUmlauts: Boolean,
        restoreSharpS: Boolean,
    ): Map<String, Int> {
        val lower = word.lowercase()
        if (lower.isEmpty() || lower.length > 64) return emptyMap()
        if ((!restoreUmlauts || lower.none { it == 'a' || it == 'o' || it == 'u' }) &&
            (!restoreSharpS || "ss" !in lower)
        ) {
            return emptyMap()
        }

        val variants = LinkedHashMap<String, Int>()
        var frontier = linkedSetOf(lower)
        for (transformations in 1..MAX_TRANSFORMATIONS) {
            val next = LinkedHashSet<String>()
            for (current in frontier) {
                for (candidate in oneStepVariants(current, restoreUmlauts, restoreSharpS)) {
                    if (candidate == lower || variants.containsKey(candidate)) continue
                    variants[candidate] = transformations
                    next.add(candidate)
                    if (variants.size >= MAX_VARIANTS) return variants
                }
            }
            if (next.isEmpty()) break
            frontier = next
        }
        return variants
    }

    private fun oneStepVariants(
        word: String,
        restoreUmlauts: Boolean,
        restoreSharpS: Boolean,
    ): Set<String> {
        val variants = LinkedHashSet<String>()
        for (i in word.indices) {
            if (restoreUmlauts) {
                val umlaut = when (word[i]) {
                    'a' -> 'ä'
                    'o' -> 'ö'
                    'u' -> 'ü'
                    else -> null
                }
                if (umlaut != null) {
                    variants.add(word.replaceRange(i, i + 1, umlaut.toString()))
                    if (i + 1 < word.length && word[i + 1] == 'e') {
                        variants.add(word.replaceRange(i, i + 2, umlaut.toString()))
                    }
                }
            }
            if (restoreSharpS && word[i] == 's' && i + 1 < word.length && word[i + 1] == 's') {
                variants.add(word.replaceRange(i, i + 2, "ß"))
            }
        }
        return variants
    }
}
