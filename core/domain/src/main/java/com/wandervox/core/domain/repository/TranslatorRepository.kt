package com.wandervox.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface TranslatorRepository {
    val recognizedText: StateFlow<String>
    val translatedText: StateFlow<String>
    val isListening: StateFlow<Boolean>

    suspend fun initializeModels(sourceLanguage: String, targetLanguage: String): Result<Unit>
    fun startListening()
    fun stopListening()
    fun swapLanguages()
    fun release()
}