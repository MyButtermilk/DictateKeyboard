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

/**
 * Converts Dictate's mostly ISO-639 language choices to the exact BCP-47 locales accepted by Gemini
 * 3.5 Transcribe. An unsupported choice deliberately becomes null so Gemini auto-detects instead of
 * rejecting the entire batch request or live setup message.
 */
internal fun geminiTranscriptionLanguageCode(language: String?): String? {
    val key = language?.trim()?.lowercase()?.takeIf { it.isNotEmpty() && it != "detect" } ?: return null
    return GEMINI_TRANSCRIPTION_LANGUAGE_CODES[key] ?: GEMINI_TRANSCRIPTION_LANGUAGE_ALIASES[key]
}

private val GEMINI_TRANSCRIPTION_LANGUAGE_CODES = listOf(
    "af-ZA", "am-ET", "ar-EG", "hy-AM", "as-IN", "az-AZ", "be-BY", "bn-BD", "bn-IN",
    "bs-BA", "bg-BG", "rup-BG", "my-MM", "yue-Hant-HK", "ca-ES", "ceb", "km-KH", "hr-HR",
    "cs-CZ", "da-DK", "nl-NL", "en-GB", "en-IN", "en-US", "et-EE", "fa-IR", "fil-PH",
    "fi-FI", "fr-FR", "gl-ES", "ka-GE", "de-DE", "el-GR", "gu-IN", "ha-NG", "he-IL",
    "hi-IN", "hu-HU", "is-IS", "id-ID", "it-IT", "ja-JP", "jv-ID", "kea-CV", "kn-IN",
    "kk-KZ", "ko-KR", "ky-KG", "lv-LV", "ln-CD", "lt-LT", "mk-MK", "ms-MY", "ml-IN",
    "mt-MT", "cmn-Hans-CN", "mr-IN", "mn-MN", "ne-NP", "nb-NO", "or-IN", "pl-PL", "pt-BR",
    "pt-PT", "pa-IN", "pa-Guru-IN", "ro-RO", "ru-RU", "sr-RS", "sd-Arab-IN", "sk-SK",
    "sl-SI", "es-419", "es-US", "sw-KE", "sv-SE", "tg-TJ", "te-IN", "th-TH", "tr-TR",
    "uk-UA", "uz-UZ", "vi-VN",
).associateBy { it.lowercase() }

private val GEMINI_TRANSCRIPTION_LANGUAGE_ALIASES = mapOf(
    "af" to "af-ZA",
    "am" to "am-ET",
    "ar" to "ar-EG",
    "hy" to "hy-AM",
    "as" to "as-IN",
    "az" to "az-AZ",
    "be" to "be-BY",
    "bn" to "bn-BD",
    "bs" to "bs-BA",
    "bg" to "bg-BG",
    "my" to "my-MM",
    "yue-cn" to "yue-Hant-HK",
    "yue-hk" to "yue-Hant-HK",
    "ca" to "ca-ES",
    "km" to "km-KH",
    "hr" to "hr-HR",
    "cs" to "cs-CZ",
    "da" to "da-DK",
    "nl" to "nl-NL",
    "en" to "en-US",
    "et" to "et-EE",
    "fa" to "fa-IR",
    "tl" to "fil-PH",
    "fi" to "fi-FI",
    "fr" to "fr-FR",
    "gl" to "gl-ES",
    "ka" to "ka-GE",
    "de" to "de-DE",
    "el" to "el-GR",
    "gu" to "gu-IN",
    "ha" to "ha-NG",
    "he" to "he-IL",
    "hi" to "hi-IN",
    "hu" to "hu-HU",
    "is" to "is-IS",
    "id" to "id-ID",
    "it" to "it-IT",
    "ja" to "ja-JP",
    "jw" to "jv-ID",
    "kn" to "kn-IN",
    "kk" to "kk-KZ",
    "ko" to "ko-KR",
    "lv" to "lv-LV",
    "ln" to "ln-CD",
    "lt" to "lt-LT",
    "mk" to "mk-MK",
    "ms" to "ms-MY",
    "ml" to "ml-IN",
    "mt" to "mt-MT",
    "zh-cn" to "cmn-Hans-CN",
    "mr" to "mr-IN",
    "mn" to "mn-MN",
    "ne" to "ne-NP",
    "no" to "nb-NO",
    "pl" to "pl-PL",
    "pt" to "pt-BR",
    "pa" to "pa-IN",
    "ro" to "ro-RO",
    "ru" to "ru-RU",
    "sr" to "sr-RS",
    "sd" to "sd-Arab-IN",
    "sk" to "sk-SK",
    "sl" to "sl-SI",
    "es" to "es-419",
    "sw" to "sw-KE",
    "sv" to "sv-SE",
    "tg" to "tg-TJ",
    "te" to "te-IN",
    "th" to "th-TH",
    "tr" to "tr-TR",
    "uk" to "uk-UA",
    "uz" to "uz-UZ",
    "vi" to "vi-VN",
)
