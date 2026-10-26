package com.wandervox.app.navigation

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wandervox.auth.LoginScreen
import com.wandervox.auth.RegisterScreen
import com.wandervox.features.log.presentation.LogScreen
import com.wandervox.features.log.presentation.detail.LogDetailScreen
import com.wandervox.features.log.presentation.detail.LogDetailViewModel
import com.wandervox.features.translator.presentation.TranslatorRoute
import com.wandervox.app.BuildConfig
import com.wandervox.app.main.MainScaffold
import com.wandervox.settings.SettingsScreen
import com.wandervox.settings.TermsAndConditionsScreen

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val TERMS = "terms"
    const val LOG_DETAIL = "log/detail/{${LogDetailViewModel.ENTRY_ID_ARG}}"

    fun logDetail(entryId: String) = "log/detail/${Uri.encode(entryId)}"
}

/**
 * Punto de entrada de la navegación: mientras se verifica si hay una sesión guardada se muestra
 * solo el fondo del tema (continuidad con el splash) y luego arranca en Home o en Login.
 */
@Composable
fun WanderVoxNavHost(
    navController: NavHostController = rememberNavController(),
    sessionViewModel: SessionViewModel = hiltViewModel()
) {
    val sessionState by sessionViewModel.sessionState.collectAsStateWithLifecycle()

    val startDestination = when (sessionState) {
        SessionState.Checking -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )
            return
        }
        SessionState.Authenticated -> Routes.HOME
        SessionState.Unauthenticated -> Routes.LOGIN
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER) { launchSingleTop = true }
                },
                onLoginSuccess = { navController.navigateToHome() }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onNavigateToLogin = {
                    // Register siempre se abre desde Login, así que volver es hacer pop.
                    if (!navController.popBackStack(Routes.LOGIN, inclusive = false)) {
                        navController.navigate(Routes.LOGIN)
                    }
                },
                onRegisterSuccess = { navController.navigateToHome() }
            )
        }
        composable(Routes.HOME) {
            val openLogEntry: (String) -> Unit = { entryId ->
                navController.navigate(Routes.logDetail(entryId)) { launchSingleTop = true }
            }
            MainScaffold(
                translatorContent = { modifier -> TranslatorRoute(modifier = modifier) },
                logContent = { modifier -> LogScreen(onEntryClick = openLogEntry, modifier = modifier) },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS) { launchSingleTop = true }
                },
                onOpenLogEntry = openLogEntry
            )
        }
        // Como Ajustes, el detalle vive fuera de MainScaffold: se abre a pantalla completa desde la
        // Bitácora o el Mapa, y volver restaura la pestaña de origen.
        composable(
            route = Routes.LOG_DETAIL,
            arguments = listOf(navArgument(LogDetailViewModel.ENTRY_ID_ARG) { type = NavType.StringType })
        ) {
            LogDetailScreen(
                // pop por ruta (no un pop simple) para que un doble toque en "atrás" no saque también a Home.
                onNavigateBack = { navController.popBackStack(Routes.LOG_DETAIL, inclusive = true) }
            )
        }
        // Ajustes vive fuera de MainScaffold: Home queda en el back stack con su pestaña activa
        // guardada, así que volver restaura exactamente la pestaña desde donde se abrió.
        composable(Routes.SETTINGS) {
            SettingsScreen(
                appVersion = BuildConfig.VERSION_NAME,
                onNavigateBack = { navController.popBackStack(Routes.SETTINGS, inclusive = true) },
                onNavigateToTerms = { navController.navigate(Routes.TERMS) { launchSingleTop = true } },
                onLoggedOut = { navController.navigateToLogin() }
            )
        }
        composable(Routes.TERMS) {
            TermsAndConditionsScreen(
                onNavigateBack = { navController.popBackStack(Routes.TERMS, inclusive = true) }
            )
        }
    }
}

/** Vuelve al Login tras cerrar sesión, limpiando todo el back stack. */
private fun NavHostController.navigateToLogin() {
    navigate(Routes.LOGIN) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** Navega al Home limpiando el flujo de auth para que "atrás" no vuelva al Login. */
private fun NavHostController.navigateToHome() {
    navigate(Routes.HOME) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
