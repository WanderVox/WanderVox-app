package com.wandervox.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wandervox.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        val trimmedEmail = email.trim()
        val validationError = when {
            trimmedEmail.isBlank() || password.isBlank() -> "Completá tu email y contraseña."
            !trimmedEmail.isValidEmail() -> "Ingresá un email válido."
            else -> null
        }
        submit(validationError) { authRepository.signIn(trimmedEmail, password) }
    }

    fun register(email: String, password: String, confirmPassword: String) {
        val trimmedEmail = email.trim()
        val validationError = when {
            trimmedEmail.isBlank() || password.isBlank() || confirmPassword.isBlank() ->
                "Completá todos los campos."
            !trimmedEmail.isValidEmail() -> "Ingresá un email válido."
            password.length < MIN_PASSWORD_LENGTH ->
                "La contraseña debe tener al menos $MIN_PASSWORD_LENGTH caracteres."
            password != confirmPassword -> "Las contraseñas no coinciden."
            else -> null
        }
        submit(validationError) { authRepository.signUp(trimmedEmail, password) }
    }

    /** Vuelve a [AuthUiState.Idle] cuando el usuario edita un campo después de un error. */
    fun clearError() {
        _uiState.update { if (it is AuthUiState.Error) AuthUiState.Idle else it }
    }

    private fun submit(validationError: String?, action: suspend () -> Result<Unit>) {
        if (_uiState.value == AuthUiState.Loading) return
        if (validationError != null) {
            _uiState.value = AuthUiState.Error(validationError)
            return
        }
        _uiState.value = AuthUiState.Loading
        viewModelScope.launch {
            _uiState.value = action().fold(
                onSuccess = { AuthUiState.Success },
                onFailure = { AuthUiState.Error(it.message ?: "Ocurrió un error inesperado.") }
            )
        }
    }

    private fun String.isValidEmail(): Boolean = EMAIL_REGEX.matches(this)

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
        val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
