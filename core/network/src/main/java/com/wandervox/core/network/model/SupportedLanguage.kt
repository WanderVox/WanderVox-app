package com.wandervox.core.network.model

import com.google.mlkit.nl.translate.TranslateLanguage

/**
 * Languages that have both a bundled Vosk acoustic model and an ML Kit translation pack.
 */
enum class SupportedLanguage(
    val mlKitCode: String,
    val voskModelAssetName: String,
) {
    SPANISH(TranslateLanguage.SPANISH, "vosk-model-small-es"),
    ENGLISH(TranslateLanguage.ENGLISH, "vosk-model-small-en-us");

    companion object {
        /** Accepts domain codes such as "ES", "en", "en-US" or "en_US". */
        fun fromCode(code: String): SupportedLanguage? {
            val primaryTag = code.trim().substringBefore('-').substringBefore('_')
            return entries.firstOrNull { it.mlKitCode.equals(primaryTag, ignoreCase = true) }
        }
    }
}

data class LanguagePair(
    val source: SupportedLanguage,
    val target: SupportedLanguage,
) {
    fun swapped(): LanguagePair = LanguagePair(source = target, target = source)
}
