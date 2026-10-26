package com.wandervox.settings

import com.wandervox.core.domain.model.User
import com.wandervox.core.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
class SettingsViewModelTest {

    private val repository = FakeAuthRepository()
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = SettingsViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `exposes the signed in user's email`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals("viajero@wandervox.app", viewModel.uiState.value.userEmail)
    }

    @Test
    fun `sign out success marks the session as logged out`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.signOut()

        val state = viewModel.uiState.value
        assertEquals(1, repository.signOutCalls)
        assertTrue(state.isLoggedOut)
        assertFalse(state.isLoggingOut)
        assertNull(state.userEmail)
    }

    @Test
    fun `sign out failure shows error and keeps the session`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        repository.signOutResult = Result.failure(Exception("Sin conexión."))

        viewModel.signOut()

        val state = viewModel.uiState.value
        assertFalse(state.isLoggedOut)
        assertEquals("Sin conexión.", state.errorMessage)
    }

    @Test
    fun `permissions update the state`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.onPermissionsChecked(isMicrophoneGranted = true, isLocationGranted = false)

        val state = viewModel.uiState.value
        assertTrue(state.isMicrophoneGranted)
        assertFalse(state.isLocationGranted)
    }

    private class FakeAuthRepository : AuthRepository {
        private val user = MutableStateFlow<User?>(User(uid = "1", email = "viajero@wandervox.app"))
        var signOutResult: Result<Unit> = Result.success(Unit)
        var signOutCalls = 0

        override val currentUser = user

        override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)

        override suspend fun signUp(email: String, password: String): Result<Unit> = Result.success(Unit)

        override suspend fun signOut(): Result<Unit> {
            signOutCalls++
            if (signOutResult.isSuccess) user.value = null
            return signOutResult
        }
    }
}
