package com.wandervox.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface TraductorRepository {
    val textoReconocido: StateFlow<String>
    val textoTraducido: StateFlow<String>
    val estaEscuchando: StateFlow<Boolean>

    suspend fun inicializarModelos(idiomaOrigen: String, idiomaDestino: String): Result<Unit>
    fun iniciarCaptura()
    fun detenerCaptura()
    fun cambiarSentidoTraduccion()
    fun liberarRecursos()
}