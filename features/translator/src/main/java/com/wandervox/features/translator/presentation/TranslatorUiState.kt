package com.wandervox.features.translator.presentation

/** Single, immutable snapshot of everything the translator screen renders. */
data class TranslatorUiState(
    val sourceLanguage: String = "ES",
    val targetLanguage: String = "EN",
    val recognizedText: String = "",
    val translatedText: String = "",
    val isListening: Boolean = false,
    val isInitializingModels: Boolean = false,
    val errorMessage: String? = null,
    val isSavedSuccess: Boolean = false,
)
