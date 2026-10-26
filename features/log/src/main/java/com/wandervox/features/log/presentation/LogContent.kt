package com.wandervox.features.log.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.SyncStatus
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.ui.theme.WanderVoxTheme
import com.wandervox.features.log.presentation.components.CategoryFilterRow
import com.wandervox.features.log.presentation.components.DeleteEntryDialog
import com.wandervox.features.log.presentation.components.LogEntryCard
import com.wandervox.features.log.presentation.components.LogPlaceholder
import com.wandervox.features.log.presentation.components.ShareEntryDialog
import com.wandervox.features.log.presentation.components.labelRes
import com.wandervox.log.R

/**
 * Stateless Travel Log UI: the "Mis viajes" / "Comunidad" tabs, the category filter and the list of
 * the selected tab, plus the delete confirmation for [pendingDeletionId] and the share confirmation
 * for [pendingShareId].
 */
@Composable
fun LogContent(
    uiState: LogUiState,
    snackbarHostState: SnackbarHostState,
    pendingDeletionId: String?,
    pendingShareId: String?,
    onTabSelected: (LogTab) -> Unit,
    onCategorySelected: (LogCategory?) -> Unit,
    onRetry: () -> Unit,
    onEntryClick: (entryId: String) -> Unit,
    onToggleLike: (entryId: String) -> Unit,
    onShareRequest: (entryId: String) -> Unit,
    onMakePrivate: (entryId: String) -> Unit,
    onDeleteRequest: (entryId: String) -> Unit,
    onPickPhoto: (entryId: String) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onShareConfirm: () -> Unit,
    onShareDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                LogTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == uiState.selectedTab,
                        onClick = { onTabSelected(tab) },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }

            CategoryFilterRow(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = onCategorySelected,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LogBody(
                    uiState = uiState,
                    onCategorySelected = onCategorySelected,
                    onRetry = onRetry,
                    onEntryClick = onEntryClick,
                    onToggleLike = onToggleLike,
                    onShareRequest = onShareRequest,
                    onMakePrivate = onMakePrivate,
                    onDeleteRequest = onDeleteRequest,
                    onPickPhoto = onPickPhoto,
                )
            }
        }

        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (pendingDeletionId != null) {
        DeleteEntryDialog(onConfirm = onDeleteConfirm, onDismiss = onDeleteDismiss)
    }
    if (pendingShareId != null) {
        ShareEntryDialog(onConfirm = onShareConfirm, onDismiss = onShareDismiss)
    }
}

@Composable
private fun LogBody(
    uiState: LogUiState,
    onCategorySelected: (LogCategory?) -> Unit,
    onRetry: () -> Unit,
    onEntryClick: (entryId: String) -> Unit,
    onToggleLike: (entryId: String) -> Unit,
    onShareRequest: (entryId: String) -> Unit,
    onMakePrivate: (entryId: String) -> Unit,
    onDeleteRequest: (entryId: String) -> Unit,
    onPickPhoto: (entryId: String) -> Unit,
) {
    when {
        uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        uiState.isError -> LogPlaceholder(
            icon = Icons.Outlined.CloudOff,
            text = stringResource(R.string.log_error_loading),
            actionLabel = stringResource(R.string.log_retry),
            onAction = onRetry,
        )
        uiState.entries.isEmpty() && uiState.selectedCategory != null -> LogPlaceholder(
            icon = Icons.Outlined.FilterAltOff,
            text = stringResource(R.string.log_empty_filtered, stringResource(uiState.selectedCategory.labelRes)),
            actionLabel = stringResource(R.string.log_clear_filter),
            onAction = { onCategorySelected(null) },
        )
        uiState.entries.isEmpty() -> LogPlaceholder(
            icon = uiState.selectedTab.emptyIcon,
            text = stringResource(uiState.selectedTab.emptyMessageRes),
        )
        else -> {
            val isMyTrips = uiState.selectedTab == LogTab.MY_TRIPS
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(uiState.entries, key = { it.id }) { entry ->
                    val isOwnEntry = entry.userId.isNotBlank() && entry.userId == uiState.currentUserId
                    LogEntryCard(
                        entry = entry,
                        currentUserId = uiState.currentUserId,
                        showAuthor = !isMyTrips,
                        showSyncStatus = isMyTrips,
                        onToggleLike = { onToggleLike(entry.id) },
                        isImportingPhoto = entry.id == uiState.importingPhotoEntryId,
                        onClick = { onEntryClick(entry.id) },
                        onToggleVisibility = if (isMyTrips && isOwnEntry) {
                            { if (entry.isPublic) onMakePrivate(entry.id) else onShareRequest(entry.id) }
                        } else {
                            null
                        },
                        onPickPhoto = if (isMyTrips && isOwnEntry) ({ onPickPhoto(entry.id) }) else null,
                        onDelete = if (isMyTrips && isOwnEntry) ({ onDeleteRequest(entry.id) }) else null,
                    )
                }
            }
        }
    }
}

private val LogTab.labelRes: Int
    get() = when (this) {
        LogTab.MY_TRIPS -> R.string.log_tab_my_trips
        LogTab.COMMUNITY -> R.string.log_tab_community
    }

private val LogTab.emptyMessageRes: Int
    get() = when (this) {
        LogTab.MY_TRIPS -> R.string.log_empty_my_trips
        LogTab.COMMUNITY -> R.string.log_empty_community
    }

private val LogTab.emptyIcon
    get() = when (this) {
        LogTab.MY_TRIPS -> Icons.AutoMirrored.Outlined.MenuBook
        LogTab.COMMUNITY -> Icons.Outlined.Public
    }

@Preview(name = "Mis viajes", showBackground = true)
@Composable
private fun LogContentMyTripsPreview() {
    LogContentPreview(
        LogUiState(
            isLoading = false,
            currentUserId = "me",
            entries = listOf(
                TravelLogEntry(
                    id = "1",
                    userId = "me",
                    originalText = "¿Dónde está la estación de tren?",
                    translatedText = "Where is the train station?",
                    category = LogCategory.TRANSPORT,
                    syncStatus = SyncStatus.SYNCED,
                ),
                TravelLogEntry(
                    id = "2",
                    userId = "me",
                    originalText = "Una mesa para dos, por favor",
                    translatedText = "A table for two, please",
                    notes = "Restaurante frente al puerto.",
                    category = LogCategory.FOOD,
                    isPublic = true,
                    likes = listOf("other"),
                ),
            ),
        )
    )
}

@Preview(name = "Comunidad", showBackground = true)
@Composable
private fun LogContentCommunityPreview() {
    LogContentPreview(
        LogUiState(
            selectedTab = LogTab.COMMUNITY,
            isLoading = false,
            currentUserId = "me",
            entries = listOf(
                TravelLogEntry(
                    id = "3",
                    userId = "other",
                    originalText = "¿Cuánto cuesta el boleto?",
                    translatedText = "How much is the ticket?",
                    category = LogCategory.TRANSPORT,
                    isPublic = true,
                    likes = listOf("me", "x"),
                    authorName = "Ana",
                ),
            ),
        )
    )
}

@Preview(name = "Filtro sin resultados", showBackground = true)
@Composable
private fun LogContentFilteredEmptyPreview() {
    LogContentPreview(LogUiState(isLoading = false, selectedCategory = LogCategory.EMERGENCY))
}

@Preview(name = "Error", showBackground = true)
@Composable
private fun LogContentErrorPreview() {
    LogContentPreview(LogUiState(isLoading = false, isError = true))
}

@Composable
private fun LogContentPreview(uiState: LogUiState) {
    WanderVoxTheme {
        LogContent(
            uiState = uiState,
            snackbarHostState = remember { SnackbarHostState() },
            pendingDeletionId = null,
            pendingShareId = null,
            onTabSelected = {},
            onCategorySelected = {},
            onRetry = {},
            onEntryClick = {},
            onToggleLike = {},
            onShareRequest = {},
            onMakePrivate = {},
            onDeleteRequest = {},
            onPickPhoto = {},
            onDeleteConfirm = {},
            onDeleteDismiss = {},
            onShareConfirm = {},
            onShareDismiss = {},
        )
    }
}
