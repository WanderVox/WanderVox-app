package com.wandervox.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wandervox.core.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface SessionState {
    /** Todavía no sabemos si hay una sesión persistida. */
    data object Checking : SessionState
    data object Authenticated : SessionState
    data object Unauthenticated : SessionState
}

/**
 * Resuelve una sola vez, al arrancar, si ya hay un usuario logueado. Firebase persiste la sesión
 * en disco y su listener emite el usuario restaurado apenas se registra.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    authRepository: AuthRepository
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = flow {
        val user = authRepository.currentUser.first()
        emit(if (user != null) SessionState.Authenticated else SessionState.Unauthenticated)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Checking)
}
