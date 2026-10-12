package com.wandervox.app.main

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wandervox.ui.components.UnderDevelopmentScreen
import com.wandervox.ui.components.WanderVoxLogo
import com.wandervox.ui.theme.Neutral
import com.wandervox.ui.theme.TextPrimary

/**
 * Contenedor principal tras el login: top bar, contenido de la pestaña activa y barra inferior.
 * Es la parte con estado (back stack de pestañas); la barra en sí es [WanderVoxBottomBar].
 *
 * @param translatorContent contenido de la pestaña Traductor, provisto por quien arma la app.
 * @param onNavigateToSettings abre Ajustes desde cualquier pestaña (ícono de engranaje).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    translatorContent: @Composable (Modifier) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedTab = MainTab.fromRoute(backStackEntry?.destination?.route) ?: MainTab.Translator

    Scaffold(
        containerColor = Neutral,
        topBar = {
            CenterAlignedTopAppBar(
                title = { WanderVoxLogo(style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Ajustes",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Neutral)
            )
        },
        bottomBar = {
            WanderVoxBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { tab -> navController.navigateToTab(tab) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.Translator.route,
            // consumeWindowInsets: las pantallas de cada pestaña pueden tener su propio Scaffold
            // (ej. el Traductor); sin esto volverían a sumar el inset de la barra de estado.
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable(MainTab.Translator.route) {
                translatorContent(Modifier)
            }
            composable(MainTab.Log.route) {
                UnderDevelopmentScreen(title = MainTab.Log.label, icon = MainTab.Log.icon)
            }
            composable(MainTab.Map.route) {
                UnderDevelopmentScreen(title = MainTab.Map.label, icon = MainTab.Map.icon)
            }
        }
    }
}

/** Patrón estándar de bottom navigation: una sola instancia por pestaña y estado preservado. */
private fun NavHostController.navigateToTab(tab: MainTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
