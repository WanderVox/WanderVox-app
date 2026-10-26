package com.wandervox.features.log.presentation.detail

import androidx.lifecycle.SavedStateHandle
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.domain.repository.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogDetailViewModelTest {

    private val logRepository = FakeLogRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        logRepository.entries.value = listOf(ENTRY, TravelLogEntry(id = "other", originalText = "Gracias"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts loading before anyone collects`() {
        val viewModel = LogDetailViewModel(SavedStateHandle(mapOf(ARG to ENTRY.id)), logRepository)

        assertEquals(LogDetailUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `existing entry id emits the loaded entry`() = runTest {
        val viewModel = createSubscribedViewModel(entryId = ENTRY.id)

        assertEquals(LogDetailUiState.Loaded(ENTRY), viewModel.uiState.value)
        assertEquals(listOf(ENTRY.id), logRepository.requestedIds)
    }

    @Test
    fun `unknown entry id emits not found`() = runTest {
        val viewModel = createSubscribedViewModel(entryId = "missing")

        assertEquals(LogDetailUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun `missing or blank argument emits not found without querying the repository`() = runTest {
        val withoutArg = createSubscribedViewModel(entryId = null)
        val blankArg = createSubscribedViewModel(entryId = " ")

        assertEquals(LogDetailUiState.NotFound, withoutArg.uiState.value)
        assertEquals(LogDetailUiState.NotFound, blankArg.uiState.value)
        assertEquals(emptyList<String>(), logRepository.requestedIds)
    }

    @Test
    fun `entry updates are reflected while the screen is open`() = runTest {
        val viewModel = createSubscribedViewModel(entryId = ENTRY.id)

        val edited = ENTRY.copy(notes = "Editada desde otro dispositivo")
        logRepository.entries.update { all -> all.map { if (it.id == ENTRY.id) edited else it } }

        assertEquals(LogDetailUiState.Loaded(edited), viewModel.uiState.value)
    }

    @Test
    fun `deleting the open entry switches to not found`() = runTest {
        val viewModel = createSubscribedViewModel(entryId = ENTRY.id)

        logRepository.entries.update { all -> all.filterNot { it.id == ENTRY.id } }

        assertEquals(LogDetailUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun `failing read emits not found`() = runTest {
        logRepository.failGetEntry = true
        val viewModel = createSubscribedViewModel(entryId = ENTRY.id)

        assertEquals(LogDetailUiState.NotFound, viewModel.uiState.value)
    }

    private fun TestScope.createSubscribedViewModel(entryId: String?): LogDetailViewModel {
        val savedStateHandle = SavedStateHandle(entryId?.let { mapOf(ARG to it) }.orEmpty())
        val viewModel = LogDetailViewModel(savedStateHandle, logRepository)
        // uiState is WhileSubscribed: keep a collector alive like the screen does.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private companion object {
        const val ARG = LogDetailViewModel.ENTRY_ID_ARG
        val ENTRY = TravelLogEntry(
            id = "entry-1",
            userId = "me",
            originalText = "¿Dónde está la estación?",
            translatedText = "Where is the station?",
        )
    }
}

/** Only [getEntry] matters here; the rest of the contract is unused by the detail screen. */
private class FakeLogRepository : LogRepository {
    val entries = MutableStateFlow<List<TravelLogEntry>>(emptyList())
    var failGetEntry = false
    val requestedIds = mutableListOf<String>()

    override fun getEntry(entryId: String): Flow<TravelLogEntry?> {
        requestedIds += entryId
        return if (failGetEntry) flow { throw IllegalStateException("boom") }
        else entries.map { all -> all.firstOrNull { it.id == entryId } }
    }

    override fun getUserEntries(userId: String): Flow<List<TravelLogEntry>> = emptyFlow()
    override fun getCommunityEntries(): Flow<List<TravelLogEntry>> = emptyFlow()
    override suspend fun saveEntry(entry: TravelLogEntry) = Result.success(Unit)
    override suspend fun toggleLike(entryId: String, userId: String) = Result.success(Unit)
    override suspend fun deleteEntry(entryId: String) = Result.success(Unit)
    override suspend fun syncPending() = Result.success(Unit)
}
