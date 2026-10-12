package com.wandervox.auth

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wandervox.ui.components.WanderVoxErrorMessage
import com.wandervox.ui.components.WanderVoxPasswordField
import com.wandervox.ui.components.WanderVoxPrimaryButton
import com.wandervox.ui.components.WanderVoxTextField
import com.wandervox.ui.components.WanderVoxTextLink
import com.wandervox.ui.theme.WanderVoxTheme

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState == AuthUiState.Success) onLoginSuccess()
    }

    LoginContent(
        uiState = uiState,
        email = email,
        password = password,
        onEmailChange = { email = it; viewModel.clearError() },
        onPasswordChange = { password = it; viewModel.clearError() },
        onLoginClick = { viewModel.login(email, password) },
        onNavigateToRegister = onNavigateToRegister,
        modifier = modifier
    )
}

@Composable
fun LoginContent(
    uiState: AuthUiState,
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onNavigateToRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val isLoading = uiState == AuthUiState.Loading

    AuthLayout(
        title = "Bienvenido",
        subtitle = "Iniciá sesión para continuar tu viaje",
        modifier = modifier
    ) {
        WanderVoxTextField(
            value = email,
            onValueChange = onEmailChange,
            label = "Email",
            leadingIcon = Icons.Outlined.Email,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            enabled = !isLoading
        )
        WanderVoxPasswordField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Contraseña",
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onLoginClick()
            }),
            enabled = !isLoading
        )
        if (uiState is AuthUiState.Error) {
            WanderVoxErrorMessage(message = uiState.message)
        }
        WanderVoxPrimaryButton(
            text = "Iniciar sesión",
            onClick = {
                focusManager.clearFocus()
                onLoginClick()
            },
            isLoading = isLoading
        )
        WanderVoxTextLink(
            prompt = "¿No tenés cuenta?",
            action = "Registrate",
            onClick = onNavigateToRegister,
            enabled = !isLoading
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginContentPreview() {
    WanderVoxTheme {
        LoginContent(
            uiState = AuthUiState.Error("Email o contraseña incorrectos."),
            email = "viajero@wandervox.app",
            password = "123456",
            onEmailChange = {},
            onPasswordChange = {},
            onLoginClick = {},
            onNavigateToRegister = {}
        )
    }
}
