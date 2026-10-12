package com.wandervox.core.network.model

class UnsupportedLanguageException(code: String) :
    IllegalArgumentException("Unsupported language code: '$code'")

class LanguagePackUnavailableException(language: SupportedLanguage, cause: Throwable) :
    IllegalStateException(
        "ML Kit language pack for $language is not on the device and could not be downloaded. " +
            "Connect to the internet once to download it.",
        cause,
    )
