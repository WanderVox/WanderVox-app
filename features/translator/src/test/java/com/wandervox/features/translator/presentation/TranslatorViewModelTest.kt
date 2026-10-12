package com.wandervox.features.translator.presentation

import com.wandervox.domain.model.Coordinates
import com.wandervox.domain.model.TravelLogEntry
import com.wandervox.domain.repository.LogRepository
import com.wandervox.domain.repository.TranslatorRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslatorViewModelTest {

    private val translatorRepository = FakeTranslatorRepository()
    private val logRepository = FakeLogRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows initializing state until models are ready`() = runTest {
        val gate = CompletableDeferred<Result<Unit>>()
        translatorRepository.initializeGate = gate
        val viewModel = createSubscribedViewModel()

        assertTrue(viewModel.uiState.value.isInitializingModels)
        gate.complete(Result.success(Unit))
        assertFalse(viewModel.uiState.value.isInitializingModels)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("ES" to "EN", translatorRepository.initializedWith)
    }

    @Test
    fun `failed initialization surfaces an error and toggle retries it`() = runTest {
        translatorRepository.initializeResult = Result.failure(IllegalStateException("offline"))
        val viewModel = createSubscribedViewModel()

        assertNotNull(viewModel.uiState.value.errorMessage)
        translatorRepository.initializeResult = Result.success(Unit)
        viewModel.onEvent(TranslatorUiEvent.OnToggleListening)

        assertEquals(2, translatorRepository.initializeCalls)
        assertEquals(0, translatorRepository.startCalls)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `toggle starts and stops listening based on repository state`() = runTest {
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(TranslatorUiEvent.OnToggleListening)
        assertTrue(viewModel.uiState.value.isListening)

        viewModel.onEvent(TranslatorUiEvent.OnToggleListening)
        assertFalse(viewModel.uiState.value.isListening)
        assertEquals(1, translatorRepository.startCalls)
        assertEquals(1, translatorRepository.stopCalls)
    }

    @Test
    fun `repository text streams are reflected in ui state`() = runTest {
        val viewModel = createSubscribedViewModel()

        translatorRepository.recognized.value = "Hola"
        translatorRepository.translated.value = "Hello"

        assertEquals("Hola", viewModel.uiState.value.recognizedText)
        assertEquals("Hello", viewModel.uiState.value.translatedText)
    }

    @Test
    fun `swap flips language labels and delegates to repository`() = runTest {
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(TranslatorUiEvent.OnSwapLanguages)

        assertEquals("EN", viewModel.uiState.value.sourceLanguage)
        assertEquals("ES", viewModel.uiState.value.targetLanguage)
        assertEquals(1, translatorRepository.swapCalls)
    }

    @Test
    fun `swap is ignored while models are not ready`() = runTest {
        translatorRepository.initializeResult = Result.failure(IllegalStateException())
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(TranslatorUiEvent.OnSwapLanguages)

        assertEquals("ES", viewModel.uiState.value.sourceLanguage)
        assertEquals(0, translatorRepository.swapCalls)
    }

    @Test
    fun `save assembles the log entry and reports success`() = runTest {
        val viewModel = createSubscribedViewModel()
        translatorRepository.recognized.value = " ¿Dónde está la estación? "
        translatorRepository.translated.value = "Where is the station?"
        val coordinates = Coordinates(latitude = -34.6, longitude = -58.4)

        viewModel.onEvent(TranslatorUiEvent.OnSaveToLog(category = "  ", notes = " Ask twice ", coordinates))

        val saved = logRepository.savedEntries.single()
        assertEquals("¿Dónde está la estación?", saved.originalText)
        assertEquals("Where is the station?", saved.translatedText)
        assertEquals("ES", saved.sourceLanguage)
        assertEquals("EN", saved.targetLanguage)
        assertEquals("General", saved.category)
        assertEquals("Ask twice", saved.notes)
        assertEquals(coordinates, saved.coordinates)
        assertTrue(viewModel.uiState.value.isSavedSuccess)

        viewModel.onEvent(TranslatorUiEvent.OnResetSaveSuccess)
        assertFalse(viewModel.uiState.value.isSavedSuccess)
    }

    @Test
    fun `save without recognized text shows an error and persists nothing`() = runTest {
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(TranslatorUiEvent.OnSaveToLog(category = "Food", notes = "", coordinates = null))

        assertTrue(logRepository.savedEntries.isEmpty())
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.onEvent(TranslatorUiEvent.OnDismissError)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `failed save surfaces an error`() = runTest {
        logRepository.saveResult = Result.failure(IllegalStateException("disk full"))
        val viewModel = createSubscribedViewModel()
        translatorRepository.recognized.value = "Hola"

        viewModel.onEvent(TranslatorUiEvent.OnSaveToLog(category = "Food", notes = "", coordinates = null))

        assertFalse(viewModel.uiState.value.isSavedSuccess)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }

    /** Subscribes to uiState so SharingStarted.WhileSubscribed keeps it updated, as the UI would. */
    private fun TestScope.createSubscribedViewModel(): TranslatorViewModel {
        val viewModel = TranslatorViewModel(translatorRepository, logRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private class FakeTranslatorRepository : TranslatorRepository {
        val recognized = MutableStateFlow("")
        val translated = MutableStateFlow("")
        private val listening = MutableStateFlow(false)

        override val recognizedText: StateFlow<String> = recognized
        override val translatedText: StateFlow<String> = translated
        override val isListening: StateFlow<Boolean> = listening

        var initializeResult: Result<Unit> = Result.success(Unit)
        var initializeGate: CompletableDeferred<Result<Unit>>? = null
        var initializedWith: Pair<String, String>? = null
        var initializeCalls = 0
        var startCalls = 0
        var stopCalls = 0
        var swapCalls = 0

        override suspend fun initializeModels(sourceLanguage: String, targetLanguage: String): Result<Unit> {
            initializeCalls++
            initializedWith = sourceLanguage to targetLanguage
            return initializeGate?.await() ?: initializeResult
        }

        override fun startListening() {
            startCalls++
            listening.value = true
        }

        override fun stopListening() {
            stopCalls++
            listening.value = false
        }

        override fun swapLanguages() {
            swapCalls++
        }

        override fun release() = Unit
    }

    private class FakeLogRepository : LogRepository {
        val savedEntries = mutableListOf<TravelLogEntry>()
        var saveResult: Result<Unit> = Result.success(Unit)

        override fun getUserEntries(userId: String): Flow<List<TravelLogEntry>> = emptyFlow()
        override fun getCommunityEntries(): Flow<List<TravelLogEntry>> = emptyFlow()

        override suspend fun saveEntry(entry: TravelLogEntry): Result<Unit> {
            if (saveResult.isSuccess) savedEntries += entry
            return saveResult
        }

        override suspend fun toggleLike(entryId: String, userId: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteEntry(entryId: String): Result<Unit> = Result.success(Unit)
    }
}
