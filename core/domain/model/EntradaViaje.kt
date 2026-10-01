package com.wandervox.core.domain.model

data class Coordenadas(
    val latitud: Double,
    val longitud: Double
)

data class EntradaViaje(
    val id: String = "",
    val userId: String = "",
    val textoOriginal: String = "",
    val textoTraducido: String = "",
    val idiomaOrigen: String = "ES",
    val idiomaDestino: String = "EN",
    val timestamp: Long = System.currentTimeMillis(),
    val ubicacion: Coordenadas? = null,
    val fotoLocalPath: String? = null,
    val fotoUrlRemota: String? = null,
    val categoria: String = "General",
    val notas: String = "",
    val esPublica: Boolean = false,
    val likes: List<String> = emptyList()
)