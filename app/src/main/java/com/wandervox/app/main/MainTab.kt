package com.wandervox.app.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.ui.graphics.vector.ImageVector

/** Pestañas principales de la barra inferior. [inDevelopment] muestra la etiqueta "Próximamente". */
enum class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val inDevelopment: Boolean
) {
    Translator(route = "translator", label = "Traductor", icon = Icons.Outlined.Mic, inDevelopment = false),
    Log(route = "log", label = "Bitácora", icon = Icons.AutoMirrored.Outlined.MenuBook, inDevelopment = true),
    Map(route = "map", label = "Mapa", icon = Icons.Outlined.Map, inDevelopment = true);

    companion object {
        fun fromRoute(route: String?): MainTab? = entries.firstOrNull { it.route == route }
    }
}
