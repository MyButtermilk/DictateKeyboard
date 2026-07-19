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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GermanOrthographyTest {

    @Test
    fun restoresPlainAndDigraphUmlauts() {
        assertVariant("Madchen", "mädchen")
        assertVariant("Maedchen", "mädchen")
        assertVariant("ueber", "über")
        assertVariant("schoen", "schön")
    }

    @Test
    fun restoresSharpSAndCombinesTransformations() {
        assertVariant("strasse", "straße")
        assertVariant("gross", "groß")
        assertVariant("weiss", "weiß")
        assertVariant("Fussball", "fußball")
        assertVariant("heissen", "heißen")
        assertVariant("Grusse", "grüße", transformations = 2)
    }

    @Test
    fun swissGermanKeepsSsButStillRestoresUmlauts() {
        val strasseVariants = GermanOrthography.variants("strasse", allowSharpS = false)
        assertFalse(strasseVariants.keys.any { 'ß' in it })
        assertVariant("Grusse", "grüsse", allowSharpS = false)
        assertVariant("Gruesse", "grüsse", allowSharpS = false)
        assertEquals("Strasse", GermanOrthography.toSwissSpelling("Straße"))
        assertEquals("GRUSS", GermanOrthography.toSwissSpelling("GRUẞ"))
    }

    @Test
    fun knownWordsNeedPositiveContextEvidence() {
        assertFalse(
            GermanOrthography.shouldAutoCommitKnownVariant(
                typedFrequency = 200,
                candidateFrequency = 255,
                typedContextScore = 0.0,
                candidateContextScore = 0.0,
                transformations = 1,
            ),
        )
        assertFalse(
            GermanOrthography.shouldAutoCommitKnownVariant(
                typedFrequency = 200,
                candidateFrequency = 200,
                typedContextScore = 1.0,
                candidateContextScore = 1.0,
                transformations = 1,
            ),
        )
        assertTrue(
            GermanOrthography.shouldAutoCommitKnownVariant(
                typedFrequency = 200,
                candidateFrequency = 200,
                typedContextScore = 0.0,
                candidateContextScore = 1.0,
                transformations = 1,
            ),
        )
    }

    private fun assertVariant(
        typed: String,
        expected: String,
        allowSharpS: Boolean = true,
        transformations: Int = 1,
    ) {
        assertEquals(
            transformations,
            GermanOrthography.variants(typed, allowSharpS)[expected],
            "$typed should generate $expected",
        )
    }
}
