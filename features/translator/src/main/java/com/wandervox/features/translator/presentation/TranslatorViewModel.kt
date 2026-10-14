package com.wandervox.features.translator.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.domain.repository.LogRepository
import com.wandervox.core.domain.repository.TranslatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TranslatorViewModel @Inject constructor(
    private val translatorRepository: TranslatorRepository,
    private val logRepository: LogRepository,
) : ViewModel() {

    /** State owned by this screen; speech/translation state comes from the repository. */
    private data class PresentationState(
        val sourceLanguage: String = DEFAULT_SOURCE_LANGUAGE,
        val targetLanguage: String = DEFAULT_TARGET_LANGUAGE,
        val isInitializingModels: Boolean = true,
        val areModelsReady: Boolean = false,
        val errorMessage: String? = null,
        val isSavedSuccess: Boolean = false,
    )

    private val presentationState = MutableStateFlow(PresentationState())
    private var initializationJob: Job? = null
    private var saveJob: Job? = null

    val uiState: StateFlow<TranslatorUiState> = combine(
        presentationState,
        translatorRepository.recognizedText,
        translatorRepository.translatedText,
        translatorRepository.isListening,
    ) { presentation, recognizedText, translatedText, isListening ->
        TranslatorUiState(
            sourceLanguage = presentation.sourceLanguage,
            targetLanguage = presentation.targetLanguage,
            recognizedText = recognizedText,
            translatedText = translatedText,
            isListening = isListening,
            isInitializingModels = presentation.isInitializingModels,
            errorMessage = presentation.errorMessage,
            isSavedSuccess = presentation.isSavedSuccess,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = TranslatorUiState(isInitializingModels = true),
    )

    init {
        initializeModels()
    }

    fun onEvent(event: TranslatorUiEvent) {
        when (event) {
            TranslatorUiEvent.OnToggleListening -> toggleListening()
            TranslatorUiEvent.OnSwapLanguages -> swapLanguages()
            is TranslatorUiEvent.OnSaveToLog -> saveToLog(event)
            TranslatorUiEvent.OnDismissError -> presentationState.update { it.copy(errorMessage = null) }
            TranslatorUiEvent.OnResetSaveSuccess -> presentationState.update { it.copy(isSavedSuccess = false) }
        }
    }

    private fun initializeModels() {
        if (initializationJob?.isActive == true) return
        initializationJob = viewModelScope.launch {
            presentationState.update { it.copy(isInitializingModels = true, errorMessage = null) }
            val languages = presentationState.value
            translatorRepository.initializeModels(languages.sourceLanguage, languages.targetLanguage)
                .onSuccess {
                    presentationState.update { it.copy(isInitializingModels = false, areModelsReady = true) }
                }
                .onFailure {
                    presentationState.update {
                        it.copy(
                            isInitializingModels = false,
                            areModelsReady = false,
                            errorMessage = MODELS_UNAVAILABLE_MESSAGE,
                        )
                    }
                }
        }
    }

    private fun toggleListening() {
        val presentation = presentationState.value
        when {
            presentation.isInitializingModels -> Unit
            // A failed first launch (e.g. no connection for the translation packs) retries here.
            !presentation.areModelsReady -> initializeModels()
            translatorRepository.isListening.value -> translatorRepository.stopListening()
            else -> translatorRepository.startListening()
        }
    }

    private fun swapLanguages() {
        // The repository ignores swaps before its models are loaded; keep the labels in sync.
        if (!presentationState.value.areModelsReady) return
        translatorRepository.swapLanguages()
        presentationState.update {
            it.copy(sourceLanguage = it.targetLanguage, targetLanguage = it.sourceLanguage)
        }
    }

    private fun saveToLog(event: TranslatorUiEvent.OnSaveToLog) {
        if (saveJob?.isActive == true) return

        val originalText = translatorRepository.recognizedText.value.trim()
        if (originalText.isEmpty()) {
            presentationState.update { it.copy(errorMessage = NOTHING_TO_SAVE_MESSAGE) }
            return
        }

        val languages = presentationState.value
        val entry = TravelLogEntry(
            originalText = originalText,
            translatedText = translatorRepository.translatedText.value.trim(),
            sourceLanguage = languages.sourceLanguage,
            targetLanguage = languages.targetLanguage,
            timestamp = System.currentTimeMillis(),
            coordinates = event.coordinates,
            category = event.category.trim().ifEmpty { DEFAULT_CATEGORY },
            notes = event.notes.trim(),
        )

        saveJob = viewModelScope.launch {
            logRepository.saveEntry(entry)
                .onSuccess { presentationState.update { it.copy(isSavedSuccess = true) } }
                .onFailure { presentationState.update { it.copy(errorMessage = SAVE_FAILED_MESSAGE) } }
        }
    }

    override fun onCleared() {
        // The repository releases on its own scope, so this is safe after viewModelScope is cancelled.
        translatorRepository.release()
        super.onCleared()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val DEFAULT_SOURCE_LANGUAGE = "ES"
        const val DEFAULT_TARGET_LANGUAGE = "EN"
        const val DEFAULT_CATEGORY = "General"

        // TODO: move to string resources (UiText) once the screen is localized.
        const val MODELS_UNAVAILABLE_MESSAGE =
            "Offline models could not be prepared. Connect to the internet once to download the language packs, then try again."
        const val NOTHING_TO_SAVE_MESSAGE = "There is no translation to save yet."
        const val SAVE_FAILED_MESSAGE = "The entry could not be saved to your travel log. Please try again."
    }
}
