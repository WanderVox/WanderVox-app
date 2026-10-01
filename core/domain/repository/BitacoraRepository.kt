package com.wandervox.core.domain.repository

import com.wandervox.core.domain.model.EntradaViaje
import kotlinx.coroutines.flow.Flow

interface BitacoraRepository {
    fun obtenerMisEntradas(userId: String): Flow<List<EntradaViaje>>
    fun obtenerEntradasComunidad(): Flow<List<EntradaViaje>>
    suspend fun guardarEntrada(entrada: EntradaViaje): Result<Unit>
    suspend fun alternarLike(entradaId: String, userId: String): Result<Unit>
    suspend fun eliminarEntrada(entradaId: String): Result<Unit>
}