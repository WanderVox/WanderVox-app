package com.wandervox.features.map.presentation

import com.wandervox.core.domain.model.Coordinates
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.domain.model.User
import com.wandervox.core.domain.repository.AuthRepository
import com.wandervox.core.domain.repository.LogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val authRepository = FakeAuthRepository()
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
    fun `starts loading`() = runTest {
        assertTrue(MapViewModel(authRepository, logRepository).uiState.value.isLoading)
    }

    @Test
    fun `only entries with coordinates become markers`() = runTest {
        authRepository.user.value = User(uid = "uid-1", email = null)
        logRepository.entries.value = listOf(
            TravelLogEntry(id = "geo", userId = "uid-1", originalText = "Hola", translatedText = "Hello",
                coordinates = Coordinates(-34.6, -58.4)),
            TravelLogEntry(id = "no-geo", userId = "uid-1"),
        )

        val state = createSubscribedViewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals(listOf(MapMarker("geo", -34.6, -58.4, "Hola", "Hello")), state.markers)
    }

    @Test
    fun `shows only the signed-in user's entries`() = runTest {
        authRepository.user.value = User(uid = "uid-1", email = null)
        logRepository.entries.value = listOf(
            TravelLogEntry(id = "mine", userId = "uid-1", coordinates = Coordinates(1.0, 1.0)),
            TravelLogEntry(id = "theirs", userId = "uid-2", coordinates = Coordinates(2.0, 2.0)),
        )

        assertEquals(listOf("mine"), createSubscribedViewModel().uiState.value.markers.map { it.id })
    }

    @Test
    fun `signed out shows no markers`() = runTest {
        logRepository.entries.value = listOf(TravelLogEntry(id = "x", userId = "uid-1", coordinates = Coordinates(1.0, 1.0)))

        val state = createSubscribedViewModel().uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.markers.isEmpty())
    }

    @Test
    fun `switching user switches entries`() = runTest {
        authRepository.user.value = User(uid = "uid-1", email = null)
        logRepository.entries.value = listOf(
            TravelLogEntry(id = "a", userId = "uid-1", coordinates = Coordinates(1.0, 1.0)),
            TravelLogEntry(id = "b", userId = "uid-2", coordinates = Coordinates(2.0, 2.0)),
        )
        val viewModel = createSubscribedViewModel()

        authRepository.user.value = User(uid = "uid-2", email = null)

        assertEquals(listOf("b"), viewModel.uiState.value.markers.map { it.id })
    }

    @Test
    fun `new geotagged entry appears without reloading`() = runTest {
        authRepository.user.value = User(uid = "uid-1", email = null)
        val viewModel = createSubscribedViewModel()
        assertTrue(viewModel.uiState.value.markers.isEmpty())

        logRepository.entries.value = listOf(TravelLogEntry(id = "new", userId = "uid-1", coordinates = Coordinates(1.0, 1.0)))

        assertEquals(listOf("new"), viewModel.uiState.value.markers.map { it.id })
    }

    /** Subscribes to uiState so SharingStarted.WhileSubscribed keeps it updated, as the UI would. */
    private fun TestScope.createSubscribedViewModel(): MapViewModel {
        val viewModel = MapViewModel(authRepository, logRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private class FakeAuthRepository : AuthRepository {
        val user = MutableStateFlow<User?>(null)
        override val currentUser: Flow<User?> = user
        override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun signUp(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
    }

    private class FakeLogRepository : LogRepository {
        val entries = MutableStateFlow<List<TravelLogEntry>>(emptyList())

        override fun getUserEntries(userId: String): Flow<List<TravelLogEntry>> =
            entries.map { all -> all.filter { it.userId == userId } }

        override fun getCommunityEntries(): Flow<List<TravelLogEntry>> = emptyFlow()
        override fun getEntry(entryId: String): Flow<TravelLogEntry?> = emptyFlow()
        override suspend fun saveEntry(entry: TravelLogEntry): Result<Unit> = Result.success(Unit)
        override suspend fun toggleLike(entryId: String, userId: String): Result<Unit> = Result.success(Unit)
        override suspend fun deleteEntry(entryId: String): Result<Unit> = Result.success(Unit)
        override suspend fun syncPending(): Result<Unit> = Result.success(Unit)
    }
}
