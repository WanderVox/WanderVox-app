package com.wandervox.features.translator.presentation

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wandervox.core.ui.theme.WanderVoxTheme
import com.wandervox.features.translator.presentation.components.LanguageSelectorBar
import com.wandervox.features.translator.presentation.components.LogCategory
import com.wandervox.features.translator.presentation.components.MicButton
import com.wandervox.features.translator.presentation.components.RecognizedTextCard
import com.wandervox.features.translator.presentation.components.SaveToLogSheet
import com.wandervox.features.translator.presentation.components.TranslatedTextCard
import com.wandervox.features.translator.presentation.components.languageDisplayName
import com.wandervox.features.translator.presentation.sensor.ShakeEffect
import com.wandervox.translator.R
import kotlinx.coroutines.launch

/**
 * Stateful entry point: owns the ViewModel, Android-framework wiring (permission launcher,
 * accelerometer, clipboard, haptics) and one-off UI messages. All rendering is delegated to the
 * stateless [TranslatorContent].
 */
@Composable
fun TranslatorRoute(
    modifier: Modifier = Modifier,
    viewModel: TranslatorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val haptics = LocalHapticFeedback.current
    var isSaveSheetVisible by rememberSaveable { mutableStateOf(false) }

    val savedMessage = stringResource(R.string.translator_saved_success)
    val copiedMessage = stringResource(R.string.translator_copied)
    val permissionDeniedMessage = stringResource(R.string.translator_mic_permission_denied)
    val permissionBlockedMessage = stringResource(R.string.translator_mic_permission_settings)
    val retryLabel = stringResource(R.string.translator_retry)
    val settingsLabel = stringResource(R.string.translator_open_settings)

    lateinit var requestMicPermission: () -> Unit
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            // The user tapped the mic to start listening; honour that intent now.
            viewModel.onEvent(TranslatorUiEvent.OnToggleListening)
            return@rememberLauncherForActivityResult
        }
        // No rationale after a denial means "don't ask again": only Settings can grant it now.
        val canAskAgain = context.findActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
        } ?: false
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = if (canAskAgain) permissionDeniedMessage else permissionBlockedMessage,
                actionLabel = if (canAskAgain) retryLabel else settingsLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (canAskAgain) requestMicPermission() else context.openAppSettings()
            }
        }
    }
    requestMicPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    // Messages are consumed first, then shown on the screen's scope: resetting the flag changes the
    // effect key, which would otherwise cancel the snackbar that is being displayed.
    LaunchedEffect(uiState.isSavedSuccess) {
        if (uiState.isSavedSuccess) {
            viewModel.onEvent(TranslatorUiEvent.OnResetSaveSuccess)
            scope.launch { snackbarHostState.showSnackbar(savedMessage) }
        }
    }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            viewModel.onEvent(TranslatorUiEvent.OnDismissError)
            scope.launch { snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long) }
        }
    }

    // Disabled while the save sheet is open: a swap clears the transcript the user is saving.
    ShakeEffect(enabled = !uiState.isInitializingModels && !isSaveSheetVisible) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.onEvent(TranslatorUiEvent.OnSwapLanguages)
    }

    TranslatorContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        isSaveSheetVisible = isSaveSheetVisible,
        onMicClick = {
            // Stopping never needs the permission; starting does.
            if (uiState.isListening || context.hasRecordAudioPermission()) {
                viewModel.onEvent(TranslatorUiEvent.OnToggleListening)
            } else {
                requestMicPermission()
            }
        },
        onSwapClick = { viewModel.onEvent(TranslatorUiEvent.OnSwapLanguages) },
        onCopyClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, uiState.translatedText)))
                // Android 13+ shows its own clipboard confirmation; avoid a duplicate message.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    snackbarHostState.showSnackbar(copiedMessage)
                }
            }
        },
        onSaveClick = { isSaveSheetVisible = true },
        onDismissSaveSheet = { isSaveSheetVisible = false },
        onConfirmSave = { category, notes ->
            isSaveSheetVisible = false
            // Location capture is not wired yet; the entry is saved without coordinates.
            viewModel.onEvent(TranslatorUiEvent.OnSaveToLog(category.storageKey, notes, coordinates = null))
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorContent(
    uiState: TranslatorUiState,
    snackbarHostState: SnackbarHostState,
    isSaveSheetVisible: Boolean,
    onMicClick: () -> Unit,
    onSwapClick: () -> Unit,
    onCopyClick: () -> Unit,
    onSaveClick: () -> Unit,
    onDismissSaveSheet: () -> Unit,
    onConfirmSave: (LogCategory, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canInteract = !uiState.isInitializingModels

    // No top bar here: MainScaffold already provides the app-wide header (logo + settings).
    // The Scaffold is kept for its SnackbarHost.
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            if (uiState.isInitializingModels) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.translator_preparing_models),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }

            LanguageSelectorBar(
                sourceLanguage = uiState.sourceLanguage,
                targetLanguage = uiState.targetLanguage,
                swapEnabled = canInteract,
                onSwapClick = onSwapClick,
                modifier = Modifier.padding(top = 8.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RecognizedTextCard(
                    languageName = languageDisplayName(uiState.sourceLanguage),
                    text = uiState.recognizedText,
                    isListening = uiState.isListening,
                )
                TranslatedTextCard(
                    languageName = languageDisplayName(uiState.targetLanguage),
                    text = uiState.translatedText,
                    onCopyClick = onCopyClick,
                )
            }

            MicButton(
                isListening = uiState.isListening,
                enabled = canInteract,
                onClick = onMicClick,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            FilledTonalButton(
                onClick = onSaveClick,
                enabled = canInteract && uiState.recognizedText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp)
                    .heightIn(min = 52.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bookmark_add),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.translator_save_to_log), style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (isSaveSheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        SaveToLogSheet(
            sheetState = sheetState,
            onDismissRequest = onDismissSaveSheet,
            onConfirm = { category, notes ->
                // Let the sheet animate out before it leaves the composition.
                scope.launch { sheetState.hide() }.invokeOnCompletion { onConfirmSave(category, notes) }
            },
        )
    }
}

private fun Context.hasRecordAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val CLIP_LABEL = "WanderVox translation"

@Preview(name = "Idle", showSystemUi = true)
@Composable
private fun TranslatorContentIdlePreview() {
    TranslatorContentPreview(TranslatorUiState())
}

@Preview(name = "Listening", showSystemUi = true)
@Composable
private fun TranslatorContentListeningPreview() {
    TranslatorContentPreview(
        TranslatorUiState(
            recognizedText = "¿Dónde está la estación de tren?",
            translatedText = "Where is the train station?",
            isListening = true,
        )
    )
}

@Preview(name = "Preparing models", showSystemUi = true)
@Composable
private fun TranslatorContentInitializingPreview() {
    TranslatorContentPreview(TranslatorUiState(isInitializingModels = true))
}

@Composable
private fun TranslatorContentPreview(uiState: TranslatorUiState) {
    WanderVoxTheme {
        TranslatorContent(
            uiState = uiState,
            snackbarHostState = remember { SnackbarHostState() },
            isSaveSheetVisible = false,
            onMicClick = {},
            onSwapClick = {},
            onCopyClick = {},
            onSaveClick = {},
            onDismissSaveSheet = {},
            onConfirmSave = { _, _ -> },
        )
    }
}
