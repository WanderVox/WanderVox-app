package com.wandervox.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wandervox.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val userEmail: String? = null,
    val isMicrophoneGranted: Boolean = false,
    val isLocationGranted: Boolean = false,
    val isLoggingOut: Boolean = false,
    /** Se vuelve true cuando el cierre de sesión terminó; la pantalla navega al Login. */
    val isLoggedOut: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val localState = MutableStateFlow(SettingsUiState())

    val uiState: StateFlow<SettingsUiState> =
        combine(authRepository.currentUser, localState) { user, state ->
            state.copy(userEmail = user?.email)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** La pantalla consulta los permisos al Android framework y reporta el resultado acá. */
    fun onPermissionsChecked(isMicrophoneGranted: Boolean, isLocationGranted: Boolean) {
        localState.update {
            it.copy(isMicrophoneGranted = isMicrophoneGranted, isLocationGranted = isLocationGranted)
        }
    }

    fun signOut() {
        if (localState.value.isLoggingOut) return
        localState.update { it.copy(isLoggingOut = true, errorMessage = null) }
        viewModelScope.launch {
            val result = authRepository.signOut()
            localState.update {
                it.copy(
                    isLoggingOut = false,
                    isLoggedOut = result.isSuccess,
                    errorMessage = result.exceptionOrNull()?.let { error ->
                        error.message ?: "No pudimos cerrar la sesión. Intentá de nuevo."
                    }
                )
            }
        }
    }

    fun clearError() {
        localState.update { it.copy(errorMessage = null) }
    }
}
