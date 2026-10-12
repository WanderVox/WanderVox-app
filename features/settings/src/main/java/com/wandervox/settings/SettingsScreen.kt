package com.wandervox.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wandervox.ui.components.DevelopmentBadge
import com.wandervox.ui.components.WanderVoxErrorMessage
import com.wandervox.ui.components.WanderVoxSettingsDivider
import com.wandervox.ui.components.WanderVoxSettingsItem
import com.wandervox.ui.components.WanderVoxSettingsSection
import com.wandervox.ui.components.WanderVoxSettingsSwitchItem
import com.wandervox.ui.components.WanderVoxStatusChip
import com.wandervox.ui.theme.Forest
import com.wandervox.ui.theme.Neutral
import com.wandervox.ui.theme.TextPrimary
import com.wandervox.ui.theme.TextSecondary
import com.wandervox.ui.theme.WanderVoxTheme

/**
 * Contenedor con estado de Ajustes.
 *
 * @param appVersion versión visible de la app (la provee `:app`, dueño de BuildConfig).
 * @param onLoggedOut se invoca una vez cerrada la sesión, para volver al Login.
 */
@Composable
fun SettingsScreen(
    appVersion: String,
    onNavigateBack: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }

    // Se vuelve a consultar al regresar de los ajustes del sistema, donde el usuario pudo cambiarlos.
    LifecycleResumeEffect(context) {
        viewModel.onPermissionsChecked(
            isMicrophoneGranted = context.hasPermission(Manifest.permission.RECORD_AUDIO),
            isLocationGranted = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        )
        onPauseOrDispose { }
    }

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }

    SettingsContent(
        uiState = uiState,
        appVersion = appVersion,
        onNavigateBack = onNavigateBack,
        onNavigateToTerms = onNavigateToTerms,
        onOfflineModeChange = viewModel::onOfflineModeChange,
        onOpenAppPermissions = { context.openAppDetailsSettings() },
        onLogoutClick = { viewModel.clearError(); showLogoutDialog = true },
        modifier = modifier
    )

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutDialog = false
                viewModel.signOut()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    appVersion: String,
    onNavigateBack: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onOfflineModeChange: (Boolean) -> Unit,
    onOpenAppPermissions: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        containerColor = Neutral,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Ajustes", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Neutral)
            )
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                AccountCard(
                    email = uiState.userEmail,
                    isLoggingOut = uiState.isLoggingOut,
                    errorMessage = uiState.errorMessage,
                    onLogoutClick = onLogoutClick
                )
            }
            item {
                WanderVoxSettingsSection(title = "Conectividad") {
                    WanderVoxSettingsSwitchItem(
                        icon = Icons.Outlined.CloudOff,
                        title = "Modo offline forzado",
                        subtitle = "Simular que no hay conexión y usar solo datos locales",
                        checked = uiState.isOfflineModeEnabled,
                        onCheckedChange = onOfflineModeChange
                    )
                    WanderVoxSettingsDivider()
                    WanderVoxSettingsItem(
                        icon = Icons.Outlined.Sync,
                        title = "Sincronización de Bitácora",
                        subtitle = "Subir tus entradas guardadas a la nube",
                        enabled = false,
                        trailing = { DevelopmentBadge() }
                    )
                }
            }
            item {
                WanderVoxSettingsSection(title = "Permisos") {
                    PermissionItem(
                        icon = Icons.Outlined.Mic,
                        title = "Micrófono",
                        subtitle = "Para reconocer tu voz al traducir",
                        isGranted = uiState.isMicrophoneGranted,
                        onClick = onOpenAppPermissions
                    )
                    WanderVoxSettingsDivider()
                    PermissionItem(
                        icon = Icons.Outlined.LocationOn,
                        title = "Ubicación",
                        subtitle = "Para ubicar tus entradas en la Bitácora y el Mapa",
                        isGranted = uiState.isLocationGranted,
                        onClick = onOpenAppPermissions
                    )
                }
            }
            item {
                WanderVoxSettingsSection(title = "Acerca de y legales") {
                    WanderVoxSettingsItem(
                        icon = Icons.Outlined.Info,
                        title = "Versión",
                        subtitle = "WanderVox v$appVersion"
                    )
                    WanderVoxSettingsDivider()
                    WanderVoxSettingsItem(
                        icon = Icons.Outlined.Description,
                        title = "Términos y Condiciones",
                        subtitle = "Condiciones de uso y descargo de responsabilidad",
                        onClick = onNavigateToTerms,
                        trailing = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    email: String?,
    isLoggingOut: Boolean,
    errorMessage: String?,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Forest.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = Forest,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "Tu cuenta", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = email ?: "Sin email",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (errorMessage != null) {
                WanderVoxErrorMessage(message = errorMessage)
            }
            OutlinedButton(
                onClick = onLogoutClick,
                enabled = !isLoggingOut,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Forest),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                if (isLoggingOut) {
                    CircularProgressIndicator(color = Forest, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Cerrar sesión",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    WanderVoxSettingsItem(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = {
            WanderVoxStatusChip(
                text = if (isGranted) "Concedido" else "Sin permiso",
                isPositive = isGranted
            )
        }
    )
}

@Composable
private fun LogoutConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "¿Cerrar sesión?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = "Vas a tener que volver a ingresar tu email y contraseña.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Cerrar sesión", style = MaterialTheme.typography.labelMedium, color = Forest)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancelar", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
        },
        containerColor = Color.White
    )
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Abre la pantalla de la app en los ajustes del sistema, donde se gestionan sus permisos. */
private fun Context.openAppDetailsSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F8F5)
@Composable
private fun SettingsContentPreview() {
    WanderVoxTheme {
        SettingsContent(
            uiState = SettingsUiState(userEmail = "viajero@wandervox.app", isMicrophoneGranted = true),
            appVersion = "1.0",
            onNavigateBack = {},
            onNavigateToTerms = {},
            onOfflineModeChange = {},
            onOpenAppPermissions = {},
            onLogoutClick = {}
        )
    }
}
