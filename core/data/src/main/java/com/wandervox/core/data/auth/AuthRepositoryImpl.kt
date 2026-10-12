package com.wandervox.core.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.wandervox.core.domain.model.User
import com.wandervox.core.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    override val currentUser: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.let { User(uid = it.uid, email = it.email) })
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(email: String, password: String): Result<Unit> = authCall {
        firebaseAuth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun signUp(email: String, password: String): Result<Unit> = authCall {
        firebaseAuth.createUserWithEmailAndPassword(email, password).await()
    }

    override suspend fun signOut(): Result<Unit> = authCall {
        firebaseAuth.signOut()
    }

    /**
     * Ejecuta una operación de Firebase envolviéndola en [Result]. Las excepciones del SDK se
     * traducen a [AuthException] con un mensaje legible, así ningún tipo de Firebase sale de
     * la capa de datos. La cancelación de corrutinas se propaga sin envolver.
     */
    private suspend fun authCall(block: suspend () -> Any?): Result<Unit> =
        runCatching { block(); Unit }
            .onFailure { if (it is CancellationException) throw it }
            .recoverCatching { throw AuthException(it.toUserMessage(), it) }

    /**
     * Usuario inexistente y contraseña errónea comparten mensaje a propósito: distinguirlos
     * permitiría averiguar qué emails tienen cuenta (enumeración de usuarios).
     */
    private fun Throwable.toUserMessage(): String = when (this) {
        is FirebaseAuthWeakPasswordException -> WEAK_PASSWORD_MESSAGE
        is FirebaseAuthUserCollisionException -> "Ya existe una cuenta con ese email."
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> INVALID_CREDENTIALS_MESSAGE
        is FirebaseNetworkException -> "Sin conexión. Revisá tu internet e intentá de nuevo."
        is FirebaseTooManyRequestsException -> "Demasiados intentos. Esperá unos minutos."
        else -> "Ocurrió un error inesperado. Intentá de nuevo."
    }

    private companion object {
        const val INVALID_CREDENTIALS_MESSAGE = "Email o contraseña incorrectos."
        const val WEAK_PASSWORD_MESSAGE =
            "La contraseña debe tener al menos 8 caracteres, incluyendo al menos una letra y un número."
    }
}

class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
