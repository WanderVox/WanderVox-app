package com.wandervox.features.log.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wandervox.log.R
import kotlinx.coroutines.launch

/**
 * Stateful entry point of the Travel Log tab: owns the ViewModel, the Photo Picker launcher, the
 * delete confirmation and one-off messages, and forwards every user intent as a [LogUiEvent]. All
 * rendering is delegated to the stateless [LogContent].
 */
@Composable
fun LogScreen(
    modifier: Modifier = Modifier,
    viewModel: LogViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
    // Saveable: the picker is another activity, so this screen may be recreated before it returns.
    var photoTargetEntryId by rememberSaveable { mutableStateOf<String?>(null) }

    // The system Photo Picker needs no storage permission; on devices without it, the contract
    // falls back to ACTION_OPEN_DOCUMENT, which needs none either.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val entryId = photoTargetEntryId
        photoTargetEntryId = null
        if (uri != null && entryId != null) {
            viewModel.onEvent(LogUiEvent.OnPhotoPicked(entryId, uri.toString()))
        }
    }

    val likeErrorMessage = stringResource(R.string.log_error_like)
    val deleteErrorMessage = stringResource(R.string.log_error_delete)
    val photoErrorMessage = stringResource(R.string.log_error_photo)

    // Consumed first, then shown on the screen's scope so resetting the flag does not cancel it.
    LaunchedEffect(uiState.actionError) {
        val error = uiState.actionError ?: return@LaunchedEffect
        viewModel.onEvent(LogUiEvent.OnActionErrorShown)
        val message = when (error) {
            LogActionError.LIKE -> likeErrorMessage
            LogActionError.DELETE -> deleteErrorMessage
            LogActionError.PHOTO -> photoErrorMessage
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    LogContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        pendingDeletionId = pendingDeletionId,
        onTabSelected = { viewModel.onEvent(LogUiEvent.OnTabSelected(it)) },
        onCategorySelected = { viewModel.onEvent(LogUiEvent.OnCategorySelected(it)) },
        onRetry = { viewModel.onEvent(LogUiEvent.OnRetry) },
        onToggleLike = { viewModel.onEvent(LogUiEvent.OnToggleLike(it)) },
        onDeleteRequest = { pendingDeletionId = it },
        onPickPhoto = { entryId ->
            photoTargetEntryId = entryId
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onDeleteConfirm = {
            pendingDeletionId?.let { viewModel.onEvent(LogUiEvent.OnDeleteEntry(it)) }
            pendingDeletionId = null
        },
        onDeleteDismiss = { pendingDeletionId = null },
        modifier = modifier,
    )
}
