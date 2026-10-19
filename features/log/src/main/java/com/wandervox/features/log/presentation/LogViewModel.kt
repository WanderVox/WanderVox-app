package com.wandervox.features.log.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.SyncStatus
import com.wandervox.core.domain.model.TravelLogEntry
import com.wandervox.core.domain.repository.AuthRepository
import com.wandervox.core.domain.repository.LogRepository
import com.wandervox.features.log.photo.LogPhotoStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class LogViewModel @Inject constructor(
    private val logRepository: LogRepository,
    authRepository: AuthRepository,
    private val photoStorage: LogPhotoStorage,
) : ViewModel() {

    /** Load state of the list behind the selected tab, before the category filter is applied. */
    private sealed interface EntriesLoad {
        data object Loading : EntriesLoad
        data class Loaded(val entries: List<TravelLogEntry>) : EntriesLoad
        data object Failed : EntriesLoad
    }

    private data class PresentationState(
        val selectedTab: LogTab = LogTab.MY_TRIPS,
        val selectedCategory: LogCategory? = null,
        /** Bumped by [LogUiEvent.OnRetry] to resubscribe to a source that failed. */
        val retryCount: Int = 0,
        val actionError: LogActionError? = null,
        val importingPhotoEntryId: String? = null,
    )

    private val presentationState = MutableStateFlow(PresentationState())

    private val currentUserId: Flow<String?> = authRepository.currentUser
        .map { it?.uid }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val entriesLoad: Flow<EntriesLoad> = combine(
        presentationState.map { it.selectedTab to it.retryCount }.distinctUntilChanged(),
        currentUserId,
    ) { (tab, _), userId -> tab to userId }
        .flatMapLatest { (tab, userId) -> entriesFor(tab, userId) }

    val uiState: StateFlow<LogUiState> = combine(
        presentationState,
        entriesLoad,
        currentUserId,
    ) { presentation, load, userId ->
        val entries = (load as? EntriesLoad.Loaded)?.entries.orEmpty()
        LogUiState(
            selectedTab = presentation.selectedTab,
            entries = presentation.selectedCategory
                ?.let { category -> entries.filter { it.category == category } }
                ?: entries,
            selectedCategory = presentation.selectedCategory,
            isLoading = load is EntriesLoad.Loading,
            isError = load is EntriesLoad.Failed,
            currentUserId = userId,
            actionError = presentation.actionError,
            importingPhotoEntryId = presentation.importingPhotoEntryId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = LogUiState(),
    )

    fun onEvent(event: LogUiEvent) {
        when (event) {
            is LogUiEvent.OnTabSelected -> presentationState.update { it.copy(selectedTab = event.tab) }
            is LogUiEvent.OnCategorySelected -> presentationState.update { it.copy(selectedCategory = event.category) }
            LogUiEvent.OnRetry -> presentationState.update { it.copy(retryCount = it.retryCount + 1) }
            is LogUiEvent.OnToggleLike -> toggleLike(event.entryId)
            is LogUiEvent.OnDeleteEntry -> deleteEntry(event.entryId)
            LogUiEvent.OnActionErrorShown -> presentationState.update { it.copy(actionError = null) }
            is LogUiEvent.OnPhotoPicked -> attachPhoto(event.entryId, event.sourceUri)
        }
    }

    private fun toggleLike(entryId: String) {
        // The UI only enables the like button for a signed-in user.
        val userId = uiState.value.currentUserId ?: return
        viewModelScope.launch {
            logRepository.toggleLike(entryId, userId)
                .onFailure { presentationState.update { it.copy(actionError = LogActionError.LIKE) } }
        }
    }

    private fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            val photoPath = logRepository.getEntry(entryId).first()?.localPhotoPath
            logRepository.deleteEntry(entryId)
                // The copy belongs to this entry only; a failed cleanup just leaves an orphan file.
                .onSuccess { photoPath?.let { photoStorage.deletePhoto(it) } }
                .onFailure { presentationState.update { it.copy(actionError = LogActionError.DELETE) } }
        }
    }

    private fun attachPhoto(entryId: String, sourceUri: String) {
        if (presentationState.value.importingPhotoEntryId != null) return
        presentationState.update { it.copy(importingPhotoEntryId = entryId) }
        viewModelScope.launch {
            val result = runCatching {
                val entry = checkNotNull(logRepository.getEntry(entryId).first()) { "Entry $entryId not found" }
                val newPath = photoStorage.importPhoto(sourceUri, entryId).getOrThrow()
                val updated = entry.copy(
                    localPhotoPath = newPath,
                    // The old upload no longer matches; Stage 5 uploads the new photo on the next sync.
                    remotePhotoUrl = null,
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = SyncStatus.PENDING,
                )
                logRepository.saveEntry(updated)
                    .onFailure { photoStorage.deletePhoto(newPath) }
                    .getOrThrow()
                entry.localPhotoPath?.let { photoStorage.deletePhoto(it) }
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            presentationState.update {
                it.copy(
                    importingPhotoEntryId = null,
                    actionError = if (result.isFailure) LogActionError.PHOTO else it.actionError,
                )
            }
        }
    }

    private fun entriesFor(tab: LogTab, userId: String?): Flow<EntriesLoad> {
        val source = when (tab) {
            // Signed-out users have no own entries; the session gate normally prevents this state.
            LogTab.MY_TRIPS -> userId?.let(logRepository::getUserEntries) ?: flowOf(emptyList())
            LogTab.COMMUNITY -> logRepository.getCommunityEntries()
        }
        return source
            .map<List<TravelLogEntry>, EntriesLoad> { EntriesLoad.Loaded(it) }
            .onStart { emit(EntriesLoad.Loading) }
            .catch { emit(EntriesLoad.Failed) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
