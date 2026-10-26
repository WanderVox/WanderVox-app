package com.wandervox.features.log.presentation

import com.wandervox.core.domain.model.LogCategory
import com.wandervox.core.domain.model.TravelLogEntry

/** The two lists the Travel Log shows: the user's own entries and the public Community feed. */
enum class LogTab { MY_TRIPS, COMMUNITY }

/** A one-off action on an entry that failed; the UI shows it once and reports it consumed. */
enum class LogActionError { LIKE, DELETE, PHOTO, VISIBILITY }

/** Single, immutable snapshot of everything the Travel Log screen renders. */
data class LogUiState(
    val selectedTab: LogTab = LogTab.MY_TRIPS,
    /** Entries of [selectedTab], already filtered by [selectedCategory]. */
    val entries: List<TravelLogEntry> = emptyList(),
    /** `null` means "all categories". */
    val selectedCategory: LogCategory? = null,
    val isLoading: Boolean = true,
    val isError: Boolean = false,
    /** Used to tell own entries apart and to render the like state; `null` while signed out. */
    val currentUserId: String? = null,
    val actionError: LogActionError? = null,
    /** Entry whose picked photo is being copied into the app sandbox, if any. */
    val importingPhotoEntryId: String? = null,
)
