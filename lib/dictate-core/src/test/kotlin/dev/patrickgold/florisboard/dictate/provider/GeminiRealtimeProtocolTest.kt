/*
 * Copyright (C) 2026 DevEmperor (Dictate)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package dev.patrickgold.florisboard.dictate.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeminiRealtimeProtocolTest {

    @Test
    fun setupUsesDedicatedModelLanguageAndVocabulary() {
        val setup = Json.parseToJsonElement(
            GeminiRealtimeProtocol.setup(
                model = "models/gemini-3.5-transcribe-live",
                language = "de",
                customVocabulary = listOf("Immler", " Vier Höhen ", "Immler"),
            ),
        ).jsonObject.getValue("setup").jsonObject

        assertEquals("models/gemini-3.5-transcribe-live", setup.getValue("model").jsonPrimitive.content)
        assertEquals(
            listOf("TEXT"),
            setup.getValue("generationConfig").jsonObject
                .getValue("responseModalities").jsonArray.map { it.jsonPrimitive.content },
        )
        val transcription = setup.getValue("inputAudioTranscription").jsonObject
        assertEquals(
            listOf("de-DE"),
            transcription.getValue("languageCodes").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("Immler", "Vier Höhen"),
            transcription.getValue("customVocabulary").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun setupLeavesLanguageCodesEmptyForAutoDetection() {
        val transcription = Json.parseToJsonElement(
            GeminiRealtimeProtocol.setup("gemini-3.5-transcribe-live", "detect", emptyList()),
        ).jsonObject.getValue("setup").jsonObject
            .getValue("inputAudioTranscription").jsonObject

        assertTrue(transcription.getValue("languageCodes").jsonArray.isEmpty())
        assertNull(transcription["customVocabulary"])
    }

    @Test
    fun setupFallsBackToDetectionForUnsupportedLanguage() {
        val transcription = Json.parseToJsonElement(
            GeminiRealtimeProtocol.setup("gemini-3.5-transcribe-live", "sq", emptyList()),
        ).jsonObject.getValue("setup").jsonObject
            .getValue("inputAudioTranscription").jsonObject

        assertTrue(transcription.getValue("languageCodes").jsonArray.isEmpty())
    }

    @Test
    fun parserSeparatesInterimAndAuthoritativeFinalText() {
        val event = GeminiRealtimeProtocol.parse(
            """{"serverContent":{"interimInputTranscription":{"text":"Hallo Gem"},"inputTranscription":{"text":"Hallo Gemini"},"turnComplete":true}}""",
        )!!

        assertEquals("Hallo Gem", event.interim)
        assertEquals("Hallo Gemini", event.final)
        assertTrue(event.completed)
        assertNull(event.error)
    }

    @Test
    fun parserSurfacesSetupAndErrors() {
        assertTrue(GeminiRealtimeProtocol.parse("""{"setupComplete":{}}""")!!.setupComplete)
        assertEquals(
            "invalid API key",
            GeminiRealtimeProtocol.parse("""{"error":{"message":"invalid API key"}}""")!!.error,
        )
    }
}
