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
import com.wandervox.ui.components.WanderVoxFieldHint
import com.wandervox.ui.components.WanderVoxPasswordField
import com.wandervox.ui.components.WanderVoxPrimaryButton
import com.wandervox.ui.components.WanderVoxTextField
import com.wandervox.ui.components.WanderVoxTextLink
import com.wandervox.ui.theme.WanderVoxTheme

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState == AuthUiState.Success) onRegisterSuccess()
    }

    RegisterContent(
        uiState = uiState,
        email = email,
        password = password,
        confirmPassword = confirmPassword,
        onEmailChange = { email = it; viewModel.clearError() },
        onPasswordChange = { password = it; viewModel.clearError() },
        onConfirmPasswordChange = { confirmPassword = it; viewModel.clearError() },
        onRegisterClick = { viewModel.register(email, password, confirmPassword) },
        onNavigateToLogin = onNavigateToLogin,
        modifier = modifier
    )
}

@Composable
fun RegisterContent(
    uiState: AuthUiState,
    email: String,
    password: String,
    confirmPassword: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val isLoading = uiState == AuthUiState.Loading

    AuthLayout(
        title = "Creá tu cuenta",
        subtitle = "Registrate para guardar tu bitácora de viaje",
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
            imeAction = ImeAction.Next,
            enabled = !isLoading
        )
        WanderVoxFieldHint(text = "Mínimo 8 caracteres, con al menos una letra y un número.")
        WanderVoxPasswordField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = "Confirmar contraseña",
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                onRegisterClick()
            }),
            enabled = !isLoading,
            isError = confirmPassword.isNotEmpty() && confirmPassword != password
        )
        if (uiState is AuthUiState.Error) {
            WanderVoxErrorMessage(message = uiState.message)
        }
        WanderVoxPrimaryButton(
            text = "Registrarse",
            onClick = {
                focusManager.clearFocus()
                onRegisterClick()
            },
            isLoading = isLoading
        )
        WanderVoxTextLink(
            prompt = "¿Ya tenés cuenta?",
            action = "Iniciá sesión",
            onClick = onNavigateToLogin,
            enabled = !isLoading
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterContentPreview() {
    WanderVoxTheme {
        RegisterContent(
            uiState = AuthUiState.Idle,
            email = "",
            password = "",
            confirmPassword = "",
            onEmailChange = {},
            onPasswordChange = {},
            onConfirmPasswordChange = {},
            onRegisterClick = {},
            onNavigateToLogin = {}
        )
    }
}
