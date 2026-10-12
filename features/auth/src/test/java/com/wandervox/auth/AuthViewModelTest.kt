package com.wandervox.auth

import com.wandervox.core.domain.model.User
import com.wandervox.core.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val repository = FakeAuthRepository()
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = AuthViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `register with mismatched passwords does not call repository`() {
        viewModel.register("viajero@wandervox.app", "viajero123", "viajero321")

        assertEquals(AuthUiState.Error("Las contraseñas no coinciden."), viewModel.uiState.value)
        assertEquals(0, repository.signUpCalls)
    }

    @Test
    fun `register with valid data calls repository and emits success`() {
        viewModel.register(" viajero@wandervox.app ", "viajero123", "viajero123")

        assertEquals(AuthUiState.Success, viewModel.uiState.value)
        assertEquals(1, repository.signUpCalls)
        assertEquals("viajero@wandervox.app", repository.lastEmail)
    }

    @Test
    fun `register rejects passwords shorter than 8 characters`() {
        viewModel.register("viajero@wandervox.app", "abc123", "abc123")

        assertEquals(AuthUiState.Error(PASSWORD_POLICY_MESSAGE), viewModel.uiState.value)
        assertEquals(0, repository.signUpCalls)
    }

    @Test
    fun `register rejects passwords without numbers`() {
        viewModel.register("viajero@wandervox.app", "soloLetras", "soloLetras")

        assertEquals(AuthUiState.Error(PASSWORD_POLICY_MESSAGE), viewModel.uiState.value)
        assertEquals(0, repository.signUpCalls)
    }

    @Test
    fun `register rejects passwords without letters`() {
        viewModel.register("viajero@wandervox.app", "12345678", "12345678")

        assertEquals(AuthUiState.Error(PASSWORD_POLICY_MESSAGE), viewModel.uiState.value)
        assertEquals(0, repository.signUpCalls)
    }

    @Test
    fun `login does not enforce the password policy`() {
        viewModel.login("viajero@wandervox.app", "123456")

        assertEquals(AuthUiState.Success, viewModel.uiState.value)
        assertEquals(1, repository.signInCalls)
    }

    @Test
    fun `login with blank fields does not call repository`() {
        viewModel.login("", "")

        assertTrue(viewModel.uiState.value is AuthUiState.Error)
        assertEquals(0, repository.signInCalls)
    }

    @Test
    fun `login failure exposes repository message`() {
        repository.result = Result.failure(Exception("Email o contraseña incorrectos."))

        viewModel.login("viajero@wandervox.app", "123456")

        assertEquals(AuthUiState.Error("Email o contraseña incorrectos."), viewModel.uiState.value)
    }

    @Test
    fun `clearError returns to idle`() {
        viewModel.login("", "")
        viewModel.clearError()

        assertEquals(AuthUiState.Idle, viewModel.uiState.value)
    }

    private companion object {
        const val PASSWORD_POLICY_MESSAGE =
            "La contraseña debe tener al menos 8 caracteres, incluyendo al menos una letra y un número."
    }

    private class FakeAuthRepository : AuthRepository {
        var result: Result<Unit> = Result.success(Unit)
        var signInCalls = 0
        var signUpCalls = 0
        var lastEmail: String? = null

        override val currentUser: Flow<User?> = flowOf(null)

        override suspend fun signIn(email: String, password: String): Result<Unit> {
            signInCalls++
            lastEmail = email
            return result
        }

        override suspend fun signUp(email: String, password: String): Result<Unit> {
            signUpCalls++
            lastEmail = email
            return result
        }

        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
    }
}
