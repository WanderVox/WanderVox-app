package com.wandervox.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wandervox.auth.LoginScreen
import com.wandervox.auth.RegisterScreen

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
}

@Composable
fun WanderVoxNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.LOGIN) {
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
            // TODO: reemplazar por la pantalla de bitácora (:features:log)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Text(text = "¡Bienvenido a WanderVox!", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/** Navega al Home limpiando el flujo de auth para que "atrás" no vuelva al Login. */
private fun NavHostController.navigateToHome() {
    navigate(Routes.HOME) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
