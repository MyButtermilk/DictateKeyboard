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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import okhttp3.Dns
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.io.path.createTempFile

class OpenAiCompatibleClientNetworkTest : FunSpec({
    test("batch clients preserve system DNS order and bound only the connect timeout") {
        val client = OpenAiCompatibleClient(
            ProviderConfig(
                baseUrl = "https://example.test/v1/",
                apiKey = "test",
                timeoutSeconds = 120,
            ),
        ).buildClient()

        client.dns shouldBe Dns.SYSTEM
        client.connectTimeoutMillis shouldBe 8_000
        client.callTimeoutMillis shouldBe 120_000
        client.readTimeoutMillis shouldBe 120_000
        client.writeTimeoutMillis shouldBe 120_000
    }

    test("OpenRouter streams an OpenAI-compatible multipart upload") {
        ProviderRegistry.OPENROUTER.transcriptionApi shouldBe TranscriptionApi.OPENROUTER_MULTIPART

        val audio = createTempFile(suffix = ".wav").toFile().apply {
            writeBytes("RIFF-test-audio".encodeToByteArray())
        }
        try {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(200).setBody("""{"text":"Hallo Welt"}"""))
                val client = OpenAiCompatibleClient(
                    ProviderConfig(
                        baseUrl = server.url("/").toString(),
                        apiKey = "test",
                        transcriptionApi = TranscriptionApi.OPENROUTER_MULTIPART,
                    ),
                )

                val result = client.transcribe(
                    TranscriptionRequest(
                        audioFile = audio,
                        model = "microsoft/mai-transcribe-1.5",
                        language = "de",
                        prompt = "Eigennamen beibehalten",
                    ),
                )
                val recorded = server.takeRequest()
                val body = recorded.body.readUtf8()

                result.text shouldBe "Hallo Welt"
                recorded.method shouldBe "POST"
                recorded.path shouldBe "/audio/transcriptions"
                recorded.getHeader("Content-Type").orEmpty() shouldStartWith "multipart/form-data; boundary="
                body shouldContain "name=\"file\"; filename=\"${audio.name}\""
                body shouldContain "name=\"model\""
                body shouldContain "microsoft/mai-transcribe-1.5"
                body shouldContain "name=\"language\""
                body shouldContain "de"
                body shouldContain "name=\"prompt\""
                body shouldContain "name=\"temperature\""
                body shouldContain "0.0"
                body shouldContain "RIFF-test-audio"
                body shouldNotContain "input_audio"
                server.requestCount shouldBe 1
            }
        } finally {
            audio.delete()
        }
    }

    // Issue #248: gpt-transcribe renamed the singular `language` field to `languages`. Sending the wrong
    // one silently drops the user's language choice instead of failing, so both directions are asserted.
    test("gpt-transcribe receives the language as `languages`, older models as `language`") {
        val audio = createTempFile(suffix = ".wav").toFile().apply {
            writeBytes("RIFF-test-audio".encodeToByteArray())
        }
        try {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(200).setBody("""{"text":"Hallo"}"""))
                server.enqueue(MockResponse().setResponseCode(200).setBody("""{"text":"Hallo"}"""))
                val client = OpenAiCompatibleClient(
                    ProviderConfig(baseUrl = server.url("/").toString(), apiKey = "test"),
                )

                client.transcribe(TranscriptionRequest(audio, "gpt-transcribe", language = "de"))
                val newModel = server.takeRequest().body.readUtf8()
                newModel shouldContain "name=\"languages\""
                newModel shouldNotContain "name=\"language\"\r\n"

                client.transcribe(TranscriptionRequest(audio, "gpt-4o-mini-transcribe", language = "de"))
                val oldModel = server.takeRequest().body.readUtf8()
                oldModel shouldContain "name=\"language\""
                oldModel shouldNotContain "name=\"languages\""
            }
        } finally {
            audio.delete()
        }
    }

    test("separate provider instances reuse the same HTTP connection") {
        val audio = createTempFile(suffix = ".wav").toFile().apply {
            writeBytes("RIFF-test-audio".encodeToByteArray())
        }
        try {
            MockWebServer().use { server ->
                repeat(2) {
                    server.enqueue(MockResponse().setResponseCode(200).setBody("""{"text":"ok"}"""))
                }
                val config = ProviderConfig(
                    baseUrl = server.url("/").toString(),
                    apiKey = "test",
                    transcriptionApi = TranscriptionApi.OPENROUTER_MULTIPART,
                )

                repeat(2) {
                    OpenAiCompatibleClient(config).transcribe(
                        TranscriptionRequest(audio, "microsoft/mai-transcribe-1.5"),
                    )
                }

                val first = server.takeRequest()
                val second = server.takeRequest()
                first.sequenceNumber shouldBe 0
                second.sequenceNumber shouldBe 1
                server.requestCount shouldBe 2
            }
        } finally {
            audio.delete()
        }
    }

    test("OpenRouter falls back to documented JSON only when multipart is rejected") {
        val audio = createTempFile(suffix = ".wav").toFile().apply {
            writeBytes("RIFF-test-audio".encodeToByteArray())
        }
        try {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setResponseCode(415).setBody("unsupported media type"))
                server.enqueue(MockResponse().setResponseCode(200).setBody("""{"text":"fallback ok"}"""))
                val client = OpenAiCompatibleClient(
                    ProviderConfig(
                        baseUrl = server.url("/").toString(),
                        apiKey = "test",
                        transcriptionApi = TranscriptionApi.OPENROUTER_MULTIPART,
                    ),
                )

                val result = client.transcribe(
                    TranscriptionRequest(audio, "microsoft/mai-transcribe-1.5", language = "de"),
                )

                val multipart = server.takeRequest()
                val json = server.takeRequest()
                val jsonBody = json.body.readUtf8()
                result.text shouldBe "fallback ok"
                multipart.getHeader("Content-Type").orEmpty() shouldStartWith "multipart/form-data"
                json.getHeader("Content-Type").orEmpty() shouldStartWith "application/json"
                jsonBody shouldContain "\"input_audio\""
                jsonBody shouldContain "\"temperature\":0.0"
                jsonBody shouldNotContain "multipart/form-data"
                server.requestCount shouldBe 2
            }
        } finally {
            audio.delete()
        }
    }

    test("OpenRouter transcription policy never replays a billable POST") {
        val audio = createTempFile(suffix = ".wav").toFile().apply { writeBytes(ByteArray(32)) }
        try {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse().setResponseCode(503).setBody("""{"error":{"message":"busy"}}"""),
                )
                server.enqueue(
                    MockResponse().setResponseCode(200).setBody("""{"text":"duplicate"}"""),
                )

                val client = OpenAiCompatibleClient(
                    ProviderConfig(
                        baseUrl = server.url("/").toString(),
                        apiKey = "test",
                        transcriptionApi = TranscriptionApi.OPENROUTER_MULTIPART,
                    ),
                )

                val error = shouldThrow<DictateApiException> {
                    client.transcribe(TranscriptionRequest(audio, "microsoft/mai-transcribe-1.5"))
                }

                error.kind shouldBe DictateApiException.Kind.SERVER_ERROR
                server.requestCount shouldBe 1
            }
        } finally {
            audio.delete()
        }
    }

    test("OpenRouter does not fall back for semantic client errors") {
        val audio = createTempFile(suffix = ".wav").toFile().apply { writeBytes(ByteArray(32)) }
        try {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse().setResponseCode(400)
                        .setBody("""{"error":{"message":"unknown model"}}"""),
                )
                val client = OpenAiCompatibleClient(
                    ProviderConfig(
                        baseUrl = server.url("/").toString(),
                        apiKey = "test",
                        transcriptionApi = TranscriptionApi.OPENROUTER_MULTIPART,
                    ),
                )

                shouldThrow<DictateApiException> {
                    client.transcribe(TranscriptionRequest(audio, "missing/model"))
                }
                server.requestCount shouldBe 1
            }
        } finally {
            audio.delete()
        }
    }

    // Issue #284: a rewording that answers with nothing used to come back as "" — and the auto-apply
    // chain then committed that empty string over the user's dictation without a word being said.
    test("an empty completion is a failure, not an answer") {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"choices":[{"message":{"content":""},"finish_reason":"length"}]}""",
                ),
            )
            val client = OpenAiCompatibleClient(
                ProviderConfig(baseUrl = server.url("/v1/").toString(), apiKey = "test"),
            )

            val error = shouldThrow<DictateApiException> {
                client.complete(ChatRequest.ofUser("some-reasoning-model", "Fix my typos"))
            }
            error.kind shouldBe DictateApiException.Kind.UNKNOWN
            error.message.orEmpty() shouldContain "finish_reason=length"
        }
    }

    // Issue #284: decoding runs outside executeForBody's catch, so an unexpected shape escaped as a raw
    // SerializationException — "unknown error" with a kotlinx message that never showed what came back.
    test("a response that cannot be read says what the provider sent") {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200)
                    .setBody("<html><body>502 Bad Gateway (proxy)</body></html>"),
            )
            val client = OpenAiCompatibleClient(
                ProviderConfig(baseUrl = server.url("/v1/").toString(), apiKey = "test"),
            )

            val error = shouldThrow<DictateApiException> {
                client.complete(ChatRequest.ofUser("gpt-4o-mini", "Fix my typos"))
            }
            error.kind shouldBe DictateApiException.Kind.UNKNOWN
            error.message.orEmpty() shouldContain "502 Bad Gateway (proxy)"
        }
    }

    test("Deepgram offers only models the file endpoint can serve") {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {"stt":[
                      {"canonical_name":"nova-3","batch":true,"streaming":true},
                      {"canonical_name":"flux-general-en","batch":false,"streaming":true},
                      {"canonical_name":"whisper-large"}
                    ]}
                    """.trimIndent(),
                ),
            )
            val client = OpenAiCompatibleClient(
                ProviderConfig(
                    baseUrl = server.url("/v1/").toString(),
                    apiKey = "test",
                    transcriptionApi = TranscriptionApi.DEEPGRAM,
                ),
            )

            // flux is streaming-only (#291) and would fail every upload; an entry that says nothing about
            // its endpoints is kept, so a changed catalog leaves the user with a list rather than none.
            client.listModels().map { it.id } shouldBe listOf("nova-3", "whisper-large")
            server.takeRequest().getHeader("Authorization") shouldBe "Token test"
        }
    }

    test("Gemini 3.5 Transcribe uploads audio and calls the Interactions API") {
        ProviderRegistry.GEMINI.transcriptionApi shouldBe TranscriptionApi.GEMINI_INTERACTIONS
        ProviderRegistry.GEMINI.defaultTranscriptionModel shouldBe "gemini-3.5-transcribe"
        ProviderRegistry.GEMINI.supportsRealtime shouldBe true
        ProviderRegistry.GEMINI.defaultRealtimeModel shouldBe "gemini-3.5-transcribe-live"

        val audio = createTempFile(suffix = ".m4a").toFile().apply {
            writeBytes("RIFF-gemini-audio".encodeToByteArray())
        }
        try {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse().setResponseCode(200)
                        .addHeader("X-Goog-Upload-URL", server.url("/upload-session/42")),
                )
                server.enqueue(
                    MockResponse().setResponseCode(200).setBody(
                        """{"file":{"name":"files/test-file","uri":"https://files.test/audio","mimeType":"audio/m4a"}}""",
                    ),
                )
                server.enqueue(
                    MockResponse().setResponseCode(200).setBody(
                        """{"status":"completed","steps":[{"type":"model_output","content":[{"type":"text","text":"Hallo Gemini"}]}]}""",
                    ),
                )
                server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
                val client = OpenAiCompatibleClient(
                    ProviderConfig(
                        baseUrl = server.url("/v1beta/openai/").toString(),
                        apiKey = "gemini-key",
                        transcriptionApi = TranscriptionApi.GEMINI_INTERACTIONS,
                    ),
                )

                val result = client.transcribe(
                    TranscriptionRequest(
                        audioFile = audio,
                        model = "gemini-3.5-transcribe",
                        language = "de",
                        customVocabulary = listOf("Immler", " Vier Höhen ", "Immler"),
                    ),
                )

                val start = server.takeRequest()
                val upload = server.takeRequest()
                val interaction = server.takeRequest()
                val cleanup = server.takeRequest()
                result.text shouldBe "Hallo Gemini"
                start.method shouldBe "POST"
                start.path shouldBe "/upload/v1beta/files"
                start.getHeader("x-goog-api-key") shouldBe "gemini-key"
                start.getHeader("X-Goog-Upload-Protocol") shouldBe "resumable"
                start.getHeader("X-Goog-Upload-Command") shouldBe "start"
                start.body.readUtf8() shouldContain "\"display_name\":\"${audio.name}\""
                upload.method shouldBe "POST"
                upload.path shouldBe "/upload-session/42"
                upload.getHeader("X-Goog-Upload-Command") shouldBe "upload, finalize"
                upload.getHeader("Content-Type") shouldBe "audio/m4a"
                upload.body.readUtf8() shouldBe "RIFF-gemini-audio"
                interaction.method shouldBe "POST"
                interaction.path shouldBe "/v1beta/interactions"
                interaction.getHeader("x-goog-api-key") shouldBe "gemini-key"
                interaction.body.readUtf8().let { body ->
                    body shouldContain "\"model\":\"gemini-3.5-transcribe\""
                    body shouldContain "\"uri\":\"https://files.test/audio\""
                    body shouldContain "\"mime_type\":\"audio/m4a\""
                    body shouldContain "\"language_codes\":[\"de-DE\"]"
                    body shouldContain "\"custom_vocabulary\":[\"Immler\",\"Vier Höhen\"]"
                }
                cleanup.method shouldBe "DELETE"
                cleanup.path shouldBe "/v1beta/files/test-file"
                server.requestCount shouldBe 4
            }
        } finally {
            audio.delete()
        }
    }

    test("Gemini keeps generateContent for an older explicitly selected model") {
        val audio = createTempFile(suffix = ".wav").toFile().apply {
            writeBytes("RIFF-legacy".encodeToByteArray())
        }
        try {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse().setResponseCode(200).setBody(
                        """{"candidates":[{"content":{"parts":[{"text":"legacy works"}]}}]}""",
                    ),
                )
                val client = OpenAiCompatibleClient(
                    ProviderConfig(
                        baseUrl = server.url("/v1beta/openai/").toString(),
                        apiKey = "test",
                        transcriptionApi = TranscriptionApi.GEMINI_INTERACTIONS,
                    ),
                )

                client.transcribe(TranscriptionRequest(audio, "gemini-2.5-flash")).text shouldBe "legacy works"
                server.takeRequest().path shouldBe "/v1beta/models/gemini-2.5-flash:generateContent"
                server.requestCount shouldBe 1
            }
        } finally {
            audio.delete()
        }
    }
})
