package com.wandervox.features.log.presentation

import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.SyncStatus
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.domain.model.User
import com.wandervox.core.domain.repository.AuthRepository
import com.wandervox.core.domain.repository.LogRepository
import com.wandervox.features.log.photo.LogPhotoStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModelTest {

    private val logRepository = FakeLogRepository()
    private val authRepository = FakeAuthRepository()
    private val photoStorage = FakePhotoStorage()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        logRepository.entries.value = listOf(
            entry("mine-food", userId = ME, category = LogCategory.FOOD),
            entry("mine-transport", userId = ME, category = LogCategory.TRANSPORT, isPublic = true),
            entry("other-food", userId = OTHER, category = LogCategory.FOOD, isPublic = true),
            entry("other-private", userId = OTHER, category = LogCategory.LODGING),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Tab switching ---

    @Test
    fun `starts on my trips with the signed-in user's entries`() = runTest {
        val viewModel = createSubscribedViewModel()

        val state = viewModel.uiState.value
        assertEquals(LogTab.MY_TRIPS, state.selectedTab)
        assertEquals(listOf("mine-food", "mine-transport"), state.entryIds())
        assertEquals(ME, state.currentUserId)
        assertFalse(state.isLoading)
        assertFalse(state.isError)
    }

    @Test
    fun `selecting community shows only public entries`() = runTest {
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(LogUiEvent.OnTabSelected(LogTab.COMMUNITY))

        val state = viewModel.uiState.value
        assertEquals(LogTab.COMMUNITY, state.selectedTab)
        assertEquals(listOf("mine-transport", "other-food"), state.entryIds())
    }

    @Test
    fun `switching back to my trips restores the user's entries`() = runTest {
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(LogUiEvent.OnTabSelected(LogTab.COMMUNITY))
        viewModel.onEvent(LogUiEvent.OnTabSelected(LogTab.MY_TRIPS))

        assertEquals(listOf("mine-food", "mine-transport"), viewModel.uiState.value.entryIds())
    }

    @Test
    fun `my trips is empty while signed out`() = runTest {
        authRepository.user.value = null
        val viewModel = createSubscribedViewModel()

        val state = viewModel.uiState.value
        assertTrue(state.entries.isEmpty())
        assertNull(state.currentUserId)
        assertFalse(state.isLoading)
    }

    @Test
    fun `tab list follows repository updates`() = runTest {
        val viewModel = createSubscribedViewModel()

        logRepository.entries.update { it + entry("mine-new", userId = ME, category = LogCategory.GENERAL) }

        assertEquals(listOf("mine-food", "mine-transport", "mine-new"), viewModel.uiState.value.entryIds())
    }

    // --- Category filtering ---

    @Test
    fun `category filter keeps only matching entries of the active tab`() = runTest {
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(LogUiEvent.OnCategorySelected(LogCategory.FOOD))

        val state = viewModel.uiState.value
        assertEquals(LogCategory.FOOD, state.selectedCategory)
        assertEquals(listOf("mine-food"), state.entryIds())
    }

    @Test
    fun `category filter is kept when switching tabs`() = runTest {
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(LogUiEvent.OnCategorySelected(LogCategory.FOOD))
        viewModel.onEvent(LogUiEvent.OnTabSelected(LogTab.COMMUNITY))

        val state = viewModel.uiState.value
        assertEquals(LogCategory.FOOD, state.selectedCategory)
        assertEquals(listOf("other-food"), state.entryIds())
    }

    @Test
    fun `clearing the filter shows every entry again`() = runTest {
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(LogUiEvent.OnCategorySelected(LogCategory.TRANSPORT))
        viewModel.onEvent(LogUiEvent.OnCategorySelected(null))

        assertNull(viewModel.uiState.value.selectedCategory)
        assertEquals(listOf("mine-food", "mine-transport"), viewModel.uiState.value.entryIds())
    }

    @Test
    fun `category without entries yields an empty list`() = runTest {
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(LogUiEvent.OnCategorySelected(LogCategory.EMERGENCY))

        assertTrue(viewModel.uiState.value.entries.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // --- Errors and actions ---

    @Test
    fun `failing source sets the error flag and retry recovers`() = runTest {
        logRepository.failUserEntries = true
        val viewModel = createSubscribedViewModel()
        assertTrue(viewModel.uiState.value.isError)

        logRepository.failUserEntries = false
        viewModel.onEvent(LogUiEvent.OnRetry)

        assertFalse(viewModel.uiState.value.isError)
        assertEquals(listOf("mine-food", "mine-transport"), viewModel.uiState.value.entryIds())
    }

    @Test
    fun `toggle like uses the signed-in user`() = runTest {
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(LogUiEvent.OnToggleLike("other-food"))

        assertEquals(listOf("other-food" to ME), logRepository.likeCalls)
    }

    @Test
    fun `delete removes the entry and failures surface an action error`() = runTest {
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(LogUiEvent.OnDeleteEntry("mine-food"))
        assertEquals(listOf("mine-transport"), viewModel.uiState.value.entryIds())

        viewModel.onEvent(LogUiEvent.OnDeleteEntry("missing"))
        assertEquals(LogActionError.DELETE, viewModel.uiState.value.actionError)

        viewModel.onEvent(LogUiEvent.OnActionErrorShown)
        assertNull(viewModel.uiState.value.actionError)
    }

    // --- Photos ---

    @Test
    fun `picked photo is copied and stored as a pending local photo`() = runTest {
        logRepository.entries.update { all ->
            all.map {
                if (it.id == "mine-food") it.copy(remotePhotoUrl = "https://old", syncStatus = SyncStatus.SYNCED) else it
            }
        }
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(LogUiEvent.OnPhotoPicked("mine-food", "content://picked/1"))

        val saved = logRepository.entries.value.single { it.id == "mine-food" }
        assertEquals("/files/log_photos/mine-food_1.jpg", saved.localPhotoPath)
        assertNull(saved.remotePhotoUrl)
        assertEquals(SyncStatus.PENDING, saved.syncStatus)
        assertEquals(listOf("content://picked/1" to "mine-food"), photoStorage.imports)
        assertNull(viewModel.uiState.value.importingPhotoEntryId)
        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun `replacing a photo deletes the previous copy`() = runTest {
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(LogUiEvent.OnPhotoPicked("mine-food", "content://picked/1"))
        viewModel.onEvent(LogUiEvent.OnPhotoPicked("mine-food", "content://picked/2"))

        val saved = logRepository.entries.value.single { it.id == "mine-food" }
        assertEquals("/files/log_photos/mine-food_2.jpg", saved.localPhotoPath)
        assertEquals(listOf("/files/log_photos/mine-food_1.jpg"), photoStorage.deleted)
    }

    @Test
    fun `failed copy keeps the entry unchanged and reports a photo error`() = runTest {
        photoStorage.failImport = true
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(LogUiEvent.OnPhotoPicked("mine-food", "content://picked/1"))

        assertNull(logRepository.entries.value.single { it.id == "mine-food" }.localPhotoPath)
        assertEquals(LogActionError.PHOTO, viewModel.uiState.value.actionError)
        assertNull(viewModel.uiState.value.importingPhotoEntryId)
    }

    @Test
    fun `failed save removes the copied photo`() = runTest {
        logRepository.failSave = true
        val viewModel = createSubscribedViewModel()

        viewModel.onEvent(LogUiEvent.OnPhotoPicked("mine-food", "content://picked/1"))

        assertEquals(listOf("/files/log_photos/mine-food_1.jpg"), photoStorage.deleted)
        assertEquals(LogActionError.PHOTO, viewModel.uiState.value.actionError)
    }

    @Test
    fun `deleting an entry also deletes its photo`() = runTest {
        val viewModel = createSubscribedViewModel()
        viewModel.onEvent(LogUiEvent.OnPhotoPicked("mine-food", "content://picked/1"))

        viewModel.onEvent(LogUiEvent.OnDeleteEntry("mine-food"))

        assertEquals(listOf("/files/log_photos/mine-food_1.jpg"), photoStorage.deleted)
    }

    private fun TestScope.createSubscribedViewModel(): LogViewModel {
        val viewModel = LogViewModel(logRepository, authRepository, photoStorage)
        // uiState is WhileSubscribed: keep a collector alive like the screen does.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun LogUiState.entryIds() = entries.map { it.id }

    private fun entry(id: String, userId: String, category: LogCategory, isPublic: Boolean = false) =
        TravelLogEntry(id = id, userId = userId, originalText = id, category = category, isPublic = isPublic)

    private companion object {
        const val ME = "me"
        const val OTHER = "other"
    }
}

private class FakeAuthRepository : AuthRepository {
    val user = MutableStateFlow<User?>(User(uid = "me", email = "me@example.com"))
    override val currentUser: Flow<User?> = user
    override suspend fun signIn(email: String, password: String) = Result.success(Unit)
    override suspend fun signUp(email: String, password: String) = Result.success(Unit)
    override suspend fun signOut() = Result.success(Unit)
}

private class FakePhotoStorage : LogPhotoStorage {
    var failImport = false
    val imports = mutableListOf<Pair<String, String>>()
    val deleted = mutableListOf<String>()

    override suspend fun importPhoto(sourceUri: String, entryId: String): Result<String> {
        if (failImport) return Result.failure(IllegalStateException("unreadable"))
        imports += sourceUri to entryId
        return Result.success("/files/log_photos/${entryId}_${imports.size}.jpg")
    }

    override suspend fun deletePhoto(path: String): Result<Unit> {
        deleted += path
        return Result.success(Unit)
    }
}

/** Keeps insertion order so assertions do not depend on timestamps. */
private class FakeLogRepository : LogRepository {
    val entries = MutableStateFlow<List<TravelLogEntry>>(emptyList())
    var failUserEntries = false
    var failSave = false
    val likeCalls = mutableListOf<Pair<String, String>>()

    override fun getUserEntries(userId: String): Flow<List<TravelLogEntry>> =
        if (failUserEntries) flow { throw IllegalStateException("boom") }
        else entries.map { all -> all.filter { it.userId == userId } }

    override fun getCommunityEntries(): Flow<List<TravelLogEntry>> =
        entries.map { all -> all.filter { it.isPublic } }

    override fun getEntry(entryId: String): Flow<TravelLogEntry?> =
        entries.map { all -> all.firstOrNull { it.id == entryId } }

    override suspend fun saveEntry(entry: TravelLogEntry): Result<Unit> {
        if (failSave) return Result.failure(IllegalStateException("disk full"))
        entries.update { all ->
            if (all.any { it.id == entry.id }) all.map { if (it.id == entry.id) entry else it } else all + entry
        }
        return Result.success(Unit)
    }

    override suspend fun toggleLike(entryId: String, userId: String): Result<Unit> {
        likeCalls += entryId to userId
        return Result.success(Unit)
    }

    override suspend fun deleteEntry(entryId: String): Result<Unit> {
        if (entries.value.none { it.id == entryId }) return Result.failure(NoSuchElementException(entryId))
        entries.update { all -> all.filterNot { it.id == entryId } }
        return Result.success(Unit)
    }

    override suspend fun syncPending(): Result<Unit> = Result.success(Unit)
}
